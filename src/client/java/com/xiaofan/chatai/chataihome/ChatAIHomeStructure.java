package com.xiaofan.chatai.chataihome;

import net.minecraft.block.Block;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// 文件路径：src/main/java/com/xiaofan/chatai/structure/ChatAIHomeStructure.java
public class ChatAIHomeStructure {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChatAIHomeStructure");

    public static boolean generate(ServerWorld world, BlockPos pos, BlockRotation rotation) {
        StructureTemplateManager manager = world.getStructureTemplateManager();
        Identifier structureId = new Identifier("chatai", "chataihome");
        
        java.util.Optional<StructureTemplate> templateOptional = manager.getTemplate(structureId);
        
        if (templateOptional.isEmpty()) {
            LOGGER.error("无法找到结构模板: {}", structureId);
            return false;
        }

        StructureTemplate template = templateOptional.get();
        StructurePlacementData placementData = new StructurePlacementData()
                .setRotation(rotation)
                .setMirror(BlockMirror.NONE)
                .setIgnoreEntities(false);

        // 直接使用玩家坐标的Y值（不再计算地表高度）
        BlockPos adjustedPos = pos; // 保持原Y坐标

        try {
            template.place(
                    world,
                    adjustedPos,
                    adjustedPos,
                    placementData,
                    world.random,
                    Block.NOTIFY_ALL
            );
            LOGGER.info("成功生成结构 {} 在位置 {}", structureId, adjustedPos);
            return true;
        } catch (Exception e) {
            LOGGER.error("生成结构时发生错误: {}", e.getMessage(), e);
            return false;
        }
    }
}