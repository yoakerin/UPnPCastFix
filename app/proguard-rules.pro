# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# UPnPCast Library ProGuard Rules

# 保留公共API类和方法
-keep public class com.yinnho.upnpcast.DLNACastManager {
    public *;
}

-keep public class com.yinnho.upnpcast.RemoteDevice {
    public *;
}

-keep public interface com.yinnho.upnpcast.CastListener {
    *;
}

-keep public interface com.yinnho.upnpcast.PlaybackStateListener {
    *;
}

-keep public class com.yinnho.upnpcast.DLNAException {
    public *;
}

-keep public enum com.yinnho.upnpcast.PlaybackState {
    *;
}

-keep public enum com.yinnho.upnpcast.DLNAErrorType {
    *;
}

# 保留Kotlin相关
-dontwarn kotlin.**
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }

# 保留协程相关
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keep class kotlinx.coroutines.** { *; }

# 保留网络相关类
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**

# 保留Gson相关
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# 保留DLNA/UPnP相关的反射类
-keep class com.yinnho.upnpcast.internal.DeviceDescriptionParser$ServiceInfo {
    *;
}

# 通用规则
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# 避免混淆泛型
-keepattributes Signature

# 保留异常信息
-keepattributes Exceptions