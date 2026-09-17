package com.haiom.app.network

import com.haiom.app.model.FreeProviderRanking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * HAI OM has exactly one AI integration: OmniRoute.
 * Provider discovery, free-tier filtering, model ranking, fallback, quota handling,
 * and token compression remain OmniRoute responsibilities.
 */
class OmniRouteClient(
    baseUrl: String,
    private val apiKey: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val root = normalizeRoot(baseUrl)

    data class Completion(val text: String, val modelLabel: String)

    suspend fun verifyAndConfigure(onEvent: (String) -> Unit = {}) = withContext(Dispatchers.IO) {
        onEvent("التحقق من OmniRoute")
        putJson(
            "/api/settings",
            buildJsonObject {
                put("freeAccessPolicy", "strict")
                put("excludeTosAvoid", false)
            },
            "تعذر تفعيل Zero-Cost الصارم. استخدم OmniRoute محليًا أو مفتاحًا يملك صلاحية إدارة الإعدادات."
        )
        onEvent("✓ Zero-Cost strict مفعل")

        val stackedEnabled = runCatching {
            putJson(
                "/api/settings/compression",
                compressionBody(mode = "stacked", includeCaveman = true),
                "تعذر تفعيل ضغط RTK → Caveman في OmniRoute."
            )
        }.isSuccess

        if (stackedEnabled) {
            onEvent("✓ ضغط RTK → Caveman مفعل")
        } else {
            putJson(
                "/api/settings/compression",
                compressionBody(mode = "rtk", includeCaveman = false),
                "تعذر تفعيل ضغط RTK في OmniRoute."
            )
            onEvent("✓ RTK مفعل (Caveman غير متاح في هذا الخادم)")
        }

        val rankings = runCatching { fetchCodingRankings() }.getOrDefault(emptyList())
        if (rankings.isEmpty()) {
            // Ranking sync is advisory; OmniRoute intentionally remains usable when its
            // Arena data is temporarily unavailable. Never block coding for this alone.
            onEvent("تنبيه: ترتيب Coding غير متاح الآن؛ auto/coding ما زال يعمل")
        } else {
            onEvent("✓ ${rankings.size} مزودًا مجانيًا مصنفًا للبرمجة متاح")
        }
    }

    private fun compressionBody(mode: String, includeCaveman: Boolean): JsonObject = buildJsonObject {
        put("defaultMode", mode)
        put("autoTriggerMode", mode)
        put("autoTriggerTokens", 32_000)
        if (includeCaveman) {
            put("stackedPipeline", buildJsonArray {
                add(buildJsonObject { put("engine", "rtk"); put("intensity", "standard") })
                add(buildJsonObject { put("engine", "caveman"); put("intensity", "full") })
            })
        }
        put("rtkConfig", buildJsonObject {
            put("enabled", true)
            put("intensity", "standard")
            put("applyToToolResults", true)
            put("applyToCodeBlocks", false)
            put("applyToAssistantMessages", false)
            put("maxLinesPerResult", 120)
            put("maxCharsPerResult", 12_000)
            put("deduplicateThreshold", 3)
            put("rawOutputRetention", "never")
        })
    }

    suspend fun fetchCodingRankings(limit: Int = 50): List<FreeProviderRanking> = withContext(Dispatchers.IO) {
        val safeLimit = limit.coerceIn(1, 100)
        val response = execute("GET", "/api/free-provider-rankings?category=coding&limit=$safeLimit")
        response.use {
            val raw = it.body?.string().orEmpty()
            if (!it.isSuccessful) error("فشل جلب ترتيب النماذج المجانية من OmniRoute: HTTP ${it.code}")
            val parsed = json.parseToJsonElement(raw).jsonObject
            parsed["rankings"]?.jsonArray.orEmpty().mapNotNull { item ->
                val obj = item.jsonObject
                val top = obj["topModel"]?.jsonObject ?: return@mapNotNull null
                val providerName = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val modelId = top["modelId"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                FreeProviderRanking(
                    providerId = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    providerName = providerName,
                    freeType = obj["category"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    modelId = modelId,
                    modelName = top["modelName"]?.jsonPrimitive?.contentOrNull ?: modelId,
                    score = top["score"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    confidence = top["confidence"]?.jsonPrimitive?.contentOrNull.orEmpty()
                )
            }.sortedByDescending { it.score }
        }
    }

    suspend fun complete(system: String, user: String, toolContext: String? = null): Completion = withContext(Dispatchers.IO) {
        val messages = buildJsonArray {
            add(buildJsonObject { put("role", "system"); put("content", system) })
            add(buildJsonObject { put("role", "user"); put("content", user) })
            if (!toolContext.isNullOrBlank()) {
                add(buildJsonObject { put("role", "tool"); put("content", toolContext) })
            }
        }
        val body = buildJsonObject {
            put("model", CODING_ROUTE)
            put("temperature", 0.1)
            put("max_tokens", 12_000)
            put("messages", messages)
        }
        execute("POST", "/v1/chat/completions", body).use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("OmniRoute coding request failed HTTP ${response.code}: ${raw.take(700)}")
            }
            val parsed = json.parseToJsonElement(raw).jsonObject
            val text = parsed["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
                ?.trim().orEmpty()
            require(text.isNotBlank()) { "OmniRoute أعاد استجابة فارغة" }
            val servedModel = parsed["model"]?.jsonPrimitive?.contentOrNull ?: CODING_ROUTE
            Completion(text, servedModel)
        }
    }

    private fun putJson(path: String, body: JsonObject, failureMessage: String) {
        execute("PUT", path, body).use { response ->
            if (!response.isSuccessful) {
                val raw = response.body?.string().orEmpty()
                error("$failureMessage HTTP ${response.code}: ${raw.take(500)}")
            }
        }
    }

    private fun execute(method: String, path: String, body: JsonObject? = null): okhttp3.Response {
        val builder = Request.Builder()
            .url("$root$path")
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("User-Agent", "HAI-OM-Android/0.2")
        if (apiKey.isNotBlank()) builder.header("Authorization", "Bearer $apiKey")
        val requestBody = body?.toString()?.toRequestBody(JSON)
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(requestBody ?: "{}".toRequestBody(JSON))
            "PUT" -> builder.put(requestBody ?: "{}".toRequestBody(JSON))
            else -> error("Unsupported HTTP method: $method")
        }
        return client.newCall(builder.build()).execute()
    }

    companion object {
        const val CODING_ROUTE = "auto/coding"
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun normalizeRoot(value: String): String {
            var clean = value.trim().trimEnd('/')
            require(clean.isNotBlank()) { "أدخل رابط OmniRoute" }
            if (clean.endsWith("/v1", ignoreCase = true)) clean = clean.dropLast(3).trimEnd('/')
            val uri = runCatching { URI(clean) }.getOrElse { throw IllegalArgumentException("رابط OmniRoute غير صحيح") }
            require(uri.scheme == "http" || uri.scheme == "https") { "OmniRoute يجب أن يستخدم http أو https" }
            require(!uri.host.isNullOrBlank() && uri.userInfo == null) { "رابط OmniRoute غير آمن أو غير صحيح" }
            if (uri.scheme == "http") {
                require(isPrivateOrLoopback(uri.host)) {
                    "HTTP مسموح فقط لـ OmniRoute المحلي أو داخل الشبكة الخاصة. استخدم HTTPS للسيرفر العام."
                }
            }
            return clean
        }

        private fun isPrivateOrLoopback(host: String): Boolean {
            val h = host.lowercase()
            if (h == "localhost" || h == "::1" || h.endsWith(".local")) return true
            if (h.startsWith("127.") || h.startsWith("10.") || h.startsWith("192.168.")) return true
            val parts = h.split('.')
            if (parts.size == 4 && parts[0] == "172") {
                val second = parts[1].toIntOrNull()
                if (second != null && second in 16..31) return true
            }
            return false
        }
    }
}
