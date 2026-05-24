# ProGuard rules for DeepShield AI

# Keep TFLite classes
-keep class org.tensorflow.** { *; }
-keepclassmembers class org.tensorflow.** { *; }

# Keep ONNX Runtime classes
-keep class ai.onnxruntime.** { *; }

# Keep Bouncy Castle crypto
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Keep Room entities
-keep class com.deepshield.ai.data.local.model.** { *; }

# Keep domain models (used in serialization)
-keep class com.deepshield.ai.domain.model.** { *; }

# Keep ML classes
-keep class com.deepshield.ai.ml.** { *; }

# Keep Watermark engine
-keep class com.deepshield.ai.watermark.** { *; }

# iText PDF
-dontwarn com.itextpdf.**
-keep class com.itextpdf.** { *; }

# SQLCipher
-keep class net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**
