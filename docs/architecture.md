# ROADAR architecture

## Current implementation

ROADAR is a single Android app module with one `ComponentActivity` and a Jetpack Compose UI.

- `MainActivity` applies `ROADARTheme` and hosts `RoadarApp`.
- `RoadarApp` owns top-level screen selection, bottom navigation, system-back behavior, the connectivity indicator, and app-scoped report/location providers.
- `ui/screens/` contains the home, detection, reports, map, and settings screens.
- `ui/camera/` contains the lifecycle-bound CameraX Preview/ImageAnalysis controller, `PreviewView` host, and detection overlay.
- `ai/` contains the ONNX Runtime adapter, camera preprocessor, output decoder/NMS, and detection session.
- `ai/PotholeDetectionTracker` contains the experimental, detector-independent IoU tracker.
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
| `reportingMode` | `MANUAL` or `AUTOMATIC`; automatic persistence is currently disabled |
| `synchronizationStatus` | `PENDING`, `SYNCED`, or `FAILED` |
| `imageReference` | Nullable; no image capture is implemented |

Entity construction validates UUID, timestamps, coordinate ranges, accuracy, confidence, and model metadata. The DAO supports insert, ordered observation/query, lookup, delete, and sync-status update. Repository suspend operations dispatch through `Dispatchers.IO`; observers are flows. There is no destructive migration fallback. Version 1 is the initial schema; future schema changes must add and test explicit migrations before release.

The local database does not require network access by design. Instrumentation tests cover insert/query/observe/update/delete and persistence after closing/reopening a file-backed test database. A dedicated airplane-mode end-to-end app test has not been performed. No synchronization worker or backend is implemented.

## Foreground location

Only `ACCESS_COARSE_LOCATION` and `ACCESS_FINE_LOCATION` are declared. ROADAR requests them when the user confirms a manual report; it does not request background location or continuously track the device. Android may grant approximate location when both permissions are requested; that fix and its accuracy are stored as provided.

`FusedDeviceLocationProvider` makes one high-accuracy current-location request. A fix is rejected if its coordinates or timestamps are invalid, its monotonic or UTC age exceeds 30 seconds, accuracy is missing, or horizontal accuracy exceeds 1,000 m. A missing/stale/poor fix produces an explicit retry message; there is no default coordinate and no `0,0` fallback. These thresholds permit approximate permission while rejecting unusably poor results.

## Automatic reporting status

The Detection screen offers `MANUAL` (default) and `AUTOMATIC · EXPERIMENTAL`. Automatic mode runs a separate lightweight tracker over actual model boxes on the existing analyzer thread; it does not make inference wait for location or Room work. Default configurable thresholds are confidence >= 0.60, IoU >= 0.30, and three consecutive matching frames. Tracks are associated greedily one-to-one by descending IoU, tolerate one missed frame, expire after the next miss, and expose per-track confirmation/report state. Track IDs are local session IDs created by ROADAR; they are not supplied by YOLO26n. Unit tests cover confidence/box validation, multi-object association, confirmation, expiry/reacquisition, and once-per-track report marking.

Automatic persistence is deliberately disabled. Camera motion, perspective changes, occlusion, box overlap, and re-acquisition can split or switch tracks; a per-track duplicate guard cannot prevent a re-acquired physical pothole from receiving a new ID. GPS coordinates represent device position and their uncertainty does not reliably distinguish nearby potholes. Until field validation establishes acceptable tracking and duplicate behavior, ROADAR creates no automatic Room rows, makes no automatic GPS requests, and displays an automatic-save count of zero. The tracking toggle is session-scoped and the tracker is cleared when paused or the Detection session ends. This is not a real-world tracking-reliability claim.

## Map and offline-tile status

The Map screen embeds MapLibre Android 11.8.0, compatible with the project's Kotlin 2.0.21 toolchain and Android API 26 minimum. It loads MapTiler's documented `streets-v4` online style, with a restricted key read from the untracked root `local.properties` and exposed as a BuildConfig client value. MapTiler's key guidance recommends securing native-app keys with an allowed User-Agent substring; ROADAR sets `ROADAR-Android/<app-version>` on MapLibre's OkHttp requests. This key is necessarily extractable from a distributed mobile app, so restrict its use/quota and rotate it if compromised; never commit it.

The map observes `PotholeReportRepository.observeReports()` and creates/removes one marker per Room row. Marker selection shows mode, confidence, device coordinates, and accuracy. Reports can navigate to and center the map on their ID. User centering requests foreground approximate/precise permission only after a tap, obtains a one-shot validated fix, and does not start continuous tracking. Visible MapTiler and OpenStreetMap attribution links are displayed. A missing key or map request failure has an explicit unavailable state; local report details remain available.

MapLibre Native Android is BSD-2-Clause-licensed and exposes offline region APIs, but is a renderer rather than a data provider. MapTiler's documented Maps API permits online display under its terms and subscription limits; its terms reviewed for this task prohibit bulk downloading/export without a separate agreement. The public OpenStreetMap tile service prohibits bulk downloads/prefetch. ROADAR performs no tile prefetch, download, or offline-region creation.

Online basemap rendering requires internet and a user-provided MapTiler key. A key was configured only in the ignored local `local.properties` for device verification; no credential is stored in the repository. The Streets v4 basemap rendered on the connected phone and panning was verified. Basemap tiles are not available in airplane mode. Room reports remain stored and accessible offline in Reports and in the map screen's local report-detail list; this is offline report storage, not offline mapping.

## Future boundaries

1. Keep Compose screens focused on rendering and user actions; introduce screen ViewModels if lifecycle requirements grow.
2. Keep inference isolated behind a detector interface before introducing reliable track identities.
3. Keep `PotholeReportRepository` as the boundary for new report query/update use cases.
4. Configure and physically verify the online MapTiler map with an app-restricted key; select a separately licensed offline source only if offline basemaps become a requirement.
5. Validate tracker thresholds, association, and duplicate handling using recorded/moving-camera field sequences before considering automatic report writes; design GPS sampling only for confirmed candidates.
6. Add WorkManager only after a backend, authentication, and idempotent upload contract exist.
7. Evaluate inference, geolocation error, privacy, battery use, offline behavior, and field limitations on representative devices.

The architecture is a research prototype, not a road-safety or surveyed infrastructure system.
