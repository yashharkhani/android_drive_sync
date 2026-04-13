# Add project specific ProGuard rules here.
-keep class com.google.api.** { *; }
-keep class com.google.gson.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.api.**
-dontwarn com.google.gson.**
-dontwarn org.apache.**
-dontwarn javax.annotation.**
