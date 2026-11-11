# ROADAR

**Development and Evaluation of an Offline-Capable Android Application for Pothole Detection Using a Fine-Tuned YOLO26n Model**

> **Development status: on-device inference prototype; Phase 3 verification is incomplete.** CameraX preview and frame analysis run the selected Stage 4 YOLO26n ONNX model when its local asset is present. Previously reported device checks passed, but the final repeated performance benchmark and broader validation are outstanding. Detections are not saved; GPS, reports, maps, and synchronization are not implemented.

## Overview

ROADAR is a Computer Engineering research project exploring an Android application for identifying potholes and supporting road-condition reporting. Its research goal is to evaluate a fine-tuned YOLO26n model running on-device, with future report capture designed to work when network access is unavailable.

This repository contains the Android project foundation, a Jetpack Compose interface, CameraX live preview/analysis, and the inference integration for the selected fine-tuned Stage 4 YOLO26n checkpoint. Model/checkpoint/dataset binaries are intentionally excluded from Git. The app can only initialize inference when the matching local ONNX export is added to the app assets.

## Research motivation and objectives

Road-condition observations can be difficult to collect consistently and share promptly. ROADAR aims to study whether an Android phone can support practical pothole detection and structured field reporting while keeping model inference and report capture available offline.

The planned research objectives are to:

1. Integrate and evaluate a fine-tuned YOLO26n model on Android.
2. Measure inference latency, throughput (FPS), and detection confidence on target devices.
3. Associate verified detections with GPS coordinates and timestamps.
4. Persist reports locally and explore synchronization when connectivity returns.

These are project objectives, not claims about the current app.

## Feature status

| Area | Status |
|---|---|
| Android build and Compose/Material 3 foundation | Implemented |
| Home, detection, reports, map, and settings screens | Implemented as UI |
| In-app navigation and device connectivity indicator | Implemented |
| CameraX rear-camera live preview and runtime permission flow | Implemented; preview verified on a physical Android 15 phone |
| CameraX ImageAnalysis with latest-frame backpressure | Implemented; analyzer releases each frame |
| Fine-tuned Stage 4 YOLO26n model export and ONNX inference | Implemented; local ONNX asset is not included in Git |
| Actual pothole boxes/confidence overlay | Implemented; tested with a held-out Stage 4 image and live camera |
| On-device performance instrumentation | Implemented for model load, inference, processing latency, and processed FPS; final repeated benchmark is incomplete |
| GPS association and local report storage | Planned; not implemented |
| Map visualization and background synchronization | Planned; not implemented |
| Broader device/field performance evaluation | In progress; the tested phone's CPU throughput is not yet suitable evidence for road-speed use |

The connectivity indicator reflects whether Android currently reports a validated internet connection. It does not indicate that report synchronization is available.

## Technology stack

- Kotlin and Android Gradle Plugin
- Jetpack Compose and Material 3
- CameraX 1.4.2 (Preview and ImageAnalysis)
- ONNX Runtime Android 1.22.0 (CPU inference)
- Android SDK 36 (`compileSdk`); minimum Android API 26; target API 35
- Gradle Wrapper 8.11.1
- Java 17

Room, WorkManager, and a maps SDK are intentionally not included until their respective features are designed and implemented.

## Architecture

The current app is a single-activity Compose application. `MainActivity` applies the app theme and hosts the app UI; screen selection is local Compose state with system-back handling and a bottom navigation bar. CameraX Preview and ImageAnalysis are lifecycle-bound. A single background analyzer uses latest-frame backpressure, converts YUV frames to normalized RGB tensors, executes the local ONNX model, applies confidence filtering and NMS, and overlays detections on the preview.

The export contract is fixed at `[1, 3, 640, 640]` float32 RGB input and raw `[1, 5, 8400]` output. NMS is not embedded in this export. The current detector uses CPU inference; measured device performance and export comparisons are documented in [docs/testing.md](docs/testing.md). Detection results are transient and are not recorded or geotagged.

Future work should introduce feature boundaries and data/domain layers only as real integrations are added. See [docs/architecture.md](docs/architecture.md) for the current design and a proposed evolution path.

## Project structure

