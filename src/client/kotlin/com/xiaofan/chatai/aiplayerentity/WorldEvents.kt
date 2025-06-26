package com.xiaofan.chatai.aiplayerentity

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents
import net.minecraft.entity.SpawnReason
import net.minecraft.nbt.NbtCompound
import net.minecraft.server.world.ServerWorld

object ModWorldEvents {
    fun register() {
        ServerWorldEvents.LOAD.register { server, world ->
            if (world is ServerWorld) {
                AIPlayerData.loadAll(world).forEach { (uuid, nbt) ->
                    val aiPlayer = AIPlayerEntity(ModEntities.AI_PLAYER, world,)
                    aiPlayer.readCustomDataFromNbt(nbt)
                    // 1.20.1 使用 getLocalDifficultyAt 替代 localDifficulty
                    aiPlayer.initialize(
                        world,
                        world.getLocalDifficulty(aiPlayer.blockPos),
                        SpawnReason.NATURAL,
                        null,
                        null
                    )
                    world.spawnEntity(aiPlayer)
                }
            }
        }

        ServerWorldEvents.UNLOAD.register { server, world ->
            if (world is ServerWorld) {
                world.iterateEntities()
                    .filterIsInstance<AIPlayerEntity>()
                    .forEach { aiPlayer ->
                        val nbt = NbtCompound()
                        aiPlayer.writeCustomDataToNbt(nbt)
                        AIPlayerData.save(world, aiPlayer.uuid, nbt)
                    }
            }
        }
    }
}