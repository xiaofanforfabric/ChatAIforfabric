package com.xiaofan.chatai.aitree

import com.xiaofan.chatai.ChatAI
import net.minecraft.nbt.NbtCompound
import net.minecraft.util.math.BlockPos
import com.xiaofan.chatai.aiplayerentity.AIPlayerEntity
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.mob.CreeperEntity
import net.minecraft.entity.mob.HostileEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.*
import net.minecraft.registry.tag.BlockTags
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Hand
import net.minecraft.util.Identifier
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import java.util.function.Predicate
import kotlin.math.abs
import kotlin.math.max

class BehaviorTree(private val aiPlayer: AIPlayerEntity) {



    private fun shouldSmeltOres(): Boolean {
        return isDayTime(aiPlayer.world) &&
                !hasNearbyHostileMobs(1) &&
                hasFurnaceInInventory() &&
                hasSmeltableOres(64)
    }


    private fun hasSmeltableOres(required: Int): Boolean {
        val smeltableOres = listOf(
            Items.IRON_ORE,
            Items.GOLD_ORE,
            Items.COPPER_ORE,
            Items.RAW_IRON,
            Items.RAW_GOLD,
            Items.RAW_COPPER
        )

        var total = 0
        for (i in 0 until aiPlayer.inventory.size()) {
            val stack = aiPlayer.inventory.getStack(i)
            if (stack.item in smeltableOres) {
                total += stack.count
                if (total >= required) return true
            }
        }
        return false
    }



    private fun shouldCraftFurnace(): Boolean {
        return isDayTime(aiPlayer.world) &&
                !hasNearbyHostileMobs(1) &&
                !hasFurnaceInInventory() &&
                hasEnoughCobblestone(8)
    }

    private fun hasFurnaceInInventory(): Boolean {
        for (i in 0 until aiPlayer.inventory.size()) {
            if (aiPlayer.inventory.getStack(i).item == Items.FURNACE) {
                return true
            }
        }
        return false
    }

    private fun hasEnoughCobblestone(required: Int): Boolean {
        var total = 0
        for (i in 0 until aiPlayer.inventory.size()) {
            val stack = aiPlayer.inventory.getStack(i)
            if (stack.item == Items.COBBLESTONE) {
                total += stack.count
                if (total >= required) return true
            }
        }
        return false
    }


    public val clearAction = ClearInventoryAction(aiPlayer)

    private fun shouldCraftStoneTools(): Boolean {
        if (!isDayTime(aiPlayer.world) || hasNearbyHostileMobs(1)) return false

        var hasCobblestone = false
        var hasSticks = false
        var hasStoneTools = false

        // 检查背包和手持物品
        for (i in 0 until aiPlayer.inventory.size()) {
            val stack = aiPlayer.inventory.getStack(i)
            when (stack.item) {
                Items.COBBLESTONE -> if (stack.count >= 8) hasCobblestone = true
                Items.STICK -> if (stack.count >= 5) hasSticks = true
                Items.STONE_PICKAXE, Items.STONE_SWORD, Items.STONE_AXE -> hasStoneTools = true
            }
        }

        return hasCobblestone && hasSticks && !hasStoneTools
    }

    private var currentAction: ActionNode? = null
    private var lastStatusUpdateTime = 0L

    private fun isDaytime(world: World): Boolean {
        val time = world.timeOfDay % 24000
        return time in 0..12000  // 0-12000为白天
    }

    private fun hasNearbyHostiles(range: Int): Boolean {
        val box = aiPlayer.boundingBox.expand(range.toDouble())
        return aiPlayer.world.getEntitiesByClass(
            HostileEntity::class.java,
            box
        ) { it.isAlive && it !is CreeperEntity }.isNotEmpty()
    }

    private fun hasEnoughItems(item: Item, count: Int): Boolean {
        var total = 0
        for (i in 0 until aiPlayer.inventory.size()) {
            val stack = aiPlayer.inventory.getStack(i)
            if (stack.item == item) {
                total += stack.count
                if (total >= count) return true
            }
        }
        return false
    }

    private fun findMineableStone(): BlockPos? {
        val world = aiPlayer.world
        val center = aiPlayer.blockPos
        val minY = world.bottomY

        // 向下搜索16格范围内的石头
        for (y in center.y downTo max(center.y - 16, minY)) {
            for (x in -1..1) {
                for (z in -1..1) {
                    val pos = BlockPos(center.x + x, y, center.z + z)
                    if (world.getBlockState(pos).isOf(Blocks.STONE)) {
                        return pos
                    }
                }
            }
        }
        return null
    }

    private fun shouldMine(): Boolean {
        return isDaytime(aiPlayer.world) &&
                !hasNearbyHostiles(1) &&
                !hasEnoughItems(Items.STONE, 64) &&
                hasPickaxe() &&
                findMineableStone() != null
    }

    private fun hasEnoughStone(required: Int): Boolean {
        return aiPlayer.countWood(Items.STONE) >= required
    }

    private fun hasPickaxe(): Boolean {
        for (i in 0 until aiPlayer.inventory.size()) {
            if (aiPlayer.inventory.getStack(i).item is PickaxeItem) {
                return true
            }
        }
        return false
    }

    private fun findStoneToMine(): BlockPos? {
        val world = aiPlayer.world
        val center = aiPlayer.blockPos.down()

        // 向下搜索32格范围内的原石
        for (y in center.y downTo max(center.y - 32, world.bottomY)) {
            for (x in -1..1) {
                for (z in -1..1) {
                    val pos = center.add(x, center.y - y, z)
                    val state = world.getBlockState(pos)
                    if (state.isOf(Blocks.STONE)) {
                        return pos
                    }
                }
            }
        }
        return null
    }

