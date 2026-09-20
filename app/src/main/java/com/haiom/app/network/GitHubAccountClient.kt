package com.haiom.app.network

import com.haiom.app.model.GitHubRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class GitHubAccountClient(
    private val token: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun login(): String = withContext(Dispatchers.IO) {
        request("https://api.github.com/user").use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("تعذر الاتصال بـ GitHub")
            json.parseToJsonElement(raw).jsonObject["login"]?.jsonPrimitive?.contentOrNull
                ?: error("تعذر قراءة الحساب")
        }
    }

    suspend fun repositories(): List<GitHubRepository> = withContext(Dispatchers.IO) {
        val all = LinkedHashMap<String, GitHubRepository>()
        var page = 1

        while (true) {
            val url =
                "https://api.github.com/user/repos" +
                    "?sort=updated" +
                    "&direction=desc" +
                    "&per_page=100" +
                    "&page=$page" +
                    "&affiliation=owner,collaborator,organization_member"

            val pageItems = request(url).use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) error("تعذر تحميل المشاريع")

                json.parseToJsonElement(raw).jsonArray.mapNotNull { item ->
                    val obj = item.jsonObject
                    val fullName =
                        obj["full_name"]?.jsonPrimitive?.contentOrNull
                            ?: return@mapNotNull null

                    GitHubRepository(
                        fullName = fullName,
                        htmlUrl =
                            obj["html_url"]?.jsonPrimitive?.contentOrNull
                                ?: "https://github.com/$fullName",
                        isPrivate =
                            obj["private"]?.jsonPrimitive?.contentOrNull
                                ?.toBooleanStrictOrNull() ?: false,
                        updatedAt =
                            obj["updated_at"]?.jsonPrimitive?.contentOrNull
                                .orEmpty()
                    )
                }
            }

            pageItems.forEach { repo ->
                all[repo.fullName] = repo
            }

            if (pageItems.size < 100) break
            page++
        }

        all.values.toList()
    }

    private fun request(url: String): okhttp3.Response {
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "Bearer $token")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "H-AGENT-Android/0.11")
        return client.newCall(builder.build()).execute()
    }
}
