package com.yinnho.upnpcast.model

import com.yinnho.upnpcast.interfaces.Argument
import com.yinnho.upnpcast.interfaces.DLNAAction
import com.yinnho.upnpcast.interfaces.ServiceInterface
import com.yinnho.upnpcast.interfaces.StateVariable
import java.net.URL

/**
 * StateVariable接口实现
 */
class StateVariableImpl(
    override val name: String,
    override val dataType: String,
    override val defaultValue: String? = null,
    override val allowedValues: List<String>? = null,
    override val minimum: Number? = null,
    override val maximum: Number? = null,
    override val step: Number? = null
) : StateVariable {
    private var currentValue: String? = defaultValue

    override fun getValue(): String? = currentValue

    override fun setValue(value: String) {
        this.currentValue = value
    }

    override fun validate(value: Any): Boolean {
        return when {
            allowedValues?.isNotEmpty() == true -> 
                allowedValues.contains(value.toString())
            minimum != null && maximum != null -> {
                val numericValue = value as? Number ?: return false
                val minValue = minimum.toDouble()
                val maxValue = maximum.toDouble()
                numericValue.toDouble() in minValue..maxValue
            }
            else -> true
        }
    }
}

/**
 * DLNAAction接口的实现类
 */
class DLNAActionImpl(
    override val name: String,
    val service: ServiceInterface,
    val inputArguments: List<DLNAAction.ActionArgument>,
    val outputArguments: List<DLNAAction.ActionArgument> = emptyList()
) : DLNAAction {
    // 实现DLNAAction接口所需的arguments属性
    override val arguments: List<DLNAAction.ActionArgument>
        get() = inputArguments + outputArguments
        
    // 存储输入和输出参数的值
    private val inputValues = mutableMapOf<String, String>()
    private val outputValues = mutableMapOf<String, String>()

    // 实现所有必需的接口方法
    override fun setInput(name: String, value: String) {
        val argument = inputArguments.find { it.name == name }
        if (argument == null) {
            throw IllegalArgumentException("Input argument not found: $name")
        }
        
        // 验证状态变量约束
        argument.relatedStateVariable?.let { stateVar ->
            if (!stateVar.validate(value)) {
                throw IllegalArgumentException("Invalid value for $name: $value")
            }
        }
        
        inputValues[name] = value
    }

    override fun getInput(name: String): String? {
        return inputValues[name]
    }

    override fun getOutput(name: String): String? {
        val argument = outputArguments.find { it.name == name }
        if (argument == null) {
            throw IllegalArgumentException("Output argument not found: $name")
        }
        
        return outputValues[name]
    }

    fun getInputMap(): Map<String, String> {
        return inputValues.toMap()
    }

    fun getOutputMap(): Map<String, String> {
        return outputValues.toMap()
    }

    fun invoke(): Boolean {
        // 检查必要输入参数是否已设置
        val missingInputs = inputArguments
            .filter { it.direction == Argument.Direction.IN && !inputValues.containsKey(it.name) }
            .map { it.name }
            
        if (missingInputs.isNotEmpty()) {
            throw IllegalStateException("Missing required input arguments: $missingInputs")
        }
        
        return try {
            // 实际调用服务的execute方法
            val results = service.execute(this)
            
            // 将结果填充到输出参数中
            outputArguments.forEach { arg ->
                if (arg.direction == Argument.Direction.OUT) {
                    val resultValue = results[arg.name]
                    outputValues[arg.name] = resultValue ?: ""
                }
            }
            
            true
        } catch (e: Exception) {
            false
        }
    }

    // 执行动作
    fun execute(args: Map<String, String>): Map<String, String> {
        // 设置参数值
        args.forEach { (name, value) ->
            setInput(name, value)
        }

        // 执行动作并返回结果
        return service.execute(this)
    }

    // 内部实现ActionArgument接口的类
    class ActionArgumentImpl(
        override val name: String,
        override val direction: Argument.Direction,
        override val relatedStateVariable: StateVariable? = null
    ) : DLNAAction.ActionArgument
}

/**
 * DLNA动作工厂类
 * 用于创建DLNA动作
 */
object DLNAActionFactory {
    /**
     * 创建参数
     */
    fun createArgument(
        name: String,
        direction: Argument.Direction,
        relatedStateVariable: StateVariable? = null
    ): DLNAAction.ActionArgument {
        return DLNAActionImpl.ActionArgumentImpl(name, direction, relatedStateVariable)
    }

    /**
     * 创建DLNA动作
     * @param name 动作名称
     * @param service 服务实例
     * @param inputArgs 输入参数列表
     * @param outputArgs 输出参数列表
     * @return DLNA动作实例
     */
    fun createAction(
        name: String,
        service: ServiceInterface,
        inputArgs: List<Pair<String, Argument.Direction>>,
        outputArgs: List<Pair<String, Argument.Direction>> = emptyList()
    ): DLNAActionImpl {
        val inputArguments = inputArgs.map { (argName, direction) ->
            createArgument(argName, direction)
        }
        
        val outputArguments = outputArgs.map { (argName, direction) ->
            createArgument(argName, direction)
        }
        
        return DLNAActionImpl(
            name = name,
            service = service,
            inputArguments = inputArguments,
            outputArguments = outputArguments
        )
    }
}

/**
 * DLNA URL封装类，统一URL类型
 */
class DLNAUrlImpl private constructor(private val javaUrl: URL) {
    val protocol: String get() = javaUrl.protocol
    val host: String get() = javaUrl.host
    val port: Int get() = javaUrl.port
    val path: String get() = javaUrl.path
    val query: String? get() = javaUrl.query

    companion object {
        fun parse(urlString: String): DLNAUrlImpl {
            return DLNAUrlImpl(URL(urlString))
        }

        fun fromJavaUrl(url: URL): DLNAUrlImpl {
            return DLNAUrlImpl(url)
        }
    }

    override fun toString(): String = javaUrl.toString()
}

/**
 * 为保持兼容性，提供类型别名
 */
typealias DLNAUrl = DLNAUrlImpl

/**
 * 网络配置参数
 */
data class NetworkConfig(
    val multicastAddress: String = "239.255.255.250",
    val multicastPort: Int = 1900,
    val searchTimeout: Int = 5000,
    val searchRetries: Int = 3,
    val maxAge: Int = 1800
) 