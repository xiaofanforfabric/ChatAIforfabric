// src/main/kotlin/com/xiaofan/chatai/command/ServerCommand.kt
package com.xiaofan.chatai.servercommand

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import com.xiaofan.chatai.aiplayerentity.AIPlayerEntity
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.command.argument.EntityArgumentType
import net.minecraft.item.ItemStack
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Hand
import net.minecraft.world.World

object ServerCommand {
    fun register(dispatcher: CommandDispatcher<ServerCommandSource>, registryAccess: CommandRegistryAccess) {
        dispatcher.register(
            CommandManager.literal("aiplayer-debug")
                .requires { it.hasPermissionLevel(2) }
                .then(
                    CommandManager.literal("inventory")
                        .executes { executeInventoryCommand(it) }
                        .then(
                            CommandManager.argument("player", EntityArgumentType.player())
                                .executes { executeInventoryCommand(it, EntityArgumentType.getPlayer(it, "player")) }
                        )
                )
        )
    }

    private fun executeInventoryCommand(
        context: CommandContext<ServerCommandSource>,
        targetPlayer: ServerPlayerEntity? = null
    ): Int {
        val source = context.source
        val player = targetPlayer ?: source.player ?: run {
            source.sendError(Text.literal("必须由玩家执行").formatted(Formatting.RED))
            return 0
        }

        source.server.execute {
            try {
                // 1. 直接查找最近的 AIPlayerEntity
                val aiPlayer = findNearestAIPlayer(player.world, player) ?: run {
                    source.sendError(Text.literal("附近未找到AI玩家").formatted(Formatting.RED))
                    logNearbyEntities(player.world, player)
                    return@execute
                }

                // 2. 直接调用 AIPlayerEntity 的方法获取物品栏
                val items = aiPlayer.getFullInventoryForCommand()

                // 3. 发送反馈
                source.sendFeedback(
                    { Text.literal("=== AI物品栏 ===").formatted(Formatting.GOLD) },
                    false
                )
                items.forEach { item ->
                    source.sendFeedback({ item }, false)
                }

            } catch (e: Exception) {
                source.sendError(Text.literal("系统错误: ${e.message}").formatted(Formatting.RED))
            }
        }
        return 1
    }

    private fun findNearestAIPlayer(world: World, player: ServerPlayerEntity): AIPlayerEntity? {
        return world.getEntitiesByClass(
            AIPlayerEntity::class.java,
            player.boundingBox.expand(20.0),
            { it.isAlive }
        ).minByOrNull { it.distanceTo(player) }
    }

    private fun logNearbyEntities(world: World, player: ServerPlayerEntity) {
        val entities = world.getOtherEntities(player, player.boundingBox.expand(20.0)) { it.isAlive }
        if (world is ServerWorld) {
            world.server.commandManager.executeWithPrefix(
                world.server.commandSource,
                "say ==== 附近实体 ===="
            )
            entities.forEach { e ->
                world.server.commandManager.executeWithPrefix(
                    world.server.commandSource,
                    "say - ${e.type.translationKey} (${e.blockPos.x}, ${e.blockPos.y}, ${e.blockPos.z})"
                )
            }
        }
    }
}