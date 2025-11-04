package com.xiaofan.chatai.aitree

import com.xiaofan.chatai.aiplayerentity.AIPlayerEntity
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.*
import net.minecraft.nbt.NbtCompound
import net.minecraft.registry.tag.BlockTags
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/**
 * 主行为树类 - 负责行为选择和更新逻辑
 */
class BehaviorTree(private val aiPlayer: AIPlayerEntity) {

    val clearAction = ClearInventoryAction(aiPlayer)
    
    private var currentAction: ActionNode? = null
    private var lastStatusUpdateTime = 0L

    /**
     * 重置行为树为休息状态
     * 用于死亡、重生等需要清除当前行为的情况
     */
    fun resetToIdle() {
        currentAction = IdleAction(aiPlayer).apply { start() }
        aiPlayer.debugLog("[行为树] 已重置为休息状态")
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
            else -> "[未知行为] ${currentAction?.javaClass?.simpleName}"
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

    // ========== 判断条件方法 ==========

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

    private fun shouldGoToBed(): Boolean {
        return !hasNearbyHostileMobs(1) &&
                isNightTime(aiPlayer.world) &&
                aiPlayer.bedPos != null &&
                !aiPlayer.isSleeping
    }

    private fun shouldSmeltOres(): Boolean {
        return isDayTime(aiPlayer.world) &&
                !hasNearbyHostileMobs(1) &&
                hasFurnaceInInventory() &&
                hasSmeltableOres(64)
    }

    private fun shouldChopTree(): Boolean {
        return isDayTime(aiPlayer.world) &&
                !hasNearbyHostileMobs(1) &&
                !hasEnoughWood(10) &&
                findNearestTree() != null
    }

    private fun shouldCraftStoneTools(): Boolean {
        if (!isDayTime(aiPlayer.world) || hasNearbyHostileMobs(1)) return false

        var hasCobblestone = false
        var hasSticks = false
        var hasStoneTools = false

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

    private fun shouldCraftFurnace(): Boolean {
        return isDayTime(aiPlayer.world) &&
                !hasNearbyHostileMobs(1) &&
                !hasFurnaceInInventory() &&
                hasEnoughCobblestone(8)
    }

    private fun shouldMineIron(): Boolean {
        return IronMiningAction(aiPlayer).shouldStartIronMining()
    }

    private fun shouldCraftTools(): Boolean {
        return isDayTime(aiPlayer.world) &&
                !hasNearbyHostileMobs(1) &&
                hasEnoughWood(10) &&
                !hasBasicTools() &&
                !hasCobblestoneInInventory()
    }

    private fun shouldMine(): Boolean {
        return isDayTime(aiPlayer.world) &&
                !hasNearbyHostileMobs(1) &&
                !hasEnoughItems(Items.STONE, 64) &&
                hasPickaxe() &&
                findMineableStone() != null
    }

    // ========== 辅助判断方法 ==========

    private fun hasNearbyHostileMobs(minCount: Int): Boolean {
        return getNearbyHostileMobsCount() >= minCount
    }

    private fun getNearbyHostileMobsCount(): Int {
        val box = aiPlayer.boundingBox.expand(16.0, 8.0, 16.0)
        return aiPlayer.world.getEntitiesByClass(
            net.minecraft.entity.LivingEntity::class.java,
            box,
            { entity ->
                entity !is AIPlayerEntity &&
                        entity !is net.minecraft.entity.player.PlayerEntity &&
                        entity !is net.minecraft.entity.mob.CreeperEntity &&
                        entity is net.minecraft.entity.mob.HostileEntity
            }
        ).size
    }

    private fun isDayTime(world: World): Boolean {
        val time = world.timeOfDay % 24000
        return time !in 13000..23000
    }

    private fun isNightTime(world: World): Boolean {
        val time = world.timeOfDay % 24000
        return time in 13000..23000
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

    private fun hasCobblestoneInInventory(): Boolean {
        for (i in 0 until aiPlayer.inventory.size()) {
            if (aiPlayer.inventory.getStack(i).isOf(Items.COBBLESTONE)) {
                return true
            }
        }
        return false
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

    private fun hasBasicTools(): Boolean {
        return aiPlayer.inventory.containsAnyOf(
            Items.WOODEN_SWORD,
            Items.WOODEN_AXE,
            Items.WOODEN_PICKAXE
        )
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

    private fun hasPickaxe(): Boolean {
        for (i in 0 until aiPlayer.inventory.size()) {
            if (aiPlayer.inventory.getStack(i).item is PickaxeItem) {
                return true
            }
        }
        return false
    }

    private fun findMineableStone(): BlockPos? {
        val world = aiPlayer.world
        val center = aiPlayer.blockPos
        val minY = world.bottomY

        for (y in center.y downTo kotlin.math.max(center.y - 16, minY)) {
            for (x in -1..1) {
                for (z in -1..1) {
                    val pos = BlockPos(center.x + x, y, center.z + z)
                    if (world.getBlockState(pos).isIn(net.minecraft.registry.tag.BlockTags.BASE_STONE_OVERWORLD)) {
                        return pos
                    }
                }
            }
        }
        return null
    }

    // ========== 扩展方法 ==========

    private fun SimpleInventory.containsAnyOf(vararg items: Item): Boolean {
        return (0 until size()).any { slot ->
            items.any { item -> getStack(slot).item == item }
        }
    }

    // ========== 序列化方法 ==========

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
}

