// ConfigManager.kt
package com.xiaofan.chatai.config

import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

object ConfigManager {

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val configDir = FabricLoader.getInstance().configDir
    private val configFile: Path = configDir.resolve("deepseek_chat_config.json")

    // 配置项
    var apiUrl = "https://api.deepseek.com/chat/completions"
    var apiKey = ""
    var maxTokens = 500
    var temperature = 0.7
    var timeoutSeconds = 60
    var model = "deepseek-chat"

    init {
        loadConfig()
    }

    private fun loadConfig() {
        try {
            if (Files.notExists(configDir)) {
                Files.createDirectories(configDir)
            }

            if (Files.exists(configFile)) {
                val json = Files.readString(configFile)
                gson.fromJson(json, ConfigManager::class.java).let {
                    apiUrl = it.apiUrl
                    apiKey = it.apiKey
                    maxTokens = it.maxTokens
                    temperature = it.temperature
                    timeoutSeconds = it.timeoutSeconds
                    model = it.model
                }
            } else {
                saveConfig()
            }
        } catch (e: Exception) {
            println("Failed to load config: ${e.message}")
            saveConfig()
        }
    }

    fun saveConfig() {
        try {
            Files.writeString(configFile, gson.toJson(this))
            println("配置已保存至 $configFile")
        } catch (e: Exception) {
            println("保存配置失败: ${e.message}")
        }
    }
}