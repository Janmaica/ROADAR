# ROADAR model asset

## Local installation

The Android app expects the separately supplied FP32 ONNX file at:

```text
app/src/main/assets/roadar_yolo26n.onnx
```

Obtain it only from an authorized private model source. From the repository root, copy it into place and verify its integrity:

```powershell
New-Item -ItemType Directory -Force app\src\main\assets | Out-Null
Copy-Item "<approved-private-model-location>\roadar_yolo26n.onnx" `
  app\src\main\assets\roadar_yolo26n.onnx
.\tools\verify-model.ps1
```

The current approved local export's expected SHA-256 is:

```text
D4F3BEF0FEB16C7A3354342E8A8684A02578555EF6B531902ADCDCA19185F3CA
```

The verifier fails if the asset is absent or does not match that exact export. Do not update the pinned checksum to accept a different file without validating its provenance, tensor contract, and predictions against the selected Stage 4 checkpoint.

The ONNX file is excluded by `.gitignore`. Do not add it to Git, Git LFS, GitHub Releases, or another public host unless redistribution rights are independently confirmed. Prefer access-controlled artifact storage for authorized collaborators, with restricted access, a recorded checksum, and a documented owner/source.

## What the available provenance establishes

The local companion NCNN export metadata identifies an Ultralytics YOLO26n pothole detector, class `pothole`, trained using a dataset configuration named `ROADAR_STAGE4_HYBRID`, and records Ultralytics version `8.4.202`. The local `best.pt` and ONNX files are consistent with the Stage 4 files referenced by this project, and the testing notes record a paired checkpoint/export evaluation.

This evidence supports that the intended model is ROADAR's Stage 4 fine-tuned model. It is not a complete reproducible provenance chain: training/export scripts, run logs, the exact base-weight origin, and dataset redistribution terms are not part of this repository. Companion metadata is not cryptographic proof that a particular ONNX file was exported from a particular checkpoint. Keep the model private until those records and rights are resolved.

## License and redistribution status

The companion export metadata labels the model `AGPL-3.0`. The upstream [Ultralytics repository](https://github.com/ultralytics/ultralytics) declares AGPL-3.0, and Ultralytics describes academic/research use as an AGPL use case when the project is fully open source; it offers a separate Enterprise license for other use cases. The app's ONNX Runtime dependency is MIT-licensed, but that runtime license does not grant rights to the model weights.

Fine-tuning does not itself establish that weights can be redistributed without conditions. The pretrained checkpoint's precise source/license and the training dataset's terms have not been verified, and the complete training/export source material needed for a confident compliance review is unavailable here. Therefore ROADAR does **not** make a legal determination that the ONNX export or checkpoint may be published. Do not redistribute either publicly until the applicable licenses, notices, dataset terms, and any source-code obligations have been reviewed by the project owner or qualified counsel.

## Behavior without the model

The repository is source-only with respect to the model. Gradle can build the application without the ONNX file. The camera preview remains available; Detection reports that the model is unavailable and produces no detections. Consequently, inference and detection-based report creation require a locally supplied, checksum-verified model. Instrumentation tests that need real inference skip when the model/sample assets are absent.
