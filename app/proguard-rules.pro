# kotlinx.serialization keeps its own generated serializers via consumer rules.
# Readable stack traces from release crashes (file names and line numbers stay, names are obfuscated).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
# Debug logging is stripped from release builds.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