    fun update() {
        val shouldInterrupt = when {
            shouldFlee() && currentAction !is FleeAction -> true
            shouldRetaliateAgainstPlayer() && currentAction !is CombatAction -> true
            shouldEngageCombat() && currentAction !is CombatAction -> true
            shouldGoToBed() && currentAction !is GoToBedAction -> true
            else -> false
        }

        if (shouldInterrupt) {
            currentAction = selectNextAction()?.apply { start() }
            return
        }

        if (currentAction?.status == ActionNode.Status.RUNNING) {
            currentAction?.tick()
        } else {
            currentAction = selectNextAction()?.apply { start() }
        }

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastStatusUpdateTime > 5000) {
            displayCurrentStatus()
            lastStatusUpdateTime = currentTime
        }
    }

    private fun displayCurrentStatus() {
        val status = when (currentAction) {
            is GoToBedAction -> "正在前往床的位置: ${(currentAction as GoToBedAction).bedPos ?: "未知"}"
            is ChopTreeAction -> {
                val action = currentAction as ChopTreeAction
                when {
                    action.targetTree == null -> "正在寻找树木"
                    action.pathBlocked -> "正在清除树叶/原木障碍 (目标树: ${action.targetTree})"
                    else -> "正在砍树，目标位置: ${action.targetTree} (使用工具: ${action.bestAxe?.item?.name?.string ?: "空手"})"
                }
            }
            is CombatAction -> "正在${if ((currentAction as CombatAction).retaliate) "反击" else "战斗"}"
            is FleeAction -> "正在逃跑"
            is IdleAction -> "正在休息中"
            // 新增对挖矿和合成行为的处理
            is MiningAction -> {
                val action = currentAction as MiningAction
                when {
                    action.returning -> "正在返回地面 (已收集圆石: ${action.countCobblestone()}/64)"
                    action.targetPos == null -> "正在寻找可挖掘的石头"
                    else -> "正在挖矿，目标位置: ${action.targetPos} (使用工具: ${action.bestPickaxe.item.name.string})"
                }
            }
            is CraftAction -> {
                val action = currentAction as CraftAction
                when (action.stage) {
                    CraftAction.CraftStage.CHECK_WOOD -> "检查木材数量"
                    CraftAction.CraftStage.MAKE_PLANKS -> "制作木板"
                    CraftAction.CraftStage.MAKE_STICKS -> "制作木棍"
                    CraftAction.CraftStage.MAKE_TOOLS -> "制作工具 (进度: ${action.currentToolIndex + 1}/3)"
                    CraftAction.CraftStage.DONE -> "合成完成"
                }
            }
            else -> "[未知行为] ${currentAction?.javaClass?.simpleName}" // 保留未知行为的调试信息
        }
        aiPlayer.debugLog("[AI状态] $status")
    }

    private fun selectNextAction(): ActionNode? {
        return when {
            shouldFlee() -> FleeAction(aiPlayer)
            shouldRetaliateAgainstPlayer() -> CombatAction(aiPlayer, true)
            shouldEngageCombat() -> CombatAction(aiPlayer, false)
            shouldGoToBed() -> GoToBedAction(aiPlayer)
            shouldSmeltOres() -> SmeltOresAction(aiPlayer) // 较高优先级
            shouldChopTree() -> ChopTreeAction(aiPlayer)
            shouldCraftStoneTools() -> StoneToolCraftAction(aiPlayer)
            shouldCraftFurnace() -> CraftFurnaceAction(aiPlayer)
            shouldMineIron() -> IronMiningAction(aiPlayer)
            shouldCraftTools() -> CraftAction(aiPlayer)
            shouldMine() -> MiningAction(aiPlayer)
            else -> IdleAction(aiPlayer)
        }
    }

    private fun shouldMineIron(): Boolean {
        return IronMiningAction(aiPlayer).shouldStartIronMining()
    }

    private fun shouldCraftTools(): Boolean {
        return isDayTime(aiPlayer.world) &&
                !hasNearbyHostileMobs(1) &&
                hasEnoughWood(10) && // 至少有10个原木
                !hasBasicTools() &&  // 缺少基础工具
                !hasCobblestoneInInventory() // 使用新方法名避免冲突
    }

    // 新增方法：检查是否有圆石
