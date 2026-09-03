# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\Users\MaphutiTeffo\AppData\Local\Android\Sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.

# OpenAI / Retrofit / Gson
-keepattributes Signature, InnerClasses, AnnotationDefault, EnclosingMethod
-keep class com.maphutimoviousteffo.wizprly.network.** { *; }
-keep class com.maphutimoviousteffo.wizprly.data.** { *; }

# Room
-keep class androidx.room.RoomDatabase { *; }

# PDFBox / POI
-dontwarn com.tom_roush.pdfbox.**
-dontwarn org.apache.poi.**
-keep class com.tom_roush.pdfbox.** { *; }
-keep class org.apache.poi.** { *; }
