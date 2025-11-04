package com.xiaofan.chatai.aitree

import com.xiaofan.chatai.ChatAI
import com.xiaofan.chatai.aiplayerentity.AIPlayerEntity
import net.minecraft.block.Blocks
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.item.*
import net.minecraft.registry.tag.BlockTags
import net.minecraft.util.Hand
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import kotlin.math.abs
import kotlin.math.max

/**
 * 资源收集行为 - 包括砍树和挖矿
 */
class ChopTreeAction(private val aiPlayer: AIPlayerEntity) : ActionNode("chop") {
    var targetTree: BlockPos? = null
    var bestAxe: ItemStack? = null
    var pathBlocked = false
    private var chopTicks = 0
    private var travelTicks = 0
    private val MAX_TRAVEL_TICKS = 600
    private val BLOCK_CHECK_INTERVAL = 100L
    private var lastBlockCheckTime: Long = 0

    override fun start() {
        targetTree = findNearestTree()
        bestAxe = findBestAxe()
        status = if (targetTree != null) Status.RUNNING else Status.FAILURE
        pathBlocked = false
        travelTicks = 0

        bestAxe?.let { axe ->
            aiPlayer.setStackInHand(Hand.MAIN_HAND, axe)
            aiPlayer.debugLog("[砍树] 已装备工具: ${axe.item.name.string}")
        }

        aiPlayer.debugLog("[砍树] 开始砍树行为，目标位置: $targetTree")
    }

    override fun tick() {
        val currentTime = aiPlayer.world.time
        val shouldCheckBlock = currentTime - lastBlockCheckTime > BLOCK_CHECK_INTERVAL
        targetTree?.let { pos ->
            if (travelTicks++ > MAX_TRAVEL_TICKS) {
                aiPlayer.debugLog("[砍树] 前往树木超时，放弃目标")
                targetTree = findNearestTree()
                travelTicks = 0
                if (targetTree == null) {
                    status = Status.FAILURE
                    aiPlayer.debugLog("[砍树] 找不到可替代的树木")
                }
                return
            }

            if (!aiPlayer.world.getBlockState(pos).isIn(BlockTags.LOGS)) {
                aiPlayer.debugLog("[砍树] 目标树木已消失")
                if (hasEnoughWood(10)) {
                    status = Status.SUCCESS
                    aiPlayer.debugLog("[砍树] 已获得足够木材")
                } else {
                    targetTree = findNearestTree()
                    travelTicks = 0
                    if (targetTree == null) {
                        status = Status.FAILURE
                        aiPlayer.debugLog("[砍树] 找不到可砍伐的树木")
                    }
                }
                return
            }

            if (shouldCheckBlock) {
                lastBlockCheckTime = currentTime
                if (!pathBlocked && isPathBlocked(targetTree!!)) {
                    pathBlocked = true
                    aiPlayer.debugLog("[砍树] 定期检测到路径阻塞")
                }
            }

            if (pathBlocked) {
                clearPathToTree(pos)
                return
            }

            if (aiPlayer.squaredDistanceTo(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5) > 4.0) {
                aiPlayer.navigation.startMovingTo(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5, 2.0)
                return
            } else {
                travelTicks = 0
            }

            if (chopTicks++ >= getChopInterval()) {
                aiPlayer.swingHand(Hand.MAIN_HAND)
                if (aiPlayer.world.breakBlock(pos, true)) {
                    aiPlayer.debugLog("[砍树] 成功砍伐树木 at $pos")
                    if (hasEnoughWood(10)) {
                        status = Status.SUCCESS
                    } else {
                        targetTree = findNearestTree()
                        travelTicks = 0
                        if (targetTree == null) {
                            status = Status.FAILURE
                        }
                    }
                }
                chopTicks = 0
            }
        } ?: run {
            status = Status.FAILURE
        }
    }

    private fun findBestAxe(): ItemStack? {
        val hands = listOf(
            aiPlayer.getStackInHand(Hand.MAIN_HAND),
            aiPlayer.getStackInHand(Hand.OFF_HAND)
        )
        val inventoryItems = (0 until aiPlayer.inventory.size()).map {
            aiPlayer.inventory.getStack(it)
        }
        return (hands + inventoryItems).maxByOrNull { stack ->
            when (stack.item) {
                Items.NETHERITE_AXE -> 4
                Items.DIAMOND_AXE -> 3
                Items.IRON_AXE -> 2
                Items.STONE_AXE -> 1
                Items.WOODEN_AXE -> 0
                else -> -1
            }
        }?.takeIf { it.item is AxeItem }
    }

