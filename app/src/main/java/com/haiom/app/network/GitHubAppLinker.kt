package com.haiom.app.network

import android.util.Base64
import com.haiom.app.model.GitHubRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

data class GitHubAppLinkResult(
    val appId: Long,
    val slug: String,
    val privateKeyPem: String,
    val ownerLogin: String,
    val installationId: Long,
    val token: String,
    val repositories: List<GitHubRepository>
)

enum class GitHubLinkStage {
    PREPARING,
    APPROVE_APP,
    CHOOSE_REPOSITORIES,
    LOADING_REPOSITORIES
}

class GitHubAppLinker(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun link(
        onOpenUrl: (String) -> Unit,
        onStage: (GitHubLinkStage) -> Unit = {}
    ): GitHubAppLinkResult = coroutineScope {
        onStage(GitHubLinkStage.PREPARING)

        val state = UUID.randomUUID().toString().replace("-", "")
        val sessionPath = UUID.randomUUID().toString().replace("-", "")
        val appSuffix = UUID.randomUUID().toString().take(8)
        val manifestCode = CompletableDeferred<String>()
        val installationUrl = CompletableDeferred<String>()
        val installationId = CompletableDeferred<Long>()

        val server = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        val baseUrl = "http://127.0.0.1:${server.localPort}"
        val prefix = "/$sessionPath"
        val manifest = buildManifest(
            name = "H AGENT Mobile $appSuffix",
            redirectUrl = "$baseUrl$prefix/manifest",
            setupUrl = "$baseUrl$prefix/installed"
        )

        val serverJob = launch(Dispatchers.IO) {
            serveLoopback(
                server = server,
                state = state,
                sessionPath = sessionPath,
                manifest = manifest,
                manifestCode = manifestCode,
                installationUrl = installationUrl,
                installationId = installationId
            )
        }

        try {
            onStage(GitHubLinkStage.APPROVE_APP)
            onOpenUrl("$baseUrl$prefix/start")

            val code = withTimeout(LINK_TIMEOUT_MS) {
                manifestCode.await()
            }
            val app = convertManifest(code)

            val installUrl =
                "https://github.com/apps/${app.slug}/installations/new?state=${enc(state)}"

            onStage(GitHubLinkStage.CHOOSE_REPOSITORIES)
            installationUrl.complete(installUrl)

            val installed = withTimeout(LINK_TIMEOUT_MS) {
                installationId.await()
            }

            onStage(GitHubLinkStage.LOADING_REPOSITORIES)
            val token = createInstallationToken(
                appId = app.appId,
                privateKeyPem = app.privateKeyPem,
                installationId = installed
            )
            val repositories = installationRepositories(token)

            GitHubAppLinkResult(
                appId = app.appId,
                slug = app.slug,
                privateKeyPem = app.privateKeyPem,
                ownerLogin = app.ownerLogin,
                installationId = installed,
                token = token,
                repositories = repositories
            )
        } finally {
            runCatching { server.close() }
            serverJob.cancel()
        }
    }

    suspend fun installationToken(
        appId: Long,
        privateKeyPem: String,
        installationId: Long
    ): String = createInstallationToken(appId, privateKeyPem, installationId)

    suspend fun installationRepositories(token: String): List<GitHubRepository> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/installation/repositories?per_page=100")
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "Bearer $token")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "H-AGENT-Android/0.11")
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("تعذر تحميل المشاريع")
            val root = json.parseToJsonElement(raw).jsonObject
            root["repositories"]?.jsonArray.orEmpty().mapNotNull { element ->
                val obj = element.jsonObject
                val fullName = obj["full_name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                GitHubRepository(
                    fullName = fullName,
                    htmlUrl = obj["html_url"]?.jsonPrimitive?.contentOrNull ?: "https://github.com/$fullName",
                    isPrivate = obj["private"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false,
                    updatedAt = obj["updated_at"]?.jsonPrimitive?.contentOrNull.orEmpty()
                )
            }
        }
    }

    private data class AppManifestResult(
        val appId: Long,
        val slug: String,
        val privateKeyPem: String,
        val ownerLogin: String
    )

    private fun buildManifest(name: String, redirectUrl: String, setupUrl: String): String {
        return buildJsonObject {
            put("name", name)
            put("url", "https://github.com/Malik05255/HAI_OM")
            put("description", "H AGENT Android coding assistant")
            put("redirect_url", redirectUrl)
            put("setup_url", setupUrl)
            put("setup_on_update", false)
            put("public", false)
            put("hook_attributes", buildJsonObject {
                put("url", "https://example.invalid/om")
                put("active", false)
            })
            put("default_permissions", buildJsonObject {
                put("metadata", "read")
                put("contents", "write")
                put("pull_requests", "write")
                put("actions", "read")
                put("workflows", "write")
            })
        }.toString()
    }

    private suspend fun convertManifest(code: String): AppManifestResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/app-manifests/__CODE__/conversions".replace("__CODE__", enc(code)))
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "H-AGENT-Android/0.11")
            .post("{}".toRequestBody(JSON))
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("تعذر إنشاء ربط GitHub: HTTP __STATUS__".replace("__STATUS__", response.code.toString()))
            val root = json.parseToJsonElement(raw).jsonObject
            AppManifestResult(
                appId = root["id"]?.jsonPrimitive?.longOrNull ?: error("تعذر قراءة GitHub App"),
                slug = root["slug"]?.jsonPrimitive?.contentOrNull ?: error("تعذر قراءة GitHub App"),
                privateKeyPem = root["pem"]?.jsonPrimitive?.contentOrNull ?: error("تعذر قراءة مفتاح GitHub App"),
                ownerLogin = root["owner"]?.jsonObject?.get("login")?.jsonPrimitive?.contentOrNull.orEmpty()
            )
        }
    }

    private suspend fun createInstallationToken(
        appId: Long,
        privateKeyPem: String,
        installationId: Long
    ): String = withContext(Dispatchers.IO) {
        val jwt = createJwt(appId, privateKeyPem)
        val request = Request.Builder()
            .url("https://api.github.com/app/installations/$installationId/access_tokens")
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "Bearer $jwt")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "H-AGENT-Android/0.11")
            .post("{}".toRequestBody(JSON))
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("تعذر اعتماد ربط GitHub")
            json.parseToJsonElement(raw).jsonObject["token"]?.jsonPrimitive?.contentOrNull
                ?: error("تعذر اعتماد ربط GitHub")
        }
    }

    private fun createJwt(appId: Long, privateKeyPem: String): String {
        val now = Instant.now().epochSecond
        val header = """{"alg":"RS256","typ":"JWT"}"""
        val payload = """{"iat":${now - 60},"exp":${now + 540},"iss":$appId}"""
        val encodedHeader = b64Url(header.toByteArray())
        val encodedPayload = b64Url(payload.toByteArray())
        val signingInput = "$encodedHeader.$encodedPayload"

        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(parsePrivateKey(privateKeyPem))
        signature.update(signingInput.toByteArray(StandardCharsets.UTF_8))
        return "$signingInput.${b64Url(signature.sign())}"
    }

    private fun parsePrivateKey(pem: String): PrivateKey {
        val clean = pem
            .replace("-----BEGIN RSA PRIVATE KEY-----", "")
            .replace("-----END RSA PRIVATE KEY-----", "")
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace(Regex("\\s+"), "")
        val raw = Base64.decode(clean, Base64.DEFAULT)
        val pkcs8 = if (pem.contains("BEGIN RSA PRIVATE KEY")) wrapPkcs1InPkcs8(raw) else raw
        return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(pkcs8))
    }

    private fun wrapPkcs1InPkcs8(pkcs1: ByteArray): ByteArray {
        val version = byteArrayOf(0x02, 0x01, 0x00)
        val rsaAlgorithm = byteArrayOf(
            0x30, 0x0D, 0x06, 0x09,
            0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(),
            0x0D, 0x01, 0x01, 0x01, 0x05, 0x00
        )
        val privateKey = der(0x04, pkcs1)
        return der(0x30, version + rsaAlgorithm + privateKey)
    }

    private fun der(tag: Int, value: ByteArray): ByteArray =
        byteArrayOf(tag.toByte()) + derLength(value.size) + value

    private fun derLength(length: Int): ByteArray {
        if (length < 128) return byteArrayOf(length.toByte())
        var value = length
        val bytes = mutableListOf<Byte>()
        while (value > 0) {
            bytes.add(0, (value and 0xFF).toByte())
            value = value ushr 8
        }
        return byteArrayOf((0x80 or bytes.size).toByte()) + bytes.toByteArray()
    }

    private suspend fun serveLoopback(
        server: ServerSocket,
        state: String,
        sessionPath: String,
        manifest: String,
        manifestCode: CompletableDeferred<String>,
        installationUrl: CompletableDeferred<String>,
        installationId: CompletableDeferred<Long>
    ) {
        val prefix = "/$sessionPath"

        while (!server.isClosed && !installationId.isCompleted) {
            val socket = try {
                server.accept()
            } catch (_: Throwable) {
                break
            }

            socket.use { clientSocket ->
                clientSocket.soTimeout = 5_000
                val reader = BufferedReader(
                    InputStreamReader(clientSocket.getInputStream(), Charsets.UTF_8)
                )
                val firstLine = reader.readLine().orEmpty().take(8_192)
                if (!firstLine.startsWith("GET ")) return@use

                var line = reader.readLine()
                var headerLines = 0
                while (!line.isNullOrBlank() && headerLines < 100) {
                    headerLines++
                    line = reader.readLine()
                }

                val target = firstLine.split(' ').getOrNull(1).orEmpty()
                val uri = runCatching {
                    java.net.URI("http://127.0.0.1$target")
                }.getOrNull()
                val path = uri?.path.orEmpty()
                val params = parseQuery(uri?.rawQuery.orEmpty())

                val body = when (path) {
                    "$prefix/start" -> autoSubmitPage(state, manifest)

                    "$prefix/manifest" -> {
                        val returnedState = params["state"].orEmpty()
                        val code = params["code"].orEmpty()

                        if (returnedState == state && code.isNotBlank()) {
                            manifestCode.complete(code)
                            val nextUrl = withTimeout(CONVERSION_TIMEOUT_MS) {
                                installationUrl.await()
                            }
                            redirectPage(nextUrl)
                        } else {
                            simplePage("تعذر التحقق من الربط")
                        }
                    }

                    "$prefix/installed" -> {
                        val id = params["installation_id"]?.toLongOrNull()
                        val returnedState = params["state"].orEmpty()

                        if (id != null && returnedState == state) {
                            installationId.complete(id)
                            returnToAppPage()
                        } else {
                            simplePage("تعذر إكمال الربط")
                        }
                    }

                    else -> simplePage("H AGENT")
                }

                val bytes = body.toByteArray(Charsets.UTF_8)
                val output = clientSocket.getOutputStream()
                output.write(
                    (
                        "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/html; charset=utf-8\r\n" +
                            "Cache-Control: no-store\r\n" +
                            "Content-Length: ${bytes.size}\r\n" +
                            "Connection: close\r\n\r\n"
                    ).toByteArray(Charsets.UTF_8)
                )
                output.write(bytes)
                output.flush()
            }
        }
    }

    private fun autoSubmitPage(state: String, manifest: String): String {
        val action = "https://github.com/settings/apps/new?state=${enc(state)}"
        val safeManifest = htmlEscape(manifest)
        return """
            <!doctype html>
            <html lang="ar" dir="rtl">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width,initial-scale=1">
              <title>H AGENT</title>
            </head>
            <body style="font-family:sans-serif;padding:24px">
              <form id="f" action="$action" method="post">
                <input type="hidden" name="manifest" value="$safeManifest">
              </form>
              <p>جاري فتح GitHub…</p>
              <script>document.getElementById('f').submit();</script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun redirectPage(url: String): String {
        val safeUrl = htmlEscape(url)
        return """
            <!doctype html>
            <html lang="ar" dir="rtl">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width,initial-scale=1">
              <meta http-equiv="refresh" content="0;url=$safeUrl">
              <title>H AGENT</title>
            </head>
            <body style="font-family:sans-serif;padding:24px">
              <p>جاري إكمال الربط…</p>
              <p><a href="$safeUrl">متابعة</a></p>
            </body>
            </html>
        """.trimIndent()
    }

    private fun returnToAppPage(): String = """
        <!doctype html>
        <html lang="ar" dir="rtl">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width,initial-scale=1">
          <title>H AGENT</title>
        </head>
        <body style="font-family:sans-serif;padding:24px">
          <h3>تم ربط GitHub</h3>
          <p>جاري الرجوع للتطبيق…</p>
          <p><a href="om://github-connected">العودة للتطبيق</a></p>
          <script>
            setTimeout(function () {
              window.location.href = 'om://github-connected';
            }, 350);
          </script>
        </body>
        </html>
    """.trimIndent()

    private fun simplePage(message: String): String = """
        <!doctype html>
        <html lang="ar" dir="rtl">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width,initial-scale=1">
          <title>H AGENT</title>
        </head>
        <body style="font-family:sans-serif;padding:24px"><h3>$message</h3></body>
        </html>
    """.trimIndent()

    private fun htmlEscape(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

    private fun parseQuery(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return raw.split('&').mapNotNull { part ->
            val pieces = part.split('=', limit = 2)
            val key = URLDecoder.decode(pieces[0], "UTF-8")
            val value = URLDecoder.decode(pieces.getOrElse(1) { "" }, "UTF-8")
            key to value
        }.toMap()
    }

    private fun b64Url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private fun enc(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    companion object {
        private const val LINK_TIMEOUT_MS = 15 * 60 * 1000L
        private const val CONVERSION_TIMEOUT_MS = 45 * 1000L
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
