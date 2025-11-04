package com.xiaofan.chatai.servercommand

import com.mojang.brigadier.Command
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.world.World
import org.apache.logging.log4j.LogManager

object AIDebugManager {
    // 调试状态
    internal var debugEnabled = false

    // 注册命令
    fun registerCommand() = literal("aiplayer-debug")
        .executes { ctx ->
            ctx.source.sendFeedback(
                { Text.literal("AI调试状态: ${if (debugEnabled) "开启" else "关闭"}") },
                false
            )
            Command.SINGLE_SUCCESS
        }
        .then(literal("on").executes { ctx ->
            debugEnabled = true
            ctx.source.sendFeedback({ Text.literal("已开启AI调试输出") }, false)
            Command.SINGLE_SUCCESS
        })
        .then(literal("off").executes { ctx ->
            debugEnabled = false
            ctx.source.sendFeedback({ Text.literal("已关闭AI调试输出") }, false)
            Command.SINGLE_SUCCESS
        })

    // 调试日志输出
    internal fun debuglog(world: World?, message: String) {
        if (!debugEnabled) return

        if (world is ServerWorld) {
            try {
                world.server.playerManager.broadcast(
                    Text.literal("[AI] $message"),
                    false
                )
                LogManager.getLogger("ChatAI").info("[AI] $message")
            } catch (e: Exception) {
                println("[AI] $message")
            }
        }
    }
}