-keepattributes *Annotation*, InnerClasses, Signature, Exception
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class de.spardirekt.ugcagent.bridge.NativeBridge { *; }

-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
