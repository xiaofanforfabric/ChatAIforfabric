package com.xiaofan.chatai.aiplayerentity

import com.mojang.authlib.GameProfile
import com.xiaofan.chatai.ChatAI
import com.xiaofan.chatai.aitree.BehaviorTree
import com.xiaofan.chatai.config.AIPlayerConfig
import com.xiaofan.chatai.servercommand.AIDebugManager
import net.minecraft.block.BedBlock
import net.minecraft.block.Block
import net.minecraft.command.argument.EntityAnchorArgumentType
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.ItemEntity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.ai.goal.LookAtEntityGoal
import net.minecraft.entity.ai.goal.SwimGoal
import net.minecraft.entity.ai.goal.WanderAroundFarGoal
import net.minecraft.entity.ai.pathing.PathNodeType
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.data.DataTracker
import net.minecraft.entity.data.TrackedData
import net.minecraft.entity.data.TrackedDataHandlerRegistry
import net.minecraft.entity.mob.HostileEntity
import net.minecraft.entity.mob.PathAwareEntity
import net.minecraft.entity.passive.AnimalEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.*
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtList
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket
import net.minecraft.predicate.entity.EntityPredicates
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.tag.BlockTags
import net.minecraft.registry.tag.TagKey
import net.minecraft.server.world.ChunkTicketType
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundEvent
import net.minecraft.sound.SoundEvents
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Hand
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.Heightmap
import net.minecraft.world.World
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

