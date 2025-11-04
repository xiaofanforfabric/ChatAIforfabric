package com.xiaofan.chatai.item

import com.xiaofan.chatai.ChatAI
import com.xiaofan.chatai.aiplayerentity.ModEntities
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents
import net.minecraft.item.Item
import net.minecraft.item.ItemGroups
import net.minecraft.item.ItemStack
import net.minecraft.item.ItemUsageContext
import net.minecraft.registry.Registries
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier
import net.minecraft.world.World
import net.minecraft.world.event.GameEvent

/**
 * 模组物品注册类
 */
object ModItems {
    
    /**
     * AI玩家刷怪蛋
     * 用于生成AIPlayer实体
     */
    val AI_PLAYER_SPAWN_EGG: Item = AIPlayerSpawnEggItem(
        Item.Settings()
            .maxCount(64)
    )

    /**
     * 注册所有物品
     */
    fun register() {
        net.minecraft.registry.Registry.register(
            Registries.ITEM,
            Identifier("chatai", "ai_player_spawn_egg"),
            AI_PLAYER_SPAWN_EGG
        )
        
        // 将刷怪蛋添加到创造模式的刷怪蛋物品栏
        try {
            ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register { entries ->
                entries.add(ItemStack(AI_PLAYER_SPAWN_EGG))
            }
        } catch (e: Exception) {
            ChatAI.LOGGER.warn("无法添加到刷怪蛋物品栏，可能API版本不兼容: ${e.message}")
        }
        
        ChatAI.LOGGER.info("AI玩家刷怪蛋注册成功")
    }
}

/**
 * 自定义刷怪蛋物品类
 * 用于生成AIPlayer实体
 */
class AIPlayerSpawnEggItem(settings: Settings) : Item(settings) {
    
    override fun useOnBlock(context: ItemUsageContext): ActionResult {
        val world = context.world
        val player = context.player
        
        // 只在服务端执行
        if (world.isClient) {
            return ActionResult.SUCCESS
        }
        
        // 检查玩家是否有权限
        if (player != null && !player.isCreative && !world.isInBuildLimit(context.blockPos)) {
            return ActionResult.PASS
        }
        
        // 检查该世界是否已有AI玩家
        if (hasExistingAIPlayer(world)) {
            if (player != null) {
                player.sendMessage(
                    Text.literal("⚠️ 该世界已存在AI玩家，无法生成新的").formatted(Formatting.RED),
                    false
                )
            }
            world.playSound(
                null,
                context.blockPos,
                SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(),
                SoundCategory.BLOCKS,
                1.0f,
                0.5f
            )
            return ActionResult.FAIL
        }
        
        // 计算生成位置（点击方块的上方）
        val spawnPos = context.blockPos.up()
        
        // 创建AIPlayer实体
        try {
            val serverWorld = world as ServerWorld
            val aiPlayer = com.xiaofan.chatai.aiplayerentity.AIPlayerEntity(
                ModEntities.AI_PLAYER,
                serverWorld
            )
            
            // 设置生成位置（偏移到方块中心上方）
            aiPlayer.setPosition(
                spawnPos.x + 0.5,
                spawnPos.y.toDouble(),
                spawnPos.z + 0.5
            )
            
            // 初始化实体
            aiPlayer.initialize(
                serverWorld,
                serverWorld.getLocalDifficulty(spawnPos),
                net.minecraft.entity.SpawnReason.SPAWN_EGG,
                null,
                null
            )
            
            // 生成实体
            if (serverWorld.spawnEntity(aiPlayer)) {
                // 消耗物品（创造模式不消耗）
                if (player != null && !player.isCreative) {
                    context.stack.decrement(1)
                }
                
                // 播放声音
                serverWorld.playSound(
                    null,
                    spawnPos,
                    SoundEvents.ENTITY_CHICKEN_EGG,
                    SoundCategory.NEUTRAL,
                    1.0f,
                    1.0f
                )
                
                // 触发游戏事件
                serverWorld.emitGameEvent(player, GameEvent.ENTITY_PLACE, spawnPos)
                
                // 发送提示消息
                if (player != null) {
                    player.sendMessage(
                        Text.literal("✅ AI玩家已生成").formatted(Formatting.GREEN),
                        false
                    )
                }
                
                ChatAI.LOGGER.info("玩家 ${player?.name?.string ?: "未知"} 使用刷怪蛋生成了AI玩家")
                return ActionResult.SUCCESS
            } else {
                if (player != null) {
                    player.sendMessage(
                        Text.literal("⚠️ 生成失败").formatted(Formatting.RED),
                        false
                    )
                }
                return ActionResult.FAIL
            }
        } catch (e: Exception) {
            ChatAI.LOGGER.error("生成AI玩家时出错: ${e.message}", e)
            if (player != null) {
                player.sendMessage(
                    Text.literal("⚠️ 生成时发生错误: ${e.message}").formatted(Formatting.RED),
                    false
                )
            }
            return ActionResult.FAIL
        }
    }
    
    /**
     * 检查世界中是否已存在AI玩家
     */
    private fun hasExistingAIPlayer(world: World): Boolean {
        if (world.isClient) return false
        
        if (world is ServerWorld) {
            return world.iterateEntities()
                .filterIsInstance<com.xiaofan.chatai.aiplayerentity.AIPlayerEntity>()
                .any { it.isAlive }
        }
        
        return false
    }
    
    override fun getName(stack: ItemStack?): Text {
        return Text.translatable("item.chatai.ai_player_spawn_egg")
    }
}

