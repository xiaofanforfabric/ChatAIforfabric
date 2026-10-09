package com.xiaofan.chatai.aiplayerentity

import com.xiaofan.chatai.ChatAI
import com.mojang.authlib.GameProfile
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.network.ServerPlayNetworkHandler
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import java.util.*

/**
 * AI 玩家包装器
 * 用于将 AIPlayerEntity 伪装成 ServerPlayerEntity
 * 注意：这是一个"假"的 ServerPlayerEntity，主要用于满足类型要求
 */
class AIPlayerEntityWrapper(
    private val aiPlayer: AIPlayerEntity,
    server: MinecraftServer,
    world: ServerWorld
) : ServerPlayerEntity(
    server,
    world,
    GameProfile(aiPlayer.uuid, aiPlayer.name.string)
) {
    
    init {
        // 同步基本属性
        try {
            // 使用反射同步位置、生命值等属性
            val xField = ServerPlayerEntity::class.java.getDeclaredField("x")
            val yField = ServerPlayerEntity::class.java.getDeclaredField("y")
            val zField = ServerPlayerEntity::class.java.getDeclaredField("z")
            val healthField = ServerPlayerEntity::class.java.getDeclaredField("health")
            
            xField.isAccessible = true
            yField.isAccessible = true
            zField.isAccessible = true
            healthField.isAccessible = true
            
            xField.setDouble(this, aiPlayer.x)
            yField.setDouble(this, aiPlayer.y)
            zField.setDouble(this, aiPlayer.z)
            healthField.setFloat(this, aiPlayer.health)
        } catch (e: Exception) {
            ChatAI.LOGGER.warn("无法同步 AI 玩家属性: ${e.message}")
        }
    }
    
    /**
     * 获取被包装的 AI 玩家
     */
    fun getAIPlayer(): AIPlayerEntity {
        return aiPlayer
    }
    
    /**
     * 同步 AI 玩家的状态到包装器
     */
    fun syncFromAIPlayer() {
        try {
            val xField = ServerPlayerEntity::class.java.getDeclaredField("x")
            val yField = ServerPlayerEntity::class.java.getDeclaredField("y")
            val zField = ServerPlayerEntity::class.java.getDeclaredField("z")
            val healthField = ServerPlayerEntity::class.java.getDeclaredField("health")
            
            xField.isAccessible = true
            yField.isAccessible = true
            zField.isAccessible = true
            healthField.isAccessible = true
            
            xField.setDouble(this, aiPlayer.x)
            yField.setDouble(this, aiPlayer.y)
            zField.setDouble(this, aiPlayer.z)
            healthField.setFloat(this, aiPlayer.health)
        } catch (e: Exception) {
            // 忽略同步错误
        }
    }
    
    override fun getName(): Text {
        return aiPlayer.name
    }
    
    override fun getUuid(): UUID {
        return aiPlayer.uuid
    }
    
    // 重写其他必要的方法，委托给 aiPlayer
    // 注意：这只是一个基础实现，可能需要更多方法
}
