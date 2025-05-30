package com.yinnho.upnpcast.demo

// 使用库中的真实DLNACastManager实现
// 通过类型别名重新导出真实的类，保持demo代码兼容性
typealias DLNACastManager = com.yinnho.upnpcast.DLNACastManager
typealias RemoteDevice = com.yinnho.upnpcast.RemoteDevice
typealias CastListener = com.yinnho.upnpcast.CastListener
typealias PlaybackStateListener = com.yinnho.upnpcast.PlaybackStateListener
typealias PlaybackState = com.yinnho.upnpcast.PlaybackState
typealias DLNAException = com.yinnho.upnpcast.DLNAException 