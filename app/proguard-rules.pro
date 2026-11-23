# JNI entry points into the Cactus engine shim
-keep class app.nimble.engine.CactusNative { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
