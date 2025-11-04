package com.xiaofan.chatai.aitree

import com.xiaofan.chatai.aiplayerentity.AIPlayerEntity
import net.minecraft.item.*
import net.minecraft.registry.tag.BlockTags
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Hand

/**
 * 合成行为 - 包括木制工具、石器工具和熔炉
 */
class CraftAction(private val aiPlayer: AIPlayerEntity) : ActionNode("craft") {
    var stage = CraftStage.CHECK_WOOD
    private var craftTicks = 0
    private var toolsToCraft = listOf(Items.WOODEN_SWORD, Items.WOODEN_AXE, Items.WOODEN_PICKAXE)
    var currentToolIndex = 0

    enum class CraftStage {
        CHECK_WOOD,
        MAKE_PLANKS,
        MAKE_STICKS,
        MAKE_TOOLS,
        DONE
    }

    override fun start() {
        status = Status.RUNNING
        aiPlayer.debugLog("[合成] 开始合成流程")
    }

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
                    val remaining = aiPlayer.inventory.addStack(ItemStack(Items.OAK_PLANKS, 40))
                    if (remaining.isEmpty) {
                        aiPlayer.debugLog("[合成] 已制作40个木板")
                        stage = CraftStage.MAKE_STICKS
                    } else {
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
                    addItem(ItemStack(Items.STICK, 40))
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
                            if (consumeItem(Items.OAK_PLANKS, 3) && consumeItem(Items.STICK, 2)) {
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
        val totalWood = listOf(
            aiPlayer.getStackInHand(Hand.MAIN_HAND),
            aiPlayer.getStackInHand(Hand.OFF_HAND)
        ).sumOf { if (isLog(it)) it.count else 0 } +
                aiPlayer.countWood(null)

        aiPlayer.debugLog("[合成] 原木总数: $totalWood (需要: $required)")
        return totalWood >= required
    }

    private fun consumeWood(amount: Int): Boolean {
        var remaining = amount

        for (hand in listOf(Hand.MAIN_HAND, Hand.OFF_HAND)) {
            val stack = aiPlayer.getStackInHand(hand)
            if (isLog(stack)) {
                val consume = minOf(stack.count, remaining)
                stack.decrement(consume)
                remaining -= consume
                if (remaining <= 0) return true
            }
        }

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

        for (i in 0 until aiPlayer.inventory.size()) {
            val stack = aiPlayer.inventory.getStack(i)
            when (stack.item) {
                Items.COBBLESTONE -> cobblestoneCount += stack.count
                Items.STICK -> stickCount += stack.count
            }
        }

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

        for (i in 0 until aiPlayer.inventory.size()) {
            val stack = aiPlayer.inventory.getStack(i)
            if (stack.item in woodenTools) {
                aiPlayer.inventory.setStack(i, ItemStack.EMPTY)
            }
        }

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

class CraftFurnaceAction(private val aiPlayer: AIPlayerEntity) : ActionNode("craftFurnace") {
    private var craftingTicks = 0

    override fun start() {
        status = Status.RUNNING
        aiPlayer.debugLog("[合成] 开始制作熔炉")
    }

    override fun tick() {
        when {
            !hasMaterials() -> {
                status = Status.FAILURE
                return
            }
            craftingTicks++ < 20 -> return
            else -> tryCraftFurnace()
        }
    }

    private fun hasMaterials(): Boolean {
        var cobblestoneCount = 0
        for (i in 0 until aiPlayer.inventory.size()) {
            val stack = aiPlayer.inventory.getStack(i)
            when {
                stack.item == Items.COBBLESTONE -> cobblestoneCount += stack.count
                stack.item == Items.FURNACE -> return false
            }
        }
        return cobblestoneCount >= 8
    }

    private fun tryCraftFurnace() {
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

