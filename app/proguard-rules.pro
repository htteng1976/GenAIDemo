# Keep ML Kit GenAI (alpha) classes — they use internal/reflection paths and
# ship as pre-obfuscated libraries; keep them intact to avoid R8 stripping.
-keep class com.google.mlkit.** { *; }
-keep interface com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_genai_speech.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.internal.mlkit_genai_speech.**
