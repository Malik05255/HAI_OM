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

data class GitHubDeviceAuthorization(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresInSeconds: Int,
    val intervalSeconds: Int
)

class GitHubOAuthClient(
    private val clientId: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json { ignoreUnknownKeys = true }

    val configured: Boolean
        get() = clientId.isNotBlank()

    suspend fun requestDeviceAuthorization(): GitHubDeviceAuthorization =
        withContext(Dispatchers.IO) {
            check(configured) { "ربط GitHub غير مهيأ في هذا الإصدار" }

            val body = FormBody.Builder()
                .add("client_id", clientId)
                .add("scope", "repo workflow read:user")
                .build()

            val request = Request.Builder()
                .url(DEVICE_CODE_URL)
                .header("Accept", "application/json")
                .header("User-Agent", "H-AGENT-Android/0.11")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    error("تعذر طلب كود GitHub")
                }

                val root = json.parseToJsonElement(raw).jsonObject

                root["error"]?.jsonPrimitive?.contentOrNull?.let { errorCode ->
                    error(deviceError(errorCode))
                }

                GitHubDeviceAuthorization(
                    deviceCode = root["device_code"]
                        ?.jsonPrimitive
                        ?.contentOrNull
                        ?.takeIf { it.isNotBlank() }
                        ?: error("GitHub لم يُرجع كود الجهاز"),
                    userCode = root["user_code"]
                        ?.jsonPrimitive
                        ?.contentOrNull
                        ?.takeIf { it.isNotBlank() }
                        ?: error("GitHub لم يُرجع كود الربط"),
                    verificationUri = root["verification_uri"]
                        ?.jsonPrimitive
                        ?.contentOrNull
                        ?.takeIf { it.isNotBlank() }
                        ?: "https://github.com/login/device",
                    expiresInSeconds = root["expires_in"]
                        ?.jsonPrimitive
                        ?.intOrNull
                        ?: 900,
                    intervalSeconds = root["interval"]
                        ?.jsonPrimitive
                        ?.intOrNull
                        ?: 5
                )
            }
        }

    suspend fun waitForAuthorization(
        authorization: GitHubDeviceAuthorization
    ): String {
        var intervalSeconds = authorization.intervalSeconds.coerceAtLeast(5)
        val deadline =
            System.currentTimeMillis() +
                authorization.expiresInSeconds.coerceAtLeast(60) * 1000L

        while (System.currentTimeMillis() < deadline) {
            delay(intervalSeconds * 1000L)

            val result = pollOnce(
                deviceCode = authorization.deviceCode
            )

            when (result) {
                is PollResult.Success ->
                    return result.accessToken

                PollResult.Pending ->
                    Unit

                PollResult.SlowDown ->
                    intervalSeconds += 5

                is PollResult.Failure ->
                    error(result.message)
            }
        }

        error("انتهت صلاحية كود GitHub، حاول مرة أخرى")
    }

    private suspend fun pollOnce(
        deviceCode: String
    ): PollResult = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("device_code", deviceCode)
            .add(
                "grant_type",
                "urn:ietf:params:oauth:grant-type:device_code"
            )
            .build()

        val request = Request.Builder()
            .url(ACCESS_TOKEN_URL)
            .header("Accept", "application/json")
            .header("User-Agent", "H-AGENT-Android/0.11")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext PollResult.Failure(
                    "تعذر التحقق من موافقة GitHub"
                )
            }

            val root = json.parseToJsonElement(raw).jsonObject

            root["access_token"]
                ?.jsonPrimitive
                ?.contentOrNull
                ?.takeIf { it.isNotBlank() }
                ?.let { token ->
                    return@withContext PollResult.Success(token)
                }

            when (
                root["error"]
                    ?.jsonPrimitive
                    ?.contentOrNull
            ) {
                "authorization_pending" ->
                    PollResult.Pending

                "slow_down" ->
                    PollResult.SlowDown

                "expired_token" ->
                    PollResult.Failure(
                        "انتهت صلاحية كود GitHub، حاول مرة أخرى"
                    )

                "access_denied" ->
                    PollResult.Failure(
                        "تم رفض صلاحية GitHub"
                    )

                "device_flow_disabled" ->
                    PollResult.Failure(
                        "يجب تفعيل Device Flow لتطبيق H AGENT في GitHub"
                    )

                "incorrect_client_credentials" ->
                    PollResult.Failure(
                        "Client ID الخاص بـ GitHub غير صحيح"
                    )

                else ->
                    PollResult.Failure(
                        "تعذر إكمال ربط GitHub"
                    )
            }
        }
    }

    private fun deviceError(
        errorCode: String
    ): String =
        when (errorCode) {
            "device_flow_disabled" ->
                "يجب تفعيل Device Flow لتطبيق H AGENT في GitHub"

            "incorrect_client_credentials" ->
                "Client ID الخاص بـ GitHub غير صحيح"

            else ->
                "تعذر بدء ربط GitHub"
        }

    private sealed interface PollResult {
        data class Success(
            val accessToken: String
        ) : PollResult

        data class Failure(
            val message: String
        ) : PollResult

        data object Pending : PollResult
        data object SlowDown : PollResult
    }

    companion object {
        private const val DEVICE_CODE_URL =
            "https://github.com/login/device/code"
        private const val ACCESS_TOKEN_URL =
            "https://github.com/login/oauth/access_token"
    }
}
