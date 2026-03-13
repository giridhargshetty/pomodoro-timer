# Wild Tribe Drive ProGuard rules

# Keep BLE related classes
-keep class com.wildtribe.drive.ble.** { *; }
-keep class com.wildtribe.drive.model.** { *; }
-keep class com.wildtribe.drive.receiver.** { *; }

# Keep Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
    <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}

# Keep Android BLE classes
-keep class android.bluetooth.** { *; }

# Keep Play Review
-keep class com.google.android.play.core.review.** { *; }

# Keep annotations
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}
