package com.xiaofan.chatai

import com.xiaofan.chatai.config.ConfigManager
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.MinecraftClient
import net.minecraft.entity.EntityType
import net.minecraft.entity.mob.CreeperEntity
import net.minecraft.entity.mob.HostileEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.world.LightType
import net.minecraft.world.World
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import net.minecraft.block.Blocks

object AIChatHandler {
    // HTTP 客户端配置
    private val client = OkHttpClient.Builder()
        .connectTimeout(ConfigManager.timeoutSeconds.toLong(), TimeUnit.SECONDS)
        .readTimeout((ConfigManager.timeoutSeconds / 2).toLong(), TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    // 安全监控线程控制
    private var safetyThread: Thread? = null
    @Volatile
    private var isMonitoring = false

    // SOS监控线程控制
    private var sosThread: Thread? = null
    @Volatile
    private var isSOSMonitoring = false
    private var lastSOSTriggerTime: Long = 0
    private const val SOS_COOLDOWN_MS = 5 * 60 * 1000L // 5分钟冷却

    // 基础AI聊天功能
    fun processCommand(message: String, source: FabricClientCommandSource) {
        if (ConfigManager.apiKey.isBlank()) {
            source.sendError(Text.literal("⚠️ 请先配置API密钥: /ai-config set-key <你的密钥>"))
            return
        }

        source.sendFeedback(Text.literal("AI正在思考..."))

        Thread {
            try {
                val response = sendToDeepSeek(message)
                MinecraftClient.getInstance().execute {
                    source.sendFeedback(Text.literal("AI回复: $response"))
                }
            } catch (e: Exception) {
                MinecraftClient.getInstance().execute {
                    handleError(e, source)
                }
            }
        }.start()
    }

    // 生成Minecraft命令
    fun getMinecraftCommand(prompt: String): String {
        val requestBody = JSONObject().apply {
            put("model", ConfigManager.model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", """
                        严格只返回一个有效的Minecraft命令（不带/前缀），
                        不要任何解释或额外符号。需求：$prompt
                        重要规则：
                        1. 不要包含换行符\n
                        2. 不要包含反引号
                        3. NBT标签必须用{}包裹
                    """.trimIndent())
                })
            })
            put("temperature", 0.1)
            put("max_tokens", 50)
        }.toString()

        return sendToDeepSeek(requestBody).trim().removePrefix("/").trim()
    }

    // SOS监控功能
    fun isSOSMonitoring(): Boolean = isSOSMonitoring && sosThread?.isAlive == true

    fun startSOSMonitoring(player: PlayerEntity): Boolean {
        if (isSOSMonitoring()) return false

        // 检查冷却时间
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSOSTriggerTime < SOS_COOLDOWN_MS) {
            val remainingMinutes = (SOS_COOLDOWN_MS - (currentTime - lastSOSTriggerTime)) / 60000 + 1
            MinecraftClient.getInstance().execute {
                player.sendMessage(
                    Text.literal("⚠️ 紧急援助冷却中，请${remainingMinutes}分钟后再试")
                        .styled { it.withColor(Formatting.RED) }
                )
            }
            return false
        }

        isSOSMonitoring = true
        sosThread = Thread {
            while (isSOSMonitoring && !Thread.currentThread().isInterrupted) {
                try {
                    val playerData = """
                        |=== 玩家紧急状态 ===
                        |生命值: ${player.health}/20
                        |饥饿值: ${player.hungerManager.foodLevel}/20
                        |位置: ${player.blockPos.x}, ${player.blockPos.y}, ${player.blockPos.z}
                        |维度: ${player.world.registryKey.value}
                        |是否着火: ${player.isOnFire}
                        |是否在水中: ${player.isSubmergedInWater}
                        |是否有掉落伤害: ${player.fallDistance > 3}
                    """.trimMargin()

                    val response = sendToDeepSeek("""
                        紧急分析玩家状态，严格按规则响应：
                        1. 当生命值<5时，返回"effect give @s minecraft:regeneration 60 255"
                        2. 当饥饿值<6时，返回"effect give @s minecraft:saturation 60 255"
                        3. 当玩家着火时，返回"effect give @s minecraft:fire_resistance 60 255"
                        4. 当有掉落危险时，返回"effect give @s minecraft:slow_falling 30 255"
                        5. 判断玩家安全时只返回"安全"
                        6. 只返回Minecraft命令或"安全"，不要任何解释 
                        7. 其他危险请返回对应救援命令
                        
                        玩家数据：
                        $playerData
                    """.trimIndent())

                    // 修改 startSOSMonitoring 方法中的执行命令部分
                    MinecraftClient.getInstance().execute {
                        if (response != "安全") {
                            // 获取客户端玩家对象
                            val clientPlayer = MinecraftClient.getInstance().player
                            if (clientPlayer != null) {
                                try {
                                    clientPlayer.networkHandler.sendChatCommand(response)
                                    player.sendMessage(
                                        Text.literal("[AI紧急援助] 已执行: /$response")
                                            .styled { it.withColor(Formatting.RED) }
                                    )
                                    // 触发后立即停止监控并设置冷却时间
                                    stopSOSMonitoring()
                                    lastSOSTriggerTime = System.currentTimeMillis()
                                    player.sendMessage(
                                        Text.literal("⚠️ 紧急援助已触发，系统进入5分钟冷却")
                                            .styled { it.withColor(Formatting.GOLD) }
                                    )
                                } catch (e: Exception) {
                                    player.sendMessage(
                                        Text.literal("⚠️ 执行命令失败: ${e.message}")
                                            .styled { it.withColor(Formatting.RED) }
                                    )
                                }
                            } else {
                                player.sendMessage(
                                    Text.literal("⚠️ 无法获取玩家网络连接")
                                        .styled { it.withColor(Formatting.RED) }
                                )
                            }
                        }
                    }

                    Thread.sleep(5000)
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    MinecraftClient.getInstance().execute {
                        player.sendMessage(
                            Text.literal("⚠️ SOS监控出错: ${e.message?.substringBefore("\n")}")
                                .styled { it.withColor(Formatting.GOLD) }
                        )
                    }
                    Thread.sleep(10000)
                }
            }
            isSOSMonitoring = false
        }.apply {
            isDaemon = true
            start()
        }
        return true
    }

    fun stopSOSMonitoring() {
        isSOSMonitoring = false
        sosThread?.interrupt()
        sosThread = null
    }

    fun getSOSCooldownRemaining(): Long {
        val elapsed = System.currentTimeMillis() - lastSOSTriggerTime
        return if (elapsed >= SOS_COOLDOWN_MS) 0 else SOS_COOLDOWN_MS - elapsed
    }

    // 世界扫描与安全监控功能
    fun scanHostileEntities(world: World, center: BlockPos, radius: Int): List<Pair<String, BlockPos>> {
        val hostileEntities = mutableListOf<Pair<String, BlockPos>>()

        world.getEntitiesByClass(
            HostileEntity::class.java,
            Box.of(center.toCenterPos(), radius.toDouble(), radius.toDouble(), radius.toDouble()),
            { hostile -> hostile.isAlive }
        ).forEach { entity ->
            hostileEntities.add(entity.type.translationKey to entity.blockPos)
        }

        world.getEntitiesByType(
            EntityType.CREEPER,
            Box.of(center.toCenterPos(), radius.toDouble(), radius.toDouble(), radius.toDouble()),
            { creeper -> creeper.isAlive }
        ).forEach {
            hostileEntities.add("entity.minecraft.creeper" to it.blockPos)
        }

        return hostileEntities.distinct()
    }

    fun collectWorldData(player: PlayerEntity): String {
        val world = player.world
        val center = player.blockPos
        val radius = 80

        // 1. 扫描敌对生物
        val hostiles = scanHostileEntities(world, center, radius)
            .takeIf { it.isNotEmpty() }
            ?.joinToString { "${it.first} (${it.second.x}, ${it.second.y}, ${it.second.z})" }
            ?: "无"

        // 2. 扫描危险方块
        val dangerousBlocks = scanDangerousBlocks(world, center, radius)
            .takeIf { it.isNotEmpty() }
            ?.joinToString { "${it.first} (${it.second.x}, ${it.second.y}, ${it.second.z})" }
            ?: "无"

        // 3. 获取时间状态
        val timeStatus = when {
            world.timeOfDay in 0..12000 -> "白天"
            world.timeOfDay in 12001..23999 -> "夜晚"
            else -> "未知"
        }

        return """
        |=== 环境安全扫描 ===
        |位置: ${center.x}, ${center.y}, ${center.z}
        |半径: ${radius}格
        |敌对生物: $hostiles
        |危险方块: $dangerousBlocks
        |时间: ${world.timeOfDay} ($timeStatus)
        |光照: ${world.getLightLevel(LightType.BLOCK, center)} (安全≥7)
        |""".trimMargin()
    }

    private fun scanDangerousBlocks(world: World, center: BlockPos, radius: Int): List<Pair<String, BlockPos>> {
        val dangerousBlocks = mutableListOf<Pair<String, BlockPos>>()
        val dangerousBlockTypes = setOf(
            Blocks.LAVA,
            Blocks.FIRE,
            Blocks.TNT,
            Blocks.MAGMA_BLOCK,
            Blocks.CAMPFIRE
        )

        for (x in -radius..radius) {
            for (y in -radius..radius) {
                for (z in -radius..radius) {
                    val pos = center.add(x, y, z)
                    val blockState = world.getBlockState(pos)

                    if (dangerousBlockTypes.contains(blockState.block)) {
                        dangerousBlocks.add(
                            blockState.block.translationKey to pos
                        )
                    }
                }
            }
        }
        return dangerousBlocks
    }

    // 安全监控控制方法
    fun isSafetyCheckRunning(): Boolean = isMonitoring && safetyThread?.isAlive == true

    fun startPeriodicSafetyCheck(player: PlayerEntity) {
        if (isSafetyCheckRunning()) return

        isMonitoring = true
        safetyThread = Thread {
            while (isMonitoring && !Thread.currentThread().isInterrupted) {
                try {
                    val worldData = collectWorldData(player)
                    val response = sendToDeepSeek("""
                        |根据以下游戏数据生成安全提示：
                        |$worldData
                        |要求：
                        |- 1.判断当前危险等级，以[安全][警告][危险],在回答开头说明
                        |- 2.回复包含当前信息（尽量精简）
                        |- 3.不要建议
                        |- 4.计算危险实体与玩家距离
                        |- 5.计算危险方块玩家的距离
                        |- 6.忽略时间判断是否是白天
                        |- 7.当光照为零时，说明玩家可能在白天的陆地上
                        |- 8.计算岩浆方块与玩家的垂直距离，小于20格为警告，小于15格为危险,此为要求1的危险等级（当玩家在此方块X,Z坐标上）
                        |- 9.必须包含实体坐标（如果存在）
                        |- 10.使用中文回复
                        |- 11.尽量精简，要知道你现在每回复一个token都是金子。
                        |- 12.注意，危险方块与危险实体，20格以上，回复最简数据：安全。10-20，回复带危险物体坐标：警告。10格以内：危险。此为要求1的危险等级（判断当前危险等级，以[安全][警告][危险],在回答开头说明）
                        """.trimMargin())

                    // 在 startPeriodicSafetyCheck 方法中修改消息颜色判断部分
                    MinecraftClient.getInstance().execute {
                        player.sendMessage(
                            Text.literal("[AI安全] $response").styled {
                                when {
                                    response.contains("[危险]") -> it.withColor(Formatting.RED)
                                    response.contains("[警告]") -> it.withColor(Formatting.YELLOW)
                                    response.contains("注意") -> it.withColor(Formatting.GOLD)
                                    else -> it.withColor(Formatting.GREEN)
                                }
                            }
                        )
                    }

                    Thread.sleep(60000) // 每分钟检查一次
                } catch (e: InterruptedException) {
                    break // 正常退出
                } catch (e: Exception) {
                    MinecraftClient.getInstance().execute {
                        player.sendMessage(
                            Text.literal("⚠️ 安全监控出错: ${e.message?.substringBefore("\n")}")
                                .styled { it.withColor(Formatting.GOLD) }
                        )
                    }
                    Thread.sleep(30000) // 出错后等待30秒
                }
            }
            isMonitoring = false
        }.apply {
            isDaemon = true
            start()
        }
    }

    fun stopPeriodicSafetyCheck() {
        isMonitoring = false
        safetyThread?.interrupt()
        safetyThread = null
    }

    // 私有方法
    private fun sendToDeepSeek(prompt: String): String {
        val requestBody = JSONObject().apply {
            put("model", ConfigManager.model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("temperature", ConfigManager.temperature)
            put("max_tokens", ConfigManager.maxTokens)
        }.toString()

        val request = Request.Builder()
            .url(ConfigManager.apiUrl)
            .addHeader("Authorization", "Bearer ${ConfigManager.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                val errorBody = resp.body?.string() ?: "无错误详情"
                throw IOException("HTTP ${resp.code}: $errorBody")
            }
            parseResponse(resp.body?.string() ?: throw IOException("空响应"))
        }
    }

    private fun parseResponse(response: String): String {
        return JSONObject(response)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
    }

    private fun handleError(e: Exception, source: FabricClientCommandSource) {
        val errorMsg = when {
            e.message?.contains("401") == true -> "API密钥无效"
            e.message?.contains("429") == true -> "请求过于频繁"
            e is java.net.SocketTimeoutException -> "请求超时，请稍后重试"
            else -> "请求失败: ${e.message?.substringBefore("\n")}"
        }
        source.sendError(Text.literal("⚠️ $errorMsg"))
    }
}