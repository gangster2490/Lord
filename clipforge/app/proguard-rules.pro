-keepattributes *Annotation*, InnerClasses, Signature, Exception
-keep class de.spardirekt.clipforge.data.model.** { *; }
-keepclassmembers class de.spardirekt.clipforge.data.model.** { *; }

-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
