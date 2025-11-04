package com.xiaofan.chatai


import com.xiaofan.chatai.aiplayerentity.ModEntities
import com.xiaofan.chatai.aiplayerentity.ModEntityAttributes
import com.xiaofan.chatai.aiplayerentity.ModWorldEvents
import com.xiaofan.chatai.item.ModItems
import com.xiaofan.chatai.servercommand.CommandRegister
import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory

class ChatAI : ModInitializer {

	// 使用companion object替代object声明
	companion object {
		val LOGGER = LoggerFactory.getLogger("chatai")
	}

	override fun onInitialize() {
		LOGGER.info("ChatAI initialized!")
		
		// 注册实体
		ModEntities.register()
		LOGGER.info("实体注册成功")
		
		// 注册实体属性
		ModEntityAttributes.registerHostileAttributes(ModEntities.AI_PLAYER)
		LOGGER.info("实体属性注册成功")
		
		// 注册世界事件
		ModWorldEvents.register()
		LOGGER.info("世界事件注册成功")
		
		// 注册服务端命令
		CommandRegister.register()
		LOGGER.info("服务端命令注册成功")
		
		// 注册物品
		ModItems.register()
		LOGGER.info("物品注册成功")
	}
}