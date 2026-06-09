# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the Android SDK tools proguard-defaults.txt
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep MQTT classes (Paho uses reflection internally)
-keep class org.eclipse.paho.** { *; }
-dontwarn org.eclipse.paho.**

# Keep Gson serialization (uses reflection to read @SerializedName)
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.blex.app.BeaconData { *; }
-keep class com.blex.app.ScanBatch { *; }
