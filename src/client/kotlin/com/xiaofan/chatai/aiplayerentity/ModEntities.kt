package com.xiaofan.chatai.aiplayerentity

import com.xiaofan.chatai.aiplayerentity.ModEntityAttributes.createHostileAttributes
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricDefaultAttributeRegistry
import net.minecraft.entity.EntityType
import net.minecraft.entity.SpawnGroup
import net.minecraft.entity.SpawnReason
import net.minecraft.entity.SpawnRestriction
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.random.Random
import net.minecraft.world.Heightmap
import net.minecraft.world.ServerWorldAccess


object ModEntities {
    val AI_PLAYER: EntityType<AIPlayerEntity> = EntityType.Builder
        .create(::AIPlayerEntity, SpawnGroup.CREATURE)
        .setDimensions(0.6f, 1.8f)
        .maxTrackingRange(32)
        .build("chatai:ai_player")
        .apply {
            // 注册属性
            FabricDefaultAttributeRegistry.register(this, createHostileAttributes())

            // 修正生成限制（添加Random参数）
            SpawnRestriction.register(
                this,
                SpawnRestriction.Location.ON_GROUND,
                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                { type: EntityType<AIPlayerEntity>,
                  world: ServerWorldAccess,
                  reason: SpawnReason,
                  pos: BlockPos,
                  random: Random ->
                    false
                }
            )
        }

    fun register() {
        Registry.register(
            Registries.ENTITY_TYPE,
            Identifier("chatai", "ai_player"),
            AI_PLAYER
        )
    }

}