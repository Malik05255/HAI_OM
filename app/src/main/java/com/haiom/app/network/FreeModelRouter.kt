package com.haiom.app.network

import com.haiom.app.data.FreeModelCatalog
import com.haiom.app.model.FreeCodingModel
import com.haiom.app.security.SecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class FreeModelRouter(
    private val secrets: SecretStore,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    private val blockedUntil = ConcurrentHashMap<String, Long>()

    data class Completion(val text: String, val model: FreeCodingModel)

    suspend fun complete(system: String, user: String): Completion = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val failures = mutableListOf<String>()

        for (model in FreeModelCatalog.models) {
            FreeModelCatalog.requireAllowed(model)
            if ((blockedUntil[model.id] ?: 0L) > now) continue
            try {
                val body = buildJsonObject {
                    put("model", model.id)
                    put("temperature", 0.15)
                    put("max_tokens", 12000)
                    put("messages", buildJsonArray {
                        add(buildJsonObject { put("role", "system"); put("content", system) })
                        add(buildJsonObject { put("role", "user"); put("content", user) })
                    })
                }.toString()

                val requestBuilder = Request.Builder()
                    .url(model.endpoint)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "HAI-OM-Android/0.1")
                    .post(body.toRequestBody(JSON))

                val optionalKey = secrets.pollinationsKey()
                if (optionalKey.isNotBlank()) requestBuilder.header("Authorization", "Bearer $optionalKey")

                client.newCall(requestBuilder.build()).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        failures += "${model.id}: HTTP ${response.code}"
                        if (response.code == 429 || response.code >= 500) {
                            blockedUntil[model.id] = System.currentTimeMillis() + COOLDOWN_MS
                        }
                        return@use
                    }
                    val root = json.parseToJsonElement(raw).jsonObject
                    val text = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                        ?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
                        ?.trim().orEmpty()
                    if (text.isBlank()) {
                        failures += "${model.id}: empty response"
                        return@use
                    }
                    return@withContext Completion(text, model)
                }
            } catch (t: Throwable) {
                failures += "${model.id}: ${t.message ?: t.javaClass.simpleName}"
                blockedUntil[model.id] = System.currentTimeMillis() + COOLDOWN_MS
            }
        }
        error("كل النماذج المجانية غير متاحة حاليًا. ${failures.takeLast(4).joinToString(" | ")}")
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private const val COOLDOWN_MS = 5 * 60 * 1000L
    }
}
