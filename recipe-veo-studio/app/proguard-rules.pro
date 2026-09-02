-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature
-dontwarn kotlinx.serialization.**
-keep class kotlinx.serialization.** { *; }
-keep class de.spardirekt.recipeveo.domain.** { *; }
-keepclassmembers class de.spardirekt.recipeveo.domain.** {
    <fields>;
}
