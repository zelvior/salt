# Salt — default ProGuard rules (minification is OFF in Phase 1, kept for later).
-keepattributes *Annotation*,Signature

# Keep declarative plugin models (JSON-serialized) and AI tool-call contracts.
-keep class com.zelvior.salt.core.plugins.** { *; }
-keep class com.zelvior.salt.core.ai.** { *; }

# Shizuku uses AIDL; silence missing-class warnings for the bridge.
-dontwarn dev.rikka.shizuku.**
-dontwarn rikka.shizuku.**
