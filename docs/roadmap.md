# Development roadmap

The milestones below distinguish completed prototype work from future research work.

## 1. App shell and interface — implemented

- Compose/Material 3 theme with light and dark palettes.
- Home, detection, reports, map, and settings screens.
- Top-level navigation and a device connectivity indicator.
- Clear, non-fabricated empty states for features not implemented.

## 2. CameraX live preview — implemented and verified on a physical device

- Added stable CameraX preview, camera2, and lifecycle artifacts at version 1.4.2.
- Added an on-demand CAMERA permission flow with retry and app-settings handling after permanent denial.
- Bound the rear-camera Preview use case to the activity lifecycle and release it when leaving Detection.
- Hosted PreviewView in the Compose Detection screen with portrait-friendly fill-center scaling and an initialization state.
- CameraX `ImageAnalysis` now runs beside the preview with `STRATEGY_KEEP_ONLY_LATEST`; analyzer frames are always closed.
- A connected Android 15 physical phone passed an instrumentation test that observed the preview stream, navigated away, and re-entered Detection successfully.
- Permission denial/retry/settings recovery and camera-unavailable behavior still need manual verification on target devices.

## 3. Stage 4 YOLO26n on-device inference — prototype implemented

- **Verification status: incomplete.** Earlier build, instrumentation, and single-device checks were reported as passing, but the final repeated on-device benchmark was interrupted and broader validation remains outstanding.
- The selected Stage 4 `best.pt` was validated and exported to fixed-shape FP32 ONNX; the source checkpoint is preserved and neither it nor the export is tracked.
- ONNX Runtime Android 1.22.0 loads the local model asset, preprocesses frames, runs the actual pothole model, applies confidence filtering/NMS, and draws prediction boxes/labels.
- A connected Android 15 phone passed a model test using a held-out Stage 4 pothole image and displayed live camera inference.
- CPU inference is functional but slow on the tested phone (about 1.9 processed FPS in an observed sample); this is not yet validated for vehicle-speed field use. NNAPI session creation is incompatible with the exported graph on the tested phone.
- Reports, GPS, and confidence-history persistence are not implemented.

## 4. Local reports and GPS — planned

- Define report fields, validation rules, and how a user confirms a candidate detection.
- Request location permission only when required; represent missing or stale location accurately.
- Add Room persistence and test migrations and offline read/write behavior.

## 5. History and map — planned

- Render only locally stored report records.
- Select a mapping provider and document its privacy, licensing, and network behavior.
- Keep report history available without a map connection.

## 6. Background synchronization — planned

- Define a backend API and authentication/security model first.
- Use WorkManager constraints, retries, and idempotent upload semantics.
- Show pending, synced, and failed states based on persisted synchronization results.

## 7. Research evaluation — in progress

- Define datasets, device matrix, and evaluation methodology.
- Record model and application versions, inference latency, FPS, and detection metrics.
- Evaluate offline behavior, battery/resource impact, and failure cases.
- The Stage 4 held-out test split and one Android device have initial measurements documented in `testing.md`; broader device and field evaluations remain outstanding.
- Report measured results separately from roadmap goals.
