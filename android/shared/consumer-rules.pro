# Rust exports this exact JNI class/method name. Keep it stable under shrinking.
-keep class app.onklav.shared.NativeCore { *; }

# Shared offline speech JNI names must survive optimized builds.
-keep class app.onklav.home.NeuralSpeechNative { *; }
