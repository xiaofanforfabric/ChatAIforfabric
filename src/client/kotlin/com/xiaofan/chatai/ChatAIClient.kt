package com.xiaofan.chatai

import com.mojang.brigadier.CommandDispatcher
import com.xiaofan.chatai.aiplayerentity.ModEntities
import com.xiaofan.chatai.chataihome.StructureCommand
import com.xiaofan.chatai.command.AICommand
import com.xiaofan.chatai.item.ModItemGroups
import com.xiaofan.chatai.renderer.AIPlayerEntityRenderer
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.server.command.CommandManager.RegistrationEnvironment
import net.minecraft.server.command.ServerCommandSource
import org.slf4j.LoggerFactory


class ChatAIClient : ClientModInitializer {

	val MOD_ID = "chatai"
	private val LOGGER = LoggerFactory.getLogger(MOD_ID)

	override fun onInitializeClient() {
		ChatAI.Companion.LOGGER.info("ChatAI客户端初始化!")
		
		// 注册客户端命令
		AICommand.register()
		LOGGER.info("客户端命令注册成功")
		
		// 注册实体渲染器
		EntityRendererRegistry.register(ModEntities.AI_PLAYER) { context ->
			AIPlayerEntityRenderer(context)
		}
		LOGGER.info("实体渲染器注册成功")
		
		// 注册物品栏（客户端）
		ModItemGroups.register()
		LOGGER.info("物品栏注册成功")
		
		// 注册结构命令（服务端命令，但在客户端也需要注册回调）
		CommandRegistrationCallback.EVENT.register { dispatcher: CommandDispatcher<ServerCommandSource?>?, registryAccess: CommandRegistryAccess?, environment: RegistrationEnvironment? ->
			StructureCommand.register(dispatcher)
		}
		LOGGER.info("结构注册成功")
	}
}