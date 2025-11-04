package com.xiaofan.chatai.aitree

import com.xiaofan.chatai.aiplayerentity.AIPlayerEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.Hand

/**
 * 背包清理行为 - 用于清理背包中的特定物品
 */
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
     *              例如：clear(Items.COBBLESTONE to 64, Items.STONE to "all")
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

