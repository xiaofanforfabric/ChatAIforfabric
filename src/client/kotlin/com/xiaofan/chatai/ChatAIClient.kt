package com.xiaofan.chatai

import com.xiaofan.chatai.ChatAI.Companion.LOGGER
import com.xiaofan.chatai.aiplayerentity.AIPlayerEntity
import com.xiaofan.chatai.aiplayerentity.AIPlayerRenderer
import com.xiaofan.chatai.aiplayerentity.ModEntities
import com.xiaofan.chatai.aiplayerentity.ModEntities.AI_PLAYER
import com.xiaofan.chatai.aiplayerentity.ModEntityAttributes
import com.xiaofan.chatai.aiplayerentity.ModWorldEvents
import com.xiaofan.chatai.command.AICommand
import com.xiaofan.chatai.renderer.AIPlayerEntityRenderer
import com.xiaofan.chatai.servercommand.CommandRegister
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricDefaultAttributeRegistry


class ChatAIClient : ClientModInitializer {
	override fun onInitializeClient() {
		ChatAI.Companion.LOGGER.info("ChatAI模组加载成功!")
		AICommand.register()
		ModEntities.register()
		ModWorldEvents.register()
		CommandRegister.register()
		LOGGER.info("服务端命令注册成功")
        EntityRendererRegistry.register(ModEntities.AI_PLAYER) { context ->
            AIPlayerEntityRenderer(context) // false 表示使用默认宽手臂模型
        }
		LOGGER.info("实体渲染器注册成功")
		FabricDefaultAttributeRegistry.register(
			ModEntities.AI_PLAYER,
			ModEntityAttributes.createHostileAttributes()
		)


	}

}