class AIPlayerEntity(
    entityType: EntityType<out AIPlayerEntity>,
    world: World,
) : PathAwareEntity(entityType, world) {

    val inventoryContents: String
        get() = (0 until inventory.size()).joinToString { slot ->
            val stack = inventory.getStack(slot)
            if (stack.isEmpty) "空" else "${stack.item.name}×${stack.count}"
        }

    // 区块加载控制
    private var loadedChunkPos: ChunkPos? = null
    private var forceLoadTicket: Long? = null

    // 在类属性部分添加
    private var respawnTimer = 1
    private var deathPos: BlockPos? = null
    var keepInventoryOnDeath: Boolean = true
    private var isRespawning = false
    var customSkin: Identifier? = null
        private set


    // 数据跟踪器
    companion object {
        val TYPE: EntityType<out AIPlayerEntity>
            get() = ModEntities.AI_PLAYER

        private val worldToEntityMap = Collections.synchronizedMap(mutableMapOf<RegistryKey<World>, Int>())
        const val AI_PLAYER_NAME = "AIPlayer"

        private val DATA_INITIALIZED: TrackedData<Boolean> = DataTracker.registerData(
            AIPlayerEntity::class.java,
            TrackedDataHandlerRegistry.BOOLEAN
        )

        private val DATA_SKIN_TEXTURE: TrackedData<String> = DataTracker.registerData(
            AIPlayerEntity::class.java,
            TrackedDataHandlerRegistry.STRING
        )

        private val CAN_PICK_UP_LOOT: TrackedData<Boolean> = DataTracker.registerData(
            AIPlayerEntity::class.java,
            TrackedDataHandlerRegistry.BOOLEAN
        )
        val DATA_BED_POS: TrackedData<Optional<BlockPos>> =
            DataTracker.registerData(AIPlayerEntity::class.java, TrackedDataHandlerRegistry.OPTIONAL_BLOCK_POS)

        private var instanceExists: Boolean = false


        fun canSpawn(world: World): Boolean {
            // 仅使用我们自己的注册系统检查
            return !worldToEntityMap.containsKey(world.registryKey) ||
                    world.getEntityById(worldToEntityMap[world.registryKey]!!) == null
        }

        // 通知实例已创建
        private fun markInstanceCreated() {
            instanceExists = true
        }

        // 通知实例已移除
        fun markInstanceRemoved() {
            instanceExists = false
        }


        // 注册新创建的实体
        fun registerEntity(world: World, entity: AIPlayerEntity) {
            worldToEntityMap[world.registryKey] = entity.id
        }

        fun unregisterEntity(world: World) {
            worldToEntityMap.remove(world.registryKey)
        }

        fun getExistingEntity(world: World): AIPlayerEntity? {
            val id = worldToEntityMap[world.registryKey] ?: return null
            return world.getEntityById(id) as? AIPlayerEntity
        }

    }
    private val ARMOR_SLOT_MAPPING = mapOf(
        0 to EquipmentSlot.HEAD,    // 头盔
        1 to EquipmentSlot.CHEST,   // 胸甲
        2 to EquipmentSlot.LEGS,    // 护腿
        3 to EquipmentSlot.FEET     // 靴子
    )

    // 对应的物品栏槽位索引 (1.20.1版本)
    private val INVENTORY_ARMOR_SLOTS = mapOf(
        EquipmentSlot.HEAD to 39,
        EquipmentSlot.CHEST to 38,
        EquipmentSlot.LEGS to 37,
        EquipmentSlot.FEET to 36
    )

    fun setSkin(texture: Identifier) {
        customSkin = texture
        dataTracker.set(DATA_SKIN_TEXTURE, texture.toString())
    }
    override fun getEntityName(): String = AI_PLAYER_NAME

    override fun getName(): Text = Text.literal(AI_PLAYER_NAME)

    fun shouldReceiveCommandFeedback(): Boolean = true

    // 使实体可被名称选择器识别
    override fun isPlayer(): Boolean = true


    // 基础属性
    private val gameProfile = GameProfile(UUID.randomUUID(), "AI_Worker_${Random.nextInt(1000)}")
    private val behaviorTree = BehaviorTree(this)
    val inventory = SimpleInventory(40)
    val angerTargets = ConcurrentHashMap<UUID, Int>()
    var bedPos: BlockPos? = null
    private var lastTargetCheckTime = 0L
// 在AIPlayerEntity类中添加以下内容

    // 盔甲槽位索引 (1.20.1版本的盔甲槽位索引)
    private val ARMOR_SLOTS = listOf(36, 37, 38, 39) // 头盔、胸甲、护腿、靴子

    // 盔甲价值评估表 (1.20.1物品命名)
    private val ARMOR_VALUES = mapOf(
        Items.NETHERITE_HELMET to 8, Items.NETHERITE_CHESTPLATE to 8,
        Items.NETHERITE_LEGGINGS to 8, Items.NETHERITE_BOOTS to 8,
        Items.DIAMOND_HELMET to 6, Items.DIAMOND_CHESTPLATE to 6,
        Items.DIAMOND_LEGGINGS to 6, Items.DIAMOND_BOOTS to 6,
        Items.IRON_HELMET to 4, Items.IRON_CHESTPLATE to 4,
        Items.IRON_LEGGINGS to 4, Items.IRON_BOOTS to 4,
        Items.GOLDEN_HELMET to 2, Items.GOLDEN_CHESTPLATE to 2,
        Items.GOLDEN_LEGGINGS to 2, Items.GOLDEN_BOOTS to 2,
        Items.LEATHER_HELMET to 1, Items.LEATHER_CHESTPLATE to 1,
        Items.LEATHER_LEGGINGS to 1, Items.LEATHER_BOOTS to 1
    )

    // 检查并更新盔甲 (1.20.1兼容版本)
    fun checkAndEquipBestArmor() {
        if (world.isClient) return

        // 检查每个装备槽位
        for ((slotIndex, equipmentSlot) in ARMOR_SLOT_MAPPING) {
            val inventorySlot = INVENTORY_ARMOR_SLOTS[equipmentSlot] ?: continue
            val currentArmor = inventory.getStack(inventorySlot)

            // 找到背包中同类型更好的盔甲
            val bestArmorInfo = findBestArmorForSlot(slotIndex)

            bestArmorInfo?.let { (bestSlot, bestStack) ->
                if (currentArmor.isEmpty || isBetterArmor(bestStack, currentArmor)) {
                    // 使用标准方法装备
                    equipStack(equipmentSlot, bestStack.copy())

                    // 更新物品栏
                    inventory.setStack(bestSlot, currentArmor.copy())

                    // 同步装备状态
                    syncEquipment(equipmentSlot)
                    debugLog("已装备 ${bestStack.item.name.string} 到 ${equipmentSlot.name}")
                }
            }
        }
    }

    private fun syncEquipment(slot: EquipmentSlot) {
        if (world is ServerWorld) {
            (world as ServerWorld).chunkManager.sendToNearbyPlayers(
                this,
                EntityEquipmentUpdateS2CPacket(id,
                    listOf(Pair(slot, getEquippedStack(slot))) as List<com.mojang.datafixers.util.Pair<EquipmentSlot?, ItemStack?>?>?
                )
            )
        }
    }

    private fun findBestArmorForSlot(slotIndex: Int): Pair<Int, ItemStack>? {
        val equipmentSlot = ARMOR_SLOT_MAPPING[slotIndex] ?: return null
        var bestSlot = -1
        var bestStack: ItemStack? = null
        var bestValue = -1

        // 扫描背包(跳过装备槽位)
        for (i in 0 until inventory.size()) {
            if (INVENTORY_ARMOR_SLOTS.values.contains(i)) continue

            val stack = inventory.getStack(i)
            if (isArmorForSlot(stack, equipmentSlot)) {
                val value = getArmorValue(stack)
                if (value > bestValue) {
                    bestValue = value
                    bestStack = stack
                    bestSlot = i
                }
            }
        }

        return if (bestSlot != -1) bestSlot to bestStack!! else null
    }

    private fun isArmorForSlot(stack: ItemStack, slot: EquipmentSlot): Boolean {
        if (stack.isEmpty) return false
        val armorItem = stack.item as? ArmorItem ?: return false
        return armorItem.slotType == slot
    }
    // 获取盔甲价值
    private fun getArmorValue(stack: ItemStack): Int {
        return ARMOR_VALUES[stack.item] ?: 0
    }

    // 比较两件盔甲的价值
    private fun isBetterArmor(newArmor: ItemStack, currentArmor: ItemStack): Boolean {
        return getArmorValue(newArmor) > getArmorValue(currentArmor)
    }

    // 获取盔甲槽位名称
    private fun getArmorSlotName(slotIndex: Int): String {
        return when (slotIndex) {
            0 -> "头部"
            1 -> "胸部"
            2 -> "腿部"
            3 -> "脚部"
            else -> "未知"
        }
    }





    // 在类属性部分添加
    var hunger: Int = 20
        private set

    init {
        if (!world.isClient) {
            when {
                getExistingEntity(world) != null -> {
                    // 已有实体存在
                    remove(RemovalReason.DISCARDED)
                    debugLog("阻止重复生成，已有实体存在")
                }
                else -> {
                    // 正常注册流程
                    registerEntity(world, this)
                    initEntity()
                    debugLog("新实体注册成功 ID:$id")
                }
            }
        }
    }

    private fun handleDuplicateEntity() {
        if (world is ServerWorld) {
            world.server?.playerManager?.broadcast(
                Text.literal("§c检测到重复AI实体，正在清理..."),
                false
            )
        }
        remove(RemovalReason.DISCARDED)
        ChatAI.LOGGER.warn("重复AI实体已移除 ${world.registryKey.value}")
    }

    private fun initEntity() {
        this.setPathfindingPenalty(PathNodeType.DANGER_FIRE, 16.0f)
        this.setCustomName(Text.literal("AIPlayer"))
        this.isCustomNameVisible = true
        this.setPersistent()
        initGoals()
        initInventoryListener()
    }

    private fun initInventoryListener() {
        inventory.addListener { debugLog("Inventory changed") }
    }

    private fun forceLoadCurrentChunk() {
        if (world is ServerWorld) {
            val serverWorld = world as ServerWorld
            val newChunkPos = ChunkPos(blockPos)

            if (newChunkPos == loadedChunkPos) return

            unforceLoadChunk()

            try {
                val chunkManager = serverWorld.chunkManager
                chunkManager.addTicket(ChunkTicketType.FORCED, newChunkPos, 2, newChunkPos)
                chunkManager.getChunk(newChunkPos.x, newChunkPos.z)
                loadedChunkPos = newChunkPos
                debugLog("Force loaded chunk: $newChunkPos")
            } catch (e: Exception) {
                debugLog("Failed to force load chunk: ${e.message}")
            }
        }
    }

    private fun unforceLoadChunk() {
        loadedChunkPos?.let { pos ->
            if (world is ServerWorld) {
                (world as ServerWorld).chunkManager.removeTicket(
                    ChunkTicketType.FORCED,
                    pos,
                    2,
                    pos
                )
                debugLog("Unloaded chunk: $pos")
            }
            loadedChunkPos = null
            forceLoadTicket = null
        }
    }

    fun safeTeleport(x: Double, y: Double, z: Double): Boolean {
        refreshPositionAndAngles(x, y, z, yaw, pitch)
        forceLoadCurrentChunk()
        return true
    }

    override fun initDataTracker() {
        super.initDataTracker()
        dataTracker.startTracking(DATA_BED_POS, Optional.empty())
        dataTracker.startTracking(CAN_PICK_UP_LOOT, true)
        dataTracker.startTracking(DATA_INITIALIZED, false)
        dataTracker.startTracking(DATA_SKIN_TEXTURE, "default") // 默认值
    }


    override fun tick() {
        super.tick()

        if (!world.isClient && world.time % 100 == 0L) {
            checkAndEquipBestArmor()
        }


        if (!world.isClient) {
            behaviorTree.update()
        }


        if (!world.isClient && world.time % 100 == 0L) {
            val currentChunk = ChunkPos(blockPos)
            if (loadedChunkPos != currentChunk) {
                forceLoadCurrentChunk()
            }
        }

        if (!world.isClient) {
            handleBedSystem()
            if (world.time % 10 == 0L) {
                checkItemCollection()
                checkAndUpdateTarget()
            }
        }
        if (!world.isClient) {
            // 更新重生计时器
            if (respawnTimer > 0) {
                respawnTimer--
                debugLog("Respawn timer: $respawnTimer ticks remaining")
                if (respawnTimer <= 0) {  // 修改为<=0以确保立即执行
                    respawn()
                    checkAndUpdateTarget()
                }
            }
        }

        if (!world.isClient) {
            // 每10tick减少仇恨值
            if (world.time % 10 == 0L) {
                angerTargets.entries.removeAll { (_, anger) ->
                    (anger - 1) <= 0
                }
                angerTargets.replaceAll { _, anger -> anger - 1 }
            }
        }

    }

    private fun findRespawnPosition(): BlockPos {
        // 1. 优先尝试床位置
        bedPos?.let { pos ->
            if (world.getBlockState(pos).block is BedBlock) {
                debugLog("Attempting to respawn near bed at $pos")

                // 在床周围5格范围内寻找安全位置
                for (x in -5..5) {
                    for (z in -5..5) {
                        val checkPos = pos.add(x, 0, z)
                        val topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING, checkPos.x, checkPos.z)
                        val testPos = BlockPos(checkPos.x, topY, checkPos.z)

                        // 使用当前实体的边界框检查位置
                        val testBox = boundingBox.offset(
                            testPos.x - this.x,
                            testPos.y - this.y,
                            testPos.z - this.z
                        )

                        if (world.isSpaceEmpty(testBox)) {
                            debugLog("Found safe respawn position at $testPos")
                            return testPos
                        }
                    }
                }
                debugLog("No safe position found near bed, respawning on top of bed")
                return pos.up()
            }
        }

        // 2. 尝试死亡位置
        deathPos?.let { pos ->
            debugLog("Attempting to respawn at death position $pos")
            val topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING, pos.x, pos.z)
            val testPos = BlockPos(pos.x, topY, pos.z)

            val testBox = boundingBox.offset(
                testPos.x - this.x,
                testPos.y - this.y,
                testPos.z - this.z
            )

            if (world.isSpaceEmpty(testBox)) {
                debugLog("Respawning at death position $testPos")
                return testPos
            }
        }

        // 3. 最后使用世界出生点
        debugLog("Respawning at world spawn")
        return world.spawnPos
    }

    private fun respawn() {
        if (world.isClient || isRespawning) return

        isRespawning = true
        debugLog("Attempting to respawn current entity...")

        // 1. 找到安全位置
        val respawnPos = findRespawnPosition()

        // 2. 重置位置和状态
        refreshPositionAndAngles(
            respawnPos.x + 0.5,
            respawnPos.y.toDouble(),
            respawnPos.z + 0.5,
            yaw, pitch
        )

        // 3. 重置生命值和状态
        health = maxHealth
        hunger = 20 // 重置饥饿值（如果有）
        fireTicks = 0 // 清除燃烧状态
        fallDistance = 0f // 清除坠落伤害

        // 4. 同步到客户端
        if (world is ServerWorld) {
            (world as ServerWorld).chunkManager.sendToNearbyPlayers(
                this,
                EntityPositionS2CPacket(this)
            )
        }

        // 5. 完成重生
        isRespawning = false
        respawnTimer = -1
        debugLog("Respawn completed at $respawnPos")

        // 6. 打印物品栏状态
        if (world is ServerWorld) {

            debugLog("Inventory after respawn: $inventoryContents")
        }
    }

    override fun onRemoved() {
        if (!world.isClient && worldToEntityMap[world.registryKey] == id) {
            unregisterEntity(world)
        }
        super.onRemoved()
    }

    private fun checkItemCollection() {
        if (!canPickUpLoot()) return

        val pickupBox = boundingBox.expand(3.0, 1.0, 3.0)
        world.getEntitiesByClass(ItemEntity::class.java, pickupBox, EntityPredicates.VALID_ENTITY)
            .filter { canPickupItem(it.stack) }
            .forEach { item ->
                if (canReachItem(item)) {
                    attemptPickup(item)
                }
            }
    }

    private fun canReachItem(item: ItemEntity): Boolean {
        return squaredDistanceTo(item) < 16.0 && navigation.findPathTo(item, 0) != null
    }

    override fun canPickupItem(stack: ItemStack): Boolean {
        return !stack.isEmpty
    }

    private fun attemptPickup(item: ItemEntity) {
        lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, item.pos)
        val stack = item.stack.copy()

        when {
            stack.isEmpty -> return
            stack.item is ShieldItem -> handleShieldPickup(item, stack)
            else -> handleNormalPickup(item, stack)
        }
    }

    private fun handleShieldPickup(item: ItemEntity, stack: ItemStack) {
        val offhandStack = getStackInHand(Hand.OFF_HAND)

        when {
            offhandStack.isEmpty -> {
                setStackInHand(Hand.OFF_HAND, stack)
                item.discard()
                playPickupSound()
            }

            else -> {
                val remaining = inventory.addStack(stack)
                updateItemAfterPickup(item, stack, remaining)
            }
        }
    }

    private fun handleNormalPickup(item: ItemEntity, stack: ItemStack) {
        val mainHand = getStackInHand(Hand.MAIN_HAND)

        when {
            mainHand.isEmpty -> {
                setStackInHand(Hand.MAIN_HAND, stack)
                item.discard()
                playPickupSound()
            }

            ItemStack.canCombine(mainHand, stack) -> {
                val transferred = minOf(stack.count, mainHand.maxCount - mainHand.count)
                mainHand.increment(transferred)
                stack.decrement(transferred)
                updateStackAfterTransfer(item, stack)
            }

            else -> {
                val remaining = inventory.addStack(stack)
                updateItemAfterPickup(item, stack, remaining)
            }
        }
    }

    private fun updateStackAfterTransfer(item: ItemEntity, stack: ItemStack) {
        if (stack.isEmpty) {
            item.discard()
        } else {
            item.stack = stack
        }
        playPickupSound()
    }

    private fun updateItemAfterPickup(item: ItemEntity, original: ItemStack, remaining: ItemStack) {
        when {
            remaining.isEmpty -> item.discard()
            remaining.count < original.count -> item.stack = remaining
        }
        playPickupSound()
    }

    private fun playPickupSound() {
        playSound(SoundEvents.ENTITY_ITEM_PICKUP, 0.2f, 1.0f)
    }

    private fun handleBedSystem() {
        if (world.isClient) return

        val time = world.timeOfDay
        when {
            shouldSleep() -> {
                if (bedPos == null || !trySleep(bedPos!!)) {
                    findAndBindBed()
                }
            }

            shouldWakeUp() -> wakeUp()
        }
    }

    private fun findAndBindBed(): Boolean {
        val searchRadius = 5
        val beds = mutableListOf<BlockPos>()

        for (x in -searchRadius..searchRadius) {
            for (y in -1..1) {
                for (z in -searchRadius..searchRadius) {
                    val pos = blockPos.add(x, y, z)
                    val state = world.getBlockState(pos)

                    if (state.block is BedBlock && !state.get(BedBlock.OCCUPIED)) {
                        beds.add(pos)
                    }
                }
            }
        }

        beds.minByOrNull { squaredDistanceTo(Vec3d.of(it)) }?.let { pos ->
            if (trySleep(pos)) {
                setBedPosition(pos)
                return true
            }
        }
        return false
    }

    private fun trySleep(pos: BlockPos): Boolean {
        if (!canSleepAt(pos)) return false
        navigation.stop()
        return try {
            sleep(pos)
            true
        } catch (e: Exception) {
            debugLog("Failed to sleep: ${e.message}")
            false
        }
    }

    private fun canSleepAt(pos: BlockPos): Boolean {
        val state = world.getBlockState(pos)
        return state.block is BedBlock &&
                !state.get(BedBlock.OCCUPIED) &&
                squaredDistanceTo(Vec3d.of(pos)) < 9.0 &&
                navigation.findPathTo(pos, 0) != null
    }

    private fun shouldSleep(): Boolean {
        if (isSleeping) return false
        return world.isNight || world.isThundering
    }

    private fun shouldWakeUp(): Boolean {
        if (!isSleeping) return false
        return world.isDay && !world.isThundering
    }

    private fun setBedPosition(pos: BlockPos?) {
        bedPos = pos
        dataTracker.set(DATA_BED_POS, Optional.ofNullable(pos))

        if (pos != null) {
            debugLog("Bound to bed at $pos")
        } else {
            debugLog("Bed binding removed")
        }
    }
    // 在类中添加


