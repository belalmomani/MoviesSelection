-keepattributes *Annotation*, InnerClasses
-dontwarn kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.tvseries.Movies.**$$serializer { *; }
-keepclassmembers class com.tvseries.Movies.** { *** Companion; }
-keepclasseswithmembers class com.tvseries.Movies.** { kotlinx.serialization.KSerializer serializer(...); }
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Trailer player: JavaScript bridge methods must keep their names (called from the page by name).
-keepclassmembers class com.tvseries.Movies.ui.TrailerBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface
# Ads SDK (only present when ADS_ENABLED=true)
-keep class com.startapp.** { *; }
-dontwarn com.startapp.**
