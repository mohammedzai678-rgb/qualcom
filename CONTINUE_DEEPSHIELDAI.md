Continue DeepShieldAI stabilization from the latest local state.

What was completed:
- Patched TinyViT ONNX output flattening in `app/src/main/java/com/deepshield/ai/ml/ModelManager.kt`.
- Integrated Qualcomm QAIRT TFLite delegate packaging:
  - `app/libs/qtld-release.aar`
  - `app/src/main/jniLibs/arm64-v8a/*QnnHtp*.so`
  - `app/src/main/assets/qnn/*Skel.so`
- Wired EfficientNet to try Qualcomm `QnnDelegate` HTP first, then NNAPI, then CPU.
- Made detection threshold persistence survive restarts in `DetectionSettingsRepository.kt`.
- Verified `:app:compileDebugKotlin` succeeds in this environment.

Still not fully production-complete:
- Full `assembleDebug` / APK build is still blocked intermittently by environment access errors around local Android/Gradle jars in this Codex session.
- Audio detection is still heuristic, not model-backed.
- Privacy content findings and metadata stripping are still session-level heuristics, not a true exported sanitized file workflow.
- Watermark verification is still tied to the latest in-session payload instead of a persistent provenance store.
- Scan history is still in-memory only.
- Share intents are declared in the manifest but not fully consumed in `MainActivity`.
- Video analysis is not actually implemented end-to-end.

Next priority order:
1. Run a full Android build outside the current sandbox limitation and fix any remaining compile/runtime issues.
2. Test Qualcomm HTP delegate on a real Snapdragon arm64 device and verify fallback behavior.
3. Persist scan history and watermark provenance beyond memory.
4. Replace audio/privacy heuristic scoring with real model-backed or rule-backed analysis.
5. Implement real share-intent ingestion and end-to-end video support.

Useful files:
- `app/src/main/java/com/deepshield/ai/ml/ModelManager.kt`
- `app/src/main/java/com/deepshield/ai/data/DetectionSettingsRepository.kt`
- `app/build.gradle.kts`
- `app/src/main/java/com/deepshield/ai/ui/screens/audio/AudioViewModel.kt`
- `app/src/main/java/com/deepshield/ai/ui/screens/privacy/PrivacyViewModel.kt`
- `app/src/main/java/com/deepshield/ai/ui/screens/watermark/WatermarkViewModel.kt`
- `app/src/main/java/com/deepshield/ai/MainActivity.kt`
