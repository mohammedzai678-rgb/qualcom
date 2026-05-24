# DeepShield AI - Setup Guide

## Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- Kotlin 1.9.22+
- JDK 17
- Android SDK 34
- Python 3.10+
- Qualcomm Snapdragon device recommended for on-device acceleration

## Quick Start

### 1. Open the project

```bash
cd C:\Users\moham\Desktop\Qualcom\DeepShieldAI
```

Open this folder in Android Studio.

### 2. Prepare model assets

```bash
cd python
pip install -r requirements.txt
python download_pretrained.py
```

The script writes model assets to `app/src/main/assets/models/`.
If TensorFlow is not installed yet, it creates placeholder files so the Android
project can still boot without missing-asset errors.

### 3. Build and run

In Android Studio:

1. Sync Gradle.
2. Select a device or emulator.
3. Click Run.

For command-line builds, use a machine that already has JDK 17 and Gradle
installed, or add Gradle wrapper files to the repo first.

### 4. Permissions

Grant these on first launch:

- Camera for Live Shield
- Media access for gallery image scanning

## Current Workflow Notes

- The gallery scanner is wired for still images.
- The live camera screen now routes analyzed frames into the detector.
- Heatmap and forensic screens now read real scan results from shared app state.
- Python model preparation expects `tensorflow-hub` in addition to TensorFlow.

## QNN SDK Integration

To enable Qualcomm NPU acceleration:

1. Download the QNN SDK from Qualcomm AI Hub.
2. Add `libQnnHtp.so` to `app/src/main/jniLibs/arm64-v8a/`.
3. Uncomment the QNN delegate dependency in `app/build.gradle.kts`.
4. Replace the current delegate stub in `ModelManager.kt` with the real runtime check.

## Release Build

```bash
gradle assembleRelease
```

The release APK will be written to `app/build/outputs/apk/release/`.