// 1.20.1版本专用的圆石检查方法
    private fun hasCobblestoneInInventory(): Boolean {
        for (i in 0 until aiPlayer.inventory.size()) {
            if (aiPlayer.inventory.getStack(i).isOf(Items.COBBLESTONE)) {
                return true
            }
        }
        return false
    }

    private fun hasBasicTools(): Boolean {
        return aiPlayer.inventory.containsAnyOf(
            Items.WOODEN_SWORD,
            Items.WOODEN_AXE,
            Items.WOODEN_PICKAXE
        )
    }

    // 检查背包是否包含任意指定物品
    fun SimpleInventory.containsAnyOf(vararg items: Item): Boolean {
        return (0 until size()).any { slot ->
            items.any { item -> getStack(slot).item == item }
        }
    }


    private fun shouldFlee(): Boolean {
        return aiPlayer.health < 3f && hasNearbyHostileMobs(1)
    }

    private fun shouldRetaliateAgainstPlayer(): Boolean {
        val hasValidTarget = aiPlayer.angerTargets.entries.any { (uuid, anger) ->
            aiPlayer.world.getPlayerByUuid(uuid)?.takeIf { it.isAlive }?.let {
                anger > 0
            } ?: false
        }
        if (!hasValidTarget) {
            aiPlayer.clearAllAnger()
        }
        return hasValidTarget
    }

    private fun shouldEngageCombat(): Boolean {
        return aiPlayer.health >= 3f &&
                hasNearbyHostileMobs(1) &&
                getNearbyHostileMobsCount() in 1..5
    }

    private fun hasNearbyHostileMobs(minCount: Int): Boolean {
        return getNearbyHostileMobsCount() >= minCount
    }

    private fun getNearbyHostileMobsCount(): Int {
        val box = aiPlayer.boundingBox.expand(16.0, 8.0, 16.0)
        return aiPlayer.world.getEntitiesByClass(
            LivingEntity::class.java,
            box,
            { entity ->
                entity !is AIPlayerEntity &&
                        entity !is PlayerEntity &&
                        entity !is CreeperEntity &&
                        entity is HostileEntity
            }
        ).size
    }

    private fun shouldChopTree(): Boolean {
        return isDayTime(aiPlayer.world) &&
                !hasNearbyHostileMobs(1) &&
                !hasEnoughWood(10) &&
                findNearestTree() != null
    }

    private fun isDayTime(world: World): Boolean {
        val time = world.timeOfDay % 24000
        return time !in 13000..23000
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
        return stack.item is BlockItem &&
                (stack.item as BlockItem).block.defaultState.isIn(BlockTags.LOGS)
    }

    private fun findNearestTree(): BlockPos? {
        val world = aiPlayer.world
        val center = aiPlayer.blockPos
        for (radius in 0..32) {
            for (x in -radius..radius) {
                for (z in -radius..radius) {
                    val pos = center.add(x, 0, z)
                    val state = world.getBlockState(pos)
                    if (state.isIn(BlockTags.LOGS)) {
                        return pos
                    }
                }
            }
        }
        return null
    }

    private fun shouldGoToBed(): Boolean {
        return !hasNearbyHostileMobs(1) &&
                isNightTime(aiPlayer.world) &&
                aiPlayer.bedPos != null &&
                !aiPlayer.isSleeping
    }

    private fun isNightTime(world: World): Boolean {
        val time = world.timeOfDay % 24000
        return time in 13000..23000
    }

    fun serialize(): NbtCompound {
        return NbtCompound().apply {
            currentAction?.let { putString("currentAction", it.name) }
        }
    }

    fun deserialize(nbt: NbtCompound) {
        currentAction = when (nbt.getString("currentAction")) {
            "chop" -> ChopTreeAction(aiPlayer)
            "goToBed" -> GoToBedAction(aiPlayer)
            "combat" -> CombatAction(aiPlayer, false)
            "flee" -> FleeAction(aiPlayer)
            else -> null
        }
    }

    sealed class ActionNode(val name: String) {
        enum class Status { SUCCESS, FAILURE, RUNNING }
        var status: Status = Status.RUNNING
            protected set

        abstract fun start()
        abstract fun tick()
    }

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

    class ChopTreeAction(private val aiPlayer: AIPlayerEntity) : ActionNode("chop") {
        var targetTree: BlockPos? = null
        var bestAxe: ItemStack? = null
        var pathBlocked = false
        private var chopTicks = 0
        private var travelTicks = 0 // 新增：记录移动时间
        private val MAX_TRAVEL_TICKS = 600
        private val BLOCK_CHECK_INTERVAL = 100L
        private var lastBlockCheckTime: Long = 0

        override fun start() {
            targetTree = findNearestTree()
            bestAxe = findBestAxe()
            status = if (targetTree != null) Status.RUNNING else Status.FAILURE
            pathBlocked = false
            travelTicks = 0 // 重置移动计时器

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
                // 超时检查（新增）
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
                        travelTicks = 0 // 重置计时器
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
                    travelTicks = 0 // 到达目标后重置计时器
                }

                if (chopTicks++ >= getChopInterval()) {
                    aiPlayer.swingHand(Hand.MAIN_HAND)
                    if (aiPlayer.world.breakBlock(pos, true)) {
                        aiPlayer.debugLog("[砍树] 成功砍伐树木 at $pos")
                        if (hasEnoughWood(10)) {
                            status = Status.SUCCESS
                        } else {
                            targetTree = findNearestTree()
                            travelTicks = 0 // 重置计时器
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

    class IdleAction(aiPlayer: AIPlayerEntity) : ActionNode("idle") {
        override fun start() {
            status = Status.SUCCESS
        }

        override fun tick() {
        }
    }

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
    class CraftAction(private val aiPlayer: AIPlayerEntity) : ActionNode("craft") {
        var stage = CraftStage.CHECK_WOOD
        private var craftTicks = 0
        private var toolsToCraft = listOf(Items.WOODEN_SWORD, Items.WOODEN_AXE, Items.WOODEN_PICKAXE)
        var currentToolIndex = 0

        enum class CraftStage {
            CHECK_WOOD,      // 检查木材数量
            MAKE_PLANKS,     // 制作木板
            MAKE_STICKS,     // 制作木棍
            MAKE_TOOLS,      // 制作工具
            DONE             // 完成
        }

        override fun start() {
            // 1.20.1版本检查圆石
            //if (hasCobblestoneInInventory()) {
               // status = Status.FAILURE
               // aiPlayer.debugLog("[合成] 取消合成：背包中存在圆石")
              //  return
          //  }

            status = Status.RUNNING
            aiPlayer.debugLog("[合成] 开始合成流程")
        }

        // 添加成员方法（不是局部函数）
        //private fun hasCobblestoneInInventory(): Boolean {
            //for (i in 0 until aiPlayer.inventory.size()) {
                //val stack = aiPlayer.inventory.getStack(i)
               // if (stack.isOf(Items.COBBLESTONE)) {
                  //  return true
              //  }
           // }
          //  return false
      //  }
        override fun tick() {
            when (stage) {
                CraftStage.CHECK_WOOD -> {
                    if (hasEnoughWood(10)) {
                        stage = CraftStage.MAKE_PLANKS
                        aiPlayer.debugLog("[合成] 检测到足够木材，开始制作木板")
                    } else {
                        status = Status.FAILURE
                        aiPlayer.debugLog("[合成] 木材不足，需要至少10个原木")
                    }
                }


                CraftStage.MAKE_PLANKS -> {
                    if (consumeWood(8)) {
                        // 尝试将木板添加到物品栏
                        val remaining = aiPlayer.inventory.addStack(ItemStack(Items.OAK_PLANKS, 40))

                        // 检查是否全部添加成功(剩余为空表示全部添加)
                        if (remaining.isEmpty) {
                            aiPlayer.debugLog("[合成] 已制作40个木板")
                            stage = CraftStage.MAKE_STICKS
                        } else {
                            // 计算实际添加了多少个
                            val added = 40 - remaining.count
                            if (added > 0) {
                                aiPlayer.debugLog("[合成] 已制作${added}个木板 (背包已满)")
                                stage = CraftStage.MAKE_STICKS
                            } else {
                                aiPlayer.debugLog("[合成] 错误: 无法添加木板到背包 (背包已满)")
                                status = Status.FAILURE
                            }
                        }
                    } else {
                        aiPlayer.debugLog("[合成] 错误: 无法消耗8个原木")
                        status = Status.FAILURE
                    }
                }

                CraftStage.MAKE_STICKS -> {
                    if (consumeItem(Items.OAK_PLANKS, 10)) {
                        addItem(ItemStack(Items.STICK, 40)) // 2木板=4木棍
                        stage = CraftStage.MAKE_TOOLS
                        aiPlayer.debugLog("[合成] 已制作40个木棍")
                    } else {
                        status = Status.FAILURE
                    }
                }

                CraftStage.MAKE_TOOLS -> {
                    if (currentToolIndex < toolsToCraft.size) {
                        val tool = toolsToCraft[currentToolIndex]
                        when (tool) {
                            Items.WOODEN_SWORD -> {
                                if (consumeItem(Items.OAK_PLANKS, 2) && consumeItem(Items.STICK, 1)) {
                                    addItem(ItemStack(tool))
                                    aiPlayer.debugLog("[合成] 制作木剑完成")
                                    currentToolIndex++
                                }
                            }
                            Items.WOODEN_AXE -> {
                                if (consumeItem(Items.OAK_PLANKS, 3) && consumeItem(
                                        Items.STICK, 2)) {
                                    addItem(ItemStack(tool))
                                    aiPlayer.debugLog("[合成] 制作木斧完成")
                                    currentToolIndex++
                                }
                            }
                            Items.WOODEN_PICKAXE -> {
                                if (consumeItem(Items.OAK_PLANKS, 3) && consumeItem(Items.STICK, 2)) {
                                    addItem(ItemStack(tool))
                                    aiPlayer.debugLog("[合成] 制作木镐完成")
                                    currentToolIndex++
                                }
                            }
                        }
                    } else {
                        stage = CraftStage.DONE
                    }
                }

                CraftStage.DONE -> {
                    status = Status.SUCCESS
                }
            }
            craftTicks++
        }

        private fun hasEnoughWood(required: Int): Boolean {
            // 统计背包和手持物品中的原木总数
            val totalWood = listOf(
                aiPlayer.getStackInHand(Hand.MAIN_HAND),
                aiPlayer.getStackInHand(Hand.OFF_HAND)
            ).sumOf { if (isLog(it)) it.count else 0 } +
                    // 修改这里，正确处理标签物品
                    aiPlayer.countWood(null) // 或者实现专门处理标签的方法

            aiPlayer.debugLog("[合成] 原木总数: $totalWood (需要: $required)")
            return totalWood >= required
        }

        private fun consumeWood(amount: Int): Boolean {
            var remaining = amount

            // 先检查手持物品
            for (hand in listOf(Hand.MAIN_HAND, Hand.OFF_HAND)) {
                val stack = aiPlayer.getStackInHand(hand)
                if (isLog(stack)) {
                    val consume = minOf(stack.count, remaining)
                    stack.decrement(consume)
                    remaining -= consume
                    if (remaining <= 0) return true
                }
            }

            // 然后检查背包
            return aiPlayer.consumeWood(BlockTags.LOGS, remaining)
        }
        private fun isLog(stack: ItemStack): Boolean {
            return stack.item is BlockItem && (stack.item as BlockItem).block.defaultState.isIn(BlockTags.LOGS)
        }

        fun consumeItem(item: Item, amount: Int): Boolean {
            return aiPlayer.consumeItem(item, amount)
        }
        private fun addItem(stack: ItemStack) {
            aiPlayer.inventory.addStack(stack)
        }
    }
    class MiningAction(private val aiPlayer: AIPlayerEntity) : ActionNode("mine") {
        // 1.20.1 版本常量定义
        companion object {
            private val COBBLESTONE_ITEM = Items.COBBLESTONE
            private val STONE_BLOCK = Blocks.STONE
        }

        var targetPos: BlockPos? = null
        var bestPickaxe: ItemStack = ItemStack.EMPTY
        private var hasPlanks = false
        private var miningTicks = 0
        var returning = false

        // 新增返回相关变量
        private var returnDirection: Vec3d? = null
        private var returnStartPos: BlockPos? = null
        private var lastMinedPos: BlockPos? = null
        private val MAX_RETURN_TICKS = 1200 // 60秒超时

        // 查找可挖掘的石头（1.20.1使用BlockTags）
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
                returnStartPos = aiPlayer.blockPos // 记录起始位置
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
                    miningTicks = 0 // 重置计时器
                    aiPlayer.debugLog("[挖矿] 已收集64个圆石，开始返回地面")
                    return
                }

                if (returning) {
                    returnToSurface()
                } else {
                    mineStone()
                }

                // 超时保护
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

            // 检查玩家上方1-3格的3x3区域
            for (yOffset in 1..3) {
                for (xOffset in -1..1) {
                    for (zOffset in -1..1) {
                        val checkPos = playerPos.add(xOffset, yOffset, zOffset)
                        val state = world.getBlockState(checkPos)
                        val block = state.block
                        val blockId = block.translationKey

                        // 跳过基岩
                        if (blockId.contains(bedrockIdentifier.path)) continue

                        // 判断是否需要破坏的条件
                        val shouldBreak = state.isSolidBlock(world, checkPos) ||
                                state.isIn(BlockTags.LEAVES) ||
                                !state.isFullCube(world, checkPos)

                        if (shouldBreak) {
                            aiPlayer.swingHand(Hand.MAIN_HAND)
                            if (world.breakBlock(checkPos, true)) {
                                aiPlayer.debugLog("[挖矿] 清除障碍 at $checkPos")
                                // 短暂延迟防止连续破坏卡顿
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

            handleHeadBlocking() // 新增：破坏障碍物

            if (aiPlayer.blockPos.y >= aiPlayer.world.topY - 10) {
                status = Status.SUCCESS
            }

            // 初始化返回方向
            if (returnDirection == null) {
                calculateReturnDirection()
            }

            // 计算下一个目标位置
            val nextPos = calculateNextReturnPos() ?: run {
                status = Status.FAILURE
                return
            }

            // 处理障碍物
            if (aiPlayer.world.getBlockState(nextPos).isSolidBlock(aiPlayer.world, nextPos)) {
                handleBlockingBlock(nextPos)
                return
            }

            // 移动控制
            moveToNextPos(nextPos)

            // 到达地面判断
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

            // 45度斜向上的方向向量 (x和z分量减小)
            returnDirection = Vec3d(
                toSurface.x * 0.5,  // 减小水平分量
                0.707,             // sin(45°)
                toSurface.z * 0.5   // 减小水平分量
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
                // 短暂暂停让实体移动
                if (miningTicks % 5 == 0) return
            } else {
                // 无法破坏时调整方向
                adjustReturnDirection()
            }
        }

        private fun moveToNextPos(pos: BlockPos) {
            val targetY = pos.y + 1 // 稍微向上瞄准防止卡住
            aiPlayer.navigation.startMovingTo(
                pos.x + 0.5,
                targetY.toDouble(),
                pos.z + 0.5,
                1.0
            )

            // 跳跃控制 (1.20.1版本)
            if (aiPlayer.blockPos.y < pos.y) {
                try {
                    val jumpMethod = LivingEntity::class.java.getDeclaredMethod("jump")
                    jumpMethod.isAccessible = true
                    jumpMethod.invoke(aiPlayer)
                } catch (e: Exception) {
                    ChatAI.LOGGER.error("跳跃失败: ${e.message}")
                    // 应用20秒的20级漂浮效果（无气泡）
                    val levitationEffect = StatusEffectInstance(
                        StatusEffects.LEVITATION, // 漂浮效果
                        20 * 20,  // 20秒（20 ticks/秒）
                        19,       // 等级20（0=1级，19=20级）
                        false,    // 不显示粒子
                        false,    // 不显示状态栏图标
                        false     // 不显示气泡
                    )
                    aiPlayer.addStatusEffect(levitationEffect)
                }
            }
        }

        private fun adjustReturnDirection() {
            // 尝试微调方向 (22.5度偏移)
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

    class StoneToolCraftAction(private val aiPlayer: AIPlayerEntity) : ActionNode("stoneCraft") {
        private var stage = CraftStage.CHECK_MATERIALS
        private var currentToolIndex = 0
        private val toolsToCraft = listOf(Items.STONE_PICKAXE, Items.STONE_SWORD, Items.STONE_AXE)

        enum class CraftStage {
            CHECK_MATERIALS,
            REMOVE_WOODEN_TOOLS,
            CRAFT_TOOLS,
            DONE
        }

        override fun start() {
            status = Status.RUNNING
            aiPlayer.sendMessage(Text.literal("开始制作石器工具").formatted(Formatting.GRAY))
        }

        override fun tick() {
            when (stage) {
                CraftStage.CHECK_MATERIALS -> checkMaterials()
                CraftStage.REMOVE_WOODEN_TOOLS -> removeWoodenTools()
                CraftStage.CRAFT_TOOLS -> craftTools()
                CraftStage.DONE -> status = Status.SUCCESS
            }
        }

        private fun checkMaterials() {
            if (hasEnoughMaterials() && !hasStoneTools()) {
                stage = CraftStage.REMOVE_WOODEN_TOOLS
            } else {
                status = Status.FAILURE
            }
        }

        private fun hasEnoughMaterials(): Boolean {
            var cobblestoneCount = 0
            var stickCount = 0

            // 检查背包和手持物品
            for (i in 0 until aiPlayer.inventory.size()) {
                val stack = aiPlayer.inventory.getStack(i)
                when (stack.item) {
                    Items.COBBLESTONE -> cobblestoneCount += stack.count
                    Items.STICK -> stickCount += stack.count
                }
            }

            // 检查手持物品
            for (hand in Hand.values()) {
                val stack = aiPlayer.getStackInHand(hand)
                when (stack.item) {
                    Items.COBBLESTONE -> cobblestoneCount += stack.count
                    Items.STICK -> stickCount += stack.count
                }
            }

            return cobblestoneCount >= 8 && stickCount >= 5
        }

        private fun hasStoneTools(): Boolean {
            for (i in 0 until aiPlayer.inventory.size()) {
                val stack = aiPlayer.inventory.getStack(i)
                if (stack.item in toolsToCraft) return true
            }
            return false
        }

        private fun removeWoodenTools() {
            val woodenTools = setOf(Items.WOODEN_PICKAXE, Items.WOODEN_SWORD, Items.WOODEN_AXE)

            // 清理背包
            for (i in 0 until aiPlayer.inventory.size()) {
                val stack = aiPlayer.inventory.getStack(i)
                if (stack.item in woodenTools) {
                    aiPlayer.inventory.setStack(i, ItemStack.EMPTY)
                }
            }

            // 清理手持物品
            for (hand in Hand.values()) {
                val stack = aiPlayer.getStackInHand(hand)
                if (stack.item in woodenTools) {
                    aiPlayer.setStackInHand(hand, ItemStack.EMPTY)
                }
            }

            stage = CraftStage.CRAFT_TOOLS
        }

        private fun craftTools() {
            if (currentToolIndex >= toolsToCraft.size) {
                stage = CraftStage.DONE
                return
            }

            val tool = toolsToCraft[currentToolIndex]
            val (cobblestoneNeeded, sticksNeeded) = when (tool) {
                Items.STONE_PICKAXE -> 3 to 2
                Items.STONE_SWORD -> 2 to 1
                Items.STONE_AXE -> 3 to 2
                else -> 0 to 0
            }

            if (consumeItems(Items.COBBLESTONE, cobblestoneNeeded, Items.STICK, sticksNeeded)) {
                aiPlayer.inventory.addStack(ItemStack(tool))
                aiPlayer.sendMessage(Text.literal("制作了 ${tool.name.string}").formatted(Formatting.GREEN))
                currentToolIndex++
            } else {
                status = Status.FAILURE
            }
        }

        private fun consumeItems(item1: Item, count1: Int, item2: Item, count2: Int): Boolean {
            if (!hasItems(item1, count1) || !hasItems(item2, count2)) return false

            // 消耗第一种物品
            var remaining1 = count1
            for (i in 0 until aiPlayer.inventory.size()) {
                val stack = aiPlayer.inventory.getStack(i)
                if (stack.item == item1) {
                    val consume = minOf(stack.count, remaining1)
                    stack.decrement(consume)
                    remaining1 -= consume
                    if (remaining1 <= 0) break
                }
            }

            // 消耗第二种物品
            var remaining2 = count2
            for (i in 0 until aiPlayer.inventory.size()) {
                val stack = aiPlayer.inventory.getStack(i)
                if (stack.item == item2) {
                    val consume = minOf(stack.count, remaining2)
                    stack.decrement(consume)
                    remaining2 -= consume
                    if (remaining2 <= 0) break
                }
            }

            return true
        }

        private fun hasItems(item: Item, count: Int): Boolean {
            var total = 0
            for (i in 0 until aiPlayer.inventory.size()) {
                val stack = aiPlayer.inventory.getStack(i)
                if (stack.item == item) {
                    total += stack.count
                    if (total >= count) return true
                }
            }
            return false
        }
    }

    class IronMiningAction(private val aiPlayer: AIPlayerEntity) : ActionNode("ironMine") {
        public val clearAction = ClearInventoryAction(aiPlayer)
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
                heldItem.isOf(NETHERITE_PICKAXE) -> 3   // 下界合金镐下挖最快
                heldItem.isOf(DIAMOND_PICKAXE) -> 5     // 钻石镐
                heldItem.isOf(IRON_PICKAXE) -> 8        // 铁镐
                heldItem.isOf(STONE_PICKAXE) -> 12      // 石镐
                else -> 20                              // 空手或其他工具
            }
        }

        private fun getMiningTicksRequired(): Int {
            val heldItem = aiPlayer.mainHandStack
            return when {
                heldItem.isOf(NETHERITE_PICKAXE) -> 5   // 下界合金镐挖矿最快
                heldItem.isOf(DIAMOND_PICKAXE) -> 7     // 钻石镐
                heldItem.isOf(IRON_PICKAXE) -> 10       // 铁镐
                heldItem.isOf(STONE_PICKAXE) -> 15      // 石镐
                else -> 30                              // 空手或其他工具
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
                // 应用20秒的20级漂浮效果（无气泡）
                val levitationEffect = StatusEffectInstance(
                    StatusEffects.LEVITATION, // 漂浮效果
                    20 * 20,  // 20秒（20 ticks/秒）
                    19,       // 等级20（0=1级，19=20级）
                    false,    // 不显示粒子
                    false,    // 不显示状态栏图标
                    false     // 不显示气泡
                )
                aiPlayer.addStatusEffect(levitationEffect)
            }
        }

        fun shouldInterrupt(): Boolean = false
    }

    class ClearInventoryAction(private val aiPlayer: AIPlayerEntity) : ActionNode("clearInventory") {
        // 物品清除指令
        sealed class ClearInstruction {
            data class KeepAmount(val item: Item, val amountToKeep: Int) : ClearInstruction()
            data class RemoveAll(val item: Item) : ClearInstruction()
        }

        private var clearInstructions: List<ClearInstruction> = emptyList()
        private var clearingFinished = false

        /**
         * 设置清理规则
         * @param rules 可变参数，格式为 Pair<Item, Int|String>
         *              例如：clear(Items.COBBLESTONE to 64, Items. STONE to "all")
         */
        fun clear(vararg rules: Pair<Item, Any>): ClearInventoryAction {
            this.clearInstructions = rules.map { (item, amount) ->
                when (amount) {
                    is Int -> ClearInstruction.KeepAmount(item, amount)
                    "all" -> ClearInstruction.RemoveAll(item)
                    else -> throw IllegalArgumentException("参数必须是保留数量(Int)或\"all\"")
                }
            }
            this.clearingFinished = false
            return this
        }
        override fun start() {
            if (clearInstructions.isEmpty()) {
                status = Status.FAILURE
                aiPlayer.debugLog("[背包清理] 未指定清理规则")
                return
            }

            val instructionDesc = clearInstructions.joinToString { instruction ->
                when (instruction) {
                    is ClearInstruction.KeepAmount -> "${instruction.item.translationKey} 保留${instruction.amountToKeep}"
                    is ClearInstruction.RemoveAll -> "${instruction.item.translationKey} 全部清除"
                }
            }

            status = Status.RUNNING
            aiPlayer.debugLog("[背包清理] 开始清理: $instructionDesc")
        }

        override fun tick() {
            if (clearingFinished) {
                status = Status.SUCCESS
                return
            }

            clearSpecifiedItems()
            clearingFinished = true
            status = Status.SUCCESS
        }

        private fun clearSpecifiedItems() {
            val inventory = aiPlayer.inventory

            clearInstructions.forEach { instruction ->
                when (instruction) {
                    is ClearInstruction.KeepAmount -> {
                        var totalCount = 0

                        // 先计算总数
                        for (i in 0 until inventory.size()) {
                            val stack = inventory.getStack(i)
                            if (stack.isOf(instruction.item)) {
                                totalCount += stack.count
                            }
                        }

                        // 检查主副手
                        listOf(Hand.MAIN_HAND, Hand.OFF_HAND).forEach { hand ->
                            val stack = aiPlayer.getStackInHand(hand)
                            if (stack.isOf(instruction.item)) {
                                totalCount += stack.count
                            }
                        }

                        // 计算需要清除的数量
                        val amountToRemove = (totalCount - instruction.amountToKeep).coerceAtLeast(0)
                        if (amountToRemove == 0) return@forEach

                        // 执行清除
                        var remainingToRemove = amountToRemove
                        for (i in 0 until inventory.size()) {
                            if (remainingToRemove <= 0) break

                            val stack = inventory.getStack(i)
                            if (stack.isOf(instruction.item)) {
                                val removeAmount = minOf(stack.count, remainingToRemove)
                                inventory.removeStack(i, removeAmount)
                                remainingToRemove -= removeAmount
                                aiPlayer.debugLog("清除 ${stack.item.translationKey} x$removeAmount")
                            }
                        }

                        // 清除主副手物品
                        listOf(Hand.MAIN_HAND, Hand.OFF_HAND).forEach { hand ->
                            if (remainingToRemove <= 0) return@forEach

                            val stack = aiPlayer.getStackInHand(hand)
                            if (stack.isOf(instruction.item)) {
                                val removeAmount = minOf(stack.count, remainingToRemove)
                                aiPlayer.setStackInHand(hand,
                                    if (stack.count == removeAmount) ItemStack.EMPTY
                                    else stack.copy().apply { count = stack.count - removeAmount }
                                )
                                remainingToRemove -= removeAmount
                                aiPlayer.debugLog("清除 ${stack.item.translationKey} x$removeAmount (手持)")
                            }
                        }
                    }

                    is ClearInstruction.RemoveAll -> {
                        // 清除背包
                        for (i in 0 until inventory.size()) {
                            val stack = inventory.getStack(i)
                            if (stack.isOf(instruction.item)) {
                                inventory.removeStack(i, stack.count)
                                aiPlayer.debugLog("清除全部 ${stack.item.translationKey} x${stack.count}")
                            }
                        }

                        // 清除主副手
                        listOf(Hand.MAIN_HAND, Hand.OFF_HAND).forEach { hand ->
                            val stack = aiPlayer.getStackInHand(hand)
                            if (stack.isOf(instruction.item)) {
                                aiPlayer.setStackInHand(hand, ItemStack.EMPTY)
                                aiPlayer.debugLog("清除全部 ${stack.item.translationKey} x${stack.count} (手持)")
                            }
                        }
                    }
                }
            }
        }

        fun shouldInterrupt(): Boolean = false
    }
    class CraftFurnaceAction(private val aiPlayer: AIPlayerEntity) : BehaviorTree.ActionNode("craftFurnace") {
        private var craftingTicks = 0

        override fun start() {
            status = Status.RUNNING
            aiPlayer.debugLog("[合成] 开始制作熔炉")
        }

        override fun tick() {
            when {
                // 检查材料是否仍然满足
                !hasMaterials() -> {
                    status = Status.FAILURE
                    return
                }

                // 模拟合成延迟
                craftingTicks++ < 20 -> return

                // 执行合成
                else -> tryCraftFurnace()
            }
        }

        private fun hasMaterials(): Boolean {
            var cobblestoneCount = 0
            for (i in 0 until aiPlayer.inventory.size()) {
                val stack = aiPlayer.inventory.getStack(i)
                when {
                    stack.item == Items.COBBLESTONE -> cobblestoneCount += stack.count
                    stack.item == Items.FURNACE -> return false // 已经有熔炉了
                }
            }
            return cobblestoneCount >= 8
        }

        private fun tryCraftFurnace() {
            // 消耗材料
            var remaining = 8
            for (i in 0 until aiPlayer.inventory.size()) {
                if (remaining <= 0) break

                val stack = aiPlayer.inventory.getStack(i)
                if (stack.item == Items.COBBLESTONE) {
                    val consume = minOf(stack.count, remaining)
                    stack.decrement(consume)
                    remaining -= consume
                }
            }

            // 添加熔炉到背包
            val furnaceStack = ItemStack(Items.FURNACE)
            val remainingStack = aiPlayer.inventory.addStack(furnaceStack)

            if (remainingStack.isEmpty) {
                aiPlayer.debugLog("[合成] 成功制作熔炉")
                status = Status.SUCCESS
            } else {
                aiPlayer.debugLog("[合成] 背包已满，无法放入熔炉")
                status = Status.FAILURE
            }
        }
    }
    class SmeltOresAction(private val aiPlayer: AIPlayerEntity) : ActionNode("smeltOres") {
        private var smeltingTicks = 0
        private val SMELTING_DURATION = 200 // 10秒 (20 ticks/秒)

        override fun start() {
            status = Status.RUNNING
            aiPlayer.debugLog("[熔炼] 开始熔炼矿石行为")
            placeFurnaceIfNeeded()
        }

        override fun tick() {
            if (!validateConditions()) {
                status = Status.FAILURE
                return
            }

            if (smeltingTicks++ < SMELTING_DURATION) {
                // 模拟熔炼过程
                if (smeltingTicks % 20 == 0) {
                    aiPlayer.swingHand(Hand.MAIN_HAND)
                }
                return
            }

            completeSmelting()
        }

        private fun placeFurnaceIfNeeded() {
            val world = aiPlayer.world
            val pos = aiPlayer.blockPos.add(0, 1, 0)

            if (!world.getBlockState(pos).isOf(Blocks.FURNACE)) {
                // 从背包获取熔炉
                val furnaceSlot = findFurnaceInInventory()
                if (furnaceSlot != -1) {
                    val stack = aiPlayer.inventory.getStack(furnaceSlot)
                    aiPlayer.setStackInHand(Hand.MAIN_HAND, stack.copy())
                    aiPlayer.inventory.setStack(furnaceSlot, ItemStack.EMPTY)

                    // 放置熔炉
                    if (world.setBlockState(pos, Blocks.FURNACE.defaultState)) {
                        aiPlayer.debugLog("[熔炼] 已放置熔炉在 $pos")
                    }
                }
            }
        }

        private fun findFurnaceInInventory(): Int {
            for (i in 0 until aiPlayer.inventory.size()) {
                if (aiPlayer.inventory.getStack(i).isOf(Items.FURNACE)) {
                    return i
                }
            }
            return -1
        }

        private fun validateConditions(): Boolean {
            return isDayTime(aiPlayer.world) &&
                    !hasNearbyHostileMobs(1) &&
                    hasSmeltableOres(1) // 至少保留1个矿石用于验证
        }

        private fun hasSmeltableOres(required: Int): Boolean {
            val smeltableOres = listOf(
                Items.IRON_ORE,
                Items.GOLD_ORE,
                Items.COPPER_ORE,
                Items.RAW_IRON,
                Items.RAW_GOLD,
                Items.RAW_COPPER
            )

            var total = 0
            for (i in 0 until aiPlayer.inventory.size()) {
                val stack = aiPlayer.inventory.getStack(i)
                if (stack.item in smeltableOres) {
                    total += stack.count
                    if (total >= required) return true
                }
            }
            return false
        }

            private fun isDayTime(world: World) : Boolean {
            return world.timeOfDay % 24000 in 0..12000
        }

        private fun hasNearbyHostileMobs(minCount: Int): Boolean {
            return getNearbyHostileMobsCount() >= minCount
        }

        private fun getNearbyHostileMobsCount(): Int {
            val box = aiPlayer.boundingBox.expand(16.0, 8.0, 16.0)
            return aiPlayer.world.getEntitiesByClass(
                LivingEntity::class.java,
                box,
                { entity ->
                    entity !is AIPlayerEntity &&
                            entity !is PlayerEntity &&
                            entity !is CreeperEntity &&
                            entity is HostileEntity
                }
            ).size
        }


        private fun completeSmelting() {
            // 消耗64个矿石
            var remaining = 64
            val smeltableOres = listOf(
                Items.IRON_ORE, Items.GOLD_ORE, Items.COPPER_ORE,
                Items.RAW_IRON, Items.RAW_GOLD, Items.RAW_COPPER
            )

            for (i in 0 until aiPlayer.inventory.size()) {
                if (remaining <= 0) break

                val stack = aiPlayer.inventory.getStack(i)
                if (stack.item in smeltableOres) {
                    val consume = minOf(stack.count, remaining)
                    stack.decrement(consume)
                    remaining -= consume
                }
            }

            // 添加熔炼产物
            val results = mapOf(
                Items.IRON_ORE to Items.IRON_INGOT,
                Items.GOLD_ORE to Items.GOLD_INGOT,
                Items.COPPER_ORE to Items.COPPER_INGOT,
                Items.RAW_IRON to Items.IRON_INGOT,
                Items.RAW_GOLD to Items.GOLD_INGOT,
                Items.RAW_COPPER to Items.COPPER_INGOT
            )

            results.forEach { (ore, ingot) ->
                aiPlayer.inventory.addStack(ItemStack(ingot, 64))
            }

            aiPlayer.debugLog("[熔炼] 完成熔炼，获得64个金属锭")
            status = Status.SUCCESS
        }
    }
}