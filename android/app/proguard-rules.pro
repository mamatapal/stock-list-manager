# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class app.stocklist.app.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class app.stocklist.app.**$$serializer { *; }
# Retrofit
-keepattributes Signature, Exceptions
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
# SQLCipher
-keep class net.zetetic.database.** { *; }
-keepclassmembers @kotlinx.serialization.Serializable class app.stocklist.app.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
# Retrofit reads the annotations and generic signatures of the service interfaces at run time.
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault, EnclosingMethod
-keep,allowobfuscation interface app.stocklist.app.data.remote.ApiService
-keep,allowobfuscation interface app.stocklist.app.data.remote.AuthApi
-keep,allowobfuscation,allowshrinking class retrofit2.Response
# Optional TLS providers referenced by OkHttp; not used on Android.
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
# Crash reports keep line numbers; file names are hidden.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
