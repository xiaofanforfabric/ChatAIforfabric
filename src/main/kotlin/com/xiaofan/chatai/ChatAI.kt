package com.xiaofan.chatai


import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory

class ChatAI : ModInitializer {

	// 使用companion object替代object声明
	companion object {
		val LOGGER = LoggerFactory.getLogger("chatai")
	}

	override fun onInitialize() {
		LOGGER.info("ChatAI initialized!")
	}
}