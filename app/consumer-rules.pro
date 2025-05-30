# UPnPCast Consumer ProGuard Rules
# 这些规则会自动应用到使用UPnPCast库的应用中

# 保留UPnPCast公共API
-keep public class com.yinnho.upnpcast.** {
    public *;
}

# 保留监听器接口
-keep interface com.yinnho.upnpcast.CastListener { *; }
-keep interface com.yinnho.upnpcast.PlaybackStateListener { *; }

# 保留枚举类
-keep enum com.yinnho.upnpcast.PlaybackState { *; }
-keep enum com.yinnho.upnpcast.DLNAErrorType { *; }

# 保留异常类
-keep class com.yinnho.upnpcast.DLNAException { *; }

# 保留数据类
-keep class com.yinnho.upnpcast.RemoteDevice { *; } 