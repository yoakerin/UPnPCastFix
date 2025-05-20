package com.yinnho.upnpcast.service

import android.util.Log
import com.yinnho.upnpcast.interfaces.Argument
import com.yinnho.upnpcast.interfaces.DLNAAction
import com.yinnho.upnpcast.model.DLNAActionFactory
import com.yinnho.upnpcast.model.DLNAActionImpl
import com.yinnho.upnpcast.model.DLNAUrl
import com.yinnho.upnpcast.model.ServiceType
import com.yinnho.upnpcast.network.SoapHelper

/**
 * 渲染控制服务实现类
 * 精简版：使用通用方法减少冗余代码
 */
class RenderingControlServiceImpl(
    controlURL: DLNAUrl,
    eventSubURL: DLNAUrl,
    SCPDURL: DLNAUrl
) : BaseDLNAServiceImpl(controlURL, eventSubURL, SCPDURL), RenderingControlService {
    
    override val TAG = "RenderingControlServiceImpl"
    override val SERVICE_TYPE = "urn:schemas-upnp-org:service:RenderingControl:1"
    
    private var currentVolume: UInt = 50u
    private var currentMute: Boolean = false
    private var currentBrightness: UInt = 50u
    private var currentContrast: UInt = 50u

    companion object {
        private const val MASTER_CHANNEL = "Master"
        private const val MIN_VALUE = 0u
        private const val MAX_VALUE = 100u
    }
    
    override val serviceType: String = ServiceType.RENDERING_CONTROL.toString()
    override val serviceId: String = "RenderingControl"
    
    // 使用DLNAActionFactory创建动作
    private val _actions = listOf(
        DLNAActionFactory.createAction(
            "SetVolume", 
            this, 
            listOf(
                "InstanceID" to Argument.Direction.IN,
                "Channel" to Argument.Direction.IN,
                "DesiredVolume" to Argument.Direction.IN
            )
        ),
        DLNAActionFactory.createAction(
            "GetVolume", 
            this, 
            listOf(
                "InstanceID" to Argument.Direction.IN,
                "Channel" to Argument.Direction.IN
            ),
            listOf(
                "CurrentVolume" to Argument.Direction.OUT
            )
        ),
        DLNAActionFactory.createAction(
            "SetMute", 
            this, 
            listOf(
                "InstanceID" to Argument.Direction.IN,
                "Channel" to Argument.Direction.IN,
                "DesiredMute" to Argument.Direction.IN
            )
        ),
        DLNAActionFactory.createAction(
            "GetMute", 
            this, 
            listOf(
                "InstanceID" to Argument.Direction.IN,
                "Channel" to Argument.Direction.IN
            ),
            listOf(
                "CurrentMute" to Argument.Direction.OUT
            )
        )
    )
    override val actions: List<DLNAAction> = _actions

    // 执行动作的方法
    override fun execute(action: DLNAAction): Map<String, String> {
        try {
            // 提取输入参数
            val inputs = (action as? DLNAActionImpl)?.getInputMap() ?: emptyMap()
            val instanceId = inputs["InstanceID"] ?: "0"
            val channel = inputs["Channel"] ?: MASTER_CHANNEL
            
            return when (action.name) {
                "SetVolume" -> {
                    val volume = inputs["DesiredVolume"]?.toUIntOrNull() ?: currentVolume
                    setVolume(instanceId, channel, volume)
                    mapOf("Result" to "OK")
                }
                "GetVolume" -> {
                    val volume = getVolume(instanceId, channel)
                    mapOf("CurrentVolume" to volume.toString())
                }
                "SetMute" -> {
                    val mute = when (inputs["DesiredMute"]) {
                        "1", "true", "True", "TRUE" -> true
                        else -> false
                    }
                    setMute(instanceId, channel, mute)
                    mapOf("Result" to "OK")
                }
                "GetMute" -> {
                    val mute = getMute(instanceId, channel)
                    mapOf("CurrentMute" to if (mute) "1" else "0")
                }
                else -> mapOf("Error" to "未知动作: ${action.name}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "执行${action.name}失败", e)
            return mapOf("Error" to "执行失败: ${e.message}")
        }
    }

    /**
     * 通用方法，获取UInt类型的属性
     */
    private fun getUIntProperty(
        actionName: String,
        instanceId: String,
        responseElement: String,
        currentValue: UInt,
        additionalArgs: Map<String, String> = emptyMap()
    ): UInt {
        var result = currentValue
        
        try {
            val response = executeSoapAction(
                actionName = actionName,
                instanceId = instanceId,
                additionalArgs = additionalArgs
            )
            
            val value = SoapHelper.parseUIntValue(
                response,
                responseElement,
                currentValue,
                MIN_VALUE,
                MAX_VALUE
            )
            result = value
        } catch (e: Exception) {
            Log.e(TAG, "获取属性失败", e)
        }
        
        return result
    }

    // 音量控制方法
    override fun setVolume(instanceId: String, channel: String, desiredVolume: UInt) {
        try {
            executeSoapAction(
                actionName = "SetVolume",
                instanceId = instanceId,
                additionalArgs = mapOf(
                    "Channel" to channel,
                    "DesiredVolume" to desiredVolume.toString()
                )
            )
            
            if (channel == MASTER_CHANNEL) {
                currentVolume = desiredVolume.coerceIn(MIN_VALUE, MAX_VALUE)
            }
        } catch (e: Exception) {
            Log.e(TAG, "设置音量失败", e)
        }
    }

    override fun getVolume(instanceId: String, channel: String): UInt {
        if (channel != MASTER_CHANNEL) {
            return currentVolume
        }
        
        return getUIntProperty(
            "GetVolume", 
            instanceId, 
            "CurrentVolume", 
            currentVolume, 
            mapOf("Channel" to channel)
        ).also { currentVolume = it }
    }

    override fun setMute(instanceId: String, channel: String, desiredMute: Boolean) {
        try {
            executeSoapAction(
                actionName = "SetMute",
                instanceId = instanceId,
                additionalArgs = mapOf(
                    "Channel" to channel,
                    "DesiredMute" to if (desiredMute) "1" else "0"
                )
            )
            
            if (channel == MASTER_CHANNEL) {
                currentMute = desiredMute
            }
        } catch (e: Exception) {
            Log.e(TAG, "设置静音失败", e)
        }
    }

    override fun getMute(instanceId: String, channel: String): Boolean {
        if (channel != MASTER_CHANNEL) {
            return currentMute
        }
        
        var result = currentMute
        
        try {
            val response = executeSoapAction(
                actionName = "GetMute",
                instanceId = instanceId,
                additionalArgs = mapOf("Channel" to channel)
            )
            
            val muteBool = SoapHelper.parseBooleanValue(response, "CurrentMute", currentMute)
            currentMute = muteBool
            result = muteBool
        } catch (e: Exception) {
            Log.e(TAG, "获取静音状态失败", e)
        }
        
        return result
    }

    // 亮度控制方法
    override fun setBrightness(instanceId: String, desiredBrightness: UInt) {
        try {
            executeSoapAction(
                actionName = "SetBrightness",
                instanceId = instanceId,
                additionalArgs = mapOf("DesiredBrightness" to desiredBrightness.toString())
            )
            
            currentBrightness = desiredBrightness.coerceIn(MIN_VALUE, MAX_VALUE)
        } catch (e: Exception) {
            Log.e(TAG, "设置亮度失败", e)
        }
    }

    // 对比度控制方法
    override fun setContrast(instanceId: String, desiredContrast: UInt) {
        try {
            executeSoapAction(
                actionName = "SetContrast",
                instanceId = instanceId,
                additionalArgs = mapOf("DesiredContrast" to desiredContrast.toString())
            )
            
            currentContrast = desiredContrast.coerceIn(MIN_VALUE, MAX_VALUE)
        } catch (e: Exception) {
            Log.e(TAG, "设置对比度失败", e)
        }
    }

    override fun getRenderingControlInfo(instanceId: String): RenderingControlInfo {
        return RenderingControlInfo(
            currentVolume = currentVolume,
            currentMute = currentMute,
            currentBrightness = currentBrightness,
            currentContrast = currentContrast,
            minVolume = MIN_VALUE,
            maxVolume = MAX_VALUE,
            minBrightness = MIN_VALUE,
            maxBrightness = MAX_VALUE,
            minContrast = MIN_VALUE,
            maxContrast = MAX_VALUE
        )
    }
    
    override fun getAction(name: String): DLNAAction? {
        return _actions.find { it.name == name }
    }
    
    override fun getBrightness(instanceId: String): UInt = 
        getUIntProperty("GetBrightness", instanceId, "CurrentBrightness", currentBrightness)
            .also { currentBrightness = it }
    
    override fun getContrast(instanceId: String): UInt = 
        getUIntProperty("GetContrast", instanceId, "CurrentContrast", currentContrast)
            .also { currentContrast = it }
}