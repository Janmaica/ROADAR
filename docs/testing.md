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

## Camera preview verification

1. Build the debug APK.
2. Confirm a device appears as `device` (not `unauthorized`) in `adb devices`.
3. Install with `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
4. Launch with `adb shell monkey -p com.roadar.app -c android.intent.category.LAUNCHER 1`.
5. Open **Detect** and tap **Enable camera**. Grant permission and verify that the rear-camera image appears in portrait orientation.
6. Leave Detection using a navigation tab and confirm the camera indicator/preview stops; reopen Detection and verify it initializes again.
7. Deny camera permission and verify the retry explanation; deny permanently and verify **Open app settings** returns to the permission flow.
8. Test on a device with no available camera or with the camera occupied by another app and verify the friendly unavailable message.

`CameraPreviewController` binds the Preview use case to the activity lifecycle and unbinds it when the screen is removed. No `ImageAnalysis` use case or model inference is present.

## Scope and known gaps

Successful compilation and navigation UI tests do not by themselves validate the camera image, every permission state, model loading or inference, GPS, local report persistence, mapping, offline report capture, synchronization, or performance. YOLO26n inference, GPS, report storage, mapping, and synchronization are not implemented. Device-level checks should be documented with the device/OS and test result when performed.

## Latest local verification

On 2026-11-11, `:app:testDebugUnitTest`, `:app:assembleDebug`, and `:app:connectedDebugAndroidTest` succeeded. All three instrumentation tests passed on an Android 15 (API 35) phone, including observing the CameraX preview stream and returning to Detection after leaving it. The debug APK was installed and launched; a device screenshot confirmed the live rear-camera image in portrait orientation, and no `AndroidRuntime` startup errors appeared in the inspected log. Camera permission denial/settings recovery and unavailable-camera handling have not been manually tested. No test validates YOLO26n inference or report features.