    private fun getChopInterval(): Int {
        return when (bestAxe?.item) {
            Items.NETHERITE_AXE -> 5
            Items.DIAMOND_AXE -> 8
            Items.IRON_AXE -> 12
            Items.STONE_AXE -> 16
            Items.WOODEN_AXE -> 20
            else -> 30
        }
    }

    private fun hasEnoughWood(required: Int): Boolean {
        val handStacks = listOf(
            aiPlayer.getStackInHand(Hand.MAIN_HAND),
            aiPlayer.getStackInHand(Hand.OFF_HAND)
        )
        val inventoryStacks = (0 until aiPlayer.inventory.size()).map {
            aiPlayer.inventory.getStack(it)
        }
        return (handStacks + inventoryStacks).sumOf { stack ->
            if (isLog(stack)) stack.count else 0
        } >= required
    }

    private fun isLog(stack: ItemStack): Boolean {
        return stack.item is BlockItem && (stack.item as BlockItem).block.defaultState.isIn(BlockTags.LOGS)
    }

    private fun isPathBlocked(treePos: BlockPos): Boolean {
        val world = aiPlayer.world
        val aiPos = aiPlayer.blockPos

        val dx = treePos.x - aiPos.x
        val dz = treePos.z - aiPos.z
        val steps = max(abs(dx), abs(dz))

        for (i in 1..steps) {
            val x = aiPos.x + dx * i / steps
            val z = aiPos.z + dz * i / steps
            for (y in aiPos.y..aiPos.y + 2) {
                val pos = BlockPos(x, y, z)
                val state = world.getBlockState(pos)
                if ((state.isIn(BlockTags.LEAVES) || state.isIn(BlockTags.LOGS)) && pos != treePos) {
                    return true
                }
            }
        }
        return false
    }

    private fun clearPathToTree(treePos: BlockPos) {
        val world = aiPlayer.world
        val aiPos = aiPlayer.blockPos
        val radius = 3

        val lookVec = aiPlayer.rotationVector
        val frontPos = aiPos.add(lookVec.x.toInt(), lookVec.y.toInt(), lookVec.z.toInt())
        val frontState = world.getBlockState(frontPos)

        if ((frontState.isIn(BlockTags.LEAVES) || frontState.isIn(BlockTags.LOGS)) && frontPos != treePos) {
            if (aiPlayer.world.breakBlock(frontPos, true)) {
                aiPlayer.debugLog("[砍树] 清除前方障碍 at $frontPos")
                if (!isPathBlocked(treePos)) {
                    pathBlocked = false
                }
                return
            }
        }

        for (x in aiPos.x - radius..aiPos.x + radius) {
            for (y in aiPos.y..aiPos.y + 2) {
                for (z in aiPos.z - radius..aiPos.z + radius) {
                    val pos = BlockPos(x, y, z)
                    if (pos == treePos) continue

                    val state = world.getBlockState(pos)
                    if ((state.isIn(BlockTags.LEAVES) || state.isIn(BlockTags.LOGS))) {
                        if (aiPlayer.world.breakBlock(pos, true)) {
                            aiPlayer.debugLog("[砍树] 清除周围障碍 at $pos")
                            if (!isPathBlocked(treePos)) {
                                pathBlocked = false
                            }
                            return
                        }
                    }
                }
            }
        }

        if (!isPathBlocked(treePos)) {
            pathBlocked = false
            aiPlayer.debugLog("[砍树] 路径已畅通")
        }
    }

    private fun findNearestTree(): BlockPos? {
        val world = aiPlayer.world
        val center = aiPlayer.blockPos
        val radius = 32

        for (y in -10..10) {
            for (x in -radius..radius) {
                for (z in -radius..radius) {
                    val pos = center.add(x, y, z)
                    val state = world.getBlockState(pos)
                    if (state.isIn(BlockTags.LOGS)) {
                        return pos
                    }
                }
            }
        }
        return null
    }
}

class MiningAction(private val aiPlayer: AIPlayerEntity) : ActionNode("mine") {
    companion object {
        private val COBBLESTONE_ITEM = Items.COBBLESTONE
        private val STONE_BLOCK = Blocks.STONE
    }

    var targetPos: BlockPos? = null
    var bestPickaxe: ItemStack = ItemStack.EMPTY
    private var hasPlanks = false
    private var miningTicks = 0
    var returning = false

    private var returnDirection: Vec3d? = null
    private var returnStartPos: BlockPos? = null
    private var lastMinedPos: BlockPos? = null
    private val MAX_RETURN_TICKS = 1200

