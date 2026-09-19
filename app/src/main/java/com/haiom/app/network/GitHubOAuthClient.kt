package com.haiom.app.network

import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.TimeUnit

data class GitHubOAuthSession(
    val state: String,
    val verifier: String,
    val authorizationUrl: String
)

class GitHubOAuthClient(
    private val clientId: String,
    private val clientSecret: String,
    private val redirectUri: String = REDIRECT_URI,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json { ignoreUnknownKeys = true }

    val configured: Boolean
        get() = clientId.isNotBlank() && clientSecret.isNotBlank()

    fun createSession(): GitHubOAuthSession {
        check(configured) { "GitHub OAuth غير مهيأ في هذا الإصدار" }

        val state = UUID.randomUUID().toString().replace("-", "")
        val verifierBytes = ByteArray(64).also {
            SecureRandom().nextBytes(it)
        }
        val verifier = base64Url(verifierBytes)
        val challenge = base64Url(
            MessageDigest.getInstance("SHA-256")
                .digest(verifier.toByteArray(Charsets.US_ASCII))
        )

        val url = Uri.parse(AUTHORIZE_URL)
            .buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("scope", "repo workflow read:user")
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("allow_signup", "false")
            .build()
            .toString()

        return GitHubOAuthSession(
            state = state,
            verifier = verifier,
            authorizationUrl = url
        )
    }

    fun parseCallback(
        callbackUrl: String
    ): Pair<String, String> {
        val uri = Uri.parse(callbackUrl)

        if (
            uri.scheme != "om" ||
            uri.host != "github-auth"
        ) {
            error("رابط الرجوع من GitHub غير صالح")
        }

        uri.getQueryParameter("error")?.let { errorCode ->
            val description = uri.getQueryParameter("error_description")
                .orEmpty()
                .ifBlank { errorCode }

            error(
                if (errorCode == "access_denied") {
                    "تم إلغاء تصريح GitHub"
                } else {
                    description
                }
            )
        }

        val code = uri.getQueryParameter("code")
            ?.takeIf { it.isNotBlank() }
            ?: error("GitHub لم يُرجع رمز الاعتماد")

        val state = uri.getQueryParameter("state")
            ?.takeIf { it.isNotBlank() }
            ?: error("تعذر التحقق من جلسة GitHub")

        return code to state
    }

    suspend fun exchangeCode(
        code: String,
        verifier: String
    ): String = withContext(Dispatchers.IO) {
        check(configured) { "GitHub OAuth غير مهيأ في هذا الإصدار" }

        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("code", code)
            .add("redirect_uri", redirectUri)
            .add("code_verifier", verifier)
            .build()

        val request = Request.Builder()
            .url(ACCESS_TOKEN_URL)
            .header("Accept", "application/json")
            .header("User-Agent", "OM-Android/0.9")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                error("تعذر اعتماد GitHub")
            }

            val root = json.parseToJsonElement(raw).jsonObject

            root["error"]?.jsonPrimitive?.contentOrNull?.let { errorCode ->
                val description = root["error_description"]
                    ?.jsonPrimitive
                    ?.contentOrNull
                    .orEmpty()

                error(
                    description.ifBlank {
                        when (errorCode) {
                            "bad_verification_code" ->
                                "انتهت صلاحية محاولة الربط، حاول مرة أخرى"
                            else ->
                                "GitHub رفض عملية الربط"
                        }
                    }
                )
            }

            root["access_token"]
                ?.jsonPrimitive
                ?.contentOrNull
                ?.takeIf { it.isNotBlank() }
                ?: error("GitHub لم يُرجع رمز وصول")
        }
    }

    private fun base64Url(
        bytes: ByteArray
    ): String =
        Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or
                Base64.NO_WRAP or
                Base64.NO_PADDING
        )

    companion object {
        const val REDIRECT_URI = "om://github-auth"
        private const val AUTHORIZE_URL =
            "https://github.com/login/oauth/authorize"
        private const val ACCESS_TOKEN_URL =
            "https://github.com/login/oauth/access_token"
    }
}
