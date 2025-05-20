package com.yinnho.upnpcast.interfaces

import java.net.DatagramPacket
import java.net.InetAddress

/**
 * UPnP请求消息接口
 * 定义了处理UPnP请求消息所需的方法
 */
interface UpnpRequest {
    /**
     * 请求方法(例如：NOTIFY, M-SEARCH, SUBSCRIBE)
     */
    val method: String

    /**
     * 请求URI
     */
    val uri: String

    /**
     * 请求头
     */
    val headers: Map<String, String>

    /**
     * 请求体
     */
    val body: String

    /**
     * 获取指定头的值
     */
    fun getHeaderValue(name: String): String?
}

/**
 * UPnP响应消息接口
 * 定义了处理UPnP响应消息所需的方法
 */
interface UpnpResponse {
    /**
     * 响应状态码
     */
    val statusCode: Int

    /**
     * 响应状态消息
     */
    val statusMessage: String

    /**
     * 响应头
     */
    val headers: Map<String, String>

    /**
     * 响应体
     */
    val body: String

    /**
     * 获取指定响应头的值
     */
    fun getHeaderValue(name: String): String?
}

/**
 * 表示接收到的数据报消息
 *
 * @param T 消息内容的类型
 */
interface IncomingDatagramMessage<T> {
    /**
     * 获取消息的数据内容
     */
    val operation: T

    /**
     * 获取发送者的地址
     */
    val senderAddress: InetAddress

    /**
     * 获取发送者的端口
     */
    val senderPort: Int

    /**
     * 获取接收此消息的本地地址
     */
    val localAddress: InetAddress

    /**
     * 获取接收此消息的本地端口
     */
    val localPort: Int

    /**
     * 获取原始的数据报包
     */
    val datagramPacket: DatagramPacket

    /**
     * 获取原始消息数据
     */
    fun getData(): ByteArray

    /**
     * 原始消息的字符串表示
     */
    fun getBodyString(): String
}

/**
 * 数据报处理器接口
 * 定义了处理UDP数据报的方法
 */
interface DatagramProcessor {
    /**
     * 发送搜索请求
     */
    fun sendSearchRequest(multicastAddress: String, multicastPort: Int)

    /**
     * 处理接收到的数据包
     */
    fun process(receivedOnAddress: InetAddress, packet: DatagramPacket)

    /**
     * 发送数据包
     */
    fun send(packet: DatagramPacket)

    /**
     * 检查是否处理控制消息
     */
    fun isProcessingControl(packet: DatagramPacket): Boolean

    /**
     * 检查是否处理事件消息
     */
    fun isProcessingEvent(packet: DatagramPacket): Boolean

    /**
     * 关闭处理器
     */
    fun shutdown()

    /**
     * 检查处理器是否已关闭
     */
    fun isShutDown(): Boolean

    companion object {
        const val DEFAULT_UDP_PACKET_SIZE = 640
        const val DEFAULT_BUFFER_SIZE_SECONDS = 2
    }
} 