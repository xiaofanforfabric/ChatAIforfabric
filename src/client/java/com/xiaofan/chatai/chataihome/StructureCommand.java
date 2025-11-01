package com.xiaofan.chatai.chataihome;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import static net.minecraft.server.command.CommandManager.*;

public class StructureCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("spawnchataihome")
                .executes(ctx -> {
                    ServerCommandSource source = ctx.getSource();
                    ServerWorld world = source.getWorld(); // 1.20.1 正确方法

                    // 获取玩家位置（需检查是否为玩家执行）
                    if (source.getPlayer() == null) {
                        source.sendError(Text.literal("只有玩家可以执行此命令！"));
                        return 0;
                    }

                    BlockPos pos = source.getPlayer().getBlockPos();
                    ChatAIHomeStructure.generate(world, pos, BlockRotation.NONE);

                    source.sendFeedback(() ->
                                    Text.literal("已在坐标 " + pos.toShortString() + " 生成结构"),
                            false
                    );
                    return Command.SINGLE_SUCCESS;
                })
        );
    }
}