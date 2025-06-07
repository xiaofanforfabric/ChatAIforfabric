package com.xiaofan.chatai.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.xiaofan.chatai.AIChatHandler
import com.xiaofan.chatai.config.ConfigManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Style
import net.minecraft.text.Text
import net.minecraft.util.Formatting

object AICommand {
    private val SUCCESS_STYLE = Style.EMPTY.withColor(Formatting.GREEN)
    private val ERROR_STYLE = Style.EMPTY.withColor(Formatting.RED)
    private val INFO_STYLE = Style.EMPTY.withColor(Formatting.BLUE)
    private val WARNING_STYLE = Style.EMPTY.withColor(Formatting.YELLOW)
    private val GOLD_STYLE = Style.EMPTY.withColor(Formatting.GOLD)
    private val GRAY_STYLE = Style.EMPTY.withColor(Formatting.GRAY)

    fun register() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            registerAIChatCommand(dispatcher)
            registerAICommandCommand(dispatcher)
            registerConfigCommand(dispatcher)
            registerHelpCommand(dispatcher)
        }
    }

    private fun registerAIChatCommand(dispatcher: CommandDispatcher<FabricClientCommandSource>) {
        dispatcher.register(
            ClientCommandManager.literal("ai")
                .then(ClientCommandManager.argument("message", StringArgumentType.greedyString())
                    .executes { context ->
                        if (ConfigManager.apiKey.isBlank()) {
                            context.source.sendError(
                                Text.literal("⚠️ 请先配置API密钥: /ai-config set-key <你的密钥>")
                                    .setStyle(ERROR_STYLE)
                            )
                            return@executes Command.SINGLE_SUCCESS
                        }

                        val message = StringArgumentType.getString(context, "message")
                        if (message.length > 500) {
                            context.source.sendError(
                                Text.literal("⚠️ 问题过长，请精简到500字符以内")
                                    .setStyle(ERROR_STYLE)
                            )
                            return@executes Command.SINGLE_SUCCESS
                        }

                        context.source.sendFeedback(
                            Text.literal("⌛ AI正在思考...")
                                .setStyle(GRAY_STYLE)
                        )

                        AIChatHandler.processCommand(message, context.source)
                        Command.SINGLE_SUCCESS
                    }
                )
                .executes { context ->
                    context.source.sendFeedback(
                        Text.literal("ℹ️ 使用说明: /ai <你的问题>")
                            .setStyle(INFO_STYLE)
                    )
                    context.source.sendFeedback(
                        Text.literal("例如: /ai 如何在生存模式快速找到钻石")
                            .setStyle(GRAY_STYLE)
                    )
                    Command.SINGLE_SUCCESS
                }
        )
    }

    private fun registerAICommandCommand(dispatcher: CommandDispatcher<FabricClientCommandSource>) {
        dispatcher.register(
            ClientCommandManager.literal("ai-command")
                .then(ClientCommandManager.argument("content", StringArgumentType.greedyString())
                    .executes { context ->
                        if (ConfigManager.apiKey.isBlank()) {
                            context.source.sendError(
                                Text.literal("⚠️ 请先配置API密钥: /ai-config set-key <你的密钥>")
                                    .setStyle(ERROR_STYLE)
                            )
                            return@executes Command.SINGLE_SUCCESS
                        }

                        val content = StringArgumentType.getString(context, "content")
                        context.source.sendFeedback(
                            Text.literal("⌛ 正在生成命令...")
                                .setStyle(GRAY_STYLE)
                        )

                        Thread {
                            try {
                                val command = AIChatHandler.getMinecraftCommand(content)
                                MinecraftClient.getInstance().execute {
                                    context.source.player.networkHandler.sendChatCommand(command)
                                    context.source.sendFeedback(
                                        Text.literal("✅ 已执行命令: /$command")
                                            .setStyle(SUCCESS_STYLE)
                                    )
                                }
                            } catch (e: Exception) {
                                MinecraftClient.getInstance().execute {
                                    context.source.sendError(
                                        Text.literal("⚠️ 错误: ${e.message}")
                                            .setStyle(ERROR_STYLE)
                                    )
                                }
                            }
                        }.start()

                        Command.SINGLE_SUCCESS
                    }
                )
                .executes { context ->
                    context.source.sendFeedback(
                        Text.literal("ℹ️ 使用说明: /ai-command <需求>")
                            .setStyle(INFO_STYLE)
                    )
                    context.source.sendFeedback(
                        Text.literal("例如: /ai-command 给我64个钻石")
                            .setStyle(GRAY_STYLE)
                    )
                    Command.SINGLE_SUCCESS
                }
        )
    }

    private fun registerConfigCommand(dispatcher: CommandDispatcher<FabricClientCommandSource>) {
        dispatcher.register(
            ClientCommandManager.literal("ai-config")
                .then(ClientCommandManager.literal("set-key")
                    .then(ClientCommandManager.argument("key", StringArgumentType.string())
                        .executes { ctx ->
                            ConfigManager.apiKey = StringArgumentType.getString(ctx, "key")
                            ConfigManager.saveConfig()
                            ctx.source.sendFeedback(
                                Text.literal("✅ API密钥已更新")
                                    .setStyle(SUCCESS_STYLE)
                            )
                            Command.SINGLE_SUCCESS
                        }
                    )
                )
                .then(ClientCommandManager.literal("set-url")
                    .then(ClientCommandManager.argument("url", StringArgumentType.string())
                        .executes { ctx ->
                            ConfigManager.apiUrl = StringArgumentType.getString(ctx, "url")
                            ConfigManager.saveConfig()
                            ctx.source.sendFeedback(
                                Text.literal("✅ API地址已更新")
                                    .setStyle(SUCCESS_STYLE)
                            )
                            Command.SINGLE_SUCCESS
                        }
                    )
                )
                .then(ClientCommandManager.literal("show")
                    .executes { ctx ->
                        ctx.source.sendFeedback(
                            Text.literal("⚙️ 当前配置:")
                                .setStyle(GOLD_STYLE)
                        )
                        ctx.source.sendFeedback(
                            Text.literal("API密钥: ${if (ConfigManager.apiKey.isNotBlank()) "***" + ConfigManager.apiKey.takeLast(4) else "未设置"}")
                                .setStyle(INFO_STYLE)
                        )
                        ctx.source.sendFeedback(
                            Text.literal("API地址: ${ConfigManager.apiUrl}")
                                .setStyle(INFO_STYLE)
                        )
                        ctx.source.sendFeedback(
                            Text.literal("模型: ${ConfigManager.model}")
                                .setStyle(INFO_STYLE)
                        )
                        ctx.source.sendFeedback(
                            Text.literal("超时: ${ConfigManager.timeoutSeconds}秒")
                                .setStyle(INFO_STYLE)
                        )
                        Command.SINGLE_SUCCESS
                    }
                )
                .executes { ctx ->
                    ctx.source.sendFeedback(
                        Text.literal("ℹ️ 使用说明:")
                            .setStyle(INFO_STYLE)
                    )
                    ctx.source.sendFeedback(
                        Text.literal("/ai-config set-key <密钥> - 设置API密钥")
                            .setStyle(GRAY_STYLE)
                    )
                    ctx.source.sendFeedback(
                        Text.literal("/ai-config set-url <URL> - 设置API地址")
                            .setStyle(GRAY_STYLE)
                    )
                    ctx.source.sendFeedback(
                        Text.literal("/ai-config show - 显示当前配置")
                            .setStyle(GRAY_STYLE)
                    )
                    Command.SINGLE_SUCCESS
                }
        )
    }

    private fun registerHelpCommand(dispatcher: CommandDispatcher<FabricClientCommandSource>) {
        dispatcher.register(
            ClientCommandManager.literal("ai-help")
                .executes { context ->
                    context.source.sendFeedback(
                        Text.literal("🌟 ==== AI聊天帮助 ====")
                            .setStyle(GOLD_STYLE)
                    )
                    context.source.sendFeedback(
                        Text.literal("/ai <问题> - 向AI提问")
                            .setStyle(INFO_STYLE)
                    )
                    context.source.sendFeedback(
                        Text.literal("/ai-command <需求> - 生成并执行Minecraft命令")
                            .setStyle(INFO_STYLE)
                    )
                    context.source.sendFeedback(
                        Text.literal("/ai-config - 配置AI参数")
                            .setStyle(INFO_STYLE)
                    )
                    context.source.sendFeedback(
                        Text.literal("/ai-help - 显示帮助信息")
                            .setStyle(INFO_STYLE)
                    )
                    Command.SINGLE_SUCCESS
                }
        )
    }
}