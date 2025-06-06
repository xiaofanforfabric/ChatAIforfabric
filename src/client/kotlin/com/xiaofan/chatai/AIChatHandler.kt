// AIChatHandler.kt
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

        val response = client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("HTTP ${resp.code}: ${resp.body?.string() ?: "无错误详情"}")
            }
            resp.body?.string() ?: throw IOException("空响应")
        }

        return parseResponse(response)
    }

    private fun parseResponse(response: String): String {
        val json = JSONObject(response)
        return json.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            ?: throw IOException("响应格式不符合预期: ${json.toString(2)}")
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