// 可以添加命令或配置文件来控制这个选项

    override fun initGoals() {
        goalSelector.add(2, WanderAroundFarGoal(this, 0.6))
        goalSelector.add(3, LookAtEntityGoal(this, PlayerEntity::class.java, 8.0f))
        goalSelector.add(4, SwimGoal(this))

    }

    private fun checkAndUpdateTarget() {
        val currentTarget = target
        if (currentTarget == null || !currentTarget.isAlive || !canTarget(currentTarget)) {
            selectNewTargetFromAngerList()
        }
    }

    private fun selectNewTargetFromAngerList() {
        angerTargets.entries.sortedByDescending { it.value }
            .firstOrNull { (uuid, _) ->
                val entity = world.getEntityById(id) as? LivingEntity
                entity != null && canTarget(entity)
            }?.let { (uuid, _) ->
                target = world.getEntityById(id) as LivingEntity?
            } ?: run { target = null }
    }

    internal fun debugLog(message: String) {
        if (!AIDebugManager.debugEnabled) return

        if (world is ServerWorld) {
            try {
                (world as ServerWorld).server.playerManager.broadcast(
                    Text.literal("[AI] $message"),
                    false
                )
                org.apache.logging.log4j.LogManager.getLogger("ChatAI").info("[AI] $message")
            } catch (e: Exception) {
                println("[AI] $message")
            }
        }
    }
    override fun getDisplayName(): Text = Text.literal("AIPlayer")
    override fun isPushable(): Boolean = true
    override fun isInvulnerableTo(source: DamageSource): Boolean = false
    override fun canPickUpLoot(): Boolean = true
    override fun isPersistent(): Boolean = true
    override fun cannotDespawn(): Boolean = true

    override fun getHurtSound(source: DamageSource): SoundEvent =
        SoundEvents.ENTITY_PLAYER_HURT

    override fun getDeathSound(): SoundEvent =
        SoundEvents.ENTITY_PLAYER_DEATH

    override fun playAmbientSound() {
        if (random.nextInt(100) < 5) {
            playSound(SoundEvents.ENTITY_PLAYER_BREATH, 0.8f, pitch)
        }
    }

    fun clearAllAnger() {
        angerTargets.clear()
    }

    override fun onDeath(source: DamageSource) {


        source.attacker?.let { attacker ->
            if (attacker is PlayerEntity) {
                angerTargets.remove(attacker.uuid)
                debugLog("死亡时清除对 ${attacker.name.string} 的仇恨")
            }
        }



        if (isRespawning) {
            super.onDeath(source)
            return
        }


        // 保存死亡位置
        deathPos = blockPos

        // 发送死亡消息
        val deathMessage = source.getDeathMessage(this)
        world.server?.playerManager?.broadcast(deathMessage, false)

        if (!world.isClient) {
            if (keepInventoryOnDeath) {
                debugLog("Player died but kept inventory")
            } else {
                ChatAI.LOGGER.error("keep error")
                // 掉落物品
                //inventory.clearToList().filterNot { it.isEmpty }
                    //.forEach { world.spawnEntity(ItemEntity(world, x, y, z, it)) }
            }

            // 立即开始重生流程（不调用super.onDeath()避免实体被移除）
            respawnTimer = 1 // 设置为1 tick后重生
            debugLog("Respawn timer set to immediate")
        }
    }

    override fun canTarget(entity: LivingEntity): Boolean {
        return entity is HostileEntity || (entity is AnimalEntity && entity.isRemoved)
    }

    override fun isImmobile(): Boolean {
        return isSleeping || super.isImmobile()
    }


    // 在类中添加以下方法
