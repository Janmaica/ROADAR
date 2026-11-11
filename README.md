# ROADAR

**Development and Evaluation of an Offline-Capable Android Application for Pothole Detection Using a Fine-Tuned YOLO26n Model**

> **Development status: UI prototype.** The current application is a navigable interface and build foundation. Pothole detection, camera capture, GPS, local report storage, maps, and synchronization are not implemented.

## Overview

ROADAR is a Computer Engineering research project exploring an Android application for identifying potholes and supporting road-condition reporting. Its research goal is to evaluate a fine-tuned YOLO26n model running on-device, with future report capture designed to work when network access is unavailable.

This repository currently contains the Android project foundation and an initial Jetpack Compose interface. It does not yet include a trained model or operational detection/reporting functionality.

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
| CameraX preview and live detection | Planned; not implemented |
| Fine-tuned YOLO26n model and inference | Planned; model is not included |
| GPS association and local report storage | Planned; not implemented |
| Map visualization and background synchronization | Planned; not implemented |
| Performance evaluation | Planned; no inference metrics are available |

The connectivity indicator reflects whether Android currently reports a validated internet connection. It does not indicate that report synchronization is available.

## Technology stack

- Kotlin and Android Gradle Plugin
- Jetpack Compose and Material 3
- Android SDK 36 (`compileSdk`); minimum Android API 26; target API 35
- Gradle Wrapper 8.11.1
- Java 17

CameraX, a model runtime, Room, WorkManager, and a maps SDK are intentionally not dependencies yet. They should be selected when their respective features are designed and implemented.

## Architecture

The current app is a single-activity Compose application. `MainActivity` applies the app theme and hosts the app UI; screen selection is local Compose state with system-back handling and a bottom navigation bar. Reusable UI pieces and screen composables live in separate packages.

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
│   │   │   │   ├── ui/navigation/
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

**Verified 2026-11-11:** the debug build and APK succeeded; local unit tests passed; both instrumentation tests passed on a connected Android 15 (API 35) phone. The APK was installed and launched, and no Android runtime startup errors appeared in the inspected log. The UI test exercises dashboard actions and screen navigation; it does not validate camera, model, GPS, storage, offline reporting, synchronization, or performance behavior. See [docs/testing.md](docs/testing.md).

## Development roadmap

1. Complete the app shell and interface foundation.
2. Add CameraX acquisition and lifecycle-safe preview.
3. Integrate the fine-tuned YOLO26n model and establish a reproducible inference pipeline.
4. Associate eligible detections with timestamps and GPS data; persist reports locally with Room.
5. Add report history and map visualization without blocking offline use.
6. Add constrained background synchronization with WorkManager.
7. Evaluate detection quality, latency, FPS, resource use, and offline behavior on selected devices.

The roadmap is a plan, not a feature guarantee. See [docs/roadmap.md](docs/roadmap.md) for milestones and acceptance notes.

## Research disclaimer

ROADAR is an active research prototype. It is not a validated road-safety, navigation, or infrastructure-maintenance system. The current UI does not detect potholes or collect reports; do not rely on it for operational decisions.

## Project board

[ROADAR Development Board](https://github.com/users/Janmaica/projects/2)
