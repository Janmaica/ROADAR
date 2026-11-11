# ROADAR architecture

## Current implementation

ROADAR is a single Android app module with one `ComponentActivity` and a Jetpack Compose UI.

- `MainActivity` enables edge-to-edge content, applies `ROADARTheme`, and hosts `RoadarApp`.
- `RoadarApp` owns the current screen selection, bottom navigation, system-back behavior, and a connectivity indicator backed by Android's validated-network state.
- `ui/screens/` contains the home, detection, reports, map, and settings screens.
- `ui/components/` contains reusable headers, status rows, cards, and empty states.
- `ui/camera/` contains the lifecycle-scoped CameraX preview/analyzer controller, `PreviewView` host, and bounding-box overlay.
- `ai/` contains the YOLO26n ONNX Runtime adapter, camera-frame preprocessor, output decoder/NMS, and detection session.
- `ui/theme/` contains ROADAR light and dark Material 3 color schemes and typography.
- `ui/navigation/` contains the current screen destinations.

The app does not currently have a navigation library, ViewModel, repository, persistence layer, GPS collection, or synchronization worker. For five top-level UI destinations, local Compose navigation state keeps the dependency graph small and the screen transitions explicit. Revisit this choice if deeper navigation or restored back stacks are introduced.

## Current boundaries and data truth

Camera permission is requested only when the user asks to enable the preview. The permission gate distinguishes a retryable denial from a denial that requires opening app settings. CameraX `Preview` and `ImageAnalysis` use cases are bound to the activity lifecycle and unbound when the detection screen leaves composition. The analyzer uses `STRATEGY_KEEP_ONLY_LATEST` and a single background executor; every `ImageProxy` is closed in a `finally` block. `PreviewView` uses `FILL_CENTER` and its compatible rendering mode to maintain a portrait preview across device rotations.

The detector loads the locally supplied `roadar_yolo26n.onnx` asset only after Detection is opened. ONNX Runtime CPU inference uses a fixed `[1, 3, 640, 640]` float32 RGB input and the model's raw `[1, 5, 8400]` output. The checkpoint has one `pothole` class and no embedded NMS; app-side confidence filtering, NMS, coordinate unletterboxing, and preview overlay are required. The asset is not stored in Git. If it is absent or incompatible, the camera preview remains available and the UI reports the model error.

YUV camera frames are cropped to the CameraX crop rectangle, converted to RGB, rotated using `ImageInfo.rotationDegrees`, letterboxed to 640×640 with value 114, and normalized to `[0, 1]`. Output boxes are mapped back through the letterbox scale/padding to the upright frame, then aligned to the `PreviewView` fill-center crop. The analyzer reports ONNX `session.run` duration separately from analyzer-start-to-result latency and counts completed inferences for processed FPS. No detections are stored.

The ORT Android AAR is configured for CPU inference. NNAPI was tested on a Vivo V2430 running Android 15, but session creation failed on the exported graph's `Split` operation (`AddNnapiSplit count [0] does not evenly divide dimension 3 [32]`); the app therefore uses CPU rather than silently changing the model or provider. This device's live preview produced about 1.9 processed FPS in one observed sample, which is not sufficient evidence for field-speed operation.

Empty report and map states do not use fabricated records or coordinates. GPS, local report persistence, mapping, and synchronization are not implemented. The connectivity badge only describes the device's current validated internet connection; model inference itself uses the local model asset and does not require network access.

## Proposed evolution

Introduce production architecture as each capability is implemented, rather than adding unused frameworks in advance:

1. **Presentation:** keep Compose screens focused on rendering UI and forwarding user actions. Add screen-level ViewModels when state outlives a composition or combines asynchronous sources.
2. **Camera acquisition:** the current controller owns CameraX lifecycle and frame analysis. Keep permission, analyzer backpressure, and lifecycle policy outside reusable UI components.
3. **Inference:** the current ONNX adapter is isolated from Compose and validates the expected tensor shapes at load time. Keep preprocessing/postprocessing tests tied to the exported model contract; evaluate an Android-supported accelerated runtime before claiming real-time throughput.
4. **Location and reports:** associate optional location and capture time with a domain report. Add Room entities/DAOs and a repository only when local persistence is introduced. Handle location permission and unavailable fixes explicitly.
5. **Map:** render persisted report coordinates through a selected mapping provider; distinguish absent location data from zero results.
6. **Synchronization:** add WorkManager for constrained, retryable background upload after a backend contract exists. Keep local capture independent of network availability and represent pending/synced/error states in stored data.
7. **Evaluation:** collect inference duration and frame throughput using a documented measurement policy; avoid presenting placeholder measurements as results.

### Possible future dependency direction

```text
Compose screen -> feature state holder -> domain use case/repository
                                         -> CameraX frame source
                                         -> YOLO26n detector
                                         -> Room-backed report store
                                         -> location provider
                                         -> WorkManager sync scheduler
```

This is a target direction, not a description of code already present.
