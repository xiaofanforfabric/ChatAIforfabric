package com.xiaofan.chatai.chataihome;

import net.minecraft.block.Block;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

// 文件路径：src/main/java/com/xiaofan/chatai/structure/ChatAIHomeStructure.java
public class ChatAIHomeStructure {
    public static void generate(ServerWorld world, BlockPos pos, BlockRotation rotation) {
        StructureTemplateManager manager = world.getStructureTemplateManager();
        manager.getTemplate(new Identifier("chatai", "structures/chataihome"))
                .ifPresent(template -> {
                    StructurePlacementData placementData = new StructurePlacementData()
                            .setRotation(rotation)
                            .setMirror(BlockMirror.NONE)
                            .setIgnoreEntities(false);

                    // 直接使用玩家坐标的Y值（不再计算地表高度）
                    BlockPos adjustedPos = pos; // 保持原Y坐标

                    template.place(
                            world,
                            adjustedPos,
                            adjustedPos,
                            placementData,
                            world.random,
                            Block.NOTIFY_ALL
                    );
                });
    }
}