-keep class com.smith.espoverlay.NativeBridge { *; }
-keepclassmembers class * {
    native <methods>;
}
