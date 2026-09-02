-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature
-dontwarn kotlinx.serialization.**
-keep class kotlinx.serialization.** { *; }
-keep class de.spardirekt.svoe.domain.** { *; }
-keepclassmembers class de.spardirekt.svoe.domain.** {
    <fields>;
}
