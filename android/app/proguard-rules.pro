-keepattributes *Annotation*, InnerClasses, Signature, Exception
-keep class de.spardirekt.tiktokshop.data.model.** { *; }
-keepclassmembers class de.spardirekt.tiktokshop.data.model.** { *; }

-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
