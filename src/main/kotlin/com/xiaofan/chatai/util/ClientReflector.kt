// src/main/kotlin/com/xiaofan/chatai/util/ClientReflector.kt
package com.xiaofan.chatai.util

import net.minecraft.item.ItemStack
import net.minecraft.util.Hand
import net.minecraft.world.World

object ClientReflector {
    // 缓存反射结果提升性能
    private var aiPlayerClass: Class<*>? = null
    private var getStackMethod: java.lang.reflect.Method? = null
    private var inventoryMethod: java.lang.reflect.Method? = null

    /**
     * 预加载客户端类（可选）
     */
    fun preload() {
        try {
            aiPlayerClass = Class.forName("com.xiaofan.chatai.aiplayerentity.AIPlayerEntity")
            getStackMethod = aiPlayerClass?.getMethod("getStackInHand", Hand::class.java)
            inventoryMethod = aiPlayerClass?.getMethod("getInventory")
        } catch (e: Exception) {
            // 客户端未加载时静默失败
        }
    }

    /**
     * 安全获取AI玩家实例
     */
    fun findNearestAIPlayer(world: World, player: Any): Any? {
        return try {
            val clazz = aiPlayerClass ?: Class.forName("com.xiaofan.chatai.aiplayerentity.AIPlayerEntity")
            world.javaClass.getMethod("getEntitiesByClass", Class::class.java,
                player.javaClass.getMethod("getBoundingBox").returnType,
                java.util.function.Predicate::class.java)
                .invoke(world, clazz,
                    player.javaClass.getMethod("getBoundingBox").invoke(player),
                    { true } as java.util.function.Predicate<Any>)
                ?.let { entities ->
                    entities.javaClass.getMethod("minByOrNull",
                        java.util.Comparator::class.java)
                        .invoke(entities,
                            Comparator<Any> { a, b ->
                                player.javaClass.getMethod("squaredDistanceTo", Any::class.java)
                                    .invoke(player, a).toString().toDouble()
                                    .compareTo(
                                        player.javaClass.getMethod("squaredDistanceTo", Any::class.java)
                                            .invoke(player, b).toString().toDouble())
                            }
                        )
                }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 获取手持物品
     */
    fun getStackInHand(instance: Any, hand: Hand): ItemStack {
        return try {
            (getStackMethod?.invoke(instance, hand)
                ?: instance.javaClass.getMethod("getStackInHand", Hand::class.java)
                    .invoke(instance, hand)) as? ItemStack ?: ItemStack.EMPTY
        } catch (e: Exception) {
            ItemStack.EMPTY
        }
    }

    /**
     * 获取完整物品栏
     */
    fun getFullInventory(instance: Any): List<ItemStack> {
        return try {
            val inventory = (inventoryMethod?.invoke(instance)
                ?: instance.javaClass.getMethod("getInventory").invoke(instance))

            val size = inventory.javaClass.getMethod("size").invoke(inventory) as Int
            (0 until size).map {
                inventory.javaClass.getMethod("getStack", Int::class.java)
                    .invoke(inventory, it) as ItemStack
            }.filterNot { it.isEmpty }
        } catch (e: Exception) {
            emptyList()
        }
    }
}