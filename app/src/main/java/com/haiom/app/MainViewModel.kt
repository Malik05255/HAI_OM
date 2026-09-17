package com.haiom.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.haiom.app.agent.AgentEngine
import com.haiom.app.model.AgentRunResult
import com.haiom.app.model.FreeProviderRanking
import com.haiom.app.model.GitHubRepository
import com.haiom.app.network.GitHubAccountClient
import com.haiom.app.network.GitHubClient
import com.haiom.app.network.OmniRouteClient
import com.haiom.app.security.SecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MainUiState(
    val running: Boolean = false,
    val connecting: Boolean = true,
    val logs: List<String> = emptyList(),
    val result: AgentRunResult? = null,
    val error: String? = null,
    val hasGitHubToken: Boolean = false,
    val githubLogin: String = "",
    val repositories: List<GitHubRepository> = emptyList(),
    val omniReady: Boolean = false,
    val strictFreeVerified: Boolean = false,
    val compressionEnabled: Boolean = false,
    val rankings: List<FreeProviderRanking> = emptyList()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val secrets = SecretStore(application)
    private val _state = MutableStateFlow(
        MainUiState(hasGitHubToken = secrets.githubToken().isNotBlank())
    )
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    init { bootstrap() }

    fun bootstrap() {
        if (_state.value.running) return
        _state.update { it.copy(connecting = true, error = null) }
        viewModelScope.launch {
            val omniResult = runCatching {
                val omni = OmniRouteClient(omniUrl(), secrets.omniRouteKey())
                omni.verifyAndConfigure()
                runCatching { omni.fetchCodingRankings(30) }.getOrDefault(emptyList())
            }

            var login = ""
            var repositories = emptyList<GitHubRepository>()
            val token = secrets.githubToken()
            if (token.isNotBlank()) {
                runCatching {
                    val account = GitHubAccountClient(token)
                    login = account.login()
                    repositories = account.repositories()
                }
            }

            _state.update {
                it.copy(
                    connecting = false,
                    omniReady = omniResult.isSuccess,
                    strictFreeVerified = omniResult.isSuccess,
                    compressionEnabled = omniResult.isSuccess,
                    rankings = omniResult.getOrDefault(emptyList()),
                    hasGitHubToken = token.isNotBlank(),
                    githubLogin = login,
                    repositories = repositories
                )
            }
        }
    }

    fun runAgent(repositoryUrl: String, requirements: String) {
        if (_state.value.running || _state.value.connecting) return
        if (repositoryUrl.isBlank()) {
            _state.update { it.copy(error = "اختر المشروع أولًا") }
            return
        }
        if (requirements.isBlank()) {
            _state.update { it.copy(error = "اكتب المطلوب") }
            return
        }

        val token = secrets.githubToken()
        if (token.isBlank()) {
            _state.update { it.copy(error = "اربط GitHub أولًا") }
            return
        }

        _state.update {
            it.copy(running = true, result = null, error = null, logs = listOf("بدأ التنفيذ"))
        }

        viewModelScope.launch {
            try {
                val omni = OmniRouteClient(omniUrl(), secrets.omniRouteKey())
                omni.verifyAndConfigure(::appendLog)
                val github = GitHubClient(token)
                val result = withContext(Dispatchers.IO) {
                    AgentEngine(omni, github).run(repositoryUrl, requirements, ::appendLog)
                }
                _state.update { it.copy(running = false, result = result) }
            } catch (_: Throwable) {
                _state.update { it.copy(running = false, error = "تعذر إكمال المهمة. حاول مرة ثانية") }
            }
        }
    }

    private fun omniUrl(): String = secrets.omniRouteUrl().ifBlank { DEFAULT_OMNIROUTE_URL }

    private fun appendLog(message: String) {
        val simple = when {
            message.contains("قراءة المستودع", true) -> "قراءة المشروع"
            message.contains("إنشاء خطة", true) -> "تجهيز الخطة"
            message.contains("المهمة ", true) -> message.substringBefore(':')
            message.contains("تحديث ", true) || message.contains("حذف ", true) -> "تعديل الملفات"
            message.contains("CI", true) && message.contains("ناجح", true) -> "✓ الفحص ناجح"
            message.contains("CI", true) -> "فحص التغييرات"
            message.contains("فشل", true) || message.contains("الإصلاح", true) -> "إصلاح خطأ"
            message.contains("Commit", true) -> "تم حفظ التعديل"
            message.contains("Pull Request", true) -> "تم تجهيز النتيجة"
            message.startsWith("✓ اكتملت") -> message.substringBefore(':')
            message.contains("اكتمل التنفيذ", true) -> "✓ اكتملت المهمة"
            else -> return
        }
        _state.update { current ->
            if (current.logs.lastOrNull() == simple) current else current.copy(logs = (current.logs + simple).takeLast(80))
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    companion object {
        private const val DEFAULT_OMNIROUTE_URL = "http://127.0.0.1:20128"
    }
}
