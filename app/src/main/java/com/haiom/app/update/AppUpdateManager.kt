package com.haiom.app.update

import android.content.Context
import androidx.core.content.FileProvider
import com.haiom.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val versionName: String,
    val downloadUrl: String
)

class AppUpdateManager(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun checkLatest(): AppUpdateInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/Malik05255/HAI_OM/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "HAI-OM-Android/${BuildConfig.VERSION_NAME}")
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 404) return@withContext null
            if (!response.isSuccessful) error("تعذر البحث عن تحديث")

            val raw = response.body?.string().orEmpty()
            val root = json.parseToJsonElement(raw).jsonObject
            val tag = root["tag_name"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val latestVersion = tag.removePrefix("v").trim()
            if (latestVersion.isBlank() || compareVersions(latestVersion, BuildConfig.VERSION_NAME) <= 0) {
                return@withContext null
            }

            val asset = root["assets"]?.jsonArray?.firstOrNull { element ->
                val name = element.jsonObject["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                name.equals("HAI-OM.apk", ignoreCase = true) || name.endsWith(".apk", ignoreCase = true)
            }?.jsonObject ?: error("ملف التحديث غير متاح")

            val url = asset["browser_download_url"]?.jsonPrimitive?.contentOrNull
                ?: error("ملف التحديث غير متاح")

            AppUpdateInfo(
                versionName = latestVersion,
                downloadUrl = url
            )
        }
    }

    suspend fun download(
        update: AppUpdateInfo,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(update.downloadUrl)
            .header("User-Agent", "HAI-OM-Android/${BuildConfig.VERSION_NAME}")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("تعذر تنزيل التحديث")
            val body = response.body ?: error("تعذر تنزيل التحديث")
            val total = body.contentLength()

            val dir = File(context.externalCacheDir ?: context.cacheDir, "updates").apply { mkdirs() }
            val target = File(dir, "HAI-OM-${update.versionName}.apk")
            if (target.exists()) target.delete()

            body.byteStream().use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var read: Int
                    var written = 0L
                    var lastProgress = -1
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        written += read
                        if (total > 0) {
                            val progress = ((written * 100) / total).toInt().coerceIn(0, 100)
                            if (progress != lastProgress) {
                                lastProgress = progress
                                onProgress(progress)
                            }
                        }
                    }
                    output.flush()
                }
            }

            onProgress(100)
            target
        }
    }

    fun installUri(file: File) = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )

    private fun compareVersions(a: String, b: String): Int {
        val left = a.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val right = b.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val size = maxOf(left.size, right.size)
        for (i in 0 until size) {
            val l = left.getOrElse(i) { 0 }
            val r = right.getOrElse(i) { 0 }
            if (l != r) return l.compareTo(r)
        }
        return 0
    }
}
