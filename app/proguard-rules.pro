# Keep the JNI entry points referenced from Kotlin.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-keep class com.codotype.jarvis.llm.LlamaBridge { *; }
-keep class com.codotype.jarvis.stt.WhisperBridge { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.codotype.jarvis.** {
    *** Companion;
}
-keepclasseswithmembers class com.codotype.jarvis.** {
    kotlinx.serialization.KSerializer serializer(...);
}
