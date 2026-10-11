# Retrofit + kotlinx.serialization:保留泛型签名
-keepattributes Signature
-keepattributes *Annotation*

-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

-keep class dev.avsong.data.api.dto.** { *; }
