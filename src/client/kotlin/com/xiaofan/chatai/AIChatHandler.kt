package com.xiaofan.chatai

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

object AIChatHandler {
    const val API_URL = "https://api.deepseek.com/chat/completions"
    const val API_KEY = "sk-5cb5778e33074392b9dd08a05f90150a" // 建议改为从配置读取
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    fun processCommand(message: String, source: FabricClientCommandSource) {
        source.sendFeedback(Text.literal("AI正在思考..."))

        Thread {
            try {
                val response = sendToDeepSeek(message)
                MinecraftClient.getInstance().execute {
                    source.sendFeedback(Text.literal("AI回复: $response"))
                }
            } catch (e: Exception) {
                MinecraftClient.getInstance().execute {
                    val errorMsg = when {
                        e.message?.contains("401") == true -> "API密钥无效"
                        e.message?.contains("429") == true -> "请求过于频繁"
                        else -> "请求失败: ${e.message?.substringBefore("\n")}"
                    }
                    source.sendError(Text.literal("⚠️ $errorMsg"))
                }
            }
        }.start()
    }

    private fun sendToDeepSeek(prompt: String): String {
        val requestBody = JSONObject().apply {
            put("model", "deepseek-chat")
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("temperature", 0.7)
            put("max_tokens", 500)
        }.toString()

        val request = Request.Builder()
            .url(API_URL)
            .addHeader("Authorization", "Bearer $API_KEY")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json") // 明确要求JSON响应
            .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return try {
            val response = client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val errorBody = resp.body?.string() ?: "无错误详情"
                    throw IOException("HTTP ${resp.code}: $errorBody")
                }
                resp.body?.string()?.also {
                    // 调试用 - 打印原始响应
                    println("API原始响应: $it")
                } ?: throw IOException("空响应")
            }

            // 更健壮的JSON解析
            val json = try {
                JSONObject(response)
            } catch (e: JSONException) {
                throw IOException("无效的JSON响应: ${response.take(200)}...")
            }

            json.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?: throw IOException("响应格式不符合预期: ${json.toString(2)}")

        } catch (e: SocketTimeoutException) {
            "⚠️ 请求超时，请检查网络或稍后重试"
        } catch (e: Exception) {
            "⚠️ 错误: ${e.message?.substringBefore("\n")}"
        }
    }
}