package com.haiom.app

import android.app.Application
import android.net.Uri
import java.io.File
import java.util.concurrent.TimeUnit
import com.haiom.app.agent.RemoteAgentRunner
import com.haiom.app.automation.AutoTaskItem
import com.haiom.app.automation.AutoTaskStatus
import com.haiom.app.automation.AutoTaskStore
import com.haiom.app.automation.AutoTaskWorker
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

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
    val autoTasks: List<AutoTaskItem> = emptyList(),
    val autoQueueStarted: Boolean = false,
    val autoPaused: Boolean = false,
    val autoAwaitingConfirmation: Boolean = false,
    val autoEvents: List<String> = emptyList(),
    val autoWorkerActive: Boolean = false,
    val autoWorkerHeartbeatAt: Long = 0L,
    val autoWorkerMessage: String = "",
    val autoWorkerError: String = "",
    val autoRemoteRepository: String = "",
    val autoRemoteRunId: Long = 0L,
    val autoRemoteRunUrl: String = "",
    val autoRemoteState: String = "",
    val autoRemoteStage: String = "",
    val autoLiveCode: String = "",
    val programmingLiveCode: String = "",
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
    private val imageClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(150, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
    private val autoTaskStore = AutoTaskStore(application)
    private var githubLinkJob: Job? = null
    private var manualChatJob: Job? = null
    private var responseGeneration: Long = 0L
    private val _state = MutableStateFlow(
        MainUiState(
            hasGitHubToken = secrets.githubToken().isNotBlank() || secrets.hasGitHubApp(),
            githubOAuthAvailable = githubOAuthClient.configured,
            autoExecuteEnabled = secrets.autoExecuteEnabled()
        )
    )
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    init {
        observeAutoQueue()
        bootstrap()
    }

    private fun observeAutoQueue() {
        viewModelScope.launch {
            autoTaskStore.state.collect { queue ->
                _state.update {
                    it.copy(
                        autoTasks = queue.tasks,
                        autoQueueStarted = queue.started,
                        autoPaused = queue.paused,
                        autoAwaitingConfirmation = queue.awaitingConfirmation,
                        autoEvents = queue.events,
                        autoWorkerActive = queue.workerActive,
                        autoWorkerHeartbeatAt = queue.workerHeartbeatAt,
                        autoWorkerMessage = queue.workerMessage,
                        autoWorkerError = queue.workerError,
                        autoRemoteRepository = queue.remoteRepository,
                        autoRemoteRunId = queue.remoteRunId,
                        autoRemoteRunUrl = queue.remoteRunUrl,
                        autoRemoteState = queue.remoteState,
                        autoRemoteStage = queue.remoteStage,
                        autoLiveCode = queue.liveCode
                    )
                }
            }
        }
    }

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
                githubLinkStatus = "جاري طلب الكود…",
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
                        githubLinkStatus = "أدخل الكود في GitHub",
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
                        githubLinkStatus = "جاري تحميل المشاريع…"
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
                        message = "تم ربط GitHub"
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
        autoTaskStore.clearAll()
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
            _state.update { it.copy(error = "المشروع غير متاح") }
            return
        }

        if (_state.value.selectedRepository?.fullName != available.fullName) {
            autoTaskStore.clearAll()
        }
        secrets.saveSelectedRepositoryFullName(available.fullName)
        _state.update {
            it.copy(
                selectedRepository = available,
                message = "تم حفظ المشروع"
            )
        }
    }

    fun setAutoExecuteEnabled(enabled: Boolean) {
        // Any mode switch invalidates a reply that was already being generated.
        responseGeneration += 1L
        manualChatJob?.cancel()
        manualChatJob = null
        secrets.saveAutoExecuteEnabled(enabled)

        if (enabled) {
            autoTaskStore.setAwaitingConfirmation(false)
            _state.update {
                it.copy(
                    autoExecuteEnabled = true,
                    running = false,
                    programming = false,
                    message = "التنفيذ التلقائي"
                )
            }
            return
        }

        val queueBeforeStop = autoTaskStore.snapshot()
        val repositoryUrl = _state.value.selectedRepository?.htmlUrl.orEmpty()

        AutoTaskWorker.cancel(getApplication())
        autoTaskStore.switchToManualMode()

        _state.update {
            it.copy(
                autoExecuteEnabled = false,
                programming = false,
                programmingLiveCode = "",
                message = "الوضع اليدوي"
            )
        }

        val runId = queueBeforeStop.remoteRunId
        if (runId > 0L && repositoryUrl.isNotBlank()) {
            viewModelScope.launch {
                val token = resolveGitHubToken() ?: return@launch
                val github = GitHubClient(token)
                val repo = runCatching {
                    github.parseRepository(repositoryUrl)
                }.getOrNull() ?: return@launch

                runCatching {
                    github.cancelWorkflowRun(repo, runId)
                }
            }
        }
    }

    fun toggleAutoPause() {
        val queue = autoTaskStore.snapshot()
        if (!queue.started) return

        val paused = !queue.paused
        autoTaskStore.setPaused(paused)

        viewModelScope.launch {
            val token = resolveGitHubToken()
            val repositoryUrl = _state.value.selectedRepository?.htmlUrl.orEmpty()

            if (token.isNullOrBlank() || repositoryUrl.isBlank()) {
                autoTaskStore.setPaused(!paused)
                _state.update { current ->
                    current.copy(
                        error = if (paused) {
                            "تعذر إيقاف التنفيذ مؤقتًا"
                        } else {
                            "تعذر متابعة التنفيذ"
                        }
                    )
                }
                return@launch
            }

            val github = GitHubClient(token)
            val repo = runCatching {
                github.parseRepository(repositoryUrl)
            }.getOrNull()

            if (repo == null) {
                autoTaskStore.setPaused(!paused)
                return@launch
            }

            var applied = false
            repeat(3) { attempt ->
                if (applied) return@repeat
                applied = runCatching {
                    withContext(Dispatchers.IO) {
                        github.setRuntimePaused(repo, paused)
                    }
                    true
                }.getOrDefault(false)

                if (!applied && attempt < 2) {
                    kotlinx.coroutines.delay(500L)
                }
            }

            if (!applied) {
                autoTaskStore.setPaused(!paused)
                _state.update { current ->
                    current.copy(
                        error = if (paused) {
                            "تعذر إيقاف التنفيذ مؤقتًا"
                        } else {
                            "تعذر متابعة التنفيذ"
                        }
                    )
                }
            }
        }
    }

    fun runAgent(requirements: String) {
        if (_state.value.connecting || _state.value.githubLinking) return
        if (requirements.isBlank()) {
            _state.update { it.copy(error = "اكتب رسالتك") }
            return
        }

        if (_state.value.autoExecuteEnabled) {
            handleAutomaticInput(requirements)
            return
        }

        if (_state.value.running) return

        when {
            isImageGenerationRequest(requirements) -> {
                generateImage(requirements)
            }

            isRepositoryExecutionRequest(requirements) -> {
                runProgramming(requirements)
            }

            else -> {
                // Manual chat is intentionally repository-independent.
                // Coding questions and code generation stay inside chat unless
                // the user explicitly asks to modify/execute on a repository.
                runChat(requirements)
            }
        }
    }

    private fun handleAutomaticInput(text: String) {
        val queue = autoTaskStore.snapshot()

        if (queue.awaitingConfirmation) {
            when {
                isPositiveStart(text) -> {
                    appendChat("user", text)
                    if (queue.tasks.none { it.status == AutoTaskStatus.WAITING }) {
                        autoTaskStore.setAwaitingConfirmation(false)
                        appendChat("assistant", "لا توجد مهام.")
                        return
                    }
                    if (_state.value.selectedRepository == null) {
                        autoTaskStore.setAwaitingConfirmation(false)
                        appendChat("assistant", "اختر مشروعًا.")
                        return
                    }
                    if (!_state.value.hasGitHubToken) {
                        autoTaskStore.setAwaitingConfirmation(false)
                        appendChat("assistant", "اربط GitHub.")
                        return
                    }
                    autoTaskStore.clearEvents()
                    autoTaskStore.setStarted(true)
                    autoTaskStore.markWorkerStarting()
                    appendChat("assistant", "جاري البدء…")
                    runCatching {
                        AutoTaskWorker.enqueue(getApplication())
                    }.onFailure { error ->
                        autoTaskStore.setStarted(false)
                        autoTaskStore.markWorkerStopped(
                            message = "تعذر البدء",
                            error = error.message.orEmpty()
                        )
                        appendChat("assistant", "تعذر بدء التنفيذ.")
                    }
                }

                isNegativeStart(text) -> {
                    appendChat("user", text)
                    autoTaskStore.setAwaitingConfirmation(false)
                    appendChat("assistant", "حسنًا.")
                }

                else -> {
                    autoTaskStore.setAwaitingConfirmation(false)
                    collectAutomaticRequirements(
                        text,
                        askToStartAfter = false
                    )
                }
            }
            return
        }

        if (queue.started) {
            collectAutomaticRequirements(
                text,
                askToStartAfter = false
            )
            return
        }

        if (isExecutionTrigger(text)) {
            if (isProgrammingRequest(text) && text.length > 24) {
                collectAutomaticRequirements(text, askToStartAfter = true)
                return
            }

            appendChat("user", text)
            if (queue.tasks.any { it.status == AutoTaskStatus.WAITING }) {
                autoTaskStore.setAwaitingConfirmation(true)
                appendChat("assistant", "هل تريد أن أبدأ؟")
            } else {
                appendChat("assistant", "لا توجد مهام.")
            }
            return
        }

        collectAutomaticRequirements(
            text,
            askToStartAfter = false
        )
    }

    private fun collectAutomaticRequirements(
        text: String,
        askToStartAfter: Boolean
    ) {
        appendChat("user", text)
        val resumeExecution = autoTaskStore.snapshot().started

        viewModelScope.launch {
            val planned = planAutomaticTasks(text)
            autoTaskStore.replaceWaitingTasks(planned)

            if (resumeExecution) {
                autoTaskStore.setStarted(true)
                if (!_state.value.autoWorkerActive) {
                    autoTaskStore.markWorkerStarting()
                }
                runCatching {
                    AutoTaskWorker.enqueue(getApplication())
                }.onFailure { error ->
                    autoTaskStore.setStarted(false)
                    autoTaskStore.markWorkerStopped(
                        message = "تعذر البدء",
                        error = error.message.orEmpty()
                    )
                }
            }

            if (askToStartAfter && autoTaskStore.snapshot().tasks.any {
                    it.status == AutoTaskStatus.WAITING
                }
            ) {
                autoTaskStore.setAwaitingConfirmation(true)
                appendChat("assistant", "هل تريد أن أبدأ؟")
            }
        }
    }

    private suspend fun planAutomaticTasks(newRequest: String): List<AutoTaskItem> {
        val existingWaiting = autoTaskStore.snapshot().tasks
            .filter { it.status == AutoTaskStatus.WAITING }
            .sortedBy { it.order }

        val existingText = if (existingWaiting.isEmpty()) {
            "لا توجد مهام سابقة."
        } else {
            existingWaiting.joinToString("\n") {
                "- ${it.title}: ${it.requirements.take(700)}"
            }
        }

        val plannerPrompt = """
            رتّب قائمة مهام برمجية للتنفيذ الفعلي. ادمج المهمة الجديدة مع المهام المنتظرة،
            ورتبها حسب الاعتماد المنطقي: ما يجب إنجازه أولًا يسبق ما يعتمد عليه.
            لا تشرح ولا تنفذ. أعد القائمة فقط، كل مهمة في سطر بهذه الصيغة:
            TASK: عنوان قصير || التفاصيل التنفيذية

            المهام المنتظرة:
            $existingText

            الطلب الجديد:
            $newRequest
        """.trimIndent()

        val response = runCatching {
            directChat.chat(prompt = plannerPrompt)
        }.getOrNull().orEmpty()

        val parsed = response.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("TASK:", ignoreCase = true) }
            .map { line ->
                val body = line.substringAfter(':').trim()
                val title = body.substringBefore("||").trim().take(90)
                val details = body.substringAfter("||", body).trim().take(3_000)
                title to details
            }
            .filter { (title, _) -> title.isNotBlank() }
            .toList()

        val fallback = if (parsed.isEmpty()) {
            splitFallbackTasks(newRequest)
        } else {
            parsed
        }

        return fallback.mapIndexed { index, (title, details) ->
            AutoTaskItem(
                id = "auto-${System.currentTimeMillis()}-$index",
                title = title.ifBlank { "مهمة ${index + 1}" },
                requirements = details.ifBlank { title },
                order = index + 1
            )
        }
    }

    private fun splitFallbackTasks(text: String): List<Pair<String, String>> {
        val parts = text
            .split(Regex("""[\n؛;]+"""))
            .map { it.trim().trimStart('-', '•', ' ') }
            .filter { it.isNotBlank() }

        val usable = if (parts.size > 1) parts else listOf(text.trim())
        return usable.map { part ->
            part.take(80) to part.take(3_000)
        }
    }

    private fun appendChat(role: String, text: String) {
        if (text.isBlank()) return
        _state.update {
            it.copy(
                chatHistory = (it.chatHistory + ChatTurn(role, text)).takeLast(40)
            )
        }
    }

    private fun isAutoTaskRequest(text: String): Boolean {
        if (isProgrammingRequest(text)) return true

        val value = text.trim().lowercase()
        if (value.isBlank()) return false
        if (value in setOf("تمام", "اوكي", "أوكي", "شكرا", "شكرًا", "حلو", "ممتاز")) {
            return false
        }

        val questionStarters = listOf(
            "هل ", "وش ", "ما ", "ليش ", "كيف ", "متى ", "وين ",
            "ايش ", "اشرح", "فسر", "وضح", "لخص"
        )
        return questionStarters.none { value.startsWith(it) }
    }

    private fun isExecutionTrigger(text: String): Boolean {
        val value = text.trim().lowercase()
        return EXECUTION_TRIGGERS.any { trigger ->
            value == trigger ||
                value.startsWith("$trigger ") ||
                value.contains(" $trigger")
        }
    }

    private fun isPositiveStart(text: String): Boolean {
        val value = text.trim().lowercase()
        return START_CONFIRMATIONS.any { value == it || value.startsWith("$it ") }
    }

    private fun isNegativeStart(text: String): Boolean {
        val value = text.trim().lowercase()
        return value in setOf("لا", "لا تبدأ", "لاتبدأ", "الغ", "إلغاء", "الغي", "ألغ")
    }

    private fun isRepositoryExecutionRequest(text: String): Boolean {
        val value = text.trim().lowercase()
        if (value.isBlank()) return false

        val repositoryCue = REPOSITORY_EXECUTION_WORDS.any {
            value.contains(it)
        }

        return repositoryCue && isProgrammingRequest(text)
    }

    private fun isImageGenerationRequest(text: String): Boolean {
        val value = text.trim().lowercase()
        if (value.isBlank()) return false

        return IMAGE_GENERATION_PATTERNS.any {
            it.containsMatchIn(value)
        }
    }

    private fun generateImage(prompt: String) {
        manualChatJob?.cancel()
        responseGeneration += 1L
        val generation = responseGeneration

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

        manualChatJob = viewModelScope.launch {
            try {
                val cleanPrompt = prompt
                    .replace(
                        Regex(
                            """(?i)(صمم|صمّم|أنشئ|انشئ|ولد|ولّد|ارسم|اصنع|سوي|سو|generate|create|draw|make)\s+(لي\s+)?(صورة|صوره|image|picture)"""
                        ),
                        ""
                    )
                    .trim()
                    .ifBlank { prompt.trim() }

                val seed = (
                    cleanPrompt.hashCode().toLong() and 0x7fffffffL
                    )

                val encodedPrompt = Uri.encode(cleanPrompt)
                val candidateUrls = listOf(
                    "https://image.pollinations.ai/prompt/" +
                        encodedPrompt +
                        "?width=1024&height=1024" +
                        "&model=flux" +
                        "&seed=${seed}" +
                        "&nologo=true" +
                        "&enhance=true",
                    "https://image.pollinations.ai/prompt/" +
                        encodedPrompt +
                        "?width=1024&height=1024" +
                        "&seed=${seed}" +
                        "&nologo=true"
                )

                val imageFile = withContext(Dispatchers.IO) {
                    var lastError = "تعذر توليد الصورة"

                    for (url in candidateUrls) {
                        val request = Request.Builder()
                            .url(url)
                            .header(
                                "User-Agent",
                                "HAI-OM/${BuildConfig.VERSION_NAME}"
                            )
                            .header(
                                "Accept",
                                "image/avif,image/webp,image/apng,image/*,*/*;q=0.8"
                            )
                            .build()

                        val result = runCatching {
                            imageClient.newCall(request)
                                .execute()
                                .use { response ->
                                    if (!response.isSuccessful) {
                                        error(
                                            "HTTP ${response.code}"
                                        )
                                    }

                                    val body = response.body
                                        ?: error("استجابة صورة فارغة")

                                    val contentType = body.contentType()
                                        ?.toString()
                                        .orEmpty()

                                    if (
                                        contentType.isNotBlank() &&
                                        !contentType.startsWith("image/")
                                    ) {
                                        error(
                                            "الاستجابة ليست صورة"
                                        )
                                    }

                                    val bytes = body.bytes()
                                    if (bytes.size < 1_024) {
                                        error("ملف الصورة غير صالح")
                                    }

                                    val extension = when {
                                        contentType.contains("png") -> "png"
                                        contentType.contains("webp") -> "webp"
                                        else -> "jpg"
                                    }

                                    val directory = File(
                                        getApplication<Application>().cacheDir,
                                        "generated_images"
                                    ).apply {
                                        mkdirs()
                                    }

                                    directory.listFiles()
                                        ?.sortedByDescending {
                                            it.lastModified()
                                        }
                                        ?.drop(12)
                                        ?.forEach {
                                            runCatching { it.delete() }
                                        }

                                    File(
                                        directory,
                                        "hai_${System.currentTimeMillis()}_${seed}.${extension}"
                                    ).apply {
                                        writeBytes(bytes)
                                    }
                                }
                        }

                        val file = result.getOrNull()
                        if (file != null && file.exists()) {
                            return@withContext file
                        }

                        lastError = result.exceptionOrNull()
                            ?.message
                            ?.takeIf { it.isNotBlank() }
                            ?: lastError
                    }

                    error(lastError)
                }

                if (
                    generation != responseGeneration ||
                    _state.value.autoExecuteEnabled
                ) {
                    runCatching { imageFile.delete() }
                    return@launch
                }

                val imageTurn = ChatTurn(
                    role = "assistant",
                    text = "[[HAI_IMAGE_FILE]]${imageFile.absolutePath}"
                )

                _state.update {
                    it.copy(
                        running = false,
                        programming = false,
                        chatHistory = (
                            it.chatHistory + imageTurn
                            ).takeLast(20),
                        result = AgentRunResult(
                            branch = "",
                            pullRequestUrl = null,
                            completedTasks = 1,
                            totalTasks = 1,
                            answer = "تم إنشاء الصورة"
                        )
                    )
                }
            } catch (_: CancellationException) {
                if (generation == responseGeneration) {
                    _state.update {
                        it.copy(
                            running = false,
                            programming = false
                        )
                    }
                }
            } catch (t: Throwable) {
                if (generation == responseGeneration) {
                    _state.update {
                        it.copy(
                            running = false,
                            programming = false,
                            error = "تعذر إنشاء الصورة: " +
                                (
                                    t.message
                                        ?.takeIf { it.isNotBlank() }
                                        ?: "خطأ في خدمة الصور"
                                    )
                        )
                    }
                }
            } finally {
                if (generation == responseGeneration) {
                    manualChatJob = null
                }
            }
        }
    }

    private fun runChat(prompt: String) {
        manualChatJob?.cancel()
        responseGeneration += 1L
        val generation = responseGeneration

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

        manualChatJob = viewModelScope.launch {
            try {
                val needsRepo = needsRepositoryContext(prompt)
                val repositoryUrl = _state.value.selectedRepository?.htmlUrl.orEmpty()
                val context = if (needsRepo) {
                    if (repositoryUrl.isBlank()) error("اختر مشروعًا")
                    val token = resolveGitHubToken() ?: error("اربط GitHub")
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
                    repositoryContext = context,
                    fast = true
                )

                if (generation != responseGeneration ||
                    _state.value.autoExecuteEnabled
                ) {
                    return@launch
                }

                val updatedHistory = (
                    _state.value.chatHistory +
                        ChatTurn("assistant", answer)
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
            } catch (_: CancellationException) {
                if (generation == responseGeneration) {
                    _state.update {
                        it.copy(
                            running = false,
                            programming = false
                        )
                    }
                }
            } catch (t: Throwable) {
                if (generation == responseGeneration &&
                    !_state.value.autoExecuteEnabled
                ) {
                    _state.update {
                        it.copy(
                            running = false,
                            programming = false,
                            error = t.message?.takeIf { message ->
                                message.isNotBlank()
                            } ?: "تعذر الرد الآن"
                        )
                    }
                }
            } finally {
                if (generation == responseGeneration) {
                    manualChatJob = null
                }
            }
        }
    }

    private fun runProgramming(requirements: String) {
        val repositoryUrl = _state.value.selectedRepository?.htmlUrl.orEmpty()
        if (repositoryUrl.isBlank()) {
            _state.update { it.copy(error = "اختر مشروعًا أولًا") }
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
                programmingLiveCode = "",
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
                        .run(
                            repositoryUrl = repositoryUrl,
                            requirements = effectiveRequirements,
                            onEvent = ::appendLog,
                            onLiveCode = { code ->
                                _state.update { current ->
                                    current.copy(programmingLiveCode = code)
                                }
                            }
                        )
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
                            ?: "تعذر إكمال المهمة"
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
            raw.contains("مهلة") -> "انتهت المهلة"
            raw.contains("timeout", true) || raw.contains("مهلة") -> "انتهت المهلة"
            raw.contains("تعذر إنشاء ربط GitHub") -> "تعذر إنشاء الربط"
            else -> "تعذر ربط GitHub"
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
        private val EXECUTION_TRIGGERS = listOf(
            "نفذ", "نفّذ", "ابدأ", "ابدا", "ابدء", "شغل", "شغّل",
            "ابدأ التنفيذ", "نفذ المهام", "ابدأ المهام", "توكل", "يلا"
        )

        private val START_CONFIRMATIONS = listOf(
            "نعم", "اي", "إي", "ايوه", "أيوه", "ابدأ", "ابدا",
            "نفذ", "نفّذ", "ابدأ التنفيذ", "توكل", "يلا", "موافق"
        )

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

        private val REPOSITORY_EXECUTION_WORDS = listOf(
            "المشروع", "المستودع", "github", "جيت هب", "قيت هب",
            "repo", "repository", "اتصل بالمستودع", "على المشروع",
            "في المشروع", "داخل المشروع", "ملفات المشروع"
        )

        private val IMAGE_GENERATION_PATTERNS = listOf(
            Regex("""(^|\s)(صمم|صمّم|أنشئ|انشئ|ولد|ولّد|ارسم|اصنع|سوي|سو)\s+(لي\s+)?(صورة|صوره|تصميم|رسمة|رسمه)(\s|$)"""),
            Regex("""(^|\s)(توليد|إنشاء|انشاء|تصميم)\s+(صورة|صوره|صور)(\s|$)"""),
            Regex("""(^|\s)(generate|create|draw|make)\s+(an?\s+)?(image|picture|illustration)(\s|$)""", RegexOption.IGNORE_CASE)
        )

        private val REPOSITORY_CONTEXT_WORDS = listOf(
            "المشروع", "المستودع", "الملفات", "repo", "repository",
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
