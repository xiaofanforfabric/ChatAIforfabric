package com.xiaofan.chatai.util

import java.lang.reflect.Modifier

interface ClientInterface {
    companion object {
        /**
         * 通过反射获取 client 模块中的类
         * @param className 完整类名，例如 "com.xiaofan.chatai.aiplayerentity.AIPlayer"
         */
        fun getClientClass(className: String): Class<*>? {
            return try {
                Class.forName(className)
            } catch (e: ClassNotFoundException) {
                println("Class not found: $className")
                null
            }
        }

        /**
         * 创建 client 模块中类的实例
         * @param className 完整类名
         */
        fun createClientInstance(className: String): Any? {
            return getClientClass(className)?.newInstance()
        }

        /**
         * 调用 client 模块中类的方法
         * @param className 完整类名
         * @param methodName 方法名
         * @param args 方法参数
         */
        fun invokeClientMethod(
            className: String,
            methodName: String,
            vararg args: Any?
        ): Any? {
            return try {
                val clazz = getClientClass(className) ?: return null
                val method = clazz.methods.firstOrNull { it.name == methodName }
                    ?: return null
                val instance = if (!Modifier.isStatic(method.modifiers)) createClientInstance(className) else null
                method.invoke(instance, *args)
            } catch (e: Exception) {
                println("Failed to invoke method: $e")
                null
            }
        }
    }
}