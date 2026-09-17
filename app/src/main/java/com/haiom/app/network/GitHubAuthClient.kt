package com.haiom.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class GitHubDeviceCode(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresIn: Int,
    val interval: Int
)

class GitHubAuthClient(
    private val clientId: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun requestDeviceCode(): GitHubDeviceCode = withContext(Dispatchers.IO) {
        require(clientId.isNotBlank()) { "ربط GitHub غير مفعّل في هذه النسخة" }

        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("scope", "repo workflow read:user")
            .build()

        val request = Request.Builder()
            .url("https://github.com/login/device/code")
            .header("Accept", "application/json")
            .header("User-Agent", "HAI-OM-Android/0.2")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("تعذر بدء ربط GitHub")
            val obj = json.parseToJsonElement(raw).jsonObject
            GitHubDeviceCode(
                deviceCode = obj["device_code"]?.jsonPrimitive?.contentOrNull
                    ?: error("تعذر بدء ربط GitHub"),
                userCode = obj["user_code"]?.jsonPrimitive?.contentOrNull
                    ?: error("تعذر بدء ربط GitHub"),
                verificationUri = obj["verification_uri"]?.jsonPrimitive?.contentOrNull
                    ?: "https://github.com/login/device",
                expiresIn = obj["expires_in"]?.jsonPrimitive?.intOrNull ?: 900,
                interval = obj["interval"]?.jsonPrimitive?.intOrNull ?: 5
            )
        }
    }

    suspend fun waitForToken(code: GitHubDeviceCode): String {
        var intervalSeconds = code.interval.coerceAtLeast(5)
        val deadline = System.currentTimeMillis() + code.expiresIn.coerceAtLeast(60) * 1000L

        while (System.currentTimeMillis() < deadline) {
            delay(intervalSeconds * 1000L)

            val result = poll(code.deviceCode)
            result.token?.let { return it }

            when (result.error) {
                "authorization_pending", null -> Unit
                "slow_down" -> intervalSeconds += 5
                "access_denied" -> error("تم إلغاء الربط")
                "expired_token" -> error("انتهت مهلة الربط، حاول مرة ثانية")
                "device_flow_disabled" -> error("ربط GitHub غير مفعّل في هذه النسخة")
                else -> error("تعذر إكمال ربط GitHub")
            }
        }

        error("انتهت مهلة الربط، حاول مرة ثانية")
    }

    private suspend fun poll(deviceCode: String): PollResult = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("device_code", deviceCode)
            .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
            .build()

        val request = Request.Builder()
            .url("https://github.com/login/oauth/access_token")
            .header("Accept", "application/json")
            .header("User-Agent", "HAI-OM-Android/0.2")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("تعذر إكمال ربط GitHub")
            val obj = json.parseToJsonElement(raw).jsonObject
            PollResult(
                token = obj["access_token"]?.jsonPrimitive?.contentOrNull,
                error = obj["error"]?.jsonPrimitive?.contentOrNull
            )
        }
    }

    private data class PollResult(
        val token: String?,
        val error: String?
    )
}
