package com.yinnho.upnpcast.core

import android.util.Log
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.StringReader
import javax.xml.parsers.SAXParserFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * XML解析工具类
 * 使用SAX解析器提高大型XML解析性能
 */
object XmlParser {
    private const val TAG = "XmlParser"
    
    // 解析器缓存
    private val parserFactory = SAXParserFactory.newInstance()
    
    // XML命名空间缓存，避免重复解析
    private val namespaceCache = ConcurrentHashMap<String, String>()
    
    /**
     * 解析XML字符串为键值对映射
     * @param xml XML字符串
     * @param rootElement 根元素名称，不指定则解析所有元素
     * @return XML元素和值的映射
     */
    fun parseToMap(xml: String, rootElement: String? = null): Map<String, String> {
        try {
            val handler = MapContentHandler(rootElement)
            parse(xml.byteInputStream(Charsets.UTF_8), handler)
            return handler.result
        } catch (e: Exception) {
            Log.e(TAG, "解析XML为映射失败", e)
            return emptyMap()
        }
    }
    
    /**
     * 解析XML并提取指定元素的内容
     * @param xml XML字符串
     * @param targetElements 目标元素名称列表
     * @return 元素名称到内容的映射
     */
    fun extractElements(xml: String, vararg targetElements: String): Map<String, String> {
        try {
            val handler = ElementExtractHandler(*targetElements)
            parse(xml.byteInputStream(Charsets.UTF_8), handler)
            return handler.result
        } catch (e: Exception) {
            Log.e(TAG, "提取XML元素失败", e)
            return emptyMap()
        }
    }
    
    /**
     * 解析XML中指定路径的元素
     * @param xml XML字符串
     * @param path 元素路径，如"root/parent/child"
     * @return 匹配的元素内容
     */
    fun extractByPath(xml: String, path: String): String {
        try {
            val pathParts = path.split("/")
            val handler = PathExtractHandler(pathParts)
            parse(xml.byteInputStream(Charsets.UTF_8), handler)
            return handler.result
        } catch (e: Exception) {
            Log.e(TAG, "通过路径提取XML元素失败: $path", e)
            return ""
        }
    }
    
    /**
     * 将XML解析为结构化对象
     * @param xml XML字符串
     * @param factory 对象工厂，用于创建目标对象
     * @return 解析后的对象或null
     */
    fun <T> parseToObject(xml: String, factory: ObjectFactory<T>): T? {
        try {
            val handler = ObjectContentHandler(factory)
            parse(xml.byteInputStream(Charsets.UTF_8), handler)
            return handler.result
        } catch (e: Exception) {
            Log.e(TAG, "解析XML为对象失败", e)
            return null
        }
    }
    
    /**
     * 检查XML中是否包含指定元素
     * @param xml XML字符串
     * @param elementName 元素名称
     * @return 是否包含
     */
    fun containsElement(xml: String, elementName: String): Boolean {
        try {
            val handler = ElementCheckHandler(elementName)
            parse(xml.byteInputStream(Charsets.UTF_8), handler)
            return handler.found
        } catch (e: Exception) {
            Log.e(TAG, "检查XML是否包含元素失败: $elementName", e)
            return false
        }
    }
    
    /**
     * 解析XML
     * @param inputStream XML输入流
     * @param handler SAX处理器
     */
    private fun parse(inputStream: InputStream, handler: DefaultHandler) {
        try {
            val parser = parserFactory.newSAXParser()
            parser.parse(inputStream, handler)
        } catch (e: Exception) {
            when (e) {
                is SAXException -> Log.e(TAG, "SAX解析异常", e)
                else -> throw e
            }
        }
    }
    
    /**
     * 对象工厂接口
     */
    interface ObjectFactory<T> {
        /**
         * 创建对象
         * @return 新对象
         */
        fun createObject(): T
        
        /**
         * 设置属性
         * @param obj 目标对象
         * @param name 属性名
         * @param value 属性值
         */
        fun setProperty(obj: T, name: String, value: String)
        
        /**
         * 添加子对象
         * @param parent 父对象
         * @param child 子对象
         * @param name 子对象名称
         */
        fun addChild(parent: T, child: T, name: String) {}
    }
    
    /**
     * 映射内容处理器
     * 将XML元素解析为键值对映射
     */
    private class MapContentHandler(private val rootElement: String? = null) : DefaultHandler() {
        val result = mutableMapOf<String, String>()
        private val elementStack = mutableListOf<String>()
        private val contentBuffer = StringBuilder()
        private var isParsingRoot = rootElement == null
        
