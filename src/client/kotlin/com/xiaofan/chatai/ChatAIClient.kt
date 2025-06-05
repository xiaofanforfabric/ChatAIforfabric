package com.xiaofan.chatai

import com.xiaofan.chatai.command.AICommand
import net.fabricmc.api.ClientModInitializer

class ChatAIClient : ClientModInitializer {
	override fun onInitializeClient() {
		AICommand.register()
	}
}