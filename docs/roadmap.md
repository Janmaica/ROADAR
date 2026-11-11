# Development roadmap

The milestones below describe future work for the research project. Only the initial app shell and interface are implemented at this stage.

## 1. App shell and interface — in progress

- Compose/Material 3 theme with light and dark palettes.
- Home, detection, reports, map, and settings screens.
- Top-level navigation and a device connectivity indicator.
- Clear, non-fabricated empty states for features not implemented.

## 2. Camera acquisition — planned

- Select and add a compatible CameraX version.
- Implement permission handling, lifecycle-bound preview, and analysis.
- Define frame format, rotation, resolution, and analyzer backpressure policy.
- Verify on supported physical devices and handle permission denial.

## 3. On-device inference — planned

- Confirm the exported fine-tuned YOLO26n model format and runtime requirements.
- Implement preprocessing, inference, and postprocessing against that model.
- Test result correctness and latency on representative devices.
- Do not display confidence or throughput until measured from real inference.

## 4. Local reports and GPS — planned

- Define report fields and validation rules.
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

## 7. Research evaluation — planned

- Define datasets, device matrix, and evaluation methodology.
- Record model and application versions, inference latency, FPS, and detection metrics.
- Evaluate offline behavior, battery/resource impact, and failure cases.
- Report measured results separately from roadmap goals.
