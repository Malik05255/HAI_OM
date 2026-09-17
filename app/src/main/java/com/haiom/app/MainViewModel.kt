package com.haiom.app

import android.app.Application
import com.haiom.app.agent.RemoteAgentRunner
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.haiom.app.model.AgentRunResult
import com.haiom.app.model.FreeProviderRanking
import com.haiom.app.model.GitHubRepository
import com.haiom.app.network.GitHubAccountClient
import com.haiom.app.network.GitHubClient
import com.haiom.app.network.GitHubAppLinker
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
    private val githubAppLinker = GitHubAppLinker()
    private val _state = MutableStateFlow(
        MainUiState(hasGitHubToken = secrets.githubToken().isNotBlank() || secrets.hasGitHubApp())
    )
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    init { bootstrap() }

    fun bootstrap() {
        if (_state.value.running || _state.value.githubLinking) return
        _state.update { it.copy(connecting = true, error = null) }
        viewModelScope.launch {
            val account = loadGitHubAccount()

            _state.update {
                it.copy(
                    connecting = false,
                    omniReady = account.connected,
                    strictFreeVerified = account.connected,
                    compressionEnabled = account.connected,
                    rankings = emptyList(),
                    hasGitHubToken = account.connected,
                    githubLogin = account.login,
                    repositories = account.repositories
                )
            }
        }
    }

    fun startGitHubLink() {
        if (_state.value.githubLinking || _state.value.running) return

        _state.update {
            it.copy(
                githubLinking = true,
                githubLaunchUrl = null,
                githubUserCode = "",
                error = null,
                message = "افتح GitHub ووافق على الربط"
            )
        }

        viewModelScope.launch {
            try {
                val linked = githubAppLinker.link { url ->
                    _state.update { current -> current.copy(githubLaunchUrl = url) }
                }

                secrets.clearGitHubToken()
                secrets.saveGitHubApp(
                    appId = linked.appId,
                    slug = linked.slug,
                    privateKeyPem = linked.privateKeyPem,
                    ownerLogin = linked.ownerLogin,
                    installationId = linked.installationId
                )

                _state.update {
                    it.copy(
                        githubLinking = false,
                        githubLaunchUrl = null,
                        githubUserCode = "",
                        hasGitHubToken = true,
                        githubLogin = linked.ownerLogin.ifBlank { "GitHub" },
                        repositories = linked.repositories,
                        message = "تم ربط GitHub"
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
        secrets.clearGitHubApp()
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

        _state.update {
            it.copy(running = true, result = null, error = null, logs = listOf("بدأ التنفيذ"))
        }

        viewModelScope.launch {
            try {
                val token = resolveGitHubToken() ?: error("اربط GitHub أولًا")
                val github = GitHubClient(token)
                val result = withContext(Dispatchers.IO) {
                    RemoteAgentRunner(getApplication(), github)
                        .run(repositoryUrl, requirements, ::appendLog)
                }
                _state.update { it.copy(running = false, result = result) }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        running = false,
                        error = t.message?.takeIf { message -> message.isNotBlank() }
                            ?: "تعذر إكمال المهمة. حاول مرة ثانية"
                    )
                }
            }
        }
    }

    private suspend fun loadGitHubAccount(): GitHubAccountState {
        val legacyToken = secrets.githubToken()
        if (legacyToken.isNotBlank()) {
            return runCatching {
                val account = GitHubAccountClient(legacyToken)
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

        if (!secrets.hasGitHubApp()) return GitHubAccountState()

        return runCatching {
            val token = resolveGitHubToken() ?: error("تعذر اعتماد GitHub")
            GitHubAccountState(
                connected = true,
                login = secrets.githubAppOwner().ifBlank { "GitHub" },
                repositories = githubAppLinker.installationRepositories(token)
            )
        }.getOrElse {
            GitHubAccountState(
                connected = true,
                login = secrets.githubAppOwner().ifBlank { "GitHub" },
                repositories = emptyList()
            )
        }
    }

    private suspend fun resolveGitHubToken(): String? {
        secrets.githubToken().takeIf { it.isNotBlank() }?.let { return it }
        val appId = secrets.githubAppId() ?: return null
        val installationId = secrets.githubInstallationId() ?: return null
        val privateKey = secrets.githubAppPrivateKey()
        if (privateKey.isBlank()) return null
        return githubAppLinker.installationToken(appId, privateKey, installationId)
    }

    private fun friendlyLinkError(t: Throwable): String {
        val raw = t.message.orEmpty()
        return when {
            raw.contains("إلغاء") -> "تم إلغاء الربط"
            raw.contains("مهلة") -> "انتهت المهلة، حاول مرة ثانية"
            raw.contains("timeout", true) || raw.contains("مهلة") -> "انتهت المهلة، حاول مرة ثانية"
            raw.contains("تعذر إنشاء ربط GitHub") -> "GitHub رفض إنشاء الربط، حاول مرة ثانية"
            else -> "تعذر ربط GitHub، حاول مرة ثانية"
        }
    }

    private fun appendLog(message: String) {
        val simple = when {
            message.contains("قراءة المستودع", true) -> "قراءة المشروع"
            message.contains("إنشاء خطة", true) -> "تجهيز الخطة"
            message == "تجهيز المهمة" -> "تجهيز المهمة"
            message == "بدأ التنفيذ" -> "بدأ التنفيذ"
            message.contains("تنظيف ملفات التشغيل", true) -> "إنهاء المهمة"
            message.contains("تجهيز النتيجة", true) -> "تجهيز النتيجة"
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
