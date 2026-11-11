# Testing and verification

## Automated checks

From the repository root:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

The `RoadarNavigationTest` instrumentation test exercises the dashboard's detection and report actions and visits the main navigation destinations. Run it on an emulator or authorized device:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

`CameraPreviewTest` grants the app camera permission through the instrumentation shell, waits for the rear-camera preview to report an active stream, navigates away from Detection, and verifies that the preview starts again after returning. It requires a device/emulator with a working rear camera; permission denial, permanent denial, app-settings recovery, and camera-in-use/unavailable cases still require manual verification.

`Yolo26PostprocessorTest` checks confidence filtering, NMS, clipping, and unletterboxing. `PotholeInferenceInstrumentedTest` runs the local ONNX model against a held-out Stage 4 image and logs warm inference and processing timings. The instrumentation test assumes the ignored model and image assets are present; without them it is skipped.

## Camera preview verification

1. Build the debug APK.
2. Confirm a device appears as `device` (not `unauthorized`) in `adb devices`.
3. Install with `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
4. Launch with `adb shell monkey -p com.roadar.app -c android.intent.category.LAUNCHER 1`.
5. Open **Detect** and tap **Enable camera**. Grant permission and verify that the rear-camera image appears in portrait orientation.
6. Leave Detection using a navigation tab and confirm the camera indicator/preview stops; reopen Detection and verify it initializes again.
7. Deny camera permission and verify the retry explanation; deny permanently and verify **Open app settings** returns to the permission flow.
8. Test on a device with no available camera or with the camera occupied by another app and verify the friendly unavailable message.

The detection screen binds CameraX Preview and ImageAnalysis to the activity lifecycle. ImageAnalysis uses latest-frame backpressure and closes each frame after processing. ONNX model inference runs on a single background executor; the local model asset is not committed, so a clean checkout needs the separately exported model copied to `app/src/main/assets/roadar_yolo26n.onnx` to exercise inference. Without that asset, model initialization reports an error while the camera preview remains available.

## Scope and known gaps

Phase 3 verification is incomplete. Successful compilation and tests on one phone do not establish reliability across devices, camera conditions, or field use. The final planned repeated on-device performance run was interrupted, so no robust median benchmark is available. The previously observed live CPU throughput is only an isolated sample and is not evidence of road-speed suitability. Permission denial/settings recovery, unavailable-camera behavior, extended camera lifecycle use, and broader device/field evaluation remain to be tested. GPS, report storage, mapping, and synchronization are not implemented.

## Previously reported verification

The latest previously reported run completed `:app:testDebugUnitTest`, `:app:assembleDebug`, and `:app:connectedDebugAndroidTest`. Four connected instrumentation tests passed on a Vivo V2430 running Android 15 (API 35), including live camera preview and a held-out Stage 4 pothole-image inference test. The app was installed and launched; live camera detections were observed, with no fatal `AndroidRuntime` startup errors in the inspected log. These results have not been rerun as part of this save-progress task. The final repeated on-device benchmark results are unavailable because that run was interrupted.

## Previously reported model/export checks

The selected Stage 4 checkpoint was compared with its fixed-shape FP32 ONNX export on a 788-image held-out split (1,598 ground-truth boxes). Previously reported PyTorch metrics were precision 0.830, recall 0.785, mAP50 0.853, and mAP50-95 0.481; raw ONNX metrics were 0.851, 0.772, 0.851, and 0.476, respectively. A paired prediction comparison reported 20,517 greedy box matches, mean IoU 0.967, 20,067 matches at IoU >= 0.5, and mean confidence absolute error 0.00113. These are export-comparison results, not a substitute for broader evaluation.

An isolated early live CPU sample on the tested phone reported about 1,354 ms model load time, 416 ms inference time, 539 ms processing latency, and 1.9 processed inferences/second. These approximate values are not a robust benchmark. A final repeated run and median summary were interrupted and remain outstanding. Model inference time measures ONNX `session.run`; processing latency measures analyzer-start through result-ready and includes preprocessing and postprocessing. NNAPI session creation failed on the tested device, so the app currently uses CPU inference. Neither result demonstrates acceptable field performance.
