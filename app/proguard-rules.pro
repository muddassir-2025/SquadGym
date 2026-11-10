# SquadGym release rules.
# Retrofit / OkHttp / kotlinx.serialization keep rules.
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*

# Kotlinx serialization
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep,includedescriptorclasses class com.muddassir.squadgym.**$$serializer { *; }
-keepclassmembers class com.muddassir.squadgym.** {
    *** Companion;
}
-keepclasseswithmembers class com.muddassir.squadgym.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
