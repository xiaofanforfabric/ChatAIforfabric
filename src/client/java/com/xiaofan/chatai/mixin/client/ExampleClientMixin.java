package com.xiaofan.chatai.mixin.client;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 客户端混入类，用于在Minecraft客户端运行时注入自定义代码
 * 该类混入到MinecraftClient类中，可以在客户端启动时执行自定义逻辑
 */
@Mixin(MinecraftClient.class)
public class ExampleClientMixin {
    /**
     * 在MinecraftClient.run()方法开始处注入代码
     * @param info 回调信息，用于控制原始方法的执行
     */
    @Inject(at = @At("HEAD"), method = "run")
    private void init(CallbackInfo info) {
        // 此代码被注入到MinecraftClient.run()V方法的开始处
        // 可以在这里添加客户端启动时的初始化逻辑
    }
}
