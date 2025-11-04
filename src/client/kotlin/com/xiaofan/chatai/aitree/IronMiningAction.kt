package com.xiaofan.chatai.aitree

import com.xiaofan.chatai.ChatAI
import com.xiaofan.chatai.aiplayerentity.AIPlayerEntity
import net.minecraft.block.Blocks
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.mob.HostileEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import java.util.function.Predicate

/**
 * 铁矿挖掘行为
 */
class IronMiningAction(private val aiPlayer: AIPlayerEntity) : ActionNode("ironMine") {
    val clearAction = ClearInventoryAction(aiPlayer)
    
    companion object {
        // 方块和物品常量
        private val IRON_ORE = Blocks.IRON_ORE
        private val RAW_IRON = Items.RAW_IRON
        private val IRON_INGOT = Items.IRON_INGOT
        private val STONE_PICKAXE = Items.STONE_PICKAXE
        private val IRON_PICKAXE = Items.IRON_PICKAXE
        private val DIAMOND_PICKAXE = Items.DIAMOND_PICKAXE
        private val NETHERITE_PICKAXE = Items.NETHERITE_PICKAXE
        private val BEDROCK = Blocks.BEDROCK

        // 时间常量
        private const val WAIT_TICKS = 60 // 3秒等待时间(20 ticks/秒)
    }

    // 挖掘阶段枚举
    private enum class MiningStage {
        SEARCH_ORE,
        MOVE_TO_XZ,
        DESCEND,
        MINE_VEIN,
        WAIT_BEFORE_RETURN,
        RETURN_UP
    }

    // 状态变量
    private var targetOrePos: BlockPos? = null
    private var miningStage = MiningStage.SEARCH_ORE
    private var originalPos: BlockPos? = null
    private val minedBlocks = mutableSetOf<BlockPos>()
    private var waitTimer = 0
    private var miningTimer = 0
    private var descendTimer = 0

    override fun start() {
        if (!shouldStartIronMining()) {
            status = Status.FAILURE
            return
        }
        originalPos = aiPlayer.blockPos
        status = Status.RUNNING
        aiPlayer.debugLog("[铁矿挖掘] 开始铁矿挖掘行为")
    }

    fun shouldStartIronMining(): Boolean {
        return isDaytime(aiPlayer.world) &&
                !hasNearbyHostiles() &&
                hasStonePickaxe() &&
                !hasEnoughIron() &&
                findIronOre() != null
    }

    private fun isDaytime(world: World): Boolean {
        return world.timeOfDay % 24000 in 0..12000
    }

    private fun hasNearbyHostiles(): Boolean {
        val box = aiPlayer.boundingBox.expand(16.0)
        return !aiPlayer.world.getEntitiesByClass(
            HostileEntity::class.java,
            box,
            Predicate { it.isAlive }
        ).isEmpty()
    }

    private fun hasStonePickaxe(): Boolean {
        return (0 until aiPlayer.inventory.size()).any { i ->
            aiPlayer.inventory.getStack(i).isOf(STONE_PICKAXE)
        } || aiPlayer.getStackInHand(Hand.MAIN_HAND).isOf(STONE_PICKAXE)
    }

    private fun hasEnoughIron(): Boolean {
        var total = 0
        for (i in 0 until aiPlayer.inventory.size()) {
            val stack = aiPlayer.inventory.getStack(i)
            when {
                stack.isOf(RAW_IRON) || stack.isOf(IRON_INGOT) -> total += stack.count
            }
            if (total >= 64) return true
        }
        return false
    }

    private fun findIronOre(): BlockPos? {
        val world = aiPlayer.world
        val chunk = world.getChunk(aiPlayer.blockPos)
        val topY = world.topY - 1
        val bottomY = world.bottomY + 5

        for (y in topY downTo bottomY) {
            for (x in 0..15) {
                for (z in 0..15) {
                    val pos = chunk.pos.getBlockPos(x, y, z)
                    if (world.getBlockState(pos).isOf(IRON_ORE) &&
                        !minedBlocks.contains(pos)) {
                        return pos
                    }
                }
            }
        }
        return null
    }

    override fun tick() {
        when (miningStage) {
            MiningStage.SEARCH_ORE -> handleSearchOre()
            MiningStage.MOVE_TO_XZ -> handleMoveToXZ()
            MiningStage.DESCEND -> handleDescend()
            MiningStage.MINE_VEIN -> handleMineVein()
            MiningStage.WAIT_BEFORE_RETURN -> handleWaitBeforeReturn()
            MiningStage.RETURN_UP -> handleReturnUp()
        }
    }

    private fun handleSearchOre() {
        targetOrePos = findIronOre()
        if (targetOrePos != null) {
            miningStage = MiningStage.MOVE_TO_XZ
            aiPlayer.debugLog("发现铁矿位置: $targetOrePos")
        } else {
            status = Status.FAILURE
        }
    }

    private fun handleMoveToXZ() {
        targetOrePos?.let { pos ->
            val targetXZ = Vec3d(pos.x + 0.5, aiPlayer.y, pos.z + 0.5)
            if (aiPlayer.squaredDistanceTo(targetXZ) > 4.0) {
                aiPlayer.navigation.startMovingTo(targetXZ.x, targetXZ.y, targetXZ.z, 1.0)
            } else {
                miningStage = MiningStage.DESCEND
                aiPlayer.debugLog("已到达铁矿上方，开始下挖")
            }
        } ?: run { status = Status.FAILURE }
    }

