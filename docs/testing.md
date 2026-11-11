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

## Manual device verification

1. Build the debug APK.
2. Confirm a device appears as `device` (not `unauthorized`) in `adb devices`.
3. Install with `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
4. Launch with `adb shell monkey -p com.roadar.app -c android.intent.category.LAUNCHER 1`.
5. Review the app screens in light and dark system themes and check the connectivity badge against the device network state.

## Scope and known gaps

Successful compilation and navigation UI tests do not validate camera preview, model loading or inference, GPS, local report persistence, mapping, offline report capture, synchronization, or performance. Those capabilities are not implemented. Device-level checks should be documented with the device/OS and test result when performed.

## Latest local verification

On 2026-11-11, `:app:testDebugUnitTest` and `:app:assembleDebug` succeeded. `:app:connectedDebugAndroidTest` passed both tests on an Android 15 (API 35) phone. The debug APK installed and launched; no `AndroidRuntime` errors were present in the recent startup log inspected after launch. This verifies the current UI/build path only, not the unimplemented detection and reporting features.
