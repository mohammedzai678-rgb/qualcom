# Pretrained Models And Pipelines

This folder contains the pretrained model assets used by DeepShieldAI.

The app also loads one audio model from the root assets directory:
[app/src/main/assets/my_model.onnx](../my_model.onnx)

## Pretrained Models

| Model file | Used by | Task | Input shape / format | Runtime path |
| --- | --- | --- | --- | --- |
| `deepfake_b0.onnx` | `ModelManager`, `DeepfakeDetector`, `VideoDeepfakeDetector` | Main deepfake / authenticity backbone | 224 x 224 RGB tensor, CHW normalized input | ONNX Runtime, with `QNN HTP -> NNAPI -> CPU` fallback |
| `blazeface.tflite` | `ModelManager`, `DeepfakeDetector`, `VideoDeepfakeDetector` | Face localization | 128 x 128 RGB float input, MediaPipe-style normalization | TFLite with optional Qualcomm `QnnDelegate`, then CPU fallback |
| `tiny_vit_int8.onnx` | `ModelManager`, `DeepfakeDetector` | Auxiliary texture / patch anomaly signal | 224 x 224 RGB tensor | ONNX Runtime, with `NNAPI -> CPU` fallback |
| `audio_classifier_int8.tflite` | `ModelManager` | Packaged audio classifier inventory entry | Audio feature tensor, model-specific layout | TFLite asset |
| `mobilenet_face_int8.tflite` | `ModelManager` | Fast face-feature placeholder | Model is shipped, but inference is currently stubbed | Not used in inference yet |
| `my_model.onnx` | `AudioDeepfakeDetector` | Audio spoof / deepfake classifier | 40 MFCC bands x 500 frames, flattened to ONNX input tensor | ONNX Runtime |

## Model Notes

- `deepfake_b0.onnx` is the main image authenticity model. The app verifies the checksum and also performs a quick weight-sanity check before trusting its outputs.
- `blazeface.tflite` is the face detector used to crop the region of interest before image classification.
- `tiny_vit_int8.onnx` is treated as an auxiliary signal, not as the sole decision maker.
- `mobilenet_face_int8.tflite` is present in the asset inventory for completeness, but the current app does not call it for inference.
- `my_model.onnx` is the audio model currently loaded by `AudioDeepfakeDetector`. It lives outside this folder, so it is documented here for completeness.

## Image And Video Pipeline

The still-image and live-camera pipeline is:

1. Detect faces with BlazeFace.
2. Crop the primary face region.
3. Preprocess the crop to 224 x 224 and run `deepfake_b0.onnx`.
4. Run `tiny_vit_int8.onnx` as an auxiliary texture/anomaly signal.
5. Run frequency-domain analysis and metadata checks.
6. Fuse the signals into the final verdict: `AUTHENTIC`, `SUSPICIOUS`, or `DEEPFAKE`.

Runtime acceleration order for the main image models:

1. Qualcomm `QnnDelegate` on HTP, when available.
2. Android NNAPI.
3. CPU fallback.

Live camera mode adds two extra behaviors:

- Cached face boxes reduce per-frame overhead.
- EMA smoothing stabilizes the confidence score across frames.

## Audio Pipeline

The audio classifier pipeline is:

1. Decode the input audio.
2. Extract 40 MFCC bands across 500 frames.
3. Flatten the feature tensor for ONNX Runtime.
4. Run `my_model.onnx`.
5. Interpret the output as authentic vs deepfake probability.

## Watermark Pipelines

DeepShieldAI also includes watermark embedding flows:

### Audio watermarking

1. Decode compressed audio with `MediaExtractor` and `MediaCodec`.
2. Convert PCM into frames.
3. Apply 1-D Haar wavelet transform.
4. Apply DCT to the approximation band.
5. Embed payload bits with QIM in the mid-frequency range.
6. Reconstruct PCM and re-encode to AAC.

### Video watermarking

1. Decode the video track with `MediaExtractor` and `MediaCodec`.
2. Read YUV frames from the decoder.
3. Apply watermarking to the luma plane with the invisible watermark processor.
4. Preserve chroma data correctly.
5. Re-encode the video and mux it back with audio.

## Expected Outputs From The Pretrained Model Downloader

The helper script `python/download_pretrained.py` is expected to place these assets here:

- `efficientnet_lite_int8.tflite`
- `blazeface.tflite`
- `audio_classifier_int8.tflite`
- `mobilenet_face_int8.tflite`
- `tiny_vit_int8.onnx`

## Status

These models are wired into the app as a mixed pipeline of pretrained backbones, heuristic fusion, and hardware-accelerated inference where available. The app is not relying on a single monolithic classifier.
