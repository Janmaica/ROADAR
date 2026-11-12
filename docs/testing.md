# Testing and verification

## Automated commands

From the repository root:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:compileDebugAndroidTestKotlin
.\gradlew.bat :app:connectedDebugAndroidTest
```

The connected tests require an authorized Android device or emulator. Model-inference tests also require the ignored ONNX model and held-out image assets.
Run `.\tools\verify-model.ps1` after placing the authorized model asset locally. For source-only verification, temporarily keep the model absent; inference-specific instrumentation is skipped and the Detection screen must show the unavailable-model state.

## Phase 4 coverage

- `LocationFixValidatorTest` checks valid and invalid coordinates, UTC/monotonic age, and poor accuracy. It does not call Android's permission UI or the fused provider.
- `ManualPotholeReportFactoryTest` checks report creation from a detection result and device fix, required fields/mode/status, and stale/future detection rejection. Test values are synthetic and are not production reports.
- `PotholeReportDaoTest` uses a temporary file-backed Room database to cover insert, ordered query/observation, enum/timestamp round trips, synchronization-status update, deletion, and persistence after database close/reopen.
- `ReportsScreenTest` injects an isolated in-memory test repository to verify a stored report's details, confirmed deletion, and report-ID map navigation. Its synthetic test record is not added to the app database.
- `MapScreenTest` verifies selection and observed removal of a local test row without a basemap key. It does not exercise rendered MapLibre markers or remote tiles.
- `InferenceMetricsAccumulatorTest` verifies latency mean/min/max/median/p95, bounded rolling samples, session frame/detection counts, invalid measurement rejection, and processing-only rate estimation using synthetic durations. The displayed estimate excludes callback gaps and is not camera FPS.
- `RoadarNavigationTest` visits the app destinations, verifies either the configured map container or the missing-key fallback, and verifies that Detection exposes only manual reporting.
- `CameraPreviewTest` grants camera permission, waits for streaming, navigates away and back, and waits for the preview to resume.
- `PotholeInferenceInstrumentedTest` exercises actual model inference on the ignored held-out Stage 4 image when assets are present.

## Latest verification — 2026-11-12

On the connected Vivo V2430 running Android 15 (API 35):

- `:app:testDebugUnitTest`, `:app:connectedDebugAndroidTest`, and `:app:assembleDebug` passed using Android Studio's bundled JDK 21. Results: 12 JVM tests passed; all 12 connected tests passed with 0 failures and 0 skips.
- Connected coverage included actual held-out model inference, Room persistence, isolated report and map-list selection/deletion, camera streaming/re-entry, navigation, and the manual-only Detection screen. The reports/markers used isolated test data; the connected suite did not create or delete production records.
- The final APK was installed using `adb install -r` without clearing app data and launched on the Vivo. `MainActivity` was resumed, and no app fatal exception appeared in the inspected recent logcat buffer.
- In an earlier device session, MapTiler Streets v4 rendered and a pan gesture was visibly verified. The developer reports previously verifying manual report creation, marker display, and marker deletion; these interactions were not repeated in this run. Pinch zoom and current-location centering remain unverified.
- The local model checksum script passed. `local.properties` and the ONNX asset remain ignored and untracked; the key was not found in tracked files, and no files were staged.
- No Room schema change was made. Existing schema-v1 records remain compatible, including the retained legacy reporting-mode enum value.
- No controlled inference benchmark or airplane-mode test was performed. The metrics shown are exploratory session measurements, not controlled benchmark results; see the repeatable procedure below.
- Source-only verification was also run with the ignored ONNX asset temporarily absent and restored afterward: `:app:assembleDebug`, `:app:testDebugUnitTest`, and `:app:connectedDebugAndroidTest` passed; the inference-specific instrumentation test skipped as expected, and the camera test verified the explicit missing-model message. The generated APK was confirmed not to contain `assets/roadar_yolo26n.onnx`.
- The database schema was generated and exported as `app/schemas/com.roadar.app.data.report.RoadarDatabase/1.json`.
- Debug APK was produced at `app/build/outputs/apk/debug/app-debug.apk`.

This verification did **not** repeat the complete production manual-report flow with a fresh location fix, permission denial/approximate-vs-precise cases, GPS disabled, airplane mode, or marker interaction against existing production records. The developer reports that manual report creation, map markers, and marker deletion were physically verified in an earlier session; the current automated tests use isolated records. Room's file-backed instrumentation test verifies persistence across database close/reopen.

## Manual reporting verification still required

On a test device with the ignored local model asset installed:

1. Open Detection and verify actual Stage 4 detections appear; do not infer a report from empty or placeholder UI.
2. Tap **Save Report** for a visible detection and confirm the confidence and device-position explanation in the dialog.
3. Confirm the report. Grant only foreground approximate or precise location when prompted.
4. Verify the app rejects unavailable, stale, invalid, or insufficient-accuracy fixes with a retry path and never substitutes `0,0`.
5. On successful insertion, verify the success message appears only after save and that the report appears in Reports with confidence, event/creation/fix times, device coordinates, accuracy, model, and sync status.
6. Navigate away and reopen Reports; verify the record remains. Repeat with airplane mode enabled to test local database access without network.
7. Test permission denial, app-settings recovery, and a deliberately unavailable location service. No background location should be requested or collected.

The stored point is the phone's position associated with the detection event, not a surveyed pothole coordinate. Motion, camera direction, inference duration, and GPS error can produce displacement.

## Physical-device verification still required

### Interactive online map

1. Follow [development setup](development-setup.md) to create a MapTiler Maps API key, restrict it to the `ROADAR-Android` User-Agent substring, and add it to the ignored root `local.properties`. For real detection, obtain the authorized private model asset and verify it using [model asset instructions](model-assets.md).
2. Build/install the debug APK on a device with internet access. Confirm the MapTiler Streets map loads and both attribution links remain visible.
3. Use existing saved reports (do not create fabricated production records). Confirm one marker per saved report, zoom/pan, select a marker, and compare details with Room/Reports.
4. From Reports, use **Map** for a non-first report and verify that the map selects and centers that report. Delete a report from Reports and verify its marker/list item disappears without restarting the map.
5. Tap **Center on device**, test denial and approximate/precise permission outcomes, and confirm the map uses a recent fix only after the user action.
6. Disable network while viewing the map. Verify the basemap is marked unavailable when tile requests fail and local report details remain accessible. This tests offline data access, not an offline basemap.

### Manual reporting and offline validation

1. Install the current build without clearing app data and open Detection with the verified local Stage 4 model.
2. Confirm bounding boxes, confidence, and inference metrics appear for actual detections. Tap **Save Report**, inspect the confidence and device-position explanation, then confirm.
3. Test both granted approximate/precise location and denied permission. Confirm denial, disabled location services, unavailable/stale fixes, and poor accuracy produce explicit feedback and never create a report.
4. With a valid fix, verify the success message appears only after save; check the report’s coordinates, accuracy, confidence, and event/creation/fix timestamps in Reports. Open its map details/marker, then delete it and verify observed removal. Use only real detections and existing data, not fabricated production records.
5. Restart the app without clearing data and verify the report remains. Repeat report-list/detail checks with network disabled; separately confirm the online basemap becomes unavailable while local report details remain accessible. A GPS fix may not be available offline on every device.
6. Confirm CameraX preview and inference recover after leaving/re-entering Detection and after background/resume. Do not claim airplane-mode behavior unless this test is actually run.

Automatic reporting and the experimental tracker are removed. New records are manual only. The Room schema retains its legacy automatic reporting-mode enum value solely to preserve compatibility with any previously stored schema-v1 rows; the app performs no automatic location requests or inserts.

### Reproducible physical-device inference benchmark

Target device: Vivo V2430, Android 15 (API 35). Use the same APK, local model file/checksum, camera, scene, lighting, and device power/thermal conditions for each trial. Keep the phone stationary and do not operate ROADAR while driving.

1. Verify the local ONNX asset with `.\tools\verify-model.ps1`; record the APK build/commit, model SHA-256, device/OS, and CPU execution provider.
2. Start Detection and let the model warm up for 60 seconds. Keep the same static test scene and camera framing throughout.
3. Run three 60-second measurement trials, returning to a fresh Detection session between trials. At each trial end, record model-load time, observed processed FPS, rolling inference mean/min/max/median/p95, average pipeline latency, processing-only rate estimate, processed frames, and detection count from the UI.
4. Preserve each trial separately; report per-trial values and the median across trials. Note interruptions, thermal throttling, permission changes, or model errors. Do not invent missing values.

The UI's observed processed FPS is completed inference throughput over elapsed sampling windows, not camera capture FPS. The processing-only rate estimate divides frames by their measured processing duration and excludes callback gaps; it is not end-to-end throughput. No controlled benchmark result is currently recorded.

## Synchronization status

The research objectives mention exploring synchronization when connectivity returns. No authorized server, endpoint, credentials, or idempotent upload contract is configured in ROADAR, so synchronization is blocked and not implemented. Reports remain local and are not deleted or changed because connectivity changes. Supply the approved backend/API contract before implementing uploads, retries, or WorkManager.

### Map provider research and offline boundaries

References checked during provider research:

- [MapLibre Android quickstart](https://maplibre.org/maplibre-native/android/examples/getting-started/) and [artifacts](https://maplibre.org/maplibre-native/android/examples/artifacts/); the Android SDK is BSD 2-Clause licensed and includes offline region APIs in its source.
- [MapTiler Maps API](https://docs.maptiler.com/cloud/api/maps/) permits documented style display subject to account terms/plan; [mobile API-key guidance](https://docs.maptiler.com/guides/developer/credentials/api-key/) recommends User-Agent restrictions for native apps.
- [MapTiler attribution](https://www.maptiler.com/copyright/) requires visible `© MapTiler` and `© OpenStreetMap contributors` credits with links.
- [OpenStreetMap tile usage policy](https://operations.osmfoundation.org/policies/tiles/) prohibits bulk downloads and prefetching on its public tile service; OSM data licensing does not grant offline rights to that service.
- [MapTiler Cloud terms](https://www.maptiler.com/terms/cloud/) permit end-user device caching for one user but restrict export and bulk downloads absent a separate agreement.

ROADAR's current MapTiler integration is online-only and does not invoke MapLibre offline-region APIs. Offline Room reports and online-map availability are separate features.

## Phase 3 model verification notes

The selected Stage 4 checkpoint was compared with its fixed-shape FP32 ONNX export on a 788-image held-out split (1,598 boxes). Previously reported PyTorch metrics were precision 0.830, recall 0.785, mAP50 0.853, and mAP50-95 0.481; raw ONNX metrics were 0.851, 0.772, 0.851, and 0.476. Paired prediction comparison reported 20,517 greedy box matches, mean IoU 0.967, 20,067 matches at IoU >= 0.5, and mean confidence absolute error 0.00113.

An isolated early CPU sample on the tested phone reported about 1,354 ms model load, 416 ms inference, 539 ms processing latency, and 1.9 processed inferences/second. These approximate measurements are not a robust benchmark. The final repeated performance run was interrupted; broader field/device evaluation remains outstanding. NNAPI session creation failed on the tested device, so the app uses CPU inference. Do not treat the prototype as suitable for road-speed operation.
