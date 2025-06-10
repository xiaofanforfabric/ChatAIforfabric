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