package com.xiaofan.chatai.aiplayerentity

import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtIo
import net.minecraft.server.world.ServerWorld
import java.io.File
import java.util.*

object AIPlayerData {
    private const val SAVE_DIR = "data/ai_players"

    // 修复1：使用合法路径获取方式
    private fun getSaveDir(world: ServerWorld): File {
        return File(world.server.runDirectory, SAVE_DIR).apply {
            mkdirs()
        }
    }

    // 修复2：简化文件操作
    fun save(world: ServerWorld, uuid: UUID, nbt: NbtCompound) {
        NbtIo.writeCompressed(nbt, File(getSaveDir(world), "$uuid.dat"))
    }

    fun load(world: ServerWorld, uuid: UUID): NbtCompound? {
        val file = File(getSaveDir(world), "$uuid.dat")
        return if (file.exists()) NbtIo.readCompressed(file) else null
    }

    // 修复3：显式指定泛型类型
    fun loadAll(world: ServerWorld): Map<UUID, NbtCompound> {
        return getSaveDir(world).listFiles()
            ?.mapNotNull { file ->
                try {
                    UUID.fromString(file.nameWithoutExtension) to NbtIo.readCompressed(file)
                } catch (e: Exception) {
                    null
                }
            }
            ?.toMap() ?: emptyMap()
    }
}