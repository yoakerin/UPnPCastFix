package com.yinnho.upnpcast.interfaces

import android.content.Context
import com.yinnho.upnpcast.model.DeviceDetails
import com.yinnho.upnpcast.model.DeviceIdentity
import com.yinnho.upnpcast.model.DeviceType
import com.yinnho.upnpcast.model.RemoteServiceInfo
import com.yinnho.upnpcast.model.ServiceType
import com.yinnho.upnpcast.model.ServiceId

/**
 * 设备接口
 */
interface IDevice {
    val type: DeviceType
    val identity: DeviceIdentity
    val details: DeviceDetails
    val services: List<RemoteServiceInfo>
    val embeddedDevices: List<IDevice>
    val deviceId: String?
    val displayName: String
    val displayString: String

    fun <T : DLNAService> findService(serviceType: ServiceType): T?
    fun findServiceById(serviceId: ServiceId): RemoteServiceInfo?
}

/**
 * 服务接口
 * 提供完整的服务功能
 */
interface ServiceInterface : BaseService {
    /**
     * 服务支持的动作列表
     */
    val actions: List<DLNAAction>
    
    /**
     * 服务支持的状态变量列表
     */
    val stateVariables: List<StateVariable>

    /**
     * 获取指定名称的动作
     */
    fun getAction(name: String): DLNAAction?
    
    /**
     * 获取指定名称的状态变量
     */
    fun getStateVariable(name: String): StateVariable?
    
    /**
     * 执行服务动作
     * @param action 要执行的动作
     * @return 动作执行结果，包含输出参数
     */
    fun execute(action: DLNAAction): Map<String, String>
    
    /**
     * 添加状态变量
     * @param variable 要添加的状态变量
     */
    fun addStateVariable(variable: StateVariable)
}

/**
 * DLNA服务配置接口
 */
interface DLNAServiceConfiguration {
    val baseURL: String
    val streamListenPort: Int
    val multicastResponsePort: Int
    val exclusiveServiceTypes: List<String>

    fun getStreamServerConfiguration(): StreamServerConfiguration
    fun getNetworkAddressFactory(): NetworkAddressFactory
    fun getNamespace(): String
}

/**
 * 流媒体服务器配置接口
 */
interface StreamServerConfiguration {
    val listenPort: Int
    val streamPath: String
}

/**
 * 网络地址工厂接口
 */
interface NetworkAddressFactory {
    fun getMulticastGroup(): String
    fun getMulticastPort(): Int
    fun getStreamListenPort(): Int
}

/**
 * DLNA服务管理器接口
 */
interface DLNAServiceManager {
    var androidUpnpService: AndroidUpnpService?
    var isInitialized: Boolean
    val registry: Registry?
    val controlPoint: ControlPoint?
    val router: Router?
    val configuration: UpnpServiceConfiguration?

    fun searchDevices()
    fun startService(context: Context)
    fun stopService()
    fun bindUpnpService(context: Context): Boolean
    fun unbindUpnpService(context: Context)
    fun addRegistryListener(listener: RegistryListener)
    fun removeRegistryListener(listener: RegistryListener)
    fun setDeviceListener(listener: UnifiedDeviceListener)
    fun onServiceInitialized()
    fun onServiceError(error: Exception)
} 