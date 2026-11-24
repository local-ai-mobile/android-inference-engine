# Proguard rules for android-inference-engine

# Keep llama.cpp JNI bindings and context reflection
-keep class org.nehuatl.llamacpp.** { *; }
-keepclassmembers class org.nehuatl.llamacpp.LlamaAndroid {
    private java.util.Map contexts;
    <fields>;
    <methods>;
}

# Keep LiteRT / TensorFlow Lite classes
-keep class org.tensorflow.lite.** { *; }
-keep class com.google.android.gms.tflite.** { *; }

# Keep Google ML Kit GenAI and AICore prompt / agent classes
-keep class com.google.mlkit.genai.** { *; }

# Keep public API data models and enums
-keep class io.github.localai.inference.api.** { *; }
-keep class io.github.localai.inference.hardware.** { *; }
-keep class io.github.localai.inference.router.** { *; }
-keep class io.github.localai.inference.context.** { *; }
-keep class io.github.localai.inference.monitor.** { *; }