    private fun handleDescend() {
        targetOrePos?.let { orePos ->
            if (aiPlayer.blockPos.y > orePos.y) {
                descendTimer++

                val requiredTicks = getDiggingTicksRequired()

                if (descendTimer >= requiredTicks) {
                    descendTimer = 0
                    val digPos = aiPlayer.blockPos.down()
                    if (!aiPlayer.world.getBlockState(digPos).isOf(BEDROCK)) {
                        breakBlock(digPos)
                    }
                    aiPlayer.navigation.stop()
                    aiPlayer.updatePosition(aiPlayer.x, aiPlayer.y - 1.0, aiPlayer.z)

                    if (aiPlayer.blockPos.y <= orePos.y) {
                        miningStage = MiningStage.MINE_VEIN
                    }
                }
            } else {
                miningStage = MiningStage.MINE_VEIN
            }
        }
    }

    private fun handleMineVein() {
        targetOrePos?.let { orePos ->
            if (mineIronVein(orePos)) {
                miningStage = MiningStage.WAIT_BEFORE_RETURN
                waitTimer = 0
                aiPlayer.debugLog("铁矿脉挖掘完成，等待3秒后返回地面")
            }
        }
    }

    private fun handleWaitBeforeReturn() {
        if (++waitTimer >= WAIT_TICKS) {
            miningStage = MiningStage.RETURN_UP
            aiPlayer.debugLog("等待结束，开始返回地面")
        }
    }

    private fun handleReturnUp(): Boolean {
        originalPos?.let { surfacePos ->
            clearPathAbove()

            if (aiPlayer.blockPos.y < surfacePos.y) {
                aiPlayer.navigation.stop()
                attemptJump()
                aiPlayer.updatePosition(aiPlayer.x, aiPlayer.y + 0.5, aiPlayer.z)
                return false
            } else {
                status = Status.SUCCESS
                aiPlayer.debugLog("成功返回地面")
                // 直接调用clear方法设置清除规则
                clearAction.clear(
                    Items.COBBLESTONE to 64,      // 保留64个圆石
                    Items.STONE to "all",         // 清除所有石头
                    Items.GRANITE to "all",       // 清除所有花岗岩
                    Items.DIORITE to "all",       // 清除所有闪长岩
                    Items.ANDESITE to "all",      // 清除所有安山岩
                    Items.DEEPSLATE to "all",     // 清除所有深板岩
                    Items.BLACKSTONE to "all",    // 清除所有黑石
                    Items.BASALT to "all",        // 清除所有玄武岩
                    Items.CALCITE to "all"        // 清除所有方解石
                ).tick()
                return true
            }
        }
        return true
    }

    private fun mineIronVein(startPos: BlockPos): Boolean {
        val world = aiPlayer.world
        var foundOre = false

        for (radius in 1..5) {
            for (x in -radius..radius) {
                for (y in -radius..radius) {
                    for (z in -radius..radius) {
                        val pos = startPos.add(x, y, z)
                        if (!minedBlocks.contains(pos) &&
                            world.getBlockState(pos).isOf(IRON_ORE)) {

                            miningTimer++
                            val requiredTicks = getMiningTicksRequired()

                            if (miningTimer >= requiredTicks) {
                                if (breakBlock(pos)) {
                                    miningTimer = 0
                                    foundOre = true
                                }
                            }
                            return false
                        }
                    }
                }
                if (foundOre && radius == 1) break
            }
        }
        return !foundOre
    }

    private fun getDiggingTicksRequired(): Int {
        val heldItem = aiPlayer.mainHandStack
        return when {
            heldItem.isOf(NETHERITE_PICKAXE) -> 3
            heldItem.isOf(DIAMOND_PICKAXE) -> 5
            heldItem.isOf(IRON_PICKAXE) -> 8
            heldItem.isOf(STONE_PICKAXE) -> 12
            else -> 20
        }
    }

    private fun getMiningTicksRequired(): Int {
        val heldItem = aiPlayer.mainHandStack
        return when {
            heldItem.isOf(NETHERITE_PICKAXE) -> 5
            heldItem.isOf(DIAMOND_PICKAXE) -> 7
            heldItem.isOf(IRON_PICKAXE) -> 10
            heldItem.isOf(STONE_PICKAXE) -> 15
            else -> 30
        }
    }

    private fun breakBlock(pos: BlockPos): Boolean {
        val world = aiPlayer.world
        if (world.getBlockState(pos).isAir) return true

        aiPlayer.swingHand(Hand.MAIN_HAND)
        if (world.breakBlock(pos, true)) {
            minedBlocks.add(pos)
            return true
        }
        return false
    }

    private fun clearPathAbove() {
        val headPos = aiPlayer.blockPos.up()
        for (x in 0..1) {
            for (z in 0..1) {
                val clearPos = headPos.add(x, 0, z)
                if (!aiPlayer.world.getBlockState(clearPos).isOf(BEDROCK)) {
                    breakBlock(clearPos)
                }
            }
        }
    }

    private fun attemptJump() {
        try {
            val jumpMethod = LivingEntity::class.java.getDeclaredMethod("jump")
            jumpMethod.isAccessible = true
            jumpMethod.invoke(aiPlayer)
        } catch (e: Exception) {
            ChatAI.LOGGER.error("跳跃失败: ${e.message}")
            val levitationEffect = StatusEffectInstance(
                StatusEffects.LEVITATION,
                20 * 20,
                19,
                false,
                false,
                false
            )
            aiPlayer.addStatusEffect(levitationEffect)
        }
    }

    fun shouldInterrupt(): Boolean = false
}

