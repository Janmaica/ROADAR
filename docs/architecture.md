# ROADAR architecture

## Current implementation

ROADAR is a single Android app module with one `ComponentActivity` and a Jetpack Compose UI.

- `MainActivity` applies `ROADARTheme` and hosts `RoadarApp`.
- `RoadarApp` owns top-level screen selection, bottom navigation, system-back behavior, the connectivity indicator, and app-scoped report/location providers.
- `ui/screens/` contains the home, detection, reports, map, and settings screens.
- `ui/camera/` contains the lifecycle-bound CameraX Preview/ImageAnalysis controller, `PreviewView` host, and detection overlay.
- `ai/` contains the ONNX Runtime adapter, camera preprocessor, output decoder/NMS, and detection session.
- `data/report/` contains the Room entity, DAO, repository, database, and manual-report factory.
- `location/` contains the fused-device-location provider and location-fix validation.
- `app/schemas/com.roadar.app.data.report.RoadarDatabase/1.json` is the exported Room schema.

The app does not use a navigation library or ViewModels. Existing top-level screen state remains local Compose state; repositories expose `Flow` and suspend APIs, and Room performs database work asynchronously on its executors/IO dispatcher. A ViewModel layer can be introduced when state needs to survive beyond screen composition.

## Detection and manual reporting

Camera permission is requested to enable the preview. CameraX Preview and ImageAnalysis bind to the activity lifecycle; analysis uses latest-frame backpressure and a single background executor. Every `ImageProxy` is closed in `finally`.

The detector loads the separately supplied local `roadar_yolo26n.onnx` asset; the binary is ignored by Git and is not distributed in this source repository. Without it, the camera preview remains available, but the analyzer reports an unavailable-model error and produces no detections, so detection-based manual reports cannot be created. Its fixed input is `[1, 3, 640, 640]` float32 RGB and raw output is `[1, 5, 8400]`; NMS is not embedded. Camera frames are cropped, rotated, converted to RGB, letterboxed to 640×640, normalized, and passed to ONNX Runtime CPU inference. Detections carry confidence and image-space bounds. `detectionAtUtcMillis` is sampled when the analyzer invokes inference; it is an event-time approximation, not the camera sensor's exact capture timestamp. See [model asset distribution](model-assets.md) for its pinned SHA-256 and licensing status.

Manual report creation is gated on an actual non-empty model result that is no more than 15 seconds old. The user selects a detection and confirms it before ROADAR requests location permission or starts a one-shot location request. A successful Room insert is the only point at which the UI reports that a report was saved. A pending submission prevents repeated taps during location acquisition/database insertion.

The reported coordinates are the **device position associated with a detection event**, not a surveyed pothole coordinate. A moving phone, camera direction, model latency, and GPS error may offset the physical pothole location. Reports are not inferred from placeholder UI or pre-populated production data.

## Room report storage

Room database: `roadar-reports.db`; schema version 1. The entity `PotholeReport` stores:

| Field | Meaning |
|---|---|
| `id` | UUID primary key |
| `createdAtUtcMillis` | UTC epoch milliseconds when the report is created |
| `detectionAtUtcMillis` | UTC epoch milliseconds sampled at inference entry |
| `latitude`, `longitude` | Validated device coordinates associated with the event |
| `horizontalAccuracyMeters` | Fused location provider's reported accuracy |
| `locationFixAtUtcMillis` | UTC epoch time of the device location fix |
| `locationSource` | `DEVICE_LOCATION` |
| `detectionConfidence` | Confidence from the selected actual YOLO26n result |
| `modelIdentifier`, `modelVersion` | `ROADAR-YOLO26n`, `Stage 4` |
| `reportingMode` | `MANUAL` for new reports; the legacy `AUTOMATIC` enum value remains readable for existing schema-v1 rows |
| `synchronizationStatus` | `PENDING`, `SYNCED`, or `FAILED` |
| `imageReference` | Nullable; no image capture is implemented |

Entity construction validates UUID, timestamps, coordinate ranges, accuracy, confidence, and model metadata. The DAO supports insert, ordered observation/query, lookup, delete, and sync-status update. Repository suspend operations dispatch through `Dispatchers.IO`; observers are flows. There is no destructive migration fallback. Version 1 is the initial schema; future schema changes must add and test explicit migrations before release. The application creates manual reports only. The old enum value and converter are retained to read historical rows without changing the schema or invalidating saved data.

The local database does not require network access by design. Instrumentation tests cover insert/query/observe/update/delete and persistence after closing/reopening a file-backed test database. A dedicated airplane-mode end-to-end app test has not been performed. No synchronization worker or backend is implemented.

## Foreground location