    private fun findMineableStone(): BlockPos? {
        val world = aiPlayer.world
        val center = aiPlayer.blockPos
        val minY = world.bottomY

        for (y in center.y downTo max(center.y - 16, minY)) {
            for (x in -1..1) {
                for (z in -1..1) {
                    val pos = BlockPos(center.x + x, y, center.z + z)
                    if (world.getBlockState(pos).isIn(BlockTags.BASE_STONE_OVERWORLD)) {
                        return pos
                    }
                }
            }
        }
        return null
    }

    override fun start() {
        targetPos = findMineableStone()
        bestPickaxe = findBestPickaxe()
        hasPlanks = checkPlanks()

        status = if (targetPos != null && !bestPickaxe.isEmpty && hasPlanks) {
            equipPickaxe()
            returnStartPos = aiPlayer.blockPos
            Status.RUNNING
        } else {
            Status.FAILURE
        }
        aiPlayer.debugLog("[挖矿] 开始挖矿行为")
    }

    override fun tick() {
        if (!aiPlayer.world.isClient) {
            if (countCobblestone() >= 64 && !returning) {
                returning = true
                miningTicks = 0
                aiPlayer.debugLog("[挖矿] 已收集64个圆石，开始返回地面")
                return
            }

            if (returning) {
                returnToSurface()
            } else {
                mineStone()
            }

            if (miningTicks++ > MAX_RETURN_TICKS) {
                status = Status.FAILURE
                aiPlayer.debugLog("[挖矿] 挖矿超时，终止行为")
            }
        }
    }

    fun countCobblestone(): Int {
        var total = 0
        for (i in 0 until aiPlayer.inventory.size()) {
            val stack = aiPlayer.inventory.getStack(i)
            if (stack.item == COBBLESTONE_ITEM) {
                total += stack.count
            }
        }
        return total
    }

    private fun mineStone() {
        val stonePos = targetPos ?: run {
            status = Status.FAILURE
            return
        }

        if (aiPlayer.blockPos.y > stonePos.y) {
            digDownward()
            return
        }

        if (miningTicks % getMiningInterval() == 0) {
            mineArea(stonePos)
        }

        if (!aiPlayer.world.getBlockState(stonePos).isIn(BlockTags.BASE_STONE_OVERWORLD)) {
            findNextTarget()
        }
    }

    private fun digDownward() {
        val digPos = aiPlayer.blockPos.down()
        if (aiPlayer.world.getBlockState(digPos).isSolidBlock(aiPlayer.world, digPos)) {
            aiPlayer.swingHand(Hand.MAIN_HAND)
            if (aiPlayer.world.breakBlock(digPos, true)) {
                lastMinedPos = digPos
            }
        }
    }

    private fun mineArea(center: BlockPos) {
        for (x in -1..1) {
            for (z in -1..1) {
                val pos = center.add(x, 0, z)
                if (aiPlayer.world.getBlockState(pos).isIn(BlockTags.BASE_STONE_OVERWORLD)) {
                    aiPlayer.swingHand(Hand.MAIN_HAND)
                    if (aiPlayer.world.breakBlock(pos, true)) {
                        lastMinedPos = pos
                    }
                }
            }
        }
    }

    private fun handleHeadBlocking() {
        val world = aiPlayer.world
        val playerPos = aiPlayer.blockPos
        val bedrockIdentifier = Identifier("minecraft:bedrock")

        for (yOffset in 1..3) {
            for (xOffset in -1..1) {
                for (zOffset in -1..1) {
                    val checkPos = playerPos.add(xOffset, yOffset, zOffset)
                    val state = world.getBlockState(checkPos)
                    val block = state.block
                    val blockId = block.translationKey

                    if (blockId.contains(bedrockIdentifier.path)) continue

                    val shouldBreak = state.isSolidBlock(world, checkPos) ||
                            state.isIn(BlockTags.LEAVES) ||
                            !state.isFullCube(world, checkPos)

                    if (shouldBreak) {
                        aiPlayer.swingHand(Hand.MAIN_HAND)
                        if (world.breakBlock(checkPos, true)) {
                            aiPlayer.debugLog("[挖矿] 清除障碍 at $checkPos")
                            if (yOffset == 1 && (xOffset != 0 || zOffset != 0)) {
                                return
                            }
                        }
                    }
                }
            }
        }
    }

