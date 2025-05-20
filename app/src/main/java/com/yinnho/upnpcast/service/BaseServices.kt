package com.yinnho.upnpcast.service

import android.content.Context
import android.util.Log
import com.yinnho.upnpcast.core.DefaultControlPoint
import com.yinnho.upnpcast.core.DefaultUpnpServiceConfiguration
import com.yinnho.upnpcast.interfaces.ControlPoint
import com.yinnho.upnpcast.interfaces.DLNAAction
import com.yinnho.upnpcast.interfaces.PositionInfo
import com.yinnho.upnpcast.interfaces.Registry
import com.yinnho.upnpcast.interfaces.Router
import com.yinnho.upnpcast.interfaces.ServiceInterface
import com.yinnho.upnpcast.interfaces.StateVariable
import com.yinnho.upnpcast.interfaces.UpnpService
import com.yinnho.upnpcast.interfaces.UpnpServiceConfiguration
import com.yinnho.upnpcast.model.DLNAUrl
import com.yinnho.upnpcast.model.PositionInfoImpl
import com.yinnho.upnpcast.network.RouterImpl
import com.yinnho.upnpcast.manager.RegistryImpl
import com.yinnho.upnpcast.network.SoapHelper

/**
 * 默认UPnP服务实现
 * 精简版：提供UPnP服务的最小实现
 */
class DefaultUpnpService(
    override val configuration: UpnpServiceConfiguration = DefaultUpnpServiceConfiguration.getInstance()
) : UpnpService {
    override val registry: Registry = RegistryImpl.getInstance()
    override val router: Router = RouterImpl.getInstance(configuration)
    override val controlPoint: ControlPoint by lazy {
        DefaultControlPoint(configuration)
    }

    override fun shutdown() {
        registry.shutdown()
        controlPoint.shutdown()
    }
}

/**
 * 基础DLNA服务实现类
 * 精简版：提供最简化的SOAP操作执行
 */
abstract class BaseDLNAServiceImpl(
    override val controlURL: DLNAUrl,
    override val eventSubURL: DLNAUrl,
    override val SCPDURL: DLNAUrl,
    override val descriptorURL: DLNAUrl = SCPDURL
) : ServiceInterface {
    
    protected abstract val TAG: String
    protected abstract val SERVICE_TYPE: String
    
    private val _stateVariables = mutableMapOf<String, StateVariable>()
    override val stateVariables: List<StateVariable>
        get() = _stateVariables.values.toList()

    /**
     * 执行SOAP动作
     * 简化版：减少参数和日志
     */
    protected fun executeSoapAction(
        actionName: String,
        instanceId: String,
        additionalArgs: Map<String, String> = emptyMap()
    ): String {
        try {
            // 发送SOAP请求
            val result = SoapHelper.sendSoapRequest(
                controlURL.toString(),
                mapOf(
                    "SOAPAction" to "\"$SERVICE_TYPE#$actionName\"",
                    "Content-Type" to "text/xml; charset=\"utf-8\""
                ),
                SoapHelper.createDlnaControlSoapBody(
                    SERVICE_TYPE, 
                    actionName,
                    instanceId,
                    additionalArgs
                )
            )

            if (result.isSuccess) {
                return result.response
            } else {
                throw IllegalStateException("SOAP请求失败: ${result.errorMessage}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "执行 $actionName 失败: ${e.message}")
            throw IllegalStateException("Failed to execute $actionName", e)
        }
    }
    
    // 默认的通用实现
    override fun execute(action: DLNAAction): Map<String, String> {
        return emptyMap()
    }
    
    override fun addStateVariable(variable: StateVariable) {
        _stateVariables[variable.name] = variable
    }
    
    override fun getStateVariable(name: String): StateVariable? {
        return _stateVariables[name]
    }
    
    // 获取动作的默认实现
    override fun getAction(name: String): DLNAAction? {
        return actions.find { it.name == name }
    }
    
    // 这些是默认的空实现，子类根据需要重写
    open fun play(instanceId: String, speed: String) {}
    
    open fun pause(instanceId: String) {}
    
    open fun stop(instanceId: String) {}
    
    open fun seek(instanceId: String, target: String) {}
    
    open fun setAVTransportURI(instanceId: String, uri: String, metadata: String) {}
    
    open fun getPositionInfo(instanceId: String): PositionInfo = PositionInfoImpl(
        track = 0u,
        trackDuration = "00:00:00",
        trackMetaData = "",
        trackURI = "",
        relTime = "00:00:00",
        absTime = "00:00:00",
        relCount = 0,
        absCount = 0
    )
} 