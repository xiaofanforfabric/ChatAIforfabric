// src/main/kotlin/com/xiaofan/chatai/servercommand/AIClearCommand.kt
package com.xiaofan.chatai.servercommand

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import com.xiaofan.chatai.aiplayerentity.AIPlayerEntity
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.command.argument.EntityArgumentType
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.world.World

object AIClearCommand {
    fun register(dispatcher: CommandDispatcher<ServerCommandSource>, registryAccess: CommandRegistryAccess) {
        dispatcher.register(
            CommandManager.literal("aiplayer-debug")
                .requires { it.hasPermissionLevel(2) }
                .then(
                    CommandManager.literal("clear")
                        .executes { executeClear(it) }
                        .then(
                            CommandManager.argument("player", EntityArgumentType.player())
                                .executes { executeClear(it, EntityArgumentType.getPlayer(it, "player")) }
                        )
                )
        )
    }

    private fun executeClear(
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
                val aiPlayer = findNearestAIPlayer(player.world, player) ?: run {
                    source.sendError(Text.literal("附近未找到AI玩家").formatted(Formatting.RED))
                    return@execute
                }

                aiPlayer.clearFullInventory()
                source.sendFeedback(
                    { Text.literal("成功清除AI玩家物品栏").formatted(Formatting.GREEN) },
                    true
                )
            } catch (e: Exception) {
                source.sendError(Text.literal("清除失败: ${e.message}").formatted(Formatting.RED))
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
}