// 在 AIPlayerEntity 类中添加
    // 在 AIPlayerEntity 类中添加
// 获取完整物品栏信息（主手+副手+背包）
// 在 AIPlayerEntity.kt 中修改
    fun getFullInventoryContents(): List<String> {
        val contents = mutableListOf<String>()

        // 主副手（保持原样）
        listOf(Hand.MAIN_HAND to "主手", Hand.OFF_HAND to "副手").forEach { (hand, name) ->
            getStackInHand(hand).takeUnless { it.isEmpty }?.let { stack ->
                contents.add("$name: ${stack.item.name.string}×${stack.count}")
            }
        }

        // 背包物品（使用已验证的重生日志逻辑）
        contents += (0 until inventory.size())
            .mapNotNull { slot ->
                inventory.getStack(slot).takeUnless { it.isEmpty }?.let { stack ->
                    "背包[${slot}]: ${stack.item.name.string}×${stack.count}"
                }
            }

        return contents
    }

    // 清空完整物品栏（主手+副手+背包）
    fun clearFullInventory() {
        // 清空手持
        setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY)
        setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY)

        // 清空背包（使用您验证过的循环方式）
        for (i in 0 until inventory.size()) {
            inventory.setStack(i, ItemStack.EMPTY)
        }

        debugLog("所有物品栏已清空")
    }

    // 在AIPlayerEntity中添加调试方法
    fun debugBackpackAccess() {
        println("=== 背包调试 ===")
        println("背包类: ${inventory.javaClass.name}")
        println("背包大小: ${inventory.size()}")

        // 打印前5个槽位
        for (i in 0..4) {
            val stack = inventory.getStack(i)
            println("槽位[$i]: ${if (stack.isEmpty) "空" else stack.item.name.string}")
        }
    }

    // 在AIPlayerEntity中添加
    fun getInventoryForCommand(): List<Pair<String, ItemStack>> {
        val items = mutableListOf<Pair<String, ItemStack>>()

        // 主副手
        items.add("主手" to getStackInHand(Hand.MAIN_HAND))
        items.add("副手" to getStackInHand(Hand.OFF_HAND))

        // 背包（与重生日志相同的访问方式）
        for (i in 0 until inventory.size()) {
            items.add("背包[$i,$inventoryContents]" to inventory.getStack(i))
        }

        return items.filter { !it.second.isEmpty }
    }

    // 在 AIPlayerEntity 中添加
    // 在AIPlayerEntity类中添加/修改以下方法
    fun getFullInventoryForCommand(): List<Text> {
        return buildList {
            // 主副手
            add(createItemText("主手", getStackInHand(Hand.MAIN_HAND)))
            add(createItemText("副手", getStackInHand(Hand.OFF_HAND)))

            // 背包物品
            for (i in 0 until inventory.size()) {
                val stack = inventory.getStack(i)
                if (!stack.isEmpty) {
                    add(createItemText("背包[$i]", stack))
                }
            }
        }
    }

    private fun createItemText(prefix: String, stack: ItemStack): Text {
        return Text.literal("$prefix: ")
            .styled { it.withColor(Formatting.GRAY) }
            .append(stack.name.copy())
            .append(Text.literal("×${stack.count}").styled { it.withColor(Formatting.GOLD) })
    }

    // 添加实体类型标识
    override fun getType(): EntityType<*> {
        return EntityType.get("chatai:ai_player").orElse(EntityType.PLAYER)
    }

    // 在AIPlayerEntity类中添加
    @Suppress("unused")
    val synchronizedInventory: List<ItemStack>
        get() = (0 until inventory.size()).map { inventory.getStack(it) }

    fun getFormattedInventory(): List<String> {
        return synchronizedInventory.mapIndexed { index, stack ->
            if (stack.isEmpty) "空" else "槽位$index: ${stack.item.name.string}×${stack.count}"
        }
    }

    // 在 AIPlayerEntity 类中添加
    override fun writeCustomDataToNbt(nbt: NbtCompound) {
        super.writeCustomDataToNbt(nbt)

        customSkin?.let { nbt.putString("CustomSkin", it.toString()) }

        val armorTag = NbtList()
        ARMOR_SLOTS.forEach { slot ->
            val stack = inventory.getStack(slot)
            if (!stack.isEmpty) {
                val itemTag = NbtCompound()
                itemTag.putInt("Slot", slot) // 1.20.1使用putInt
                stack.writeNbt(itemTag)
                armorTag.add(itemTag)
            }
        }
        nbt.put("Armor", armorTag)


        // 保存背包数据
        val inventoryTag = NbtList()
        for (i in 0 until inventory.size()) {
            val stack = inventory.getStack(i)
            if (!stack.isEmpty) {
                val itemTag = NbtCompound()
                itemTag.putByte("Slot", i.toByte())
                stack.writeNbt(itemTag)
                inventoryTag.add(itemTag)
            }
        }
        nbt.put("Inventory", inventoryTag)
        // 保存其他自定义数据
        nbt.putBoolean("KeepInventoryOnDeath", keepInventoryOnDeath)
        bedPos?.let { nbt.putLong("BedPos", it.asLong()) }
    }

    override fun readCustomDataFromNbt(nbt: NbtCompound) {
        super.readCustomDataFromNbt(nbt)

        if (!world.isClient) {
            world.server?.submit {
                when (val existing = getExistingEntity(world)) {
                    null -> registerEntity(world, this)
                    this -> {} // 是自己，无需处理
                    else -> remove(RemovalReason.DISCARDED)
                }
            }
        }
        if (nbt.contains("CustomSkin")) {
            customSkin = Identifier(nbt.getString("CustomSkin"))
        }

        if (nbt.contains("Armor", 9)) { // 9是NbtList的类型ID
            val armorTag = nbt.getList("Armor", 10) // 10是NbtCompound的类型ID
            for (i in 0 until armorTag.size) {
                val itemTag = armorTag.getCompound(i)
                val slot = itemTag.getInt("Slot") // 1.20.1使用getInt
                val stack = ItemStack.fromNbt(itemTag)
                inventory.setStack(slot, stack)
            }
        }


        // 加载背包数据
        val inventoryTag = nbt.getList("Inventory", NbtCompound.COMPOUND_TYPE.toInt())
        for (i in 0 until inventoryTag.size) {
            val itemTag = inventoryTag.getCompound(i)
            val slot = itemTag.getByte("Slot").toInt()
            val stack = ItemStack.fromNbt(itemTag)
            inventory.setStack(slot, stack)
        }
        // 加载其他自定义数据
        keepInventoryOnDeath = nbt.getBoolean("KeepInventoryOnDeath")
        if (nbt.contains("BedPos")) {
            bedPos = BlockPos.fromLong(nbt.getLong("BedPos"))
        }
        markInstanceCreated() // 确保加载的实体被记录
    }

    override fun damage(source: DamageSource, amount: Float): Boolean {
        source.attacker?.let { attacker ->
            if (attacker is PlayerEntity) {
                angerTargets[attacker.uuid] = 100
                debugLog("被玩家 ${attacker.name.string} 攻击，加入仇恨列表")
            }
        }
        return super.damage(source, amount)
    }

    // 统计特定标签的物品数量
    fun SimpleInventory.countItems(tag: TagKey<Block>): Int {
        return (0 until size()).sumOf { slot ->
            val stack = getStack(slot)
            if (stack.item is BlockItem && (stack.item as BlockItem).block.defaultState.isIn(tag)) {
                stack.count
            } else 0
        }
    }

    // 消耗特定标签的物品（如原木）
    fun SimpleInventory.consumeItems(tag: TagKey<Block>, amount: Int): Boolean {
        var remaining = amount
        for (i in 0 until size()) {
            val stack = getStack(i)
            if (stack.item is BlockItem && (stack.item as BlockItem).block.defaultState.isIn(tag)) {
                val consume = minOf(stack.count, remaining)
                stack.decrement(consume)
                remaining -= consume
                if (remaining <= 0) return true
            }
        }
        return remaining <= 0
    }

    // 消耗特定物品（如木板、木棍）
    fun SimpleInventory.consumeItems(item: Item?, amount: Int): Boolean {
        var remaining = amount
        for (i in 0 until size()) {
            val stack = getStack(i)
            if (stack.item == item) {
                val consume = minOf(stack.count, remaining)
                stack.decrement(consume)
                remaining -= consume
                if (remaining <= 0) return true
            }
        }
        return remaining <= 0
    }
    private fun isLog(stack: ItemStack): Boolean {
        return stack.item is BlockItem &&
                (stack.item as BlockItem).block.defaultState.isIn(BlockTags.LOGS)
    }


    // 在 AIPlayerEntity 类中添加方法
    fun countWood(logs: Item?): Int {
        return inventory.countItems(BlockTags.LOGS)
    }

    fun consumeWood(amount1: TagKey<Block>, amount: Int): Boolean {
        return inventory.consumeItems(BlockTags.LOGS, amount)
    }

    fun consumeItem(item: Item, amount: Int): Boolean {
        return inventory.consumeItems(item, amount)
    }

}




