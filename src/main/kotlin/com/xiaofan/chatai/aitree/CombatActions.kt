package com.xiaofan.chatai.aitree

import com.xiaofan.chatai.aiplayerentity.AIPlayerEntity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.mob.CreeperEntity
import net.minecraft.entity.mob.HostileEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import java.util.function.Predicate

/**
 * 战斗行为 - 包括战斗和逃跑
 */
class CombatAction(private val aiPlayer: AIPlayerEntity, val retaliate: Boolean) : ActionNode("combat") {
    private var target: LivingEntity? = null
    private var bestSword: ItemStack? = null
    private var combatTicks = 0
    private val searchRange = if (retaliate) 32.0 else 16.0

    override fun start() {
        bestSword = findBestSword()
        bestSword?.let { sword ->
            aiPlayer.setStackInHand(Hand.MAIN_HAND, sword)
        }

        target = findTarget()
        status = if (target != null) Status.RUNNING else Status.FAILURE
        aiPlayer.debugLog("[战斗] ${if (retaliate) "反击" else "攻击"}行为开始")
    }

    override fun tick() {
        target?.takeIf { !it.isAlive }?.let { deadTarget ->
            if (deadTarget is PlayerEntity) {
                aiPlayer.angerTargets.remove(deadTarget.uuid)
                aiPlayer.debugLog("[战斗] 移除已死亡玩家 ${deadTarget.name.string} 的仇恨")
            }
        }

        if (aiPlayer.health < 3f) {
            status = Status.FAILURE
            return
        }

        target = findTarget()
        target?.let { enemy ->
            if (!enemy.isAlive) {
                status = Status.SUCCESS
                return
            }

            if (aiPlayer.canSee(enemy) && aiPlayer.distanceTo(enemy) < 3.0) {
                aiPlayer.swingHand(Hand.MAIN_HAND)
                aiPlayer.tryAttack(enemy)
            }

            if (combatTicks % 10 == 0 && aiPlayer.distanceTo(enemy) > 3.0) {
                aiPlayer.navigation.startMovingTo(enemy, 1.0)
            }

            combatTicks++
        } ?: run {
            status = Status.FAILURE
        }
    }

    private fun findTarget(): LivingEntity? {
        return aiPlayer.world.getEntitiesByClass(
            LivingEntity::class.java,
            aiPlayer.boundingBox.expand(searchRange, searchRange/2, searchRange),
            { entity ->
                when {
                    retaliate -> entity is PlayerEntity &&
                            aiPlayer.angerTargets.containsKey(entity.uuid) &&
                            entity.isAlive
                    else -> entity !is AIPlayerEntity &&
                            entity !is PlayerEntity &&
                            entity !is CreeperEntity &&
                            entity is HostileEntity &&
                            entity.isAlive
                }
            }
        ).minByOrNull { aiPlayer.distanceTo(it) }
    }

    private fun findBestSword(): ItemStack? {
        var bestTool: ItemStack? = null
        var bestLevel = -1
        for (i in 0 until aiPlayer.inventory.size()) {
            val stack = aiPlayer.inventory.getStack(i)
            val level = when (stack.item) {
                Items.NETHERITE_SWORD -> 4
                Items.DIAMOND_SWORD -> 3
                Items.IRON_SWORD -> 2
                Items.STONE_SWORD -> 1
                Items.WOODEN_SWORD -> 0
                else -> -1
            }
            if (level > bestLevel) {
                bestTool = stack
                bestLevel = level
            }
        }
        return bestTool
    }
}

class FleeAction(private val aiPlayer: AIPlayerEntity) : ActionNode("flee") {
    private var fleePos: BlockPos? = null
    private var fleeTicks = 0

    override fun start() {
        fleePos = findSafePosition()
        status = if (fleePos != null) Status.RUNNING else Status.FAILURE
        aiPlayer.debugLog("[逃跑] 血量过低，开始逃跑")
    }

    override fun tick() {
        if (aiPlayer.health >= 5f || fleeTicks > 200) {
            status = Status.SUCCESS
            return
        }

        fleePos?.let { pos ->
            val targetPos = Vec3d(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5)
            if (aiPlayer.pos.distanceTo(targetPos) > 3.0) {
                aiPlayer.navigation.startMovingTo(
                    pos.x + 0.5,
                    pos.y.toDouble(),
                    pos.z + 0.5,
                    1.5
                )
            } else {
                fleePos = findSafePosition()
            }
        } ?: run {
            status = Status.FAILURE
        }
        fleeTicks++
    }

    private fun findSafePosition(): BlockPos? {
        val center = aiPlayer.blockPos
        for (radius in 5..20 step 5) {
            for (i in 0..10) {
                val pos = center.add(
                    aiPlayer.random.nextBetween(-radius, radius),
                    0,
                    aiPlayer.random.nextBetween(-radius, radius)
                )
                if (isPositionSafe(pos)) {
                    return pos
                }
            }
        }
        return null
    }

    private fun isPositionSafe(pos: BlockPos): Boolean {
        val world = aiPlayer.world
        val state = world.getBlockState(pos)
        val predicate = Predicate<HostileEntity> { true }
        return state.isAir &&
                world.getBlockState(pos.up()).isAir &&
                !world.getBlockState(pos.down()).isAir &&
                world.getEntitiesByClass(
                    HostileEntity::class.java,
                    Box.of(Vec3d.of(pos), 8.0, 8.0, 8.0),
                    predicate
                ).isEmpty()
    }
}

