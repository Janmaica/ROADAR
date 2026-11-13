# Testing and verification

## Automated commands

From the repository root:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:compileDebugAndroidTestKotlin
.\gradlew.bat :app:connectedDebugAndroidTest
npm ci
npm run test:firestore-rules
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

## Latest verification — 2026-11-13

- Android Studio's bundled JDK 21 was used. `:app:kspDebugKotlin` passed and exported Room schemas v2 and v3 after the sync entities were made top-level Room entities.
- A `:app:compileDebugKotlin` run with the local Firebase config temporarily absent passed; the original ignored config was restored, the configured `FIREBASE_CONFIGURED` flag was checked without printing values, and the final configured APK was rebuilt.
- `:app:testDebugUnitTest` passed. The full `:app:connectedDebugAndroidTest` suite passed all 16 tests on the Vivo V2430, including the v1→v2→v3 Room migration and the default-off/confirmation Settings test. A first full run stalled after the local Android test harness lost its gRPC connection; rerunning the complete suite passed.
- `npm run test:firestore-rules` passed all 6 tests against the local Firestore Emulator with the `demo-roadar-rules` project. The first attempt failed because the shell selected an older Java runtime; rerunning with the bundled JDK 21 passed. No Firebase production project was contacted and rules were not deployed.
- `:app:assembleDebug` passed and produced `app/build/outputs/apk/debug/app-debug.apk`.
- Before installation, the Vivo had no Firebase Sync preference file, so the setting defaulted OFF. Source and migration checks confirm workers exit before Firebase initialization while OFF and all preexisting reports migrate as ineligible. The APK was installed with `adb install -r` (no app data was cleared), launched with `monkey`, and the app process remained running; no startup crash/ANR markers were found. Sync remained OFF/unset afterward.
- No live Firebase synchronization, production Firestore writes, or rules deployment was attempted.
- Existing previously recorded physical results remain historical: an earlier connected session reported CameraX, MapTiler rendering, panning, app install/launch, and 12 Android tests; those results were not repeated for this sync change. Pinch zoom, map location centering, and production Firebase behavior remain unverified.
- Do not infer field reliability or offline basemap support from these tests. Room remains the local source of truth; cloud sync behavior with a real Firebase project is not verified.

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

## Firebase synchronization verification and safety

The implementation has focused JVM tests for bounded batches, deterministic-ID retries after a simulated lost acknowledgment, permanent report rejection, identity mismatch, and delete/upload ordering. Firestore rules have six emulator cases for owner access, unauthenticated/other-owner denial, schema validation, and immutable report contents. These are local tests and do not verify deployed rules, Firebase Auth setup, quotas, or real network retries.

Before any real-project test:

1. Use a Firebase project explicitly authorized for ROADAR testing. Enable anonymous authentication and review/deploy `firestore.rules` to that project manually.
2. Confirm you intend to upload every locally pending report. On app entry, ROADAR schedules synchronization when the local Firebase configuration is present; report data includes UUID, latitude/longitude, confidence, detection/creation timestamps, and horizontal accuracy.
3. Use a dedicated test install or ensure the report set contains only approved test data. Do not clear application data to resolve identity mismatch; the implementation intentionally blocks changed/missing identities.
4. Verify successful upload, offline retry, deletion tombstone processing, and persistence with only test reports. Confirm the corresponding owner-scoped documents in the Firebase console, then test deletion and check that the remote document is gone.
5. Do not call the sync a successful production test unless it was verified against the intended project/device. No production operations were performed during the current verification.

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
