############################################
# Kotlinx Serialization
############################################
-keepclassmembers class **$$serializer { *; }
-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**

############################################
# ML Kit Text Recognition
############################################
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**
-dontwarn com.google.android.gms.internal.**

############################################
# PDFBox Android
############################################
-dontwarn org.bouncycastle.**
-dontwarn javax.xml.bind.**

-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.harmony.awt.** { *; }
-keep class com.tom_roush.fontbox.** { *; }

# JPX (JPEG2000) optional decoder missing
-dontwarn com.gemalto.jp2.**
-dontwarn org.apache.pdfbox.jpx.**

############################################
# Optional: Android PDF Viewer (safe)
############################################
-dontwarn com.github.barteksc.pdfviewer.**
-keep class com.github.barteksc.pdfviewer.** { *; }

############################################
# Credential Manager / GoogleID (safe)
############################################
-dontwarn androidx.credentials.**
-dontwarn com.google.android.libraries.identity.googleid.**