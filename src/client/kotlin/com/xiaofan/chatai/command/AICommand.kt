package com.xiaofan.chatai.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.xiaofan.chatai.AIChatHandler
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.text.Text

object AICommand {
    fun register() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                ClientCommandManager.literal("ai")
                    .then(ClientCommandManager.argument("message", StringArgumentType.greedyString())
                        .executes { context ->
                            val message = StringArgumentType.getString(context, "message")
                            AIChatHandler.processCommand(message, context.source)
                            Command.SINGLE_SUCCESS
                        }
                    )
                    .executes { context ->
                        context.source.sendError(Text.literal("请输入问题，例如: /ai 如何造房子"))
                        Command.SINGLE_SUCCESS
                    }
            )
        }
    }
}