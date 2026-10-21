# MediaPipe tasks use reflection and protobuf lite internally.
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.mediapipe.**
# LiteRT (TensorFlow Lite) native bindings.
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**
