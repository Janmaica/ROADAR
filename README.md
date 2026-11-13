<p align="center">
  <img src="docs/assets/roadar-logo.png" alt="ROADAR Official Logo" width="250">
</p>

# ROADAR

**Development and Evaluation of an Offline-Capable Android Application for Pothole Detection Using a Fine-Tuned YOLO26n Model**

> **Development status: research prototype.** ROADAR provides local YOLO26n inference, manual reporting, Room-backed reports, an interactive MapLibre map, and optional Firebase Auth/Firestore synchronization. The Firebase Sync setting is persistent and defaults OFF; existing reports remain local-only, and only reports created while sync is enabled are eligible. Firebase/MapTiler configuration stays local. Cloud synchronization has only been exercised with the local Firestore Emulator—not against a production Firebase project.

## Overview

ROADAR is a Computer Engineering research project exploring an Android application for identifying potholes and supporting road-condition reporting. Its research goal is to evaluate a fine-tuned YOLO26n model running on-device and preserve manually confirmed reports locally when network access is unavailable.

This repository contains the Android project foundation, a Jetpack Compose interface, CameraX live preview/analysis, ONNX inference, Room-backed reports, and on-demand fused-device-location capture. Model/checkpoint/dataset binaries are intentionally excluded from Git. The app can only initialize inference when the matching local ONNX export is added to the app assets.

## Research motivation and objectives

Road-condition observations can be difficult to collect consistently and share promptly. ROADAR aims to study whether an Android phone can support practical pothole detection and structured field reporting while keeping model inference and report capture available offline.

The research objectives are to:

1. Integrate and evaluate a fine-tuned YOLO26n model on Android.
2. Measure inference latency, throughput (FPS), and detection confidence on target devices.
3. Associate user-confirmed detections with the device location and timestamps.
4. Persist reports locally and evaluate optional synchronization to an explicitly authorized Firebase project.

Manual reporting is the only reporting workflow. Automatic reporting and detection tracking have been removed. When locally configured, the app queues report uploads and deletions through WorkManager; otherwise reports remain local-only.

## Feature status

| Area | Status |
|---|---|
| Android build and Compose/Material 3 foundation | Implemented |
| Home, detection, reports, map, and settings screens | Implemented as UI |
| In-app navigation and device connectivity indicator | Implemented |
| CameraX rear-camera live preview and runtime permission flow | Implemented; preview verified on a physical Android 15 phone |
| CameraX ImageAnalysis with latest-frame backpressure | Implemented; analyzer releases each frame |
| Fine-tuned Stage 4 YOLO26n model export and ONNX inference | Integration implemented; separately supplied local ONNX asset is required and excluded from Git |
| Actual pothole boxes/confidence overlay | Available with the verified local model asset; tested with a held-out Stage 4 image and live camera |
| On-device performance instrumentation | Live rolling inference mean/min/max/median/p95, average pipeline latency, processing-only rate estimate, processed frames, and detections are displayed; controlled repeated benchmark remains incomplete |
| Room database and repository | Implemented; schema v3, explicit non-destructive v1→v3 migrations, insert/query/observe/delete and sync outbox |
| Foreground location capture | Implemented using fused location; requests coarse/fine permission after report confirmation and rejects stale/inaccurate fixes |
| Manual report workflow | Only reporting method; real current model detections require user confirmation and a fresh validated foreground location before Room insertion |
| Reports history and details/deletion | Room-backed UI implemented; database and isolated UI tests added |
| Interactive online map | MapLibre Android 11.8.0 with Room-backed markers, report selection, Reports-to-map navigation, and device centering; requires a restricted user-supplied MapTiler key |
| Offline report storage | Implemented with Room; locally stored report details remain available offline |
| Offline basemap tiles | Not implemented; online tiles require network, and no offline-download rights/source is configured |
| Firebase synchronization | Persistent Settings opt-in, default OFF; only post-opt-in reports eligible, with owner-scoped Firestore docs, retryable WorkManager uploads, and durable delete tombstones |
| Firebase rules | Least-privilege owner/schema rules are provided; 6 local Firestore Emulator tests pass. Rules are not deployed to any Firebase project and production sync is unverified |
| Broader device/field performance evaluation | In progress; the tested phone's CPU throughput is not yet suitable evidence for road-speed use |

