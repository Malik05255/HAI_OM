package com.haiom.app.network

import android.util.Base64
import com.haiom.app.model.CiResult
import com.haiom.app.model.CiState
import com.haiom.app.model.EditBatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
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
import java.io.ByteArrayInputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

class GitHubClient(
    private val token: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {
    private val json = Json { ignoreUnknownKeys = true }

    data class RepoRef(val owner: String, val repo: String)
    data class FileEntry(val path: String, val sha: String, val size: Long)
    data class FileContent(val text: String, val sha: String)

    fun parseRepository(url: String): RepoRef {
        val clean = url.trim().removeSuffix("/").removeSuffix(".git")
        val match = Regex("(?:https?://)?github\\.com/([^/]+)/([^/]+)", RegexOption.IGNORE_CASE).find(clean)
            ?: error("رابط GitHub غير صحيح")
        return RepoRef(match.groupValues[1], match.groupValues[2])
    }

    suspend fun defaultBranch(repo: RepoRef): String {
        val root = getJson("/repos/${repo.owner}/${repo.repo}")
        return root["default_branch"]?.jsonPrimitive?.content ?: "main"
    }

    suspend fun headSha(repo: RepoRef, branch: String): String {
        val root = getJson("/repos/${repo.owner}/${repo.repo}/git/ref/heads/${enc(branch)}")
        return root["object"]!!.jsonObject["sha"]!!.jsonPrimitive.content
    }

    suspend fun createBranch(repo: RepoRef, baseBranch: String, newBranch: String) {
        val sha = headSha(repo, baseBranch)
        postJson(
            "/repos/${repo.owner}/${repo.repo}/git/refs",
            buildJsonObject { put("ref", "refs/heads/$newBranch"); put("sha", sha) }
        )
    }

    suspend fun listTextFiles(repo: RepoRef, branch: String): List<FileEntry> {
        val root = getJson("/repos/${repo.owner}/${repo.repo}/git/trees/${enc(branch)}?recursive=1")
        return root["tree"]?.jsonArray.orEmpty().mapNotNull { node ->
            val obj = node.jsonObject
            if (obj["type"]?.jsonPrimitive?.content != "blob") return@mapNotNull null
            val path = obj["path"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val size = obj["size"]?.jsonPrimitive?.longOrNull ?: 0L
            if (!isTextCandidate(path, size)) return@mapNotNull null
            FileEntry(path, obj["sha"]?.jsonPrimitive?.content.orEmpty(), size)
        }
    }

    suspend fun readFile(repo: RepoRef, branch: String, path: String): FileContent? {
        validatePath(path)
        val response = request("GET", "/repos/${repo.owner}/${repo.repo}/contents/${pathEnc(path)}?ref=${enc(branch)}", allow404 = true)
        if (response.code == 404) { response.close(); return null }
        response.use {
            if (!it.isSuccessful) error("GitHub read failed ${it.code}: ${it.body?.string().orEmpty().take(500)}")
            val obj = json.parseToJsonElement(it.body?.string().orEmpty()).jsonObject
            val encoded = obj["content"]?.jsonPrimitive?.content.orEmpty().replace("\n", "")
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            return FileContent(bytes.toString(Charsets.UTF_8), obj["sha"]!!.jsonPrimitive.content)
        }
    }

    suspend fun buildContext(repo: RepoRef, branch: String, maxChars: Int = 85_000): String {
        val files = listTextFiles(repo, branch)
            .sortedWith(compareBy<FileEntry> { priority(it.path) }.thenBy { it.path })
            .take(32)
        val out = StringBuilder()
        for (entry in files) {
            if (out.length >= maxChars) break
            val file = readFile(repo, branch, entry.path) ?: continue
            val remaining = maxChars - out.length
            if (remaining <= 300) break
            out.append("\n\n===== ${entry.path} =====\n")
            out.append(redactSecrets(file.text).take(remaining - 80))
        }
        return out.toString()
    }

    suspend fun applyBatch(repo: RepoRef, branch: String, batch: EditBatch, taskId: String, onEvent: (String) -> Unit) {
        require(batch.files.isNotEmpty()) { "لا توجد ملفات للتعديل" }
        require(batch.files.size <= 20) { "رفض تعديل أكثر من 20 ملفًا في دفعة واحدة" }

        val head = headSha(repo, branch)
        val treeRoot = getJson("/repos/${repo.owner}/${repo.repo}/git/trees/${enc(branch)}?recursive=1")
        val baseTree = treeRoot["sha"]?.jsonPrimitive?.content ?: error("تعذر قراءة Git tree")
        val existingModes = treeRoot["tree"]?.jsonArray.orEmpty().mapNotNull { node ->
            val obj = node.jsonObject
            val path = obj["path"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val mode = obj["mode"]?.jsonPrimitive?.content ?: return@mapNotNull null
            path to mode
        }.toMap()

        val entries = mutableListOf<JsonObject>()
        for (edit in batch.files) {
            validatePath(edit.path)
            if (edit.delete) {
                if (edit.path !in existingModes) continue
                entries += buildJsonObject {
                    put("path", edit.path)
                    put("mode", existingModes[edit.path] ?: "100644")
                    put("type", "blob")
                    put("sha", JsonNull)
                }
                onEvent("حذف ${edit.path}")
            } else {
                require(edit.content.length <= 450_000) { "الملف ${edit.path} كبير جدًا للتعديل التلقائي" }
                val blob = postJson(
                    "/repos/${repo.owner}/${repo.repo}/git/blobs",
                    buildJsonObject {
                        put("content", edit.content)
                        put("encoding", "utf-8")
                    }
                )
                val blobSha = blob["sha"]?.jsonPrimitive?.content ?: error("تعذر إنشاء blob لـ ${edit.path}")
                entries += buildJsonObject {
                    put("path", edit.path)
                    put("mode", existingModes[edit.path] ?: "100644")
                    put("type", "blob")
                    put("sha", blobSha)
                }
                onEvent("تحديث ${edit.path}")
            }
        }
        require(entries.isNotEmpty()) { "لم تنتج الدفعة أي تغييرات قابلة للتطبيق" }

        val newTree = postJson(
            "/repos/${repo.owner}/${repo.repo}/git/trees",
            buildJsonObject {
                put("base_tree", baseTree)
                put("tree", JsonArray(entries))
            }
        )["sha"]?.jsonPrimitive?.content ?: error("تعذر إنشاء Git tree جديد")

        val commit = postJson(
            "/repos/${repo.owner}/${repo.repo}/git/commits",
            buildJsonObject {
                put("message", "agent($taskId): ${batch.summary.take(120)}")
                put("tree", newTree)
                put("parents", buildJsonArray { add(JsonPrimitive(head)) })
            }
        )["sha"]?.jsonPrimitive?.content ?: error("تعذر إنشاء commit للمهمة")

        patchJson(
            "/repos/${repo.owner}/${repo.repo}/git/refs/heads/${enc(branch)}",
            buildJsonObject {
                put("sha", commit)
                put("force", false)
            }
        )
        onEvent("Commit واحد للمهمة: ${commit.take(8)}")
    }

    suspend fun waitForCi(
        repo: RepoRef,
        branch: String,
        headSha: String,
        onEvent: (String) -> Unit,
        workflowName: String? = null
    ): CiResult {
        repeat(45) { attempt ->
            val root = getJson("/repos/${repo.owner}/${repo.repo}/actions/runs?branch=${enc(branch)}&per_page=50")
            val matching = root["workflow_runs"]?.jsonArray.orEmpty()
                .map { it.jsonObject }
                .filter { run ->
                    run["head_sha"]?.jsonPrimitive?.content == headSha &&
                        (workflowName == null || run["name"]?.jsonPrimitive?.contentOrNull == workflowName)
                }

            if (matching.isNotEmpty()) {
                val pending = matching.filter { it["status"]?.jsonPrimitive?.content != "completed" }
                if (pending.isNotEmpty()) {
                    val events = pending.mapNotNull { it["event"]?.jsonPrimitive?.contentOrNull }.distinct().joinToString("+")
                    onEvent("CI${if (events.isNotBlank()) " [$events]" else ""}: يعمل (${pending.size}/${matching.size})")
                } else {
                    val failed = matching.filter {
                        it["conclusion"]?.jsonPrimitive?.contentOrNull !in setOf("success", "neutral", "skipped")
                    }
                    if (failed.isEmpty()) return CiResult(CiState.SUCCESS, matching.first()["id"]?.jsonPrimitive?.longOrNull)

                    val logs = buildString {
                        for (run in failed.take(4)) {
                            val id = run["id"]?.jsonPrimitive?.longOrNull ?: continue
                            val event = run["event"]?.jsonPrimitive?.contentOrNull.orEmpty()
                            appendLine("\n===== CI $event / run $id =====")
                            appendLine(downloadRunLogs(repo, id))
                        }
                    }.takeLast(100_000)
                    return CiResult(CiState.FAILURE, failed.first()["id"]?.jsonPrimitive?.longOrNull, logs)
                }
            } else if (attempt == 8) {
                onEvent("لم يظهر CI بعد؛ سأواصل الانتظار")
            }
            delay(12_000)
        }
        return CiResult(CiState.NOT_FOUND)
    }

    suspend fun deleteBranch(repo: RepoRef, branch: String) = withContext(Dispatchers.IO) {
        val response = request(
            "DELETE",
            "/repos/${repo.owner}/${repo.repo}/git/refs/heads/${enc(branch)}",
            allow404 = true
        )
        response.use {
            if (it.code !in setOf(204, 404)) {
                error("تعذر تنظيف فرع المهمة: HTTP ${it.code}")
            }
        }
    }

    suspend fun createPullRequest(repo: RepoRef, branch: String, base: String, title: String, body: String): String? {
        val root = postJson(
            "/repos/${repo.owner}/${repo.repo}/pulls",
            buildJsonObject {
                put("title", title)
                put("head", branch)
                put("base", base)
                put("body", body)
            }
        )
        return root["html_url"]?.jsonPrimitive?.contentOrNull
    }

    private suspend fun downloadRunLogs(repo: RepoRef, runId: Long): String = withContext(Dispatchers.IO) {
        request("GET", "/repos/${repo.owner}/${repo.repo}/actions/runs/$runId/logs").use { response ->
            if (!response.isSuccessful) return@withContext "تعذر تنزيل سجل CI: HTTP ${response.code}"
            val bytes = response.body?.bytes() ?: return@withContext ""
            val out = StringBuilder()
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var entry = zip.nextEntry
                val buffer = ByteArray(8192)
                while (entry != null && out.length < 120_000) {
                    if (!entry.isDirectory) {
                        out.append("\n--- ${entry.name} ---\n")
                        var read = zip.read(buffer)
                        while (read > 0 && out.length < 120_000) {
                            out.append(String(buffer, 0, read, Charsets.UTF_8))
                            read = zip.read(buffer)
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            out.toString().takeLast(100_000)
        }
    }

    private suspend fun getJson(path: String): JsonObject = requestJson("GET", path, null)
    private suspend fun postJson(path: String, body: JsonObject): JsonObject = requestJson("POST", path, body)
    private suspend fun patchJson(path: String, body: JsonObject): JsonObject = requestJson("PATCH", path, body)

    private suspend fun requestJson(method: String, path: String, body: JsonObject?): JsonObject = withContext(Dispatchers.IO) {
        request(method, path, body).use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("GitHub $method failed ${response.code}: ${raw.take(1000)}")
            if (raw.isBlank()) JsonObject(emptyMap()) else json.parseToJsonElement(raw).jsonObject
        }
    }

    private fun request(method: String, path: String, body: JsonObject? = null, allow404: Boolean = false): okhttp3.Response {
        val builder = Request.Builder()
            .url("https://api.github.com$path")
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "Bearer $token")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "HAI-OM-Android/0.2")
        val requestBody = body?.toString()?.toRequestBody(JSON)
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(requestBody ?: "{}".toRequestBody(JSON))
            "PATCH" -> builder.patch(requestBody ?: "{}".toRequestBody(JSON))
            "DELETE" -> builder.delete(requestBody)
            else -> error("Unsupported method")
        }
        val response = client.newCall(builder.build()).execute()
        if (!allow404 && response.code == 404) {
            val text = response.body?.string().orEmpty(); response.close(); error("GitHub resource not found: $text")
        }
        return response
    }

    private fun validatePath(path: String) {
        require(path.isNotBlank() && !path.startsWith("/") && !path.contains("..") && !path.contains('\\')) {
            "مسار ملف غير آمن: $path"
        }
        require(!path.startsWith(".git/")) { "لا يمكن تعديل .git" }
        require(!isSensitivePath(path)) { "رفض الوصول التلقائي لملف حساس: $path" }
    }

    private fun isTextCandidate(path: String, size: Long): Boolean {
        if (size > 180_000 || isSensitivePath(path)) return false
        val lower = path.lowercase()
        if (listOf("node_modules/", ".gradle/", "build/", "dist/", "vendor/", ".git/").any { lower.contains(it) }) return false
        val name = lower.substringAfterLast('/')
        if (name in setOf("readme.md", "package.json", "settings.gradle.kts", "build.gradle.kts", "gradle.properties", "pubspec.yaml", "cargo.toml", "go.mod", "requirements.txt")) return true
        val ext = lower.substringAfterLast('.', "")
        return ext in setOf("kt", "kts", "java", "xml", "json", "md", "ts", "tsx", "js", "jsx", "py", "go", "rs", "swift", "dart", "yaml", "yml", "toml", "properties", "gradle", "css", "html")
    }

    private fun isSensitivePath(path: String): Boolean {
        val lower = path.lowercase().replace('\\', '/')
        val name = lower.substringAfterLast('/')
        if (name == ".env" || name.startsWith(".env.")) return true
        if (name in setOf(
                "local.properties", "google-services.json", "googleservice-info.plist",
                "credentials.json", "secrets.json", "service-account.json", "service_account.json",
                "id_rsa", "id_ed25519", ".npmrc", ".pypirc", "netrc", ".netrc"
            )) return true
        if ("/.ssh/" in "/$lower" || "/.aws/credentials" in "/$lower" || "/.gnupg/" in "/$lower") return true
        if (name.contains("service-account") || name.contains("service_account") || name.contains("credential") || name.contains("secret")) return true
        val ext = name.substringAfterLast('.', "")
        return ext in setOf("jks", "keystore", "p12", "pfx", "pem", "key", "mobileprovision")
    }

    private fun redactSecrets(input: String): String {
        var text = input
        text = text.replace(Regex("(?i)(authorization\\s*:\\s*bearer\\s+)[^\\s]+")) {
            "${it.groupValues[1]}[REDACTED]"
        }
        text = text.replace(
            Regex("(?i)((?:api[_-]?key|access[_-]?token|refresh[_-]?token|token|secret|password|passwd)\\s*[=:]\\s*)[\\\"']?[^\\s\\\"']{6,}")
        ) {
            "${it.groupValues[1]}[REDACTED]"
        }
        text = text.replace(Regex("\\bgh[pousr]_[A-Za-z0-9_]{20,}\\b"), "[REDACTED_GITHUB_TOKEN]")
        text = text.replace(Regex("\\bAKIA[0-9A-Z]{16}\\b"), "[REDACTED_AWS_KEY]")
        return text
    }

    private fun priority(path: String): Int {
        val lower = path.lowercase()
        return when {
            lower == "readme.md" -> 0
            lower.endsWith("androidmanifest.xml") -> 1
            lower.endsWith("build.gradle.kts") || lower.endsWith("settings.gradle.kts") || lower.endsWith("package.json") -> 2
            "/src/main/" in lower -> 3
            "/src/" in lower -> 4
            else -> 8
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
    private fun pathEnc(path: String): String = path.split('/').joinToString("/") { enc(it) }

    companion object { private val JSON = "application/json; charset=utf-8".toMediaType() }
}
