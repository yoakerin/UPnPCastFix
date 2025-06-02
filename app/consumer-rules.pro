# UPnPCast Consumer ProGuard Rules
# 这些规则会自动应用到使用UPnPCast库的应用中

# 保留UPnPCast主API
-keep public class com.yinnho.upnpcast.DLNACast { public *; }

# 保留所有对外数据类型
-keep class com.yinnho.upnpcast.types.** { *; } 