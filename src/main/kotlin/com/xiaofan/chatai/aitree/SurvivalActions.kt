package com.xiaofan.chatai.aitree

import com.xiaofan.chatai.aiplayerentity.AIPlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/**
 * 生存行为 - 包括睡觉和空闲
 */
class GoToBedAction(private val aiPlayer: AIPlayerEntity) : ActionNode("goToBed") {
    var bedPos: BlockPos? = null

    override fun start() {
        bedPos = aiPlayer.bedPos
        status = if (bedPos != null && isNightTime(aiPlayer.world)) {
            Status.RUNNING
        } else {
            Status.FAILURE
        }
    }

    override fun tick() {
        bedPos?.let { pos ->
            if (aiPlayer.squaredDistanceTo(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5) > 2.25) {
                aiPlayer.navigation.startMovingTo(
                    pos.x + 0.5,
                    pos.y.toDouble(),
                    pos.z + 0.5,
                    1.0
                )
            } else {
                status = Status.SUCCESS
            }
        } ?: run {
            status = Status.FAILURE
        }
    }

    private fun isNightTime(world: World): Boolean {
        val time = world.timeOfDay % 24000
        return time in 13000..23000
    }
}

class IdleAction(aiPlayer: AIPlayerEntity) : ActionNode("idle") {
    override fun start() {
        status = Status.SUCCESS
    }

    override fun tick() {
    }
}

