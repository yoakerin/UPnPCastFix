package com.yinnho.upnpcast.network

import com.yinnho.upnpcast.api.DLNAErrorType
import com.yinnho.upnpcast.api.DLNAException
import com.yinnho.upnpcast.core.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * SOAP请求助手类
 * 精简版：整合了SoapUtils的功能
 */
object SoapHelper {
    private const val TAG = "SoapHelper"
    
    // 创建OkHttpClient实例，设置超时时间
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    // 定义SOAP相关的内容类型和命名空间
    private const val SOAP_ACTION_HEADER = "SOAPAction"
    private const val CONTENT_TYPE_SOAP = "text/xml;charset=utf-8"
    private const val SOAP_NS = "http://schemas.xmlsoap.org/soap/envelope/"
    
    /**
     * 结果数据类，包含请求结果状态和响应内容
     */
    data class SoapResult(
        val isSuccess: Boolean,
        val response: String = "",
        val errorMessage: String = ""
    )
    
    /**
     * 发送SOAP请求
     * @param controlUrl 控制URL地址
     * @param soapAction SOAP动作 (命名空间#动作名)
     * @param soapBody SOAP请求体
     * @return 响应内容
     */
    fun sendSoapRequest(controlUrl: String, soapAction: String, soapBody: String): String {
        try {
            // 简化日志记录
            LogManager.d(TAG, "发送SOAP: $soapAction, URL: $controlUrl")
            
            // 确保SOAPAction包含双引号
            val formattedSoapAction = formatSoapAction(soapAction)
            
            // 构建OkHttp请求
            val requestBody = soapBody.toRequestBody(CONTENT_TYPE_SOAP.toMediaTypeOrNull())
            val request = Request.Builder()
                .url(controlUrl)
                .addHeader("Content-Type", CONTENT_TYPE_SOAP)
                .addHeader(SOAP_ACTION_HEADER, formattedSoapAction)
                .post(requestBody)
                .build()
            
            // 执行请求并获取响应
            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                
                if (!response.isSuccessful) {
                    LogManager.e(TAG, "SOAP失败: ${response.code}")
                    throw DLNAException(DLNAErrorType.COMMUNICATION_ERROR, "SOAP返回错误: ${response.code}")
                }
                
                return responseBody
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "SOAP异常: ${e.message}")
            throw e
        }
    }
    
    /**
     * 包装发送SOAP请求，返回SoapResult
     */
    fun sendSoapRequest(url: String, headers: Map<String, String>, body: String): SoapResult {
        try {
            // 从标准请求头中提取SOAP动作
            val soapAction = headers["SOAPAction"]?.trim('"') ?: ""
            
            // 使用SoapHelper的方法发送请求
            val responseText = sendSoapRequest(url, soapAction, body)
            return SoapResult(true, responseText)
        } catch (e: Exception) {
            LogManager.e(TAG, "SOAP请求异常: ${e.message}")
            return SoapResult(false, "", "异常: ${e.message}")
        }
    }
    
    /**
     * 创建DLNA控制SOAP请求体
     * @param serviceType 服务类型 (例如: "urn:schemas-upnp-org:service:AVTransport:1")
     * @param action 动作名称 (例如: "Play")
     * @param instanceId 实例ID (通常为"0")
     * @param arguments 其他参数 (可选)
     * @return 构建的SOAP请求体
     */
    fun createDlnaControlSoapBody(
        serviceType: String,
        action: String,
        instanceId: String,
        arguments: Map<String, String> = emptyMap()
    ): String {
        val namespacePrefix = "u"
        val actionXml = StringBuilder()
        
        // 添加实例ID参数
        actionXml.append("<InstanceID>$instanceId</InstanceID>")
        
        // 添加其他参数
        arguments.forEach { (name, value) ->
            actionXml.append("<$name>${safeEscapeXml(value)}</$name>")
        }
        
        // 构建完整的SOAP请求体
        return """<?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="$SOAP_NS" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <s:Body>
                    <$namespacePrefix:$action xmlns:$namespacePrefix="$serviceType">
                        $actionXml
                    </$namespacePrefix:$action>
                </s:Body>
            </s:Envelope>""".trimIndent()
    }
    
    /**
     * 安全转义XML特殊字符
     * @param input 输入字符串
     * @return 转义后的字符串
     */
    fun safeEscapeXml(input: String): String {
        if (input.isEmpty()) return ""
        
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
    
    /**
     * 解析SOAP响应中的指定元素值
     * @param soapResponse SOAP响应内容
     * @param elementName 元素名称
     * @return 元素值，如果未找到则返回null
     */
    fun parseElementFromSoapResponse(soapResponse: String, elementName: String): String? {
        val regex = "<$elementName>([^<]+)</$elementName>".toRegex()
        val matchResult = regex.find(soapResponse)
        return matchResult?.groupValues?.get(1)
    }
    
    /**
     * 从响应中提取参数值
     * 与parseElementFromSoapResponse方法相同，提供别名方便使用
     */
    fun extractParam(response: String, paramName: String): String? {
        return parseElementFromSoapResponse(response, paramName)
    }
    
    /**
     * 解析UInt值，提供默认值和边界控制
     */
    fun parseUIntValue(
        response: String, 
        elementName: String, 
        defaultValue: UInt, 
        minValue: UInt = 0u, 
        maxValue: UInt = UInt.MAX_VALUE
    ): UInt {
        val valueStr = parseElementFromSoapResponse(response, elementName)
        return valueStr?.toUIntOrNull()?.coerceIn(minValue, maxValue) ?: defaultValue
    }
    
    /**
     * 解析布尔值，处理多种格式
     */
    fun parseBooleanValue(response: String, elementName: String, defaultValue: Boolean): Boolean {
        val valueStr = parseElementFromSoapResponse(response, elementName)
        return when (valueStr) {
            "1", "true", "True", "TRUE" -> true
            "0", "false", "False", "FALSE" -> false
            else -> defaultValue
        }
    }
    
    /**
     * 标准化SOAP Action格式
     * 确保包含双引号
     */
    fun formatSoapAction(soapAction: String): String {
        return if (soapAction.startsWith("\"") && soapAction.endsWith("\"")) {
            soapAction
        } else {
            "\"$soapAction\""
        }
    }
    
    /**
     * 创建并发送SOAP请求（协程版本）
     */
    suspend fun createAndSendSoapRequest(
        controlUrl: String,
        serviceType: String,
        actionName: String,
        instanceId: String = "0",
        arguments: Map<String, String> = emptyMap(),
        onSuccess: (String) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            LogManager.d(TAG, "SOAP请求: $actionName")
            
            // 使用SoapHelper创建请求体
            val soapBody = createDlnaControlSoapBody(
                serviceType = serviceType,
                action = actionName,
                instanceId = instanceId,
                arguments = arguments
            )
            
            // 标准SOAP Action和请求头
            val headers = mapOf(
                "Content-Type" to "text/xml; charset=\"utf-8\"",
                "SOAPAction" to "\"$serviceType#$actionName\"",
                "Connection" to "keep-alive"
            )
            
            val result = sendSoapRequest(controlUrl, headers, soapBody)
            
            if (result.isSuccess) {
                onSuccess(result.response)
                return@withContext true
            } else {
                LogManager.e(TAG, "SOAP请求失败: ${result.errorMessage}")
                return@withContext false
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "SOAP请求异常: ${e.message}", e)
            return@withContext false
        }
    }
    
    /**
     * 创建并发送SOAP请求，返回响应文本（协程版本）
     */
    suspend fun createAndSendSoapRequestWithResponse(
        controlUrl: String,
        serviceType: String,
        actionName: String,
        instanceId: String = "0",
        arguments: Map<String, String> = emptyMap()
    ): String? = withContext(Dispatchers.IO) {
        try {
            LogManager.d(TAG, "SOAP请求(带响应): $actionName")
            
            // 使用SoapHelper创建请求体
            val soapBody = createDlnaControlSoapBody(
                serviceType = serviceType,
                action = actionName,
                instanceId = instanceId,
                arguments = arguments
            )
            
            // 标准SOAP Action和请求头
            val headers = mapOf(
                "Content-Type" to "text/xml; charset=\"utf-8\"",
                "SOAPAction" to "\"$serviceType#$actionName\"",
                "Connection" to "keep-alive"
            )
            
            val result = sendSoapRequest(controlUrl, headers, soapBody)
            
            if (result.isSuccess) {
                return@withContext result.response
            } else {
                LogManager.e(TAG, "SOAP请求失败: ${result.errorMessage}")
                return@withContext null
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "SOAP请求异常: ${e.message}", e)
            return@withContext null
        }
    }
} 