```text
.
├── app/
│   ├── src/
│   │   ├── androidTest/       # Device/emulator tests
│   │   ├── main/
│   │   │   ├── java/com/roadar/app/
│   │   │   │   ├── ui/components/
│   │   │   │   ├── ui/camera/
│   │   │   ├── ai/             # ONNX inference, preprocessing, postprocessing
│   │   │   ├── ui/navigation/
│   │   │   │   ├── ui/screens/
│   │   │   │   └── ui/theme/
│   │   │   └── res/
│   │   └── test/               # Local JVM tests
│   └── build.gradle.kts
├── docs/
│   ├── architecture.md
│   ├── development-setup.md
│   ├── roadmap.md
│   └── testing.md
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
├── .github/workflows/android.yml
├── build.gradle.kts
├── gradle.properties
└── settings.gradle.kts
```

## Prerequisites

- Android SDK Platform 36 and Android SDK Platform-Tools.
- JDK 17. Android Studio's bundled JBR is suitable.
- A Windows development environment can use the checked-in Gradle Wrapper (`gradlew.bat`); macOS/Linux can use `./gradlew`.
- Android Studio or VS Code with Kotlin/Android tooling for editing. Android Studio is not required to run the Gradle commands.

See [docs/development-setup.md](docs/development-setup.md) for setup details.

## Build with VS Code and Gradle

Open the repository folder in VS Code. In PowerShell, from the project root:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:assembleDebug
```

If the Android SDK is not discovered automatically, configure the local SDK path in an untracked `local.properties` file (for example, `sdk.dir=C:/Users/<user>/AppData/Local/Android/Sdk`). Do not commit machine-specific SDK paths.

### Local model asset

The selected checkpoint and exported model are not in Git. For local inference, place the verified FP32 export at `app/src/main/assets/roadar_yolo26n.onnx`. The default setup in this environment keeps the generated export at `$env:USERPROFILE\Documents\modelstage4\roadar_yolo26n.onnx`:

```powershell
New-Item -ItemType Directory -Force app\src\main\assets | Out-Null
Copy-Item "$env:USERPROFILE\Documents\modelstage4\roadar_yolo26n.onnx" `
  app\src\main\assets\roadar_yolo26n.onnx
```

The model file is ignored by Git. Without it, the project still builds and the camera preview works, but Detection reports that the model asset is missing. See [docs/testing.md](docs/testing.md) for the exact checkpoint/export contract and optional held-out image test.

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install and launch on a connected, authorized Android device:

```powershell
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p com.roadar.app -c android.intent.category.LAUNCHER 1
```

## Testing and verification

Run local unit tests with `.\gradlew.bat :app:testDebugUnitTest`. The navigation UI test requires a connected emulator or device and can be run with `.\gradlew.bat :app:connectedDebugAndroidTest`.

Previously reported checks on a Vivo V2430 running Android 15 (API 35) include successful unit/build tasks, four connected instrumentation tests, installation and launch, and live camera detections without observed fatal startup errors. These checks do not complete Phase 3 verification: the final repeated on-device benchmark was interrupted, broader device and field evaluation remains outstanding, and the model/test assets are intentionally not included in Git. See [docs/testing.md](docs/testing.md) for scope and limitations. GPS, report storage, mapping, and synchronization remain unimplemented.

## Development roadmap

1. Complete broader camera permission/failure-state testing on target devices.
2. Improve and evaluate mobile inference throughput without changing the selected Stage 4 checkpoint.
3. Define report confirmation rules; associate eligible detections with timestamps and optional GPS, then persist reports locally with Room.
4. Add report history and map visualization without blocking offline use.
5. Add constrained background synchronization with WorkManager after a backend contract exists.
6. Evaluate detection quality, latency, FPS, resource use, and offline behavior on a representative device matrix.

The roadmap is a plan, not a feature guarantee. See [docs/roadmap.md](docs/roadmap.md) for milestones and acceptance notes.

## Research disclaimer

ROADAR is an active research prototype. It is not a validated road-safety, navigation, or infrastructure-maintenance system. The current model pipeline has not been validated for road-speed operation, can produce false detections, and does not save reports or collect location; do not rely on it for operational decisions.

## Project board

[ROADAR Development Board](https://github.com/users/Janmaica/projects/2)
