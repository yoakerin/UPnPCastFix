package com.yinnho.upnpcast.api

/**
 * DLNA异常类
 */
class DLNAException(
    val errorType: DLNAErrorType,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    companion object {
        /**
         * 创建设备发现相关错误
         */
        fun discoveryError(message: String, cause: Throwable? = null): DLNAException {
            return DLNAException(DLNAErrorType.DISCOVERY_ERROR, message, cause)
        }
        
        /**
         * 创建连接相关错误
         */
        fun connectionError(message: String, cause: Throwable? = null): DLNAException {
            return DLNAException(DLNAErrorType.CONNECTION_ERROR, message, cause)
        }
        
        /**
         * 创建播放控制相关错误
         */
        fun playbackError(message: String, cause: Throwable? = null): DLNAException {
            return DLNAException(DLNAErrorType.PLAYBACK_ERROR, message, cause)
        }
        
        /**
         * 创建网络相关错误
         */
        fun networkError(message: String, cause: Throwable? = null): DLNAException {
            return DLNAException(DLNAErrorType.NETWORK_ERROR, message, cause)
        }
        
        /**
         * 创建设备相关错误
         */
        fun deviceError(message: String, cause: Throwable? = null): DLNAException {
            return DLNAException(DLNAErrorType.DEVICE_ERROR, message, cause)
        }
        
        /**
         * 创建未知错误
         */
        fun unknownError(message: String, cause: Throwable? = null): DLNAException {
            return DLNAException(DLNAErrorType.UNKNOWN_ERROR, message, cause)
        }
        
        /**
         * 创建设备兼容性相关错误
         */
        fun compatibilityError(message: String, cause: Throwable? = null): DLNAException {
            return DLNAException(DLNAErrorType.COMPATIBILITY_ERROR, message, cause)
        }
        
        /**
         * 创建控制相关错误
         */
        fun controlError(message: String, cause: Throwable? = null): DLNAException {
            return DLNAException(DLNAErrorType.CONTROL_ERROR, message, cause)
        }
        
        /**
         * 创建参数无效错误
         */
        fun invalidParameterError(message: String, cause: Throwable? = null): DLNAException {
            return DLNAException(DLNAErrorType.INVALID_PARAMETER, message, cause)
        }
    }
} 