Only `ACCESS_COARSE_LOCATION` and `ACCESS_FINE_LOCATION` are declared. ROADAR requests them when the user confirms a manual report; it does not request background location or continuously track the device. Android may grant approximate location when both permissions are requested; that fix and its accuracy are stored as provided.

`FusedDeviceLocationProvider` makes one high-accuracy current-location request. A fix is rejected if its coordinates or timestamps are invalid, its monotonic or UTC age exceeds 30 seconds, accuracy is missing, or horizontal accuracy exceeds 1,000 m. A missing/stale/poor fix produces an explicit retry message; there is no default coordinate and no `0,0` fallback. These thresholds permit approximate permission while rejecting unusably poor results.

## Manual reporting

Manual confirmation is the only report-creation workflow. The Detection screen offers **Save Report** only for a recent actual YOLO26n detection. The user reviews and confirms the detection, then grants foreground approximate or precise location if needed. ROADAR requests one fresh location fix, validates its coordinates, timestamps, and accuracy, constructs a UUID-keyed report, and writes it through the existing Room repository. It never substitutes default coordinates. Permission denial, disabled/unavailable location, stale fixes, and inaccurate fixes produce explicit feedback and do not create a report. Detection and local report storage do not require internet; GPS availability while offline depends on the device and environment.

Automatic reporting and its image-space tracking module have been removed. New records continue to store `MANUAL`. The legacy `AUTOMATIC` mode value remains in the Room converter/entity for schema-v1 compatibility with any previously saved report; no destructive migration or data rewrite is performed.

The Detection screen reports instantaneous per-frame inference/processing latency and a bounded rolling latency summary (mean, min, max, median, p95), cumulative processed-frame and per-frame detection counts, and a processing-only rate estimate calculated from measured processing durations. The estimate excludes callback gaps and is not camera FPS or end-to-end throughput. Model load is shown separately. These are exploratory session measurements, not controlled benchmark results.

## Map and offline-tile status

The Map screen embeds MapLibre Android 11.8.0, compatible with the project's Kotlin 2.0.21 toolchain and Android API 26 minimum. It loads MapTiler's documented `streets-v4` online style, with a restricted key read from the untracked root `local.properties` and exposed as a BuildConfig client value. MapTiler's key guidance recommends securing native-app keys with an allowed User-Agent substring; ROADAR sets `ROADAR-Android/<app-version>` on MapLibre's OkHttp requests. This key is necessarily extractable from a distributed mobile app, so restrict its use/quota and rotate it if compromised; never commit it.

The map observes `PotholeReportRepository.observeReports()` and creates/removes one marker per Room row. Marker selection shows confidence, device coordinates, and accuracy. Reports can navigate to and center the map on their ID. User centering requests foreground approximate/precise permission only after a tap, obtains a one-shot validated fix, and does not start continuous location tracking. Visible MapTiler and OpenStreetMap attribution links are displayed. A missing key or map request failure has an explicit unavailable state; local report details remain available.

MapLibre Native Android is BSD-2-Clause-licensed and exposes offline region APIs, but is a renderer rather than a data provider. MapTiler's documented Maps API permits online display under its terms and subscription limits; its terms reviewed for this task prohibit bulk downloading/export without a separate agreement. The public OpenStreetMap tile service prohibits bulk downloads/prefetch. ROADAR performs no tile prefetch, download, or offline-region creation.

Camera preview, model inference, and Room storage are local and do not call a remote inference or report service. Manual saving still requires a valid fresh device location; an offline GPS fix depends on the device and environment and is not guaranteed. Online basemap rendering requires internet and a user-provided MapTiler key. A key was configured only in the ignored local `local.properties` for device verification; no credential is stored in the repository. The Streets v4 basemap rendered on the connected phone and panning was verified. Basemap tiles are not available in airplane mode. Room reports remain stored and accessible offline in Reports and in the map screen's local report-detail list; this is offline report storage, not offline mapping. An end-to-end airplane-mode test has not been run.

## Synchronization and future boundaries

1. Keep Compose screens focused on rendering and user actions; introduce screen ViewModels if lifecycle requirements grow.
2. Keep `PotholeReportRepository` as the boundary for report query/update use cases.
3. Configure and physically verify the online MapTiler map with an app-restricted key; select a separately licensed offline source only if offline basemaps become a requirement.
4. Research objectives mention synchronization when connectivity returns, but no authorized backend, endpoint, credentials, or upload contract exists in this project. Synchronization is therefore blocked and is not implemented. Do not invent a destination; obtain the approved service details before designing retries or adding WorkManager.
5. Evaluate inference, geolocation error, privacy, battery use, offline behavior, and field limitations on representative devices.

The architecture is a research prototype, not a road-safety or surveyed infrastructure system.
