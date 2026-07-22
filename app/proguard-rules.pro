# ProGuard rules for Aura AGI
# Keep Accessibility Service
-keep class com.jc.aura.AuraVoiceService { *; }
-keep class com.jc.aura.AuraAccessibilityService { *; }
-keep class com.jc.aura.BootReceiver { *; }

# Keep all modules
-keep class com.jc.aura.** { *; }

# Keep ML Kit
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit.** { *; }

# Keep WebSocket
-keep class org.java_websocket.** { *; }

# Keep Coroutines
-keep class kotlinx.coroutines.** { *; }

# Keep JSON
-keep class org.json.** { *; }

# Keep AndroidX
-keep class androidx.** { *; }

# Keep CameraX
-keep class androidx.camera.** { *; }

# General
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Don't warn
-dontwarn org.java_websocket.**
-dontwarn kotlinx.coroutines.**
