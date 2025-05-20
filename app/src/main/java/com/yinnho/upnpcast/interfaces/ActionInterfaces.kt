package com.yinnho.upnpcast.interfaces

/**
 * 动作参数
 */
interface Argument {
    val name: String
    val direction: Direction
    val relatedStateVariable: StateVariable?

    enum class Direction {
        IN, OUT
    }
}

/**
 * DLNA动作接口，基于Cling的ActionExecutor设计
 */
interface DLNAAction {
    val name: String
    val arguments: List<ActionArgument>

    fun setInput(name: String, value: String)
    fun getInput(name: String): String?
    fun getOutput(name: String): String?

    interface ActionArgument {
        val name: String
        val direction: Argument.Direction
        val relatedStateVariable: StateVariable?
    }
}

/**
 * DLNA状态变量接口
 */
interface StateVariable {
    val name: String
    val dataType: String
    val defaultValue: String?
    val allowedValues: List<String>?
    val minimum: Number?
    val maximum: Number?
    val step: Number?

    fun getValue(): String?
    fun setValue(value: String)
    fun validate(value: Any): Boolean
} 