package com.xiaofan.chatai.item

import com.xiaofan.chatai.ChatAI
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents
import net.minecraft.item.ItemGroups
import net.minecraft.item.ItemStack

/**
 * 客户端物品栏注册
 * 将物品添加到创造模式物品栏
 */
object ModItemGroups {
    fun register() {
        // 将刷怪蛋添加到创造模式的刷怪蛋物品栏
        try {
            ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register { entries ->
                entries.add(ItemStack(com.xiaofan.chatai.item.ModItems.AI_PLAYER_SPAWN_EGG))
            }
            ChatAI.LOGGER.info("刷怪蛋已添加到创造模式物品栏")
        } catch (e: Exception) {
            ChatAI.LOGGER.warn("无法添加到刷怪蛋物品栏，可能API版本不兼容: ${e.message}")
        }
    }
}
