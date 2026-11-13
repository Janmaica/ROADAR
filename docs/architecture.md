# ROADAR architecture

## Current implementation

ROADAR is a single Android app module with one `ComponentActivity` and a Jetpack Compose UI.

- `MainActivity` applies `ROADARTheme` and hosts `RoadarApp`.
- `RoadarApp` owns top-level screen selection, bottom navigation, system-back behavior, the connectivity indicator, and app-scoped report/location providers.
- `ui/screens/` contains the home, detection, reports, map, and settings screens.
- `ui/camera/` contains the lifecycle-bound CameraX Preview/ImageAnalysis controller, `PreviewView` host, and detection overlay.
- `ai/` contains the ONNX Runtime adapter, camera preprocessor, output decoder/NMS, and detection session.
- `data/report/` contains the Room entity, DAO, repository, database, and manual-report factory.
- `data/sync/` contains the anonymous Firebase identity provider, Firestore adapter, sync coordinator, Room outbox adapter, and WorkManager scheduler/worker.
- `location/` contains the fused-device-location provider and location-fix validation.
- `app/schemas/com.roadar.app.data.report.RoadarDatabase/` contains exported Room schemas v1 through v3.

The app does not use a navigation library or ViewModels. Existing top-level screen state remains local Compose state; repositories expose `Flow` and suspend APIs, and Room performs database work asynchronously on its executors/IO dispatcher. A ViewModel layer can be introduced when state needs to survive beyond screen composition.

## Detection and manual reporting

Camera permission is requested to enable the preview. CameraX Preview and ImageAnalysis bind to the activity lifecycle; analysis uses latest-frame backpressure and a single background executor. Every `ImageProxy` is closed in `finally`.

The detector loads the separately supplied local `roadar_yolo26n.onnx` asset; the binary is ignored by Git and is not distributed in this source repository. Without it, the camera preview remains available, but the analyzer reports an unavailable-model error and produces no detections, so detection-based manual reports cannot be created. Its fixed input is `[1, 3, 640, 640]` float32 RGB and raw output is `[1, 5, 8400]`; NMS is not embedded. Camera frames are cropped, rotated, converted to RGB, letterboxed to 640×640, normalized, and passed to ONNX Runtime CPU inference. Detections carry confidence and image-space bounds. `detectionAtUtcMillis` is sampled when the analyzer invokes inference; it is an event-time approximation, not the camera sensor's exact capture timestamp. See [model asset distribution](model-assets.md) for its pinned SHA-256 and licensing status.

Manual report creation is gated on an actual non-empty model result that is no more than 15 seconds old. The user selects a detection and confirms it before ROADAR requests location permission or starts a one-shot location request. A successful Room insert is the only point at which the UI reports that a report was saved. A pending submission prevents repeated taps during location acquisition/database insertion.

The reported coordinates are the **device position associated with a detection event**, not a surveyed pothole coordinate. A moving phone, camera direction, model latency, and GPS error may offset the physical pothole location. Reports are not inferred from placeholder UI or pre-populated production data.

## Room report storage

Room database: `roadar-reports.db`; schema version 3. The entity `PotholeReport` stores:

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
| `syncEligible` | False for existing reports; true only for reports created while Firebase Sync is enabled |

Entity construction validates UUID, timestamps, coordinate ranges, accuracy, confidence, and model metadata. The DAO supports insert, ordered observation/query, lookup, delete, sync eligibility, and outbox operations. Repository suspend operations dispatch through `Dispatchers.IO`; observers are flows. Explicit v1→v2 and v2→v3 migrations preserve existing report data; there is no destructive migration fallback. Existing reports receive `syncEligible = false`, and reports created while sync is off remain local-only. The application creates manual reports only. The legacy enum value and converter remain available for historical rows.

The local database does not require network access by design. The v1→v2 migration adds a singleton Firebase-owner state table and durable deletion-outbox table/index without modifying or deleting report rows. The v2→v3 migration adds a per-report `syncEligible` flag with a default of false. Instrumentation migration tests verify a schema-v1 report remains present and ineligible after migration to v3, and that eligible deletion tombstones persist.

### Optional Firebase synchronization

Synchronization requires a configured `app/google-services.json` and the persistent Firebase Sync switch in Settings, which defaults OFF. Enabling requires confirmation. Existing reports stay ineligible; only reports inserted while the setting is ON can be uploaded. Startup scheduling and workers recheck the setting before Firebase initialization or remote operations, so OFF prevents authentication, uploads, and remote deletions. A configured build requires Firebase anonymous authentication and manually publishing the provided `firestore.rules`; rules are not deployed automatically. The application stores only the bound anonymous UID in the Room singleton state row and blocks if the Firebase identity is missing or changes after binding.

Eligible post-opt-in reports are uploaded under `users/{uid}/reports/{reportId}`, with the existing report UUID as the deterministic Firestore document ID. The payload is limited to report ID, latitude/longitude, confidence, detection/creation timestamps, and horizontal location accuracy. WorkManager waits for connectivity, retries transient failures with exponential backoff, and processes bounded batches. Room marks an upload `SYNCED` only after a server read-back. Permanent report-specific rejections mark that report `FAILED`; the Reports screen offers retry. Other local report fields remain on-device.

Deleting an eligible report is a Room transaction that removes the row and adds a durable tombstone. Tombstones are retained while sync is OFF and processed before eligible uploads when sync is re-enabled, preventing stale uploads from recreating a locally deleted cloud report. Deleting a local-only report does not create a remote deletion. Tombstones clear only after the remote delete is acknowledged and verified. The anonymous Firebase identity and remote data have no in-app account-recovery or account-deletion flow; developers must consider project retention and user-data policies before enabling cloud sync.

Firebase sync unit tests cover bounded batches, deterministic-ID retries after a simulated lost acknowledgment, rejected records, identity mismatch, and upload/delete races. Firestore Emulator rules tests run only against a `demo-` project and do not touch production data. No live Firebase project, production rules deployment, phone sync, or cloud-data migration has been tested.

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

Camera preview, model inference, and Room storage are local. When Firebase sync is enabled, report metadata is sent to the configured Firebase project as described above; no image, model, or image-reference is uploaded. Manual saving still requires a valid fresh device location; an offline GPS fix depends on the device and environment and is not guaranteed. Online basemap rendering requires internet and a user-provided MapTiler key. A key was configured only in the ignored local `local.properties` for device verification; no credential is stored in the repository. The Streets v4 basemap rendered on the connected phone and panning was verified. Basemap tiles are not available in airplane mode. Room reports remain stored and accessible offline in Reports and in the map screen's local report-detail list; this is offline report storage, not offline mapping. An end-to-end airplane-mode test has not been run.

## Future boundaries

1. Keep Compose screens focused on rendering and user actions; introduce screen ViewModels if lifecycle requirements grow.
2. Keep `PotholeReportRepository` as the boundary for report query/update use cases.
3. Configure and physically verify the online MapTiler map with an app-restricted key; select a separately licensed offline source only if offline basemaps become a requirement.
4. Before enabling Firebase for real users, verify project ownership/authorization, anonymous-auth policy, published rules, privacy notices, retention, recovery, and remote-account deletion requirements. Sync requires explicit in-app opt-in; account recovery and remote-account deletion flows are not implemented.
5. Evaluate inference, geolocation error, privacy, battery use, offline behavior, and field limitations on representative devices.

The architecture is a research prototype, not a road-safety or surveyed infrastructure system.
