# ----------------------------
# PDFLite AI - release shrink rules
# ----------------------------

# Keep PDFBox Android (safe; prevents R8 from stripping internal classes)
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**

# ML Kit (Play services / unbundled)
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ML Kit internal (often referenced via reflection / generated)
-keep class com.google.android.gms.internal.mlkit_** { *; }
-dontwarn com.google.android.gms.internal.mlkit_**

# Kotlinx Serialization (only if you use @Serializable models)
-keepclassmembers class ** {
    @kotlinx.serialization.Serializable *;
}
-dontwarn kotlinx.serialization.**