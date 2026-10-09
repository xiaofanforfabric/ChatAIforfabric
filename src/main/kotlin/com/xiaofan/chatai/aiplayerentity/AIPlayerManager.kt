package com.xiaofan.chatai.aiplayerentity

import com.xiaofan.chatai.ChatAI
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * AI 玩家管理器
 * 负责管理所有 AI 玩家，并在服务器中注册它们
 */
object AIPlayerManager {
    // 存储所有已注册的 AI 玩家 (UUID -> AIPlayerEntity)
    private val registeredAIPlayers = ConcurrentHashMap<UUID, AIPlayerEntity>()
    
    // 存储服务器实例 (用于广播消息)
    private val serverInstances = ConcurrentHashMap<UUID, MinecraftServer>()
    
    /**
     * 注册一个 AI 玩家到服务器
     * 这将触发 "join the game" 消息（使用正确的格式和颜色）
     */
    fun registerAIPlayer(aiPlayer: AIPlayerEntity, server: MinecraftServer) {
        val uuid = aiPlayer.uuid
        
        // 如果已经注册，先注销
        if (registeredAIPlayers.containsKey(uuid)) {
            unregisterAIPlayer(aiPlayer, server)
        }
        
        // 注册
        registeredAIPlayers[uuid] = aiPlayer
        serverInstances[uuid] = server
        
        // 发送加入游戏消息（使用正确的格式：黄色文本 + 灰色玩家名，就像真实玩家一样）
        val joinMessage = Text.empty()
            .append(Text.literal(aiPlayer.name.string).formatted(Formatting.GRAY))
            .append(Text.literal(" joined the game").formatted(Formatting.YELLOW))
        
        server.playerManager.broadcast(joinMessage, false)
        
        ChatAI.LOGGER.info("AI玩家 ${aiPlayer.name.string} (${uuid}) 已注册到服务器")
    }
    
    /**
     * 注销一个 AI 玩家
     * 这将触发 "left the game" 消息（使用正确的格式和颜色）
     */
    fun unregisterAIPlayer(aiPlayer: AIPlayerEntity, server: MinecraftServer) {
        val uuid = aiPlayer.uuid
        
        if (registeredAIPlayers.remove(uuid) != null) {
            serverInstances.remove(uuid)
            
            // 发送离开游戏消息（使用正确的格式：黄色文本 + 灰色玩家名）
            val leaveMessage = Text.empty()
                .append(Text.literal(aiPlayer.name.string).formatted(Formatting.GRAY))
                .append(Text.literal(" left the game").formatted(Formatting.YELLOW))
            
            server.playerManager.broadcast(leaveMessage, false)
            
            ChatAI.LOGGER.info("AI玩家 ${aiPlayer.name.string} (${uuid}) 已从服务器注销")
        }
    }
    
    /**
     * 检查 UUID 是否是已注册的 AI 玩家
     */
    fun isAIPlayer(uuid: UUID): Boolean {
        return registeredAIPlayers.containsKey(uuid)
    }
    
    /**
     * 获取 AI 玩家
     */
    fun getAIPlayer(uuid: UUID): AIPlayerEntity? {
        return registeredAIPlayers[uuid]
    }
    
    /**
     * 获取所有已注册的 AI 玩家
     */
    fun getAllAIPlayers(): Collection<AIPlayerEntity> {
        return registeredAIPlayers.values
    }
    
    /**
     * 获取所有已注册的 AI 玩家 UUID
     */
    fun getAllAIPlayerUUIDs(): Set<UUID> {
        return registeredAIPlayers.keys
    }
    
    /**
     * 清理所有注册的 AI 玩家（服务器关闭时调用）
     */
    fun clearAll() {
        ChatAI.LOGGER.info("清理所有 AI 玩家注册信息 (共 ${registeredAIPlayers.size} 个)")
        registeredAIPlayers.clear()
        serverInstances.clear()
    }
}