The connectivity indicator reflects whether Android currently reports a validated internet connection. It does not guarantee that Firebase is configured or that Firestore rules have been deployed.

## Technology stack

- Kotlin and Android Gradle Plugin
- Jetpack Compose and Material 3
- CameraX 1.4.2 (Preview and ImageAnalysis)
- ONNX Runtime Android 1.22.0 (CPU inference)
- Room 2.7.2 (local report persistence)
- Firebase Authentication and Cloud Firestore (optional, local-project configuration)
- WorkManager 2.12.0 (network-constrained retry and sync outbox processing)
- Google Play services Location 21.3.0 (on-demand foreground location)
- MapLibre Android 11.8.0 and OkHttp 4.12.0 (interactive online basemap and provider-specific User-Agent)
- Android SDK 36 (`compileSdk`); minimum Android API 26; target API 35
- Gradle Wrapper 8.11.1
- JDK 21 (Java source/bytecode compatibility remains configured separately)

Camera preview, YOLO26n inference, and Room report storage are local. Manual report saving still requires a valid fresh device location; whether the phone can obtain a fix without network access depends on its GPS and environment. The map uses MapTiler's documented online Maps API with visible MapTiler/OpenStreetMap attribution. Configure a restricted client key as described in [docs/development-setup.md](docs/development-setup.md); the key is packaged into the app and must not be treated as a secret. Do not download/prefetch tiles. No offline basemap is included; Room report data remains local and readable offline. If Firebase is configured, report coordinates, accuracy, confidence, and timestamps are uploaded to the configured project under a persistent anonymous owner; deleting a local report queues its cloud deletion. Review the sync/privacy behavior and emulator-test the rules before enabling a project.

## Architecture

The current app is a single-activity Compose application. `MainActivity` applies the app theme and hosts the app UI; screen selection is local Compose state with system-back handling and a bottom navigation bar. CameraX Preview and ImageAnalysis are lifecycle-bound. A single background analyzer uses latest-frame backpressure, converts YUV frames to normalized RGB tensors, executes the local ONNX model, applies confidence filtering and NMS, and overlays detections on the preview. The user selects **Save Report** for a fresh real detection, confirms it, grants approximate or precise foreground location when requested, and saves its confidence plus the phone's location to the local Room database. No automatic detection tracker or automatic persistence workflow is present.

The export contract is fixed at `[1, 3, 640, 640]` float32 RGB input and raw `[1, 5, 8400]` output. NMS is not embedded in this export. The current detector uses CPU inference; measured device performance and export comparisons are documented in [docs/testing.md](docs/testing.md). Reports keep detection-event time, report creation time, and location-fix time separately. Coordinates describe the device position associated with a detection, not a surveyed pothole coordinate. Report records are local; basemap rendering and synchronization require connectivity.

The report stores the device position associated with the detection, not a surveyed pothole coordinate. See [docs/architecture.md](docs/architecture.md) for report validation, map-provider terms, API-key behavior, and offline limitations.

## Project structure

```text
.
├── app/
│   ├── src/
│   │   ├── androidTest/       # Device/emulator tests
│   │   ├── main/
│   │   │   ├── java/com/roadar/app/
│   │   │   │   ├── data/report/ # Room entity, DAO, repository, report factory
│   │   │   │   ├── data/sync/   # Firebase stores, sync coordinator, WorkManager worker
│   │   │   │   ├── location/    # Foreground fused-location provider and validation
│   │   │   │   ├── ai/          # ONNX inference, preprocessing, postprocessing, metrics
│   │   │   │   ├── ui/components/
│   │   │   │   ├── ui/camera/
│   │   │   │   ├── ai/          # ONNX inference, preprocessing, postprocessing
│   │   │   │   ├── ui/navigation/
│   │   │   │   ├── ui/screens/
│   │   │   │   └── ui/theme/
│   │   │   └── res/
│   │   ├── schemas/             # Exported Room database schema
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
├── firestore.rules
├── firebase.json
├── build.gradle.kts
├── gradle.properties
└── settings.gradle.kts
```

