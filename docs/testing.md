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
- `PotholeDetectionTrackerTest` covers >=0.60 confidence filtering, malformed boxes, three consecutive-frame confirmation, reordered multiple-object association, track expiry/reacquisition, and once-per-track report marking. Fixtures are synthetic and run only in JVM tests.
- `RoadarNavigationTest` visits the app destinations, verifies either the configured map container or the missing-key fallback, and exercises automatic-mode UI pause/resume.
- `CameraPreviewTest` grants camera permission, waits for streaming, navigates away and back, and waits for the preview to resume.
- `PotholeInferenceInstrumentedTest` exercises actual model inference on the ignored held-out Stage 4 image when assets are present.

## Latest verification — 2026-11-12

On the connected Vivo V2430 running Android 15 (API 35):

- MapLibre 11.8.0 and the Compose map integration compiled, and `:app:assembleDebug` passed.
- The full connected suite passed (12 tests, 0 failures) on the connected Vivo V2430 running Android 15 (API 35), after making the navigation assertion respect whether the local key is configured. Coverage included Room persistence, local report selection/removal and report-to-map callback, report UI, automatic mode UI, real held-out model inference, camera streaming/re-entry, and navigation.
- `:app:testDebugUnitTest` passed, including the new tracker unit tests; `:app:compileDebugAndroidTestKotlin` passed.
- A key in ignored local configuration enabled the MapTiler Streets v4 basemap to render on the phone; a single-finger pan was visibly confirmed. This verifies style/tile rendering and panning, but not pinch zoom. Separately, the developer reports physically verifying that saved report markers appear and that deleting a report removes its marker. That manual verification was not repeated in this automated run; the production database contained zero reports, so live marker selection/deletion was not independently retested here. Existing instrumentation checks report-list observation/selection/removal using isolated test data, not a live rendered marker.
- The app was reinstalled with `adb install -r` (without clearing app data), launched successfully, and remained on its resumed activity through repeated navigation to and from Map. Fine and coarse location were not granted, so current-location centering was not exercised. End-to-end manual reporting with live detection, a fresh location fix, and a production Room insert remains unverified.
- `:app:assembleDebug` and `:app:testDebugUnitTest` passed using Android Studio's JDK 21. The literal requested Gradle target `:app` is not a task in this project; use the explicit task paths above.
- Source-only verification was also run with the ignored ONNX asset temporarily absent and restored afterward: `:app:assembleDebug`, `:app:testDebugUnitTest`, and `:app:connectedDebugAndroidTest` passed; the inference-specific instrumentation test skipped as expected, and the camera test verified the explicit missing-model message. The generated APK was confirmed not to contain `assets/roadar_yolo26n.onnx`.
- No Room schema change was needed; existing user reports and schema v1 are preserved.
- The database schema was generated and exported as `app/schemas/com.roadar.app.data.report.RoadarDatabase/1.json`.
- Debug APK was produced at `app/build/outputs/apk/debug/app-debug.apk`.

These automated checks do **not** verify the complete manual reporting flow with a live camera detection, user-granted location permission, a real fresh location fix, successful insertion into the app's production database, and subsequent display in Reports. Location permission denial/approximate-vs-precise behavior and actual location-provider failures also remain to be tested manually. Room's device tests use an isolated file-backed test DB; a dedicated airplane-mode app test has not been run.

## Manual reporting verification still required

On a test device with the ignored local model asset installed:

1. Open Detection and verify actual Stage 4 detections appear; do not infer a report from empty or placeholder UI.
2. Select **Review** for a visible detection and confirm the confidence displayed.
3. Confirm the report. Grant only foreground approximate or precise location when prompted.
4. Verify the app rejects unavailable, stale, invalid, or insufficient-accuracy fixes with a retry path and never substitutes `0,0`.
5. On successful insertion, verify the success message appears only after save and that the report appears in Reports with confidence, event/creation/fix times, device coordinates, accuracy, mode, model, and sync status.
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

### Experimental automatic tracking

1. Enter Detection with the real local Stage 4 model available. Start in `MANUAL`; verify manual review/confirmation still behaves as before.
2. Choose `AUTOMATIC · EXPERIMENTAL`. Confirm the UI explains that persistence is disabled and GPS is not requested. Let actual camera detections flow and inspect confidence, active/confirmed tracks, and confirmation progress.
3. Confirm test mode does not write automatic rows or request location. Pause tracking, switch back to Manual, then leave Detection; the tracker should stop/reset with the session.
4. Track fixtures and session-level once-per-track marking are unit tests only. They do not establish physical identity after camera motion, occlusion, or reacquisition. Do not treat the synthetic IoU tests as field validation.

Automatic persistence is disabled. YOLO26n supplies per-frame boxes without stable IDs; image-space IoU can split or switch tracks as camera/vehicle motion changes perspective. GPS device position plus its accuracy cannot reliably distinguish nearby potholes. A cooldown or one-time mark per temporary track is insufficient to avoid duplicates after track reacquisition; therefore no automatic Room rows are generated and no location request is made per frame.

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
