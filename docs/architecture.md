# ROADAR architecture

## Current implementation

ROADAR is currently a single Android app module with one `ComponentActivity` and a Jetpack Compose UI.

- `MainActivity` enables edge-to-edge content, applies `ROADARTheme`, and hosts `RoadarApp`.
- `RoadarApp` owns the current screen selection, bottom navigation, system-back behavior, and a connectivity indicator backed by Android's validated-network state.
- `ui/screens/` contains the home, detection, reports, map, and settings screens.
- `ui/components/` contains reusable headers, status rows, cards, and empty states.
- `ui/camera/` contains a lifecycle-scoped CameraX preview controller and a Compose `PreviewView` host. The controller currently binds only the preview use case.
- `ui/theme/` contains ROADAR light and dark Material 3 color schemes and typography.
- `ui/navigation/` contains the current screen destinations.

The app does not currently have a navigation library, ViewModel, repository, persistence layer, model runtime, GPS collection, or synchronization worker. For five top-level UI destinations, local Compose navigation state keeps the dependency graph small and the screen transitions explicit. Revisit this choice if deeper navigation or restored back stacks are introduced.

## Current boundaries and data truth

Camera permission is requested only when the user asks to enable the preview. The permission gate distinguishes a retryable denial from a denial that requires opening app settings. The CameraX `Preview` use case is bound to the activity lifecycle and unbound when the detection screen leaves composition. `PreviewView` uses `FILL_CENTER` and its compatible rendering mode to maintain a portrait preview across device rotations.

The camera controller owns preview setup and teardown separately from the screen. A future `ImageAnalysis` use case can be added to the same lifecycle binding without coupling model processing to Compose or changing the preview host. No analyzer, image processing, recording, or frame persistence is active.

Empty report and map states do not use fabricated records or coordinates. Detection metrics remain unavailable until inference is connected. The connectivity badge only describes the device's current validated internet connection; it does not imply that offline detection or synchronization exists.

## Proposed evolution

Introduce production architecture as each capability is implemented, rather than adding unused frameworks in advance:

1. **Presentation:** keep Compose screens focused on rendering UI and forwarding user actions. Add screen-level ViewModels when state outlives a composition or combines asynchronous sources.
2. **Camera acquisition:** add a camera feature that owns CameraX lifecycle and frame analysis. Keep camera permission, analyzer backpressure, and lifecycle policy outside reusable UI components.
3. **Inference:** define a small detector interface and result type, then implement an on-device YOLO26n adapter. Make model loading and inference dispatch explicit, and test preprocessing/postprocessing against the model contract.
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
