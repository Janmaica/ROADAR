# Development roadmap

The milestones below distinguish implemented prototype code from features that are disabled, deferred, or not yet verified.

## 1. App shell and interface — implemented

- Compose/Material 3 theme with light and dark palettes.
- Home, detection, reports, map, and settings screens with top-level navigation.
- Connectivity status and empty states that do not fabricate results.

## 2. CameraX live preview — implemented; device-tested

- CameraX rear-camera Preview and ImageAnalysis use cases, lifecycle-bound with latest-frame backpressure.
- On-demand camera permission and preview cleanup when leaving Detection.
- A Vivo V2430 running Android 15 passed the connected preview/re-entry instrumentation test.

## 3. Stage 4 YOLO26n on-device inference — prototype implemented; research verification incomplete

- Preserves the selected Stage 4 checkpoint and uses its fixed-shape FP32 ONNX export; model binaries remain excluded from Git.
- ONNX Runtime CPU inference, frame preprocessing, output filtering/NMS, bounding boxes, and confidence labels.
- Connected tests include a held-out Stage 4 pothole image and live camera analysis.
- Broader device/field evaluation and a final repeated performance benchmark remain outstanding. An isolated CPU result is not evidence of road-speed suitability; NNAPI failed to initialize with this graph on the tested phone.

## 4. Manual GPS-based local reporting — implemented in code; end-to-end verification pending

- Room schema v1, UUID-keyed reports, DAO, IO-dispatched repository, generated/exported schema, and no destructive migration fallback.
- One-shot fused location request after user confirmation; requests foreground approximate or precise permission, validates UTC/monotonic age and accuracy, and records the device position associated with the event.
- Manual report action only appears for a real, current model detection. It requires explicit confirmation, saves confidence/model/timestamps/location with `PENDING` sync status, and reports success only after Room insertion.
- Reports UI observes database rows and provides details and delete confirmation; Home reflects saved report count.
- Local unit and connected tests cover fix validation, report construction, Room insert/query/observe/update/delete/persistence, and isolated report UI details/deletion.
- Still requires manual on-device validation of permission outcomes, fresh/poor/unavailable fixes, actual save/readback with a live detection, airplane-mode persistence, and longer-term data migration policy.

## 5. Automatic tracking — experimental; automatic persistence disabled

- The Detection screen offers a manual/automatic mode selector; manual remains the default and unchanged.
- Automatic mode runs a separate greedy IoU tracker on real YOLO26n boxes (default confidence 0.60, IoU 0.30, three consecutive frames; one missed frame tolerated).
- Unit tests cover temporal confirmation, confidence/box filtering, multiple objects, track expiry/reacquisition, and once-per-track report marking.
- UI reports active/paused state, GPS behavior, confidence, track confirmation, and an automatic-save count of zero.
- Automatic Room persistence and location acquisition remain off: camera motion and reacquisition can assign a new track ID to an already-seen pothole. The tracker is not field-validated and has no spatial duplicate guarantee.
- Validate on representative moving-camera footage before enabling any automatic saves.

## 6. Interactive map — online implementation; provider rendering verification pending

- MapLibre Android 11.8.0 loads the MapTiler Streets v4 online style when a restricted user-supplied key is configured.
- The map observes Room reports, renders one marker per row, supports pan/zoom/selection, report-ID navigation, and foreground one-shot device centering.
- MapTiler and OpenStreetMap attribution links are displayed; a missing key or online map failure is surfaced, while local report details remain available.
- A locally configured key enabled live Streets v4 rendering on the connected phone; a pan gesture was verified. The developer reports physically verifying saved-report markers and marker removal after report deletion; this was not repeated during the latest automated test run. Pinch zoom, marker taps, and location centering remain to be checked in a documented device session.
- Offline report storage is implemented; offline basemap tiles/downloads are not. No tile prefetch or offline region download is used.

## 7. Synchronization and research evaluation — planned/in progress

- Define a backend/authentication contract before adding WorkManager and retries.
- Broaden model, latency/FPS, geolocation accuracy, resource use, privacy, and offline evaluation on a representative device matrix.
- Report measurements with reproducible conditions; do not treat the prototype as a road-safety system.