    private fun returnToSurface() {
        handleHeadBlocking()

        if (aiPlayer.blockPos.y >= aiPlayer.world.topY - 10) {
            status = Status.SUCCESS
        }

        if (returnDirection == null) {
            calculateReturnDirection()
        }

        val nextPos = calculateNextReturnPos() ?: run {
            status = Status.FAILURE
            return
        }

        if (aiPlayer.world.getBlockState(nextPos).isSolidBlock(aiPlayer.world, nextPos)) {
            handleBlockingBlock(nextPos)
            return
        }

        moveToNextPos(nextPos)

        if (aiPlayer.blockPos.y >= aiPlayer.world.topY - 10) {
            status = Status.SUCCESS
            aiPlayer.debugLog("[挖矿] 已成功返回地面")
        }
    }

    private fun calculateReturnDirection() {
        val startPos = returnStartPos ?: aiPlayer.blockPos
        val toSurface = Vec3d(
            (startPos.x - aiPlayer.blockPos.x).toDouble(),
            (aiPlayer.world.topY - aiPlayer.blockPos.y).toDouble(),
            (startPos.z - aiPlayer.blockPos.z).toDouble()
        ).normalize()

        returnDirection = Vec3d(
            toSurface.x * 0.5,
            0.707,
            toSurface.z * 0.5
        ).normalize()

        aiPlayer.debugLog("[挖矿] 设置返回方向: $returnDirection")
    }

    private fun calculateNextReturnPos(): BlockPos? {
        returnDirection?.let { dir ->
            return aiPlayer.blockPos.add(
                (dir.x * 2).toInt(),
                (dir.y * 2).toInt(),
                (dir.z * 2).toInt()
            )
        }
        return null
    }

    private fun handleBlockingBlock(pos: BlockPos) {
        aiPlayer.swingHand(Hand.MAIN_HAND)
        if (aiPlayer.world.breakBlock(pos, true)) {
            lastMinedPos = pos
            if (miningTicks % 5 == 0) return
        } else {
            adjustReturnDirection()
        }
    }

    private fun moveToNextPos(pos: BlockPos) {
        val targetY = pos.y + 1
        aiPlayer.navigation.startMovingTo(
            pos.x + 0.5,
            targetY.toDouble(),
            pos.z + 0.5,
            1.0
        )

        if (aiPlayer.blockPos.y < pos.y) {
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
    }

    private fun adjustReturnDirection() {
        returnDirection = returnDirection?.rotateY(22.5f)
        aiPlayer.debugLog("[挖矿] 调整返回方向: $returnDirection")
    }

    private fun Vec3d.rotateY(degrees: Float): Vec3d {
        val rad = Math.toRadians(degrees.toDouble())
        val cos = Math.cos(rad)
        val sin = Math.sin(rad)
        return Vec3d(
            this.x * cos - this.z * sin,
            this.y,
            this.x * sin + this.z * cos
        )
    }

    private fun findNextTarget() {
        targetPos = findMineableStone()
        if (targetPos == null && countCobblestone() >= 64) {
            returning = true
        } else if (targetPos == null) {
            status = Status.FAILURE
        }
    }

    private fun findBestPickaxe(): ItemStack {
        var best = ItemStack.EMPTY
        var bestTier = -1

        for (i in 0 until aiPlayer.inventory.size()) {
            val stack = aiPlayer.inventory.getStack(i)
            if (stack.item is PickaxeItem) {
                val tier = (stack.item as PickaxeItem).material.miningLevel
                if (tier > bestTier) {
                    bestTier = tier
                    best = stack
                }
            }
        }
        return best
    }

    private fun equipPickaxe() {
        for (i in 0 until aiPlayer.inventory.size()) {
            if (aiPlayer.inventory.getStack(i) == bestPickaxe) {
                val mainHand = aiPlayer.getStackInHand(Hand.MAIN_HAND)
                aiPlayer.inventory.setStack(i, mainHand)
                aiPlayer.setStackInHand(Hand.MAIN_HAND, bestPickaxe)
                break
            }
        }
    }

    private fun checkPlanks(): Boolean {
        return findPlankSlot() != -1
    }

    private fun findPlankSlot(): Int {
        for (i in 0 until aiPlayer.inventory.size()) {
            val stack = aiPlayer.inventory.getStack(i)
            if (stack.item is BlockItem &&
                (stack.item as BlockItem).block.defaultState.isIn(BlockTags.PLANKS)) {
                return i
            }
        }
        return -1
    }

    private fun getMiningInterval(): Int {
        return when (bestPickaxe.item) {
            Items.NETHERITE_PICKAXE -> 5
            Items.DIAMOND_PICKAXE -> 8
            Items.IRON_PICKAXE -> 12
            Items.STONE_PICKAXE -> 16
            Items.WOODEN_PICKAXE -> 20
            else -> 30
        }
    }
}