        override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
            if (!isParsingRoot && qName == rootElement) {
                isParsingRoot = true
            }
            
            if (isParsingRoot) {
                elementStack.add(qName)
                contentBuffer.clear()
            }
        }
        
        override fun endElement(uri: String, localName: String, qName: String) {
            if (isParsingRoot) {
                if (elementStack.isNotEmpty() && elementStack.last() == qName) {
                    if (contentBuffer.isNotEmpty()) {
                        result[qName] = contentBuffer.toString().trim()
                    }
                    elementStack.removeAt(elementStack.size - 1)
                }
                
                if (qName == rootElement) {
                    isParsingRoot = false
                }
            }
        }
        
        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (isParsingRoot && elementStack.isNotEmpty()) {
                contentBuffer.append(ch, start, length)
            }
        }
    }
    
    /**
     * 元素提取处理器
     * 提取特定命名的元素内容
     */
    private class ElementExtractHandler(vararg targetElements: String) : DefaultHandler() {
        val result = mutableMapOf<String, String>()
        private val targets = targetElements.toSet()
        private var currentElement: String? = null
        private val contentBuffer = StringBuilder()
        
        override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
            currentElement = qName
            contentBuffer.clear()
        }
        
        override fun endElement(uri: String, localName: String, qName: String) {
            if (targets.contains(qName) && contentBuffer.isNotEmpty()) {
                result[qName] = contentBuffer.toString().trim()
            }
            currentElement = null
        }
        
        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (currentElement != null && targets.contains(currentElement)) {
                contentBuffer.append(ch, start, length)
            }
        }
    }
    
    /**
     * 路径提取处理器
     * 提取指定路径的元素内容
     */
    private class PathExtractHandler(private val pathParts: List<String>) : DefaultHandler() {
        var result = ""
        private val elementStack = mutableListOf<String>()
        private val contentBuffer = StringBuilder()
        
        override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
            elementStack.add(qName)
            
            if (isTargetPath()) {
                contentBuffer.clear()
            }
        }
        
        override fun endElement(uri: String, localName: String, qName: String) {
            if (isTargetPath() && contentBuffer.isNotEmpty()) {
                result = contentBuffer.toString().trim()
            }
            
            elementStack.removeAt(elementStack.size - 1)
        }
        
        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (isTargetPath()) {
                contentBuffer.append(ch, start, length)
            }
        }
        
        private fun isTargetPath(): Boolean {
            if (elementStack.size != pathParts.size) return false
            
            return elementStack.zip(pathParts).all { (element, target) ->
                element == target
            }
        }
    }
    
    /**
     * 元素检查处理器
     * 检查XML中是否存在指定元素
     */
    private class ElementCheckHandler(private val targetElement: String) : DefaultHandler() {
        var found = false
        
        override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
            if (qName == targetElement) {
                found = true
            }
        }
    }
    
    /**
     * 对象内容处理器
     * 将XML解析为对象
     */
    private class ObjectContentHandler<T>(private val factory: ObjectFactory<T>) : DefaultHandler() {
        var result: T? = null
        private val objectStack = mutableListOf<T>()
        private val elementStack = mutableListOf<String>()
        private val contentBuffer = StringBuilder()
        
        override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
            elementStack.add(qName)
            contentBuffer.clear()
            
            if (elementStack.size == 1) {
                // 根对象
                result = factory.createObject()
                objectStack.add(result!!)
            } else if (result != null) {
                // 子对象
                val child = factory.createObject()
                objectStack.add(child)
            }
        }
        
        override fun endElement(uri: String, localName: String, qName: String) {
            if (objectStack.isNotEmpty()) {
                val current = objectStack.last()
                
                if (elementStack.size > 1) {
                    // 设置对象属性
                    factory.setProperty(current, qName, contentBuffer.toString().trim())
                }
                
                if (elementStack.size > 1 && elementStack.size == objectStack.size) {
                    objectStack.removeAt(objectStack.size - 1)
                    
                    if (objectStack.isNotEmpty()) {
                        val parent = objectStack.last()
                        factory.addChild(parent, current, qName)
                    }
                }
            }
            
            elementStack.removeAt(elementStack.size - 1)
        }
        
        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (elementStack.isNotEmpty()) {
                contentBuffer.append(ch, start, length)
            }
        }
    }
} 