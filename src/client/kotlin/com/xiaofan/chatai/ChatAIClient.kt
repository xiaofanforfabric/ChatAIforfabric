package com.xiaofan.chatai

import com.mojang.brigadier.CommandDispatcher
import com.xiaofan.chatai.aiplayerentity.ModEntities
import com.xiaofan.chatai.aiplayerentity.ModEntityAttributes
import com.xiaofan.chatai.aiplayerentity.ModWorldEvents
import com.xiaofan.chatai.chataihome.StructureCommand
import com.xiaofan.chatai.command.AICommand
import com.xiaofan.chatai.renderer.AIPlayerEntityRenderer
import com.xiaofan.chatai.servercommand.CommandRegister
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricDefaultAttributeRegistry
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.server.command.CommandManager.RegistrationEnvironment
import net.minecraft.server.command.ServerCommandSource
import org.slf4j.LoggerFactory


class ChatAIClient : ClientModInitializer {

	val MOD_ID = "chatai"
	private val LOGGER = LoggerFactory.getLogger(MOD_ID)

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
		CommandRegistrationCallback.EVENT.register(CommandRegistrationCallback { dispatcher: CommandDispatcher<ServerCommandSource?>?, registryAccess: CommandRegistryAccess?, environment: RegistrationEnvironment? ->
			StructureCommand.register(dispatcher)
		})
		LOGGER.info("结构注册成功")
	}
}