## Prerequisites

- Android SDK Platform 36 and Android SDK Platform-Tools.
- JDK 21. Android Studio's bundled JBR is suitable.
- A Windows development environment can use the checked-in Gradle Wrapper (`gradlew.bat`); macOS/Linux can use `./gradlew`.
- Android Studio or VS Code with Kotlin/Android tooling for editing. Android Studio is not required to run the Gradle commands.

See [docs/development-setup.md](docs/development-setup.md) for Android, MapTiler, and optional Firebase setup.

## Build with VS Code and Gradle

Open the repository folder in VS Code. In PowerShell, from the project root:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:assembleDebug
```

If the Android SDK is not discovered automatically, configure the local SDK path in an untracked `local.properties` file (for example, `sdk.dir=C:/Users/<user>/AppData/Local/Android/Sdk`). Do not commit machine-specific SDK paths.

### Local model asset

The selected checkpoint and exported model are not distributed in this repository. Obtain the approved FP32 export from your authorized private model source and place it at `app/src/main/assets/roadar_yolo26n.onnx`:

```powershell
New-Item -ItemType Directory -Force app\src\main\assets | Out-Null
Copy-Item "<approved-private-model-location>\roadar_yolo26n.onnx" `
  app\src\main\assets\roadar_yolo26n.onnx
.\tools\verify-model.ps1
```

The model file is ignored by Git. Without it, the project still builds and the camera preview works, but real detection and manual report creation are unavailable; Detection displays an explicit unavailable-model state. The verification script checks the expected SHA-256 before use. See [docs/model-assets.md](docs/model-assets.md) for provenance, licensing uncertainty, and private distribution guidance.

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

On 2026-11-13, `:app:testDebugUnitTest`, all 16 connected Android tests, all six Firestore Emulator rules tests, and `:app:assembleDebug` passed using JDK 21. Connected tests included the v1→v2→v3 Room migration. The APK was installed and launched on the Vivo V2430 with Firebase Sync OFF; no live Firebase synchronization or production rules deployment was attempted. See [docs/testing.md](docs/testing.md) for verification scope and remaining limitations.

## Development roadmap

1. Re-verify manual report creation with real detections, approximate/precise permissions, GPS-disabled/unavailable conditions, and airplane mode.
2. Verify live map tiles, Room markers, report navigation, marker selection/deletion synchronization, attribution, and tile-failure UX on device.
3. Run the documented repeated inference benchmark on the target Vivo V2430 without changing the selected Stage 4 checkpoint.
4. Validate optional Firebase synchronization only in an explicitly authorized test project; review and deploy Firestore rules separately before enabling a project.
5. Select an explicitly licensed offline map source only if offline basemaps are required; do not prefetch MapTiler or public OSM tiles.
6. Broaden device evaluation and complete the research documentation without overstating prototype accuracy or road-speed suitability.

The roadmap is a plan, not a feature guarantee. See [docs/roadmap.md](docs/roadmap.md) for milestones and acceptance notes.

## Research disclaimer

ROADAR is an active research prototype. It is not a validated road-safety, navigation, or infrastructure-maintenance system. The model pipeline is not validated for road-speed operation and can produce false detections. Manual reporting code records device position, which may not match the pothole's physical location; the end-to-end reporting flow has not been verified. Do not rely on it for operational decisions.

## Project board

[ROADAR Development Board](https://github.com/users/Janmaica/projects/2)
