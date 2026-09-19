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
import com.haiom.app.network.GitHubOAuthClient
import com.haiom.app.network.GitHubAppLinker
import com.haiom.app.network.DirectChatClient
import com.haiom.app.network.ChatTurn
import com.haiom.app.security.SecretStore
import com.haiom.app.update.AppUpdateInfo
import com.haiom.app.update.AppUpdateManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    val selectedRepository: GitHubRepository? = null,
    val githubLinking: Boolean = false,
    val githubLaunchUrl: String? = null,
    val githubLinkStatus: String = "",
    val githubDeviceCode: String = "",
    val githubVerificationUrl: String = "",
    val githubOAuthAvailable: Boolean = false,
    val githubJustLinked: Boolean = false,
    val omniReady: Boolean = false,
    val strictFreeVerified: Boolean = false,
    val compressionEnabled: Boolean = false,
    val rankings: List<FreeProviderRanking> = emptyList(),
    val checkingUpdate: Boolean = false,
    val updateInfo: AppUpdateInfo? = null,
    val message: String? = null,
    val autoExecuteEnabled: Boolean = false,
    val programming: Boolean = false,
    val chatHistory: List<ChatTurn> = emptyList()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val secrets = SecretStore(application)
    private val updater = AppUpdateManager(application)
    private val githubAppLinker = GitHubAppLinker()
    private val githubOAuthClient = GitHubOAuthClient(
        clientId = BuildConfig.GITHUB_OAUTH_CLIENT_ID
    )
    private val directChat = DirectChatClient()
    private var githubLinkJob: Job? = null
    private val _state = MutableStateFlow(
        MainUiState(
            hasGitHubToken = secrets.githubToken().isNotBlank() || secrets.hasGitHubApp(),
            githubOAuthAvailable = githubOAuthClient.configured,
            autoExecuteEnabled = secrets.autoExecuteEnabled()
        )
    )
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    init { bootstrap() }

    fun bootstrap() {
        if (_state.value.running || _state.value.githubLinking) return
        _state.update { it.copy(connecting = true, error = null) }
        viewModelScope.launch {
            val account = loadGitHubAccount()
            val selectedRepository = restoreSelectedRepository(account.repositories)

            _state.update {
                it.copy(
                    connecting = false,
                    omniReady = account.connected,
                    strictFreeVerified = account.connected,
                    compressionEnabled = account.connected,
                    rankings = emptyList(),
                    hasGitHubToken = account.connected,
                    githubLogin = account.login,
                    repositories = account.repositories,
                    selectedRepository = selectedRepository
                )
            }
        }
    }

    fun startGitHubLink() {
        if (_state.value.running || _state.value.githubLinking) return

        if (!githubOAuthClient.configured) {
            _state.update {
                it.copy(
                    error = "ربط GitHub بالكود غير مهيأ في هذا الإصدار",
                    githubJustLinked = false
                )
            }
            return
        }

        githubLinkJob?.cancel()
        _state.update {
            it.copy(
                githubLinking = true,
                githubLaunchUrl = null,
                githubLinkStatus = "جاري طلب كود GitHub…",
                githubDeviceCode = "",
                githubVerificationUrl = "",
                githubJustLinked = false,
                error = null,
                message = null
            )
        }

        githubLinkJob = viewModelScope.launch {
            try {
                val authorization =
                    githubOAuthClient.requestDeviceAuthorization()

                _state.update {
                    it.copy(
                        githubLinkStatus = "أدخل الكود في GitHub ووافق على الصلاحيات",
                        githubDeviceCode = authorization.userCode,
                        githubVerificationUrl = authorization.verificationUri
                    )
                }

                val accessToken =
                    githubOAuthClient.waitForAuthorization(
                        authorization
                    )

                _state.update {
                    it.copy(
                        githubLinkStatus = "تمت الموافقة، جاري تحميل مشاريعك…"
                    )
                }

                val account = GitHubAccountClient(accessToken)
                val login = account.login()
                val repositories = account.repositories()
                val selectedRepository = restoreSelectedRepository(repositories)

                secrets.clearGitHubApp()
                secrets.saveGitHubToken(accessToken)

                _state.update {
                    it.copy(
                        githubLinking = false,
                        githubLaunchUrl = null,
                        githubLinkStatus = "",
                        githubDeviceCode = "",
                        githubVerificationUrl = "",
                        hasGitHubToken = true,
                        githubLogin = login,
                        repositories = repositories,
                        selectedRepository = selectedRepository,
                        githubJustLinked = true,
                        message = "تم ربط GitHub. اختر المشروع الذي تريد العمل عليه"
                    )
                }
            } catch (_: CancellationException) {
                _state.update {
                    it.copy(
                        githubLinking = false,
                        githubLaunchUrl = null,
                        githubLinkStatus = "",
                        githubDeviceCode = "",
                        githubVerificationUrl = ""
                    )
                }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        githubLinking = false,
                        githubLaunchUrl = null,
                        githubLinkStatus = "",
                        githubDeviceCode = "",
                        githubVerificationUrl = "",
                        error = t.message?.takeIf { message -> message.isNotBlank() }
                            ?: "تعذر ربط GitHub"
                    )
                }
            } finally {
                githubLinkJob = null
            }
        }
    }

    fun openGitHubVerification() {
        val url = _state.value.githubVerificationUrl
        if (url.isBlank()) return
        _state.update {
            it.copy(githubLaunchUrl = url)
        }
    }

    fun cancelGitHubLink() {
        githubLinkJob?.cancel()
        githubLinkJob = null
        _state.update {
            it.copy(
                githubLinking = false,
                githubLaunchUrl = null,
                githubLinkStatus = "",
                githubDeviceCode = "",
                githubVerificationUrl = ""
            )
        }
    }

    fun consumeGitHubLaunch() {
        _state.update { it.copy(githubLaunchUrl = null) }
    }

    fun consumeGitHubJustLinked() {
        _state.update { it.copy(githubJustLinked = false) }
    }

    fun disconnectGitHub() {
        if (_state.value.running) return
        githubLinkJob?.cancel()
        githubLinkJob = null
        secrets.clearGitHubToken()
        secrets.clearGitHubApp()
        secrets.clearSelectedRepository()
        _state.update {
            it.copy(
                hasGitHubToken = false,
                githubLogin = "",
                repositories = emptyList(),
                selectedRepository = null,
                githubLinking = false,
                githubLaunchUrl = null,
                githubLinkStatus = "",
                githubDeviceCode = "",
                githubVerificationUrl = "",
                githubJustLinked = false,
                message = "تم فصل GitHub"
            )
        }
    }

    fun isProgrammingRequest(text: String): Boolean {
        val value = text.trim().lowercase()
        if (value.isBlank()) return false

        val questionStarters = listOf(
            "هل ", "وش ", "وش معنى", "ما ", "ما معنى", "ليش ", "كيف ",
            "متى ", "وين ", "ايش ", "اشرح", "فسر", "وضح", "لخص", "اقرأ", "اقرا"
        )
        if (questionStarters.any { value.startsWith(it) } &&
            !EXPLICIT_PROGRAMMING_PHRASES.any { value.contains(it) }
        ) {
            return false
        }

        return PROGRAMMING_PATTERNS.any { it.containsMatchIn(value) }
    }

    fun saveSelectedRepository(repository: GitHubRepository) {
        if (_state.value.running || _state.value.connecting || _state.value.githubLinking) return
        val available = _state.value.repositories.firstOrNull {
            it.fullName == repository.fullName
        } ?: run {
            _state.update { it.copy(error = "هذا المشروع لم يعد متاحًا في حساب GitHub") }
            return
        }

        secrets.saveSelectedRepositoryFullName(available.fullName)
        _state.update {
            it.copy(
                selectedRepository = available,
                message = "تم حفظ المشروع: ${available.fullName}"
            )
        }
    }

    fun setAutoExecuteEnabled(enabled: Boolean) {
        secrets.saveAutoExecuteEnabled(enabled)
        _state.update {
            it.copy(
                autoExecuteEnabled = enabled,
                message = if (enabled) {
                    "تم تفعيل التنفيذ التلقائي"
                } else {
                    "تم إيقاف التنفيذ التلقائي"
                }
            )
        }
    }

    fun runAgent(requirements: String) {
        if (_state.value.running || _state.value.connecting || _state.value.githubLinking) return
        if (requirements.isBlank()) {
            _state.update { it.copy(error = "اكتب رسالتك") }
            return
        }

        if (isProgrammingRequest(requirements)) {
            runProgramming(requirements)
        } else {
            runChat(requirements)
        }
    }

    private fun runChat(prompt: String) {
        val existingHistory = _state.value.chatHistory
        val visibleHistory = (
            existingHistory + ChatTurn("user", prompt)
            ).takeLast(20)

        _state.update {
            it.copy(
                running = true,
                programming = false,
                result = null,
                error = null,
                logs = emptyList(),
                chatHistory = visibleHistory
            )
        }

        viewModelScope.launch {
            try {
                val needsRepo = needsRepositoryContext(prompt)
                val repositoryUrl = _state.value.selectedRepository?.htmlUrl.orEmpty()
                val context = if (needsRepo) {
                    if (repositoryUrl.isBlank()) error("اختر المشروع عشان أقرأه")
                    val token = resolveGitHubToken() ?: error("اربط GitHub عشان أقرأ المشروع")
                    val github = GitHubClient(token)
                    withContext(Dispatchers.IO) {
                        val repo = github.parseRepository(repositoryUrl)
                        val branch = github.defaultBranch(repo)
                        github.buildContext(repo, branch, maxChars = 36_000)
                    }
                } else {
                    null
                }

                val answer = directChat.chat(
                    prompt = prompt,
                    history = existingHistory,
                    repositoryContext = context
                )

                val updatedHistory = (
                    visibleHistory + ChatTurn("assistant", answer)
                    ).takeLast(20)

                _state.update {
                    it.copy(
                        running = false,
                        programming = false,
                        chatHistory = updatedHistory,
                        result = AgentRunResult(
                            branch = "",
                            pullRequestUrl = null,
                            completedTasks = 1,
                            totalTasks = 1,
                            answer = answer
                        )
                    )
                }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        running = false,
                        programming = false,
                        error = t.message?.takeIf { message -> message.isNotBlank() }
                            ?: "تعذر الرد الآن"
                    )
                }
            }
        }
    }

    private fun runProgramming(requirements: String) {
        val repositoryUrl = _state.value.selectedRepository?.htmlUrl.orEmpty()
        if (repositoryUrl.isBlank()) {
            _state.update { it.copy(error = "اختر المشروع واحفظه أولًا") }
            return
        }
        if (!_state.value.hasGitHubToken) {
            _state.update { it.copy(error = "اربط GitHub أولًا") }
            return
        }

        val existingHistory = _state.value.chatHistory
        val visibleHistory = (
            existingHistory + ChatTurn("user", requirements)
            ).takeLast(20)

        _state.update {
            it.copy(
                running = true,
                programming = true,
                result = null,
                error = null,
                logs = listOf("بدأ التنفيذ"),
                chatHistory = visibleHistory
            )
        }

        viewModelScope.launch {
            try {
                val token = resolveGitHubToken() ?: error("اربط GitHub أولًا")
                val github = GitHubClient(token)
                val autoExecute = _state.value.autoExecuteEnabled
                val effectiveRequirements = if (autoExecute) {
                    """
                    وضع التنفيذ التلقائي مفعّل.
                    نفّذ جميع الخطوات والمهام اللازمة لإكمال طلب المستخدم على المشروع المحدد تلقائيًا،
                    واستمر بين خطوات التنفيذ والإصلاح والفحص دون طلب موافقة إضافية،
                    مع عدم الانتقال إلى أي مستودع آخر.

                    طلب المستخدم:
                    $requirements
                    """.trimIndent()
                } else {
                    requirements
                }
                val result = withContext(Dispatchers.IO) {
                    RemoteAgentRunner(getApplication(), github)
                        .run(repositoryUrl, effectiveRequirements, ::appendLog)
                }
                val completedHistory = result.answer
                    ?.takeIf { it.isNotBlank() }
                    ?.let { answer ->
                        (visibleHistory + ChatTurn("assistant", answer)).takeLast(20)
                    }
                    ?: visibleHistory

                _state.update {
                    it.copy(
                        running = false,
                        programming = false,
                        result = result,
                        chatHistory = completedHistory
                    )
                }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        running = false,
                        programming = false,
                        error = t.message?.takeIf { message -> message.isNotBlank() }
                            ?: "تعذر إكمال المهمة. حاول مرة ثانية"
                    )
                }
            }
        }
    }

    private fun needsRepositoryContext(text: String): Boolean {
        val value = text.lowercase()
        return REPOSITORY_CONTEXT_WORDS.any { value.contains(it) }
    }

    private fun restoreSelectedRepository(
        repositories: List<GitHubRepository>
    ): GitHubRepository? {
        val savedFullName = secrets.selectedRepositoryFullName()
        if (savedFullName.isBlank()) return null

        val selected = repositories.firstOrNull {
            it.fullName == savedFullName
        }
        if (selected == null) {
            secrets.clearSelectedRepository()
        }
        return selected
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
            message == "فتح المشروع" -> "فتح المشروع"
            message == "تجهيز العمل" -> "تجهيز العمل"
            message == "تجهيز الذكاء" -> "تجهيز الذكاء"
            message == "قراءة المشروع وتنفيذ المطلوب" -> "قراءة المشروع وتنفيذ المطلوب"
            message == "جاري التنفيذ" -> "جاري التنفيذ"
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
        if (_state.value.checkingUpdate) return
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

    fun dismissUpdate() = _state.update { it.copy(updateInfo = null) }

    fun clearChat() = _state.update {
        it.copy(
            chatHistory = emptyList(),
            result = null,
            logs = emptyList(),
            error = null,
            message = null,
            programming = false
        )
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    fun clearError() = _state.update { it.copy(error = null) }

    companion object {
        private val EXPLICIT_PROGRAMMING_PHRASES = listOf(
            "ابدأ البرمجة", "ابدأ تنفيذ", "نفذ الآن", "نفّذ الآن",
            "كمل البرمجة", "كمل التنفيذ", "اتصل بالمستودع ونفذ",
            "اتصل بالمستودع وكمل"
        )

        private val PROGRAMMING_PATTERNS = listOf(
            Regex("""(^|\s)(برمج|نفذ|نفّذ|عدل|عدّل|اصلح|أصلح|صحح|صحّح|اضف|أضف|احذف|انشئ|أنشئ|طور|طوّر|طبق|طبّق|استبدل|ادمج|اربط)(\s|$)"""),
            Regex("""(^|\s)(غير|غيّر)\s+(ال|هذا|هذه|اسم|لون|شكل|تصميم|واجهة|شاشة|صفحة|ملف|كود|زر|ميزة)"""),
            Regex("""(اكتب|سو|سوي)\s+(لي\s+)?(الكود|كود|ملف|ميزة|شاشة|صفحة|تطبيق)"""),
            Regex("""(^|\s)(implement|fix|modify|edit|add|delete|remove|refactor|build|code)(\s|$)""", RegexOption.IGNORE_CASE),
            Regex("""اتصل\s+بالمستودع.*(نفذ|نفّذ|عدل|اصلح|أصلح|اضف|أضف|احذف|كمل)"""),
            Regex("""كمل\s+(البرمجة|التنفيذ|التعديل)""")
        )

        private val REPOSITORY_CONTEXT_WORDS = listOf(
            "المشروع", "المستودع", "الكود", "الملفات", "repo", "repository",
            "اقرأه", "اقراه", "اقرأ", "اقرا", "راجعه", "راجع المشروع",
            "لخصه", "لخص المشروع", "وش لقيت", "وش فيه"
        )
    }

    private data class GitHubAccountState(
        val connected: Boolean = false,
        val login: String = "",
        val repositories: List<GitHubRepository> = emptyList()
    )

}
