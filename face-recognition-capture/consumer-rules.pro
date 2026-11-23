# The engine resolves these by name through JNI.
-keep class com.faceplugin.facerecognitionsdk.** { *; }
-keep class faceplugin.** { *; }
-keepclassmembers class * { native <methods>; }
