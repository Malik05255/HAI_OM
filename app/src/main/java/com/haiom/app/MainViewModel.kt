package com.haiom.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.haiom.app.agent.AgentEngine
import com.haiom.app.model.AgentRunResult
import com.haiom.app.model.FreeProviderRanking
import com.haiom.app.model.GitHubRepository
import com.haiom.app.network.GitHubAccountClient
import com.haiom.app.network.GitHubAuthClient
import com.haiom.app.network.GitHubClient
import com.haiom.app.network.OmniRouteClient
import com.haiom.app.security.SecretStore
import com.haiom.app.update.AppUpdateInfo
import com.haiom.app.update.AppUpdateManager
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
    val githubLinking: Boolean = false,
    val githubLaunchUrl: String? = null,
    val githubUserCode: String = "",
    val omniReady: Boolean = false,
    val strictFreeVerified: Boolean = false,
    val compressionEnabled: Boolean = false,
    val rankings: List<FreeProviderRanking> = emptyList(),
    val checkingUpdate: Boolean = false,
    val updateInfo: AppUpdateInfo? = null,
    val downloadingUpdate: Boolean = false,
    val updateProgress: Int = 0,
    val updateInstallUri: String? = null,
    val message: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val secrets = SecretStore(application)
    private val updater = AppUpdateManager(application)
    private val _state = MutableStateFlow(
        MainUiState(hasGitHubToken = secrets.githubToken().isNotBlank())
    )
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    init { bootstrap() }

    fun bootstrap() {
        if (_state.value.running || _state.value.githubLinking) return
        _state.update { it.copy(connecting = true, error = null) }
        viewModelScope.launch {
            val omniResult = runCatching {
                val omni = OmniRouteClient(omniUrl(), "")
                omni.verifyAndConfigure()
                runCatching { omni.fetchCodingRankings(30) }.getOrDefault(emptyList())
            }

            val account = loadGitHubAccount()

            _state.update {
                it.copy(
                    connecting = false,
                    omniReady = omniResult.isSuccess,
                    strictFreeVerified = omniResult.isSuccess,
                    compressionEnabled = omniResult.isSuccess,
                    rankings = omniResult.getOrDefault(emptyList()),
                    hasGitHubToken = account.connected,
                    githubLogin = account.login,
                    repositories = account.repositories
                )
            }
        }
    }

    fun startGitHubLink() {
        if (_state.value.githubLinking || _state.value.running) return
        if (BuildConfig.GITHUB_CLIENT_ID.isBlank()) {
            _state.update { it.copy(error = "ربط GitHub غير مفعّل في هذه النسخة") }
            return
        }

        _state.update {
            it.copy(
                githubLinking = true,
                githubLaunchUrl = null,
                githubUserCode = "",
                error = null
            )
        }

        viewModelScope.launch {
            try {
                val auth = GitHubAuthClient(BuildConfig.GITHUB_CLIENT_ID)
                val code = auth.requestDeviceCode()
                _state.update {
                    it.copy(
                        githubLaunchUrl = code.verificationUri,
                        githubUserCode = code.userCode
                    )
                }

                val token = auth.waitForToken(code)
                secrets.saveGitHubToken(token)

                val account = loadGitHubAccount()
                if (!account.connected) error("تعذر ربط GitHub")

                _state.update {
                    it.copy(
                        githubLinking = false,
                        githubLaunchUrl = null,
                        githubUserCode = "",
                        hasGitHubToken = true,
                        githubLogin = account.login,
                        repositories = account.repositories
                    )
                }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        githubLinking = false,
                        githubLaunchUrl = null,
                        githubUserCode = "",
                        error = friendlyLinkError(t)
                    )
                }
            }
        }
    }

    fun consumeGitHubLaunch() {
        _state.update { it.copy(githubLaunchUrl = null) }
    }

    fun disconnectGitHub() {
        if (_state.value.running) return
        secrets.clearGitHubToken()
        _state.update {
            it.copy(
                hasGitHubToken = false,
                githubLogin = "",
                repositories = emptyList(),
                githubLinking = false,
                githubLaunchUrl = null,
                githubUserCode = ""
            )
        }
    }

    fun runAgent(repositoryUrl: String, requirements: String) {
        if (_state.value.running || _state.value.connecting || _state.value.githubLinking) return
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
                val omni = OmniRouteClient(omniUrl(), "")
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

    private suspend fun loadGitHubAccount(): GitHubAccountState {
        val token = secrets.githubToken()
        if (token.isBlank()) return GitHubAccountState()

        return runCatching {
            val account = GitHubAccountClient(token)
            GitHubAccountState(
                connected = true,
                login = account.login(),
                repositories = account.repositories()
            )
        }.getOrElse {
            secrets.clearGitHubToken()
            GitHubAccountState()
        }
    }

    private fun omniUrl(): String = BuildConfig.OMNIROUTE_BASE_URL

    private fun friendlyLinkError(t: Throwable): String {
        val raw = t.message.orEmpty()
        return when {
            raw.contains("إلغاء") -> "تم إلغاء الربط"
            raw.contains("مهلة") -> "انتهت المهلة، حاول مرة ثانية"
            raw.contains("غير مفعّل") -> "ربط GitHub غير مفعّل في هذه النسخة"
            else -> "تعذر ربط GitHub، حاول مرة ثانية"
        }
    }

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


    fun checkForUpdate() {
        if (_state.value.checkingUpdate || _state.value.downloadingUpdate) return
        _state.update { it.copy(checkingUpdate = true, updateInfo = null, message = null) }
        viewModelScope.launch {
            runCatching { updater.checkLatest() }
                .onSuccess { update ->
                    _state.update {
                        it.copy(
                            checkingUpdate = false,
                            updateInfo = update,
                            message = if (update == null) "أنت على آخر إصدار" else null
                        )
                    }
                }
                .onFailure {
                    _state.update { it.copy(checkingUpdate = false, message = "تعذر البحث عن تحديث") }
                }
        }
    }

    fun downloadUpdate() {
        val update = _state.value.updateInfo ?: return
        if (_state.value.downloadingUpdate) return
        _state.update { it.copy(downloadingUpdate = true, updateProgress = 0, message = null) }
        viewModelScope.launch {
            runCatching {
                updater.download(update) { progress ->
                    _state.update { current -> current.copy(updateProgress = progress) }
                }
            }.onSuccess { file ->
                val uri = updater.installUri(file).toString()
                _state.update {
                    it.copy(
                        downloadingUpdate = false,
                        updateProgress = 100,
                        updateInstallUri = uri
                    )
                }
            }.onFailure {
                _state.update { it.copy(downloadingUpdate = false, message = "تعذر تنزيل التحديث") }
            }
        }
    }

    fun dismissUpdate() = _state.update {
        if (it.downloadingUpdate) it else it.copy(updateInfo = null, updateInstallUri = null, updateProgress = 0)
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    fun clearError() = _state.update { it.copy(error = null) }

    private data class GitHubAccountState(
        val connected: Boolean = false,
        val login: String = "",
        val repositories: List<GitHubRepository> = emptyList()
    )

}
