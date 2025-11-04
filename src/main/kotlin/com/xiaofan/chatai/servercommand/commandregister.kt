package com.xiaofan.chatai.servercommand

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback

object CommandRegister {
    /**
     * 注册所有服务端命令
     */
    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, registryAccess, environment ->
            // 确保只在服务端环境注册（防止客户端加载）
            if (environment.dedicated || environment.integrated) {
                ServerCommand.register(dispatcher, registryAccess)
                AIClearCommand.register(dispatcher,registryAccess)
                dispatcher.register(AIDebugManager.registerCommand())
            }
        }
    }
}