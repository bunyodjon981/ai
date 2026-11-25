package com.example.data.engine

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.example.BuildConfig
import com.example.data.model.ApiConfig
import org.json.JSONObject
import java.io.File

class ApiConfigManager(private val context: Context) {

    val apiJsonFile: File
        get() = File(context.filesDir, "api.json")

    init {
        ensureInitialized()
    }

    private fun defaultBuildConfigKey(): String {
        val raw = runCatching { BuildConfig.GROQ_API_KEY }.getOrDefault("")
        return if (raw.isNotBlank() && raw != "MY_GROQ_API_KEY") raw else ""
    }

    fun ensureInitialized() {
        if (!apiJsonFile.exists()) {
            val initial = ApiConfig(
                provider = "groq",
                baseUrl = "https://api.groq.com/openai/v1/",
                model = "llama-3.3-70b-versatile",
                apiKey = defaultBuildConfigKey(),
                temperature = 0.35,
                maxTokens = 4096
            )
            saveConfig(initial)
        }
    }

    fun loadConfig(): ApiConfig {
        ensureInitialized()
        return runCatching {
            val json = JSONObject(apiJsonFile.readText(Charsets.UTF_8))
            val keyFromJson = json.optString("api_key", "").trim()
            val effectiveKey = keyFromJson.ifEmpty { defaultBuildConfigKey() }
            ApiConfig(
                provider = json.optString("provider", "groq").ifBlank { "groq" },
                baseUrl = json.optString("base_url", "https://api.groq.com/openai/v1/")
                    .ifBlank { "https://api.groq.com/openai/v1/" },
                model = json.optString("model", "llama-3.3-70b-versatile")
                    .ifBlank { "llama-3.3-70b-versatile" },
                apiKey = effectiveKey,
                temperature = json.optDouble("temperature", 0.35).coerceIn(0.0, 2.0),
                maxTokens = json.optInt("max_tokens", 4096).coerceIn(256, 32768)
            )
        }.getOrElse {
            ApiConfig(apiKey = defaultBuildConfigKey())
        }
    }

    fun readRawJson(): String {
        ensureInitialized()
        return apiJsonFile.readText(Charsets.UTF_8)
    }

    fun saveConfig(config: ApiConfig) {
        val json = JSONObject().apply {
            put("provider", config.provider.trim().ifBlank { "groq" })
            put("base_url", config.baseUrl.trim().ifBlank { "https://api.groq.com/openai/v1/" })
            put("model", config.model.trim().ifBlank { "llama-3.3-70b-versatile" })
            put("api_key", config.apiKey.trim())
            put("temperature", config.temperature)
            put("max_tokens", config.maxTokens)
            put("note", "Groq AI konfiguratsiyasi. API kalitni shu api.json faylidan o'zgartirishingiz mumkin.")
        }
        apiJsonFile.writeText(json.toString(2), Charsets.UTF_8)
    }

    fun saveRawJson(rawJson: String): Result<ApiConfig> {
        return runCatching {
            val parsed = JSONObject(rawJson)
            val config = ApiConfig(
                provider = parsed.optString("provider", "groq").ifBlank { "groq" },
                baseUrl = parsed.optString("base_url", "https://api.groq.com/openai/v1/"),
                model = parsed.optString("model", "llama-3.3-70b-versatile"),
                apiKey = parsed.optString("api_key", "").trim(),
                temperature = parsed.optDouble("temperature", 0.35).coerceIn(0.0, 2.0),
                maxTokens = parsed.optInt("max_tokens", 4096).coerceIn(256, 32768)
            )
            apiJsonFile.writeText(parsed.toString(2), Charsets.UTF_8)
            config
        }
    }

    fun exportToUri(contentResolver: ContentResolver, uri: Uri): Result<Unit> = runCatching {
        contentResolver.openOutputStream(uri)?.use { out ->
            out.write(readRawJson().toByteArray(Charsets.UTF_8))
        } ?: error("Faylga yozib bo'lmadi")
    }

    fun importFromUri(contentResolver: ContentResolver, uri: Uri): Result<ApiConfig> = runCatching {
        val raw = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
            it.readText()
        } ?: error("api.json o'qib bo'lmadi")
        saveRawJson(raw).getOrThrow()
    }
}
