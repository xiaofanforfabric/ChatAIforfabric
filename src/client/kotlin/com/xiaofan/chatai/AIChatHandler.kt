package com.xiaofan.chatai

import com.xiaofan.chatai.config.ConfigManager
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object AIChatHandler {
    private val client = OkHttpClient.Builder()
        .connectTimeout(ConfigManager.timeoutSeconds.toLong(), TimeUnit.SECONDS)
        .readTimeout((ConfigManager.timeoutSeconds / 2).toLong(), TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

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

    fun getMinecraftCommand(prompt: String): String {
        val requestBody = JSONObject().apply {
            put("model", ConfigManager.model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "严格只返回一个有效的Minecraft命令（不带/前缀），" +
                            "不要任何解释或额外符号。需求：$prompt\n" +
                            "重要规则：\n" +
                            "1. 不要包含换行符\\n\n" +
                            "2. 不要包含反引号\n" +
                            "3. NBT标签必须用{}包裹")
                })
            })
            put("temperature", 0.1)
            put("max_tokens", 50)
        }.toString()

        return sendToDeepSeek(requestBody).trim().removePrefix("/").trim()
    }

    private fun sendToDeepSeek(prompt: String): String {
        val requestBody = try {
            JSONObject().apply {
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
        } catch (e: Exception) {
            throw IOException("Failed to build JSON request: ${e.message}")
        }

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