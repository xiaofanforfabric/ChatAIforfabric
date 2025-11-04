package com.xiaofan.chatai.aitree

import com.xiaofan.chatai.aiplayerentity.AIPlayerEntity
import net.minecraft.block.Blocks
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.mob.CreeperEntity
import net.minecraft.entity.mob.HostileEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/**
 * 熔炼行为 - 熔炼矿石
 */
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

    private fun isDayTime(world: World): Boolean {
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

