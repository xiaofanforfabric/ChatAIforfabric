package com.xiaofan.chatai.mixin;

import com.xiaofan.chatai.aiplayerentity.AIPlayerEntity;
import com.xiaofan.chatai.aiplayerentity.AIPlayerEntityWrapper;
import com.xiaofan.chatai.aiplayerentity.AIPlayerManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PlayerManager Mixin
 * 拦截玩家管理逻辑，将 AIPlayerEntity 集成到玩家系统中
 */
@Mixin(PlayerManager.class)
public abstract class PlayerManagerMixin {
    
    @Shadow
    @Final
    private List<ServerPlayerEntity> players;
    
    @Shadow
    @Final
    private Map<UUID, ServerPlayerEntity> playerMap;
    
    @Shadow
    public abstract MinecraftServer getServer();
    
    /**
     * 拦截 getPlayerList，将 AI 玩家添加到列表中
     * 使用包装器来实现类型伪装
     */
    @Inject(method = "getPlayerList", at = @At("RETURN"))
    private void onGetPlayerList(CallbackInfoReturnable<List<ServerPlayerEntity>> cir) {
        List<ServerPlayerEntity> playerList = cir.getReturnValue();
        if (playerList == null) {
            return;
        }
        
        // 获取所有 AI 玩家
        // 注意：Kotlin object 在 Java 中通过 INSTANCE 访问
        java.util.Collection<? extends AIPlayerEntity> aiPlayers = 
            AIPlayerManager.INSTANCE.getAllAIPlayers();
        
        for (AIPlayerEntity aiPlayer : aiPlayers) {
            UUID uuid = aiPlayer.getUuid();
            
            // 检查是否已经在列表中
            boolean alreadyInList = playerList.stream()
                .anyMatch(p -> p.getUuid().equals(uuid));
            
            if (!alreadyInList && aiPlayer.getWorld() instanceof ServerWorld) {
                try {
                    // 创建包装器
                    ServerWorld serverWorld = (ServerWorld) aiPlayer.getWorld();
                    AIPlayerEntityWrapper wrapper = new AIPlayerEntityWrapper(
                        aiPlayer,
                        getServer(),
                        serverWorld
                    );
                    
                    // 添加到列表（但不添加到 Map，因为那是内部管理）
                    // 注意：这可能会在服务器重启时造成问题
                    if (!playerList.contains(wrapper)) {
                        playerList.add(wrapper);
                    }
                } catch (Exception e) {
                    // 包装器创建失败，忽略
                    System.err.println("无法创建 AI 玩家包装器: " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * 拦截 getPlayer，让 AI 玩家也能通过 UUID 查询到
     */
    @Inject(method = "getPlayer", at = @At("RETURN"), cancellable = true)
    private void onGetPlayer(UUID uuid, CallbackInfoReturnable<ServerPlayerEntity> cir) {
        // 如果找不到真实玩家，检查是否是 AI 玩家
        if (cir.getReturnValue() == null && AIPlayerManager.INSTANCE.isAIPlayer(uuid)) {
            AIPlayerEntity aiPlayer = (AIPlayerEntity) AIPlayerManager.INSTANCE.getAIPlayer(uuid);
            if (aiPlayer != null && aiPlayer.getWorld() instanceof ServerWorld) {
                try {
                    ServerWorld serverWorld = (ServerWorld) aiPlayer.getWorld();
                    AIPlayerEntityWrapper wrapper = new AIPlayerEntityWrapper(
                        aiPlayer,
                        getServer(),
                        serverWorld
                    );
                    cir.setReturnValue(wrapper);
                } catch (Exception e) {
                    // 包装器创建失败，返回 null
                }
            }
        }
    }
    
    /**
     * 拦截 broadcast 方法，确保消息格式正确
     */
    @Inject(method = "broadcast", at = @At("HEAD"))
    private void onBroadcast(Text message, boolean overlay, CallbackInfo ci) {
        // 消息格式已经在 AIPlayerManager 中处理
    }
}
