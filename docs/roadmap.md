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
- Live UI instrumentation includes recent inference latency mean/min/max/median/p95, average processing latency, total processed frames/detections, and a processing-only rate estimate. This estimate excludes callback gaps and is not camera FPS or a controlled benchmark.
- Connected tests include a held-out Stage 4 pothole image and live camera analysis.
- Broader device/field evaluation and a final repeated performance benchmark remain outstanding. An isolated CPU result is not evidence of road-speed suitability; NNAPI failed to initialize with this graph on the tested phone.

## 4. Manual GPS-based local reporting — implemented in code; end-to-end verification pending

- Room schema v3, UUID-keyed reports, DAO, IO-dispatched repository, exported schemas, and non-destructive v1→v2→v3 migrations. The latest migration preserves prior rows and marks them ineligible for cloud sync.
- One-shot fused location request after user confirmation; requests foreground approximate or precise permission, validates UTC/monotonic age and accuracy, and records the device position associated with the event.
- Manual report action only appears for a real, current model detection. It requires explicit confirmation, saves confidence/model/timestamps/location with `PENDING` sync status, and reports success only after Room insertion.
- Reports UI observes database rows and provides details and delete confirmation; Home reflects saved report count.
- Local unit and connected tests cover fix validation, report construction, Room insert/query/observe/update/delete/persistence, and isolated report UI details/deletion.
- Still requires manual on-device validation of permission outcomes, fresh/poor/unavailable fixes, actual save/readback with a live detection, airplane-mode persistence, and longer-term data migration policy.

## 5. Manual reporting — implemented; repeat full physical validation

- The Detection screen has a single **Save Report** workflow for recent actual model detections; users confirm before any location request or persistence.
- Fused foreground location is validated for coordinate ranges, freshness, and accuracy. Permission denial, unavailable/disabled location, invalid fixes, and inaccurate fixes must leave the report unsaved with visible feedback.
- New reports are inserted through the existing Room repository and contain detection confidence, model metadata, UTC timestamps, and the associated device location.
- Room schema v3 retains the legacy reporting-mode converter and migrates prior versions without deleting reports; new reports are marked `MANUAL` and sync-eligible only if created while explicit sync opt-in is enabled.
- Re-run device validation for precise/approximate permission, offline valid-fix availability, unavailable GPS, save/readback, restart persistence, and report deletion.

## 6. Interactive map — online implementation; provider rendering verification pending

- MapLibre Android 11.8.0 loads the MapTiler Streets v4 online style when a restricted user-supplied key is configured.
- The map observes Room reports, renders one marker per row, supports pan/zoom/selection, report-ID navigation, and foreground one-shot device centering.
- MapTiler and OpenStreetMap attribution links are displayed; a missing key or online map failure is surfaced, while local report details remain available.
- A locally configured key enabled live Streets v4 rendering on the connected phone; a pan gesture was verified. The developer reports physically verifying saved-report markers and marker removal after report deletion; this was not repeated during the latest automated test run. Pinch zoom, marker taps, and location centering remain to be checked in a documented device session.
- Offline report storage is implemented; offline basemap tiles/downloads are not. No tile prefetch or offline region download is used.

## 7. Local-first Firebase synchronization — implemented; live project validation pending

- Firebase synchronization requires the ignored `app/google-services.json` and a persistent Settings switch that defaults OFF and requires confirmation to enable.
- Room schema v3 uses a non-destructive migration that leaves every preexisting report local-only. Only reports created after opt-in are eligible; workers recheck the setting before Firebase initialization and every remote operation.
- Eligible report IDs, bounded batches, server acknowledgments, network constraints, exponential retry, and local sync status are implemented. Eligible local deletions keep durable tombstones while sync is off, preventing stale uploads from restoring deleted cloud reports.
- `firestore.rules` restricts access to the authenticated owner and enforces the report schema. Six Firestore Emulator rules tests and focused JVM coordinator tests are present.
- No production rules were deployed and no live Firebase data was read or changed. Before use, verify project ownership, enable anonymous authentication, review and publish the rules manually, and confirm the privacy/retention policy for uploading coordinates and detection metadata.
- Startup/worker checks keep sync inactive when the setting is OFF; preexisting reports are never eligible for automatic upload. Real-project synchronization, production rules review, and field/privacy validation remain pending.
- Broaden model, latency/FPS, geolocation accuracy, resource use, privacy, and offline evaluation on a representative device matrix.
- Use the repeatable physical-device benchmark procedure in `testing.md`; report only observed measurements. Do not treat the prototype as a road-safety system.
