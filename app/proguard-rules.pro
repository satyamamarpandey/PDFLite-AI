# --- Kotlinx Serialization ---
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class kotlinx.serialization.** { *; }
-keep class kotlinx.serialization.** { *; }

# --- ML Kit Text Recognition ---
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**
-dontwarn com.google.android.gms.internal.**

# --- PDFBox Android ---
-dontwarn org.bouncycastle.**
-dontwarn javax.xml.bind.**
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.harmony.awt.** { *; }
-keep class com.tom_roush.fontbox.** { *; }