package com.haiom.app.ui

import android.app.Activity
import android.Manifest
import android.content.ClipData
import android.content.Context
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import com.haiom.app.MainViewModel
import com.haiom.app.automation.AutoTaskItem
import com.haiom.app.automation.AutoTaskStatus
import com.haiom.app.model.GitHubRepository
import com.haiom.app.network.ChatTurn
import kotlinx.coroutines.delay

private val Canvas = Color(0xFFF8FBFF)
private val SurfaceSoft = Color(0xFFFFFFFF)
private val Lavender = Color(0xFFF2F1FF)
private val LavenderStrong = Color(0xFFE9E7FF)
private val Line = Color(0xFFE4EAF2)
private val Blue = Color(0xFF4F6BFF)
private val BlueSoft = Color(0xFFEEF2FF)
private val Ink = Color(0xFF172033)
private val Muted = Color(0xFF8A94A6)
private val Violet = Color(0xFF8C7CFF)
private val SurfaceElevated = Color(0xFFFFFFFF)
private val Signal = Color(0xFFFFB6A3)
private val Moss = Color(0xFF62C6A5)
private val Sky = Color(0xFFDDF7FF)
private val Peach = Color(0xFFFFEEE9)
private val PaperShadow = Color(0x120D2744)

private enum class ToolPanel { PROJECTS, HISTORY, MODELS, SETTINGS }
private enum class SettingsPage { ROOT, GITHUB, AUTOMATION }

private data class PickedMedia(val uri: Uri, val name: String)

@Composable
fun HaiOmApp(
    vm: MainViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var draft by remember { mutableStateOf("") }
    val selectedRepo = state.selectedRepository
    var dockOpen by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf<ToolPanel?>(null) }
    var showAutoExecuteConfirm by remember { mutableStateOf(false) }
    var openAutomationSettings by remember { mutableStateOf(false) }
    var lastHomeBackAt by remember { mutableStateOf(0L) }
    var runtimeNow by remember { mutableStateOf(System.currentTimeMillis()) }
    val media = remember { mutableStateListOf<PickedMedia>() }
    var voiceListening by remember { mutableStateOf(false) }
    val speechRecognizer = remember(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }
    }

    fun startVoiceRecognition() {
        val recognizer = speechRecognizer
        if (recognizer == null) {
            Toast.makeText(
                context,
                "التعرف على الصوت غير متاح على هذا الجهاز",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar-SA")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        voiceListening = true
        runCatching {
            recognizer.startListening(intent)
        }.onFailure {
            voiceListening = false
            Toast.makeText(
                context,
                "تعذر بدء التسجيل الصوتي",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val microphonePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceRecognition()
        } else {
            Toast.makeText(
                context,
                "اسمح بالميكروفون لإرسال رسالة صوتية",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    DisposableEffect(speechRecognizer, vm) {
        speechRecognizer?.setRecognitionListener(
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    voiceListening = true
                }

                override fun onBeginningOfSpeech() {
                    voiceListening = true
                }

                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit

                override fun onError(error: Int) {
                    voiceListening = false
                    if (
                        error != SpeechRecognizer.ERROR_NO_MATCH &&
                        error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                    ) {
                        Toast.makeText(
                            context,
                            "تعذر فهم الصوت، حاول مرة أخرى",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onResults(results: Bundle?) {
                    voiceListening = false
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()

                    if (text.isNotBlank()) {
                        vm.runAgent(text)
                    } else {
                        Toast.makeText(
                            context,
                            "لم يتم التقاط كلام واضح",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            }
        )

        onDispose {
            runCatching { speechRecognizer?.cancel() }
            runCatching { speechRecognizer?.destroy() }
        }
    }

    val requestVoiceInput: () -> Unit = {
        if (voiceListening) {
            speechRecognizer?.stopListening()
        } else if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startVoiceRecognition()
        } else {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val mediaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { uri ->
            if (media.none { it.uri == uri }) {
                media += PickedMedia(uri, displayName(context, uri))
            }
        }
    }

    LaunchedEffect(state.githubJustLinked) {
        if (state.githubJustLinked) {
            dockOpen = false
            panel = ToolPanel.PROJECTS
            vm.consumeGitHubJustLinked()
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            vm.clearError()
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }
    LaunchedEffect(state.githubLaunchUrl) {
        val url = state.githubLaunchUrl ?: return@LaunchedEffect
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
            )
        }
        vm.consumeGitHubLaunch()
    }

    if (state.githubLinking) {
        GitHubLinkDialog(
            status = state.githubLinkStatus,
            userCode = state.githubDeviceCode,
            onOpenGitHub = vm::openGitHubVerification,
            onCancel = vm::cancelGitHubLink
        )
    }

    LaunchedEffect(Unit) {
        while (true) {
            runtimeNow = System.currentTimeMillis()
            delay(2_000)
        }
    }

    BackHandler(enabled = panel == null) {
        when {
            showAutoExecuteConfirm -> showAutoExecuteConfirm = false
            dockOpen -> dockOpen = false
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastHomeBackAt <= 2_000L) {
                    (context as? Activity)?.finish()
                } else {
                    lastHomeBackAt = now
                    Toast.makeText(
                        context,
                        "اضغط رجوع مرة أخرى للخروج",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    state.updateInfo?.let { update ->
        UpdateDialog(
            versionName = update.versionName,
            onDismiss = vm::dismissUpdate,
            onOpenDownload = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(update.downloadUrl))
                    )
                }
                vm.dismissUpdate()
            }
        )
    }

    Scaffold(
        containerColor = Canvas,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (panel == null) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Canvas)
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    if (media.isNotEmpty()) {
                        MediaStrip(media) { media.remove(it) }
                    }
                    CompactComposer(
                        value = draft,
                        onValueChange = { draft = it },
                        enabled = !state.running || state.autoExecuteEnabled,
                        onMedia = {
                            mediaPicker.launch(
                                arrayOf("image/*", "video/*", "application/pdf", "text/plain")
                            )
                        },
                        voiceListening = voiceListening,
                        onVoiceLongPress = requestVoiceInput,
                        onSend = {
                            if (draft.isBlank() || (state.running && !state.autoExecuteEnabled)) return@CompactComposer
                            val attachmentNote = if (media.isEmpty()) "" else
                                "\n\nالمرفقات المختارة: " + media.joinToString(", ") { it.name }
                            vm.runAgent(
                                draft.trim() + attachmentNote
                            )
                            draft = ""
                            media.clear()
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().background(Canvas)) {
            if (panel == null) {
                ChatCanvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = innerPadding.calculateBottomPadding()),
                    history = state.chatHistory,
                    standaloneAnswer = state.result?.answer,
                    running = state.running,
                    programming = state.programming,
                    progressText = state.logs.lastOrNull(),
                    autoTasks = state.autoTasks,
                    autoQueueStarted = state.autoQueueStarted,
                    autoPaused = state.autoPaused,
                    autoLiveCode = state.autoLiveCode,
                    programmingLiveCode = state.programmingLiveCode,
                    autoWorkerActive = state.autoWorkerActive,
                    autoWorkerHeartbeatAt = state.autoWorkerHeartbeatAt,
                    autoWorkerMessage = state.autoWorkerMessage,
                    autoWorkerError = state.autoWorkerError,
                    remoteRepository = state.autoRemoteRepository,
                    remoteRunId = state.autoRemoteRunId,
                    remoteRunUrl = state.autoRemoteRunUrl,
                    remoteState = state.autoRemoteState,
                    remoteStage = state.autoRemoteStage,
                    runtimeNow = runtimeNow,
                    onTogglePause = vm::toggleAutoPause,
                    onOpenTasks = {
                        openAutomationSettings = true
                        panel = ToolPanel.SETTINGS
                    }
                )
            } else {
                ToolScreen(
                    panel = panel!!,
                    repositories = state.repositories,
                    selectedRepo = selectedRepo,
                    connected = state.hasGitHubToken,
                    login = state.githubLogin,
                    linking = state.githubLinking,
                    linkingStatus = state.githubLinkStatus,
                    oauthAvailable = state.githubOAuthAvailable,
                    repositoryCount = state.repositories.size,
                    logs = state.logs,
                    answer = state.result?.answer,
                    prUrl = state.result?.pullRequestUrl,
                    models = state.rankings.map { it.modelName },
                    omniReady = state.omniReady,
                    checkingUpdate = state.checkingUpdate,
                    autoExecuteEnabled = state.autoExecuteEnabled,
                    autoTasks = state.autoTasks,
                    autoQueueStarted = state.autoQueueStarted,
                    autoPaused = state.autoPaused,
                    autoWorkerActive = state.autoWorkerActive,
                    autoWorkerHeartbeatAt = state.autoWorkerHeartbeatAt,
                    autoWorkerMessage = state.autoWorkerMessage,
                    autoWorkerError = state.autoWorkerError,
                    remoteRepository = state.autoRemoteRepository,
                    remoteRunId = state.autoRemoteRunId,
                    remoteRunUrl = state.autoRemoteRunUrl,
                    remoteState = state.autoRemoteState,
                    remoteStage = state.autoRemoteStage,
                    runtimeNow = runtimeNow,
                    initialSettingsPage = if (openAutomationSettings) {
                        SettingsPage.AUTOMATION
                    } else {
                        SettingsPage.ROOT
                    },
                    onBack = {
                        panel = null
                        openAutomationSettings = false
                    },
                    onSaveRepo = {
                        vm.saveSelectedRepository(it)
                        panel = null
                    },
                    onOpenProjects = {
                        panel = ToolPanel.PROJECTS
                    },
                    onConnect = vm::startGitHubLink,
                    onCancelLink = vm::cancelGitHubLink,
                    onDisconnect = vm::disconnectGitHub,
                    onCheckUpdate = vm::checkForUpdate,
                    onRequestAutoExecute = { enabled ->
                        if (enabled) {
                            showAutoExecuteConfirm = true
                        } else {
                            vm.setAutoExecuteEnabled(false)
                        }
                    },
                    onTogglePause = vm::toggleAutoPause,
                    onOpenPr = { url ->
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    }
                )
            }

            if (showAutoExecuteConfirm) {
                AutoExecuteConfirmBanner(
                    onConfirm = {
                        vm.setAutoExecuteEnabled(true)
                        showAutoExecuteConfirm = false
                    },
                    onCancel = {
                        showAutoExecuteConfirm = false
                    },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 0.dp)
                )
            }

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                val screenHeight = LocalConfiguration.current.screenHeightDp
                val railTop = when {
                    screenHeight < 650 -> 82.dp
                    screenHeight < 780 -> 104.dp
                    else -> 122.dp
                }

                Box(Modifier.fillMaxSize()) {
                    if (dockOpen) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.025f))
                                .clickable { dockOpen = false }
                        )
                    }

                    AnimatedToolDock(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .padding(top = railTop),
                        open = dockOpen,
                        connected = state.hasGitHubToken,
                        autoEnabled = state.autoExecuteEnabled,
                        onToggle = { dockOpen = !dockOpen },
                        onAutoExecute = {
                            if (state.autoExecuteEnabled) {
                                vm.setAutoExecuteEnabled(false)
                            } else {
                                showAutoExecuteConfirm = true
                            }
                            dockOpen = false
                        },
                        onNewChat = {
                            vm.clearChat()
                            draft = ""
                            media.clear()
                            panel = null
                            dockOpen = false
                        },
                        onProjects = {
                            panel = ToolPanel.PROJECTS
                            dockOpen = false
                        },
                        onHistory = {
                            panel = ToolPanel.HISTORY
                            dockOpen = false
                        },
                        onModels = {
                            panel = ToolPanel.MODELS
                            dockOpen = false
                        },
                        onSettings = {
                            openAutomationSettings = false
                            panel = ToolPanel.SETTINGS
                            dockOpen = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatCanvas(
    modifier: Modifier,
    history: List<ChatTurn>,
    standaloneAnswer: String?,
    running: Boolean,
    programming: Boolean,
    progressText: String?,
    autoTasks: List<AutoTaskItem>,
    autoQueueStarted: Boolean,
    autoPaused: Boolean,
    autoLiveCode: String,
    programmingLiveCode: String,
    autoWorkerActive: Boolean,
    autoWorkerHeartbeatAt: Long,
    autoWorkerMessage: String,
    autoWorkerError: String,
    remoteRepository: String,
    remoteRunId: Long,
    remoteRunUrl: String,
    remoteState: String,
    remoteStage: String,
    runtimeNow: Long,
    onTogglePause: () -> Unit,
    onOpenTasks: () -> Unit
) {
    val lastAnswer = history.lastOrNull { it.role == "assistant" }?.text
    val extraAnswer = standaloneAnswer?.takeIf { it.isNotBlank() && it != lastAnswer }
    val empty =
        history.isEmpty() &&
            extraAnswer == null &&
            !running &&
            !autoQueueStarted
    val listState = rememberLazyListState()
    val liveCode = if (autoQueueStarted) autoLiveCode else programmingLiveCode
    val showLiveCode =
        liveCode.isNotBlank() &&
            (autoQueueStarted || (running && programming))
    val renderedItemCount =
        history.size +
            if (extraAnswer != null) 1 else 0 +
            if (showLiveCode) 1 else 0 +
            if (running && !programming) 1 else 0

    LaunchedEffect(
        history.size,
        extraAnswer,
        running,
        progressText,
        showLiveCode,
        liveCode,
        remoteStage
    ) {
        if (renderedItemCount > 0) {
            listState.animateScrollToItem(renderedItemCount - 1)
        }
    }

    Column(
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 14.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        if (autoQueueStarted || autoWorkerError.isNotBlank()) {
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Ltr
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.TopStart
                ) {
                    AutoRuntimeStatusBar(
                        tasks = autoTasks,
                        queueStarted = autoQueueStarted,
                        paused = autoPaused,
                        workerActive = autoWorkerActive,
                        heartbeatAt = autoWorkerHeartbeatAt,
                        workerMessage = autoWorkerMessage,
                        workerError = autoWorkerError,
                        remoteRepository = remoteRepository,
                        remoteRunId = remoteRunId,
                        remoteRunUrl = remoteRunUrl,
                        remoteState = remoteState,
                        remoteStage = remoteStage,
                        now = runtimeNow,
                        onTogglePause = onTogglePause,
                        onOpenTasks = onOpenTasks,
                        modifier = Modifier
                            .fillMaxWidth(0.54f)
                            .offset(x = (-10).dp)
                    )
                }
            }
            Spacer(Modifier.height(7.dp))
        }

        if (empty) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                EmptyState(Modifier.align(Alignment.Center))
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    top = 18.dp,
                    bottom = 10.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(history) { turn ->
                    MessageBlock(turn)
                }

                extraAnswer?.let { answer ->
                    item {
                        MessageBlock(ChatTurn("assistant", answer))
                    }
                }

                if (showLiveCode) {
                    item {
                        LiveCodePreview(
                            code = liveCode,
                            now = runtimeNow,
                            paused = autoQueueStarted && autoPaused
                        )
                    }
                }

                if (running && !programming) {
                    item {
                        RunningLine(false, progressText)
                    }
                }
            }
        }
    }
}
@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(116.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(46.dp),
                color = Sky,
                shape = CircleShape
            ) {}
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(38.dp),
                color = Peach,
                shape = CircleShape
            ) {}
            Surface(
                modifier = Modifier.size(82.dp),
                color = SurfaceElevated,
                shape = RoundedCornerShape(30.dp),
                border = BorderStroke(1.dp, Line),
                shadowElevation = 12.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = Blue,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            "HAI",
            color = Ink,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "مساعدك للمحادثة والبرمجة والتنفيذ",
            color = Muted,
            fontSize = 12.sp
        )
    }
}

private data class ChatRenderSegment(
    val text: String,
    val isCode: Boolean,
    val language: String = ""
)

private fun parseChatSegments(raw: String): List<ChatRenderSegment> {
    val fence = 96.toChar().toString().repeat(3)
    val result = mutableListOf<ChatRenderSegment>()
    var cursor = 0

    while (cursor < raw.length) {
        val open = raw.indexOf(fence, cursor)
        if (open < 0) {
            raw.substring(cursor)
                .takeIf { it.isNotBlank() }
                ?.let { result += ChatRenderSegment(it.trim(), false) }
            break
        }

        if (open > cursor) {
            raw.substring(cursor, open)
                .takeIf { it.isNotBlank() }
                ?.let { result += ChatRenderSegment(it.trim(), false) }
        }

        val afterFence = open + fence.length
        val close = raw.indexOf(fence, afterFence)
        if (close < 0) {
            raw.substring(afterFence)
                .takeIf { it.isNotBlank() }
                ?.let { result += ChatRenderSegment(it.trim(), true) }
            break
        }

        var codeBody = raw.substring(afterFence, close)
            .trimStart('\n', '\r', ' ')
        var language = ""

        val firstBreak = codeBody.indexOf('\n')
        if (firstBreak >= 0) {
            val firstLine = codeBody.substring(0, firstBreak).trim()
            if (
                firstLine.length in 1..24 &&
                firstLine.matches(Regex("[A-Za-z0-9_+.#-]+"))
            ) {
                language = firstLine
                codeBody = codeBody.substring(firstBreak + 1)
            }
        }

        result += ChatRenderSegment(
            text = codeBody.trimEnd(),
            isCode = true,
            language = language
        )
        cursor = close + fence.length
    }

    if (result.isEmpty() && raw.isNotBlank()) {
        result += ChatRenderSegment(raw.trim(), false)
    }

    return result
}

private fun containsArabic(text: String): Boolean =
    text.any { ch ->
        ch.code in 0x0600..0x06FF ||
            ch.code in 0x0750..0x077F ||
            ch.code in 0x08A0..0x08FF
    }

private fun cleanChatProse(text: String): String =
    text
        .replace("**", "")
        .replace("__", "")
        .trim()

private fun copyCode(context: Context, code: String) {
    val clipboard = context.getSystemService(
        Context.CLIPBOARD_SERVICE
    ) as ClipboardManager

    clipboard.setPrimaryClip(
        ClipData.newPlainText("HAI code", code)
    )

    Toast.makeText(
        context,
        "تم نسخ الكود",
        Toast.LENGTH_SHORT
    ).show()
}

@Composable
private fun MessageBlock(turn: ChatTurn) {
    val user = turn.role == "user"
    val context = LocalContext.current
    val segments = remember(turn.text) {
        parseChatSegments(turn.text)
    }
    val hasCode = segments.any { it.isCode }

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth()
    ) {
        val cardMaxWidth = maxWidth * when {
            user -> 0.72f
            hasCode -> 0.95f
            else -> 0.82f
        }

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = if (user) {
                AbsoluteAlignment.CenterRight
            } else {
                AbsoluteAlignment.CenterLeft
            }
        ) {
            Surface(
                modifier = Modifier.widthIn(
                    min = 92.dp,
                    max = cardMaxWidth
                ),
                color = if (user) Peach else SurfaceElevated,
                shape = if (user) {
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = 18.dp,
                        bottomEnd = 6.dp
                    )
                } else {
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = 6.dp,
                        bottomEnd = 18.dp
                    )
                },
                border = BorderStroke(
                    1.dp,
                    if (user) Color(0xFFFFD9CD) else Line
                ),
                shadowElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = 12.dp,
                        vertical = 9.dp
                    )
                ) {
                    CompositionLocalProvider(
                        LocalLayoutDirection provides LayoutDirection.Rtl
                    ) {
                        Text(
                            if (user) "أنت" else "HAI",
                            modifier = Modifier.fillMaxWidth(),
                            color = if (user) Color(0xFFB66A55) else Blue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Right
                        )
                    }

                    Spacer(Modifier.height(5.dp))

                    segments.forEachIndexed { index, segment ->
                        if (segment.isCode) {
                            CompositionLocalProvider(
                                LocalLayoutDirection provides LayoutDirection.Ltr
                            ) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Color(0xFFF5F7FC),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        Color(0xFFDCE3F1)
                                    )
                                ) {
                                    Column {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFFECF1FF))
                                                .padding(
                                                    horizontal = 9.dp,
                                                    vertical = 6.dp
                                                ),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                segment.language.ifBlank { "CODE" },
                                                modifier = Modifier.weight(1f),
                                                color = Blue,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                textAlign = TextAlign.Left
                                            )

                                            Surface(
                                                modifier = Modifier
                                                    .clickable {
                                                        copyCode(
                                                            context,
                                                            segment.text
                                                        )
                                                    },
                                                color = Color.White,
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(
                                                    1.dp,
                                                    Color(0xFFDCE3F1)
                                                )
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(
                                                        horizontal = 8.dp,
                                                        vertical = 4.dp
                                                    ),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        Icons.Outlined.ContentCopy,
                                                        contentDescription = "نسخ الكود",
                                                        tint = Blue,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(Modifier.width(4.dp))
                                                    Text(
                                                        "نسخ",
                                                        color = Blue,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }

                                        Text(
                                            segment.text,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(
                                                    horizontal = 11.dp,
                                                    vertical = 10.dp
                                                ),
                                            color = Color(0xFF24304A),
                                            fontSize = 12.sp,
                                            lineHeight = 18.sp,
                                            fontFamily = FontFamily.Monospace,
                                            textAlign = TextAlign.Left
                                        )
                                    }
                                }
                            }
                        } else {
                            val prose = cleanChatProse(segment.text)
                            val rtl = containsArabic(prose)

                            CompositionLocalProvider(
                                LocalLayoutDirection provides if (rtl) {
                                    LayoutDirection.Rtl
                                } else {
                                    LayoutDirection.Ltr
                                }
                            ) {
                                Text(
                                    prose,
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Ink,
                                    fontSize = 14.sp,
                                    lineHeight = 21.sp,
                                    textAlign = if (rtl) {
                                        TextAlign.Right
                                    } else {
                                        TextAlign.Left
                                    }
                                )
                            }
                        }

                        if (index != segments.lastIndex) {
                            Spacer(Modifier.height(9.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AutoEventLine(text: String) {
    val icon = when {
        text.contains("— تم") || text.contains("✓") -> Icons.Outlined.CheckCircle
        text.contains("فشل") -> Icons.Outlined.Close
        text.contains("قيد المعالجة") -> Icons.Outlined.AutoAwesome
        else -> Icons.Outlined.MoreVert
    }

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            null,
            tint = if (text.contains("فشل")) Muted else Blue,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text,
            color = Muted,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun RunningLine(programming: Boolean, progressText: String?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Blue)
        Spacer(Modifier.width(9.dp))
        Text(
            if (programming) progressText ?: "جاري تنفيذ المطلوب…" else "جاري الرد…",
            color = Muted,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun CompactComposer(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    onMedia: () -> Unit,
    voiceListening: Boolean,
    onVoiceLongPress: () -> Unit,
    onSend: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        color = Color(0xF8FFFFFF),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, Line),
        shadowElevation = 16.dp
    ) {
        Row(
            modifier = Modifier.padding(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .size(44.dp)
                    .pointerInput(enabled, value, voiceListening) {
                        detectTapGestures(
                            onTap = {
                                if (
                                    enabled &&
                                    value.isNotBlank() &&
                                    !voiceListening
                                ) {
                                    onSend()
                                }
                            },
                            onLongPress = {
                                if (enabled) {
                                    onVoiceLongPress()
                                }
                            }
                        )
                    },
                color = when {
                    voiceListening -> Color(0xFFFFE5DD)
                    enabled && value.isNotBlank() -> Blue
                    else -> BlueSoft
                },
                shape = CircleShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (voiceListening) {
                            Icons.Outlined.Mic
                        } else {
                            Icons.Outlined.ArrowUpward
                        },
                        contentDescription = if (voiceListening) {
                            "جاري الاستماع"
                        } else {
                            "إرسال؛ اضغط مطولًا للصوت"
                        },
                        tint = when {
                            voiceListening -> Color(0xFFB66A55)
                            enabled && value.isNotBlank() -> Color.White
                            else -> Blue
                        },
                        modifier = Modifier.size(21.dp)
                    )
                }
            }

            Spacer(Modifier.width(7.dp))

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = Ink,
                        fontSize = 15.sp
                    ),
                    cursorBrush = SolidColor(Blue),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (value.isNotBlank() && enabled) onSend()
                        }
                    ),
                    decorationBox = { inner ->
                        Box {
                            if (value.isBlank()) {
                                Text(
                                    "اكتب رسالتك…",
                                    color = Muted,
                                    fontSize = 14.sp
                                )
                            }
                            inner()
                        }
                    }
                )
            }

            Spacer(Modifier.width(7.dp))

            Surface(
                modifier = Modifier
                    .size(44.dp)
                    .clickable(enabled = enabled, onClick = onMedia),
                color = Lavender,
                shape = CircleShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = "إضافة",
                        tint = Violet,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaStrip(
    media: List<PickedMedia>,
    onRemove: (PickedMedia) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        media.take(2).forEach { item ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = BlueSoft,
                shape = RoundedCornerShape(13.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 3.dp, top = 4.dp, bottom = 4.dp)
                ) {
                    Text(
                        item.name,
                        Modifier
                            .align(Alignment.CenterStart)
                            .padding(end = 36.dp),
                        color = Color(0xFF7D8780),
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                    IconButton(
                        onClick = { onRemove(item) },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(28.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Close,
                            "إزالة",
                            Modifier.size(16.dp),
                            tint = Color(0xFF8A938D)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedToolDock(
    modifier: Modifier,
    open: Boolean,
    connected: Boolean,
    autoEnabled: Boolean,
    onToggle: () -> Unit,
    onAutoExecute: () -> Unit,
    onNewChat: () -> Unit,
    onProjects: () -> Unit,
    onHistory: () -> Unit,
    onModels: () -> Unit,
    onSettings: () -> Unit
) {
    val shift by animateDpAsState(
        targetValue = if (open) 0.dp else (-58).dp,
        animationSpec = tween(durationMillis = 230),
        label = "pearlRail"
    )

    Box(
        modifier = modifier
            .width(76.dp)
            .height(452.dp)
    ) {
        Column(
            modifier = Modifier
                .offset(x = shift)
                .padding(start = 4.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PearlAction(
                icon = Icons.Outlined.ChatBubbleOutline,
                tint = Blue,
                background = BlueSoft,
                onClick = onNewChat
            )
            PearlAction(
                icon = Icons.Outlined.FolderOpen,
                tint = Color(0xFF4A9D83),
                background = Color(0xFFE9F8F3),
                onClick = onProjects
            )
            PearlAction(
                icon = Icons.Outlined.History,
                tint = Color(0xFFB06B57),
                background = Peach,
                onClick = onHistory
            )
            PearlAction(
                icon = Icons.Outlined.Code,
                tint = Violet,
                background = Lavender,
                onClick = onModels
            )
            PearlAction(
                icon = if (connected) Icons.Outlined.Settings else Icons.Outlined.Link,
                tint = Color(0xFF566274),
                background = Color(0xFFF0F3F7),
                onClick = onSettings
            )
            Spacer(Modifier.height(110.dp))
            PearlAutoAction(
                enabled = autoEnabled,
                onClick = onAutoExecute
            )
        }

        if (!open) {
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(38.dp)
                    .clickable(onClick = onToggle),
                color = SurfaceElevated,
                shape = CircleShape,
                border = BorderStroke(1.dp, Line),
                shadowElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        contentDescription = "فتح الأدوات",
                        tint = Blue,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PearlAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    background: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(46.dp)
            .clickable(onClick = onClick),
        color = background,
        shape = CircleShape,
        border = BorderStroke(1.dp, Color.White),
        shadowElevation = 7.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(21.dp)
            )
        }
    }
}

@Composable
private fun PearlAutoAction(
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(46.dp)
            .clickable(onClick = onClick),
        color = if (enabled) BlueSoft else Lavender,
        shape = CircleShape,
        border = BorderStroke(
            1.dp,
            if (enabled) Color(0xFFCBD5FF) else Color.White
        ),
        shadowElevation = 7.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                "تلقائي",
                color = if (enabled) Blue else Violet,
                fontSize = 8.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ToolScreen(
    panel: ToolPanel,
    repositories: List<GitHubRepository>,
    selectedRepo: GitHubRepository?,
    connected: Boolean,
    login: String,
    linking: Boolean,
    linkingStatus: String,
    oauthAvailable: Boolean,
    repositoryCount: Int,
    logs: List<String>,
    answer: String?,
    prUrl: String?,
    models: List<String>,
    omniReady: Boolean,
    checkingUpdate: Boolean,
    autoExecuteEnabled: Boolean,
    autoTasks: List<AutoTaskItem>,
    autoQueueStarted: Boolean,
    autoPaused: Boolean,
    autoWorkerActive: Boolean,
    autoWorkerHeartbeatAt: Long,
    autoWorkerMessage: String,
    autoWorkerError: String,
    remoteRepository: String,
    remoteRunId: Long,
    remoteRunUrl: String,
    remoteState: String,
    remoteStage: String,
    runtimeNow: Long,
    initialSettingsPage: SettingsPage,
    onBack: () -> Unit,
    onSaveRepo: (GitHubRepository) -> Unit,
    onOpenProjects: () -> Unit,
    onConnect: () -> Unit,
    onCancelLink: () -> Unit,
    onDisconnect: () -> Unit,
    onCheckUpdate: () -> Unit,
    onRequestAutoExecute: (Boolean) -> Unit,
    onTogglePause: () -> Unit,
    onOpenPr: (String) -> Unit
) {
    var settingsPage by remember(panel, initialSettingsPage) {
        mutableStateOf(
            if (panel == ToolPanel.SETTINGS) {
                initialSettingsPage
            } else {
                SettingsPage.ROOT
            }
        )
    }

    val title = when (panel) {
        ToolPanel.PROJECTS -> "المشاريع"
        ToolPanel.HISTORY -> "السجل والتعديلات"
        ToolPanel.MODELS -> "النماذج"
        ToolPanel.SETTINGS -> when (settingsPage) {
            SettingsPage.ROOT -> "الإعدادات"
            SettingsPage.GITHUB -> "GitHub"
            SettingsPage.AUTOMATION -> "المهام التلقائية"
        }
    }

    val handleBack: () -> Unit = {
        if (panel == ToolPanel.SETTINGS && settingsPage != SettingsPage.ROOT) {
            settingsPage = SettingsPage.ROOT
        } else {
            onBack()
        }
    }

    BackHandler { handleBack() }

    Column(
        Modifier
            .fillMaxSize()
            .background(Canvas)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp)
    ) {
        ToolHeader(title, handleBack)
        Spacer(Modifier.height(18.dp))

        when (panel) {
            ToolPanel.PROJECTS -> ProjectsContent(
                repositories = repositories,
                selectedRepo = selectedRepo,
                connected = connected,
                linking = linking,
                linkingStatus = linkingStatus,
                oauthAvailable = oauthAvailable,
                onSave = onSaveRepo,
                onConnect = onConnect,
                onCancelLink = onCancelLink
            )

            ToolPanel.HISTORY -> HistoryContent(
                logs = logs,
                answer = answer,
                prUrl = prUrl,
                onOpenPr = onOpenPr,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )

            ToolPanel.MODELS -> ModelsContent(models, omniReady)

            ToolPanel.SETTINGS -> when (settingsPage) {
                SettingsPage.ROOT -> SettingsRootContent(
                    checkingUpdate = checkingUpdate,
                    onOpenGitHub = { settingsPage = SettingsPage.GITHUB },
                    onOpenAutomation = { settingsPage = SettingsPage.AUTOMATION },
                    onCheckUpdate = onCheckUpdate,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )

                SettingsPage.GITHUB -> GitHubSettingsContent(
                    connected = connected,
                    login = login,
                    repositoryCount = repositoryCount,
                    selectedRepo = selectedRepo,
                    linking = linking,
                    linkingStatus = linkingStatus,
                    oauthAvailable = oauthAvailable,
                    onConnect = onConnect,
                    onCancelLink = onCancelLink,
                    onDisconnect = onDisconnect,
                    onOpenProjects = onOpenProjects,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )

                SettingsPage.AUTOMATION -> AutomaticTasksContent(
                    tasks = autoTasks,
                    running = autoQueueStarted,
                    paused = autoPaused,
                    workerActive = autoWorkerActive,
                    heartbeatAt = autoWorkerHeartbeatAt,
                    workerMessage = autoWorkerMessage,
                    workerError = autoWorkerError,
                    remoteRepository = remoteRepository,
                    remoteRunId = remoteRunId,
                    remoteRunUrl = remoteRunUrl,
                    remoteState = remoteState,
                    remoteStage = remoteStage,
                    now = runtimeNow,
                    onTogglePause = onTogglePause,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ToolHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier
                .size(42.dp)
                .clickable(onClick = onBack),
            color = SurfaceElevated,
            shape = CircleShape,
            border = BorderStroke(1.dp, Line),
            shadowElevation = 6.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.ArrowBack,
                    contentDescription = "رجوع",
                    tint = Blue,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = Ink,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                "مساحة HAI",
                color = Muted,
                fontSize = 10.sp
            )
        }

        Surface(
            color = BlueSoft,
            shape = RoundedCornerShape(18.dp)
        ) {
            Text(
                "LIVE",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                color = Blue,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun ProjectsContent(
    repositories: List<GitHubRepository>,
    selectedRepo: GitHubRepository?,
    connected: Boolean,
    linking: Boolean,
    linkingStatus: String,
    oauthAvailable: Boolean,
    onSave: (GitHubRepository) -> Unit,
    onConnect: () -> Unit,
    onCancelLink: () -> Unit
) {
    if (!connected) {
        GitHubConnectCard(
            linking = linking,
            status = linkingStatus,
            oauthAvailable = oauthAvailable,
            onConnect = onConnect,
            onCancel = onCancelLink
        )
        return
    }

    if (repositories.isEmpty()) {
        EmptyToolCard("لا توجد مشاريع", "")
        return
    }

    var pendingFullName by remember(
        selectedRepo?.fullName,
        repositories.joinToString("|") { it.fullName }
    ) {
        mutableStateOf(selectedRepo?.fullName.orEmpty())
    }

    val pendingRepository = repositories.firstOrNull {
        it.fullName == pendingFullName
    }

    Column(Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Sky,
            shape = RoundedCornerShape(26.dp)
        ) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    color = Color.White,
                    shape = RoundedCornerShape(17.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.FolderOpen,
                            contentDescription = null,
                            tint = Blue,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "مشاريعك",
                        color = Ink,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "${repositories.size} مشروع متاح",
                        color = Muted,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(repositories) { repo ->
                val pending = pendingFullName == repo.fullName
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { pendingFullName = repo.fullName },
                    color = if (pending) BlueSoft else SurfaceElevated,
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(
                        1.dp,
                        if (pending) Color(0xFFCBD5FF) else Line
                    ),
                    shadowElevation = if (pending) 8.dp else 3.dp
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(42.dp),
                            color = if (pending) Blue else Lavender,
                            shape = RoundedCornerShape(15.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    repo.fullName.substringAfter('/').take(1).uppercase(),
                                    color = if (pending) Color.White else Violet,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }

                        Spacer(Modifier.width(11.dp))

                        Column(Modifier.weight(1f)) {
                            Text(
                                repo.fullName.substringAfter('/'),
                                color = Ink,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                repo.fullName.substringBefore('/'),
                                color = Muted,
                                fontSize = 10.sp
                            )
                        }

                        if (pending) {
                            Surface(
                                color = Color.White,
                                shape = CircleShape
                            ) {
                                Icon(
                                    Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    tint = Blue,
                                    modifier = Modifier
                                        .padding(5.dp)
                                        .size(19.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    enabled = pendingRepository != null,
                    onClick = { pendingRepository?.let(onSave) }
                ),
            color = if (pendingRepository != null) Blue else LavenderStrong,
            shape = RoundedCornerShape(20.dp),
            shadowElevation = 8.dp
        ) {
            Text(
                "استخدام هذا المشروع",
                modifier = Modifier.padding(vertical = 14.dp),
                color = if (pendingRepository != null) Color.White else Muted,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun HistoryContent(
    logs: List<String>,
    answer: String?,
    prUrl: String?,
    onOpenPr: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (logs.isEmpty() && answer.isNullOrBlank()) {
        EmptyToolCard("السجل فارغ", "")
        return
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        items(logs.reversed().take(30)) { log ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = SurfaceElevated,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Line)
            ) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(34.dp),
                        color = Sky,
                        shape = CircleShape
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.History,
                                contentDescription = null,
                                tint = Blue,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        log,
                        modifier = Modifier.weight(1f),
                        color = Ink,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        answer?.takeIf { it.isNotBlank() }?.let { text ->
            item {
                Surface(
                    Modifier.fillMaxWidth(),
                    color = Peach,
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, Color(0xFFFFD8CB))
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "النتيجة",
                            color = Color(0xFFB66A55),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text,
                            color = Ink,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }

    if (!prUrl.isNullOrBlank()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenPr(prUrl) },
            color = BlueSoft,
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                Modifier.padding(13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Code,
                    contentDescription = null,
                    tint = Blue,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "فتح آخر تعديل",
                    color = Blue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ModelsContent(models: List<String>, ready: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (ready) Color(0xFFE9F8F3) else Lavender,
        shape = RoundedCornerShape(26.dp)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                color = Color.White,
                shape = CircleShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = if (ready) Moss else Muted,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.width(11.dp))
            Column {
                Text(
                    if (ready) "المحرك جاهز" else "المحرك غير جاهز",
                    color = Ink,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp
                )
                Text(
                    if (ready) "جاهز للمساعدة" else "تحقق من الاتصال",
                    color = Muted,
                    fontSize = 10.sp
                )
            }
        }
    }

    Spacer(Modifier.height(14.dp))

    models.take(12).forEachIndexed { index, model ->
        val tileColor = when (index % 4) {
            0 -> BlueSoft
            1 -> Lavender
            2 -> Peach
            else -> Sky
        }
        val iconTint = when (index % 4) {
            0 -> Blue
            1 -> Violet
            2 -> Color(0xFFB66A55)
            else -> Color(0xFF3A8FA4)
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            color = tileColor,
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                Modifier.padding(13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(38.dp),
                    color = Color.White,
                    shape = CircleShape
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Code,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    model,
                    modifier = Modifier.weight(1f),
                    color = Ink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${index + 1}",
                    color = Muted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SettingsRootContent(
    checkingUpdate: Boolean,
    onOpenGitHub: () -> Unit,
    onOpenAutomation: () -> Unit,
    onCheckUpdate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Lavender,
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(Modifier.padding(17.dp)) {
                Text(
                    "لوحة التحكم",
                    color = Ink,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "كل إعدادات HAI في مكان واحد",
                    color = Muted,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        SettingsTile(
            icon = Icons.Outlined.Link,
            title = "GitHub",
            subtitle = "الحساب والمشروع النشط",
            accent = Blue,
            background = BlueSoft,
            onClick = onOpenGitHub
        )

        Spacer(Modifier.height(10.dp))

        SettingsTile(
            icon = Icons.Outlined.AutoAwesome,
            title = "التنفيذ التلقائي",
            subtitle = "المهام والحالة والتحكم",
            accent = Violet,
            background = Lavender,
            onClick = onOpenAutomation
        )

        Spacer(Modifier.weight(1f))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !checkingUpdate, onClick = onCheckUpdate),
            color = Sky,
            shape = RoundedCornerShape(22.dp)
        ) {
            Row(
                Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(38.dp),
                    color = Color.White,
                    shape = CircleShape
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = null,
                            tint = Blue,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    if (checkingUpdate) "جاري البحث…" else "البحث عن تحديث",
                    color = Ink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SettingsTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    background: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = background,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color.White),
        shadowElevation = 5.dp
    ) {
        Row(
            Modifier.padding(15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                color = Color.White,
                shape = CircleShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = title,
                        tint = accent,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    subtitle,
                    color = Muted,
                    fontSize = 10.sp
                )
            }

            Surface(
                modifier = Modifier.size(30.dp),
                color = Color.White.copy(alpha = 0.72f),
                shape = CircleShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "›",
                        color = accent,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun GitHubSettingsContent(
    connected: Boolean,
    login: String,
    repositoryCount: Int,
    selectedRepo: GitHubRepository?,
    linking: Boolean,
    linkingStatus: String,
    oauthAvailable: Boolean,
    onConnect: () -> Unit,
    onCancelLink: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenProjects: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        if (!connected) {
            GitHubConnectCard(
                linking = linking,
                status = linkingStatus,
                oauthAvailable = oauthAvailable,
                onConnect = onConnect,
                onCancel = onCancelLink
            )
            return@Column
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = BlueSoft,
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(Modifier.padding(17.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(54.dp),
                        color = Color.White,
                        shape = CircleShape,
                        shadowElevation = 5.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.AccountCircle,
                                contentDescription = null,
                                tint = Blue,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(Modifier.weight(1f)) {
                        Text(
                            login.ifBlank { "GitHub" },
                            color = Ink,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp
                        )
                        Text(
                            "${repositoryCount} مستودع",
                            color = Muted,
                            fontSize = 10.sp
                        )
                    }

                    Surface(
                        color = Color(0xFFE9F8F3),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            "متصل",
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            color = Moss,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White.copy(alpha = 0.78f),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(Modifier.padding(13.dp)) {
                        Text(
                            "المشروع النشط",
                            color = Muted,
                            fontSize = 9.sp
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            selectedRepo?.fullName ?: "لم يتم اختيار مشروع",
                            color = Ink,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        SettingsTile(
            icon = Icons.Outlined.FolderOpen,
            title = if (selectedRepo == null) "اختيار مشروع" else "تغيير المشروع",
            subtitle = selectedRepo?.fullName?.substringAfter('/') ?: "حدد مساحة العمل",
            accent = Blue,
            background = Sky,
            onClick = onOpenProjects
        )

        Spacer(Modifier.height(10.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onDisconnect),
            color = Peach,
            shape = RoundedCornerShape(22.dp)
        ) {
            Row(
                Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(38.dp),
                    color = Color.White,
                    shape = CircleShape
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Logout,
                            contentDescription = null,
                            tint = Color(0xFFB66A55),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "فصل الحساب",
                    color = Ink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun AutomaticTasksContent(
    tasks: List<AutoTaskItem>,
    running: Boolean,
    paused: Boolean,
    workerActive: Boolean,
    heartbeatAt: Long,
    workerMessage: String,
    workerError: String,
    remoteRepository: String,
    remoteRunId: Long,
    remoteRunUrl: String,
    remoteState: String,
    remoteStage: String,
    now: Long,
    onTogglePause: () -> Unit,
    modifier: Modifier = Modifier
) {
    val controllerLive = workerIsLive(workerActive, heartbeatAt, now)
    val remoteLive = remoteState == "queued" || remoteState == "in_progress"

    Column(modifier = modifier) {
        AutoRuntimeStatusBar(
            tasks = tasks,
            queueStarted = running,
            paused = paused,
            workerActive = workerActive,
            heartbeatAt = heartbeatAt,
            workerMessage = workerMessage,
            workerError = workerError,
            remoteRepository = remoteRepository,
            remoteRunId = remoteRunId,
            remoteRunUrl = remoteRunUrl,
            remoteState = remoteState,
            remoteStage = remoteStage,
            now = now,
            onTogglePause = onTogglePause,
            onOpenTasks = {},
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(14.dp))

        if (tasks.isEmpty()) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                color = Lavender,
                shape = RoundedCornerShape(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            modifier = Modifier.size(56.dp),
                            color = Color.White,
                            shape = CircleShape
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Outlined.AutoAwesome,
                                    contentDescription = null,
                                    tint = Violet,
                                    modifier = Modifier.size(25.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "لا توجد مهام",
                            color = Ink,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            "ستظهر المهام هنا عند بدء التنفيذ التلقائي",
                            color = Muted,
                            fontSize = 10.sp
                        )
                    }
                }
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(tasks.sortedBy { it.order }, key = { it.id }) { task ->
                val staleRunning =
                    task.status == AutoTaskStatus.RUNNING &&
                        !remoteLive &&
                        remoteState !in setOf("", "waiting") &&
                        !controllerLive

                val statusText = when {
                    staleRunning -> "متوقف"
                    task.status == AutoTaskStatus.WAITING -> "في الانتظار"
                    task.status == AutoTaskStatus.RUNNING && paused -> "متوقف مؤقتًا"
                    task.status == AutoTaskStatus.RUNNING -> "يعمل"
                    task.status == AutoTaskStatus.SUCCESS -> "تم"
                    else -> "فشل"
                }

                val statusColor = when {
                    staleRunning -> Muted
                    task.status == AutoTaskStatus.RUNNING -> Blue
                    task.status == AutoTaskStatus.SUCCESS -> Moss
                    task.status == AutoTaskStatus.FAILED -> Color(0xFFDB5D6A)
                    else -> Violet
                }

                val cardColor = when {
                    task.status == AutoTaskStatus.RUNNING -> BlueSoft
                    task.status == AutoTaskStatus.SUCCESS -> Color(0xFFE9F8F3)
                    task.status == AutoTaskStatus.FAILED -> Peach
                    else -> SurfaceElevated
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = cardColor,
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(
                        1.dp,
                        if (task.status == AutoTaskStatus.RUNNING && remoteLive) {
                            Color(0xFFCBD5FF)
                        } else {
                            Line
                        }
                    ),
                    shadowElevation = if (
                        task.status == AutoTaskStatus.RUNNING && remoteLive
                    ) 6.dp else 2.dp
                ) {
                    Row(
                        Modifier.padding(13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(42.dp),
                            color = Color.White,
                            shape = CircleShape
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    task.order.toString(),
                                    color = statusColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }

                        Spacer(Modifier.width(11.dp))

                        Column(Modifier.weight(1f)) {
                            Text(
                                task.title,
                                color = Ink,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                statusText,
                                color = statusColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Surface(
                            modifier = Modifier.size(30.dp),
                            color = Color.White.copy(alpha = 0.8f),
                            shape = CircleShape
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    when {
                                        task.status == AutoTaskStatus.RUNNING && paused -> "Ⅱ"
                                        task.status == AutoTaskStatus.RUNNING -> "●"
                                        task.status == AutoTaskStatus.SUCCESS -> "✓"
                                        task.status == AutoTaskStatus.FAILED -> "×"
                                        else -> "○"
                                    },
                                    color = statusColor,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun workerIsLive(
    active: Boolean,
    heartbeatAt: Long,
    now: Long
): Boolean =
    active &&
        heartbeatAt > 0L &&
        now - heartbeatAt in 0L..12_000L

@Composable
private fun AutoRuntimeStatusBar(
    tasks: List<AutoTaskItem>,
    queueStarted: Boolean,
    paused: Boolean,
    workerActive: Boolean,
    heartbeatAt: Long,
    workerMessage: String,
    workerError: String,
    remoteRepository: String,
    remoteRunId: Long,
    remoteRunUrl: String,
    remoteState: String,
    remoteStage: String,
    now: Long,
    onTogglePause: () -> Unit,
    onOpenTasks: () -> Unit,
    modifier: Modifier = Modifier
) {
    val controllerLive = workerIsLive(workerActive, heartbeatAt, now)
    val remoteLive = remoteState == "queued" || remoteState == "in_progress"
    val doneCount = tasks.count {
        it.status == AutoTaskStatus.SUCCESS ||
            it.status == AutoTaskStatus.FAILED
    }

    val status = when {
        paused -> "متوقف مؤقتًا"
        remoteState == "failure" -> "حدث خطأ"
        remoteState == "success" -> "تم"
        remoteState == "queued" || remoteState == "waiting" -> "في الانتظار"
        remoteState == "not_found" -> "لم يبدأ"
        workerError.isNotBlank() -> "متوقف"
        remoteStage.contains("يصلح") || remoteStage.contains("إصلاح") -> "يقوم بالإصلاح"
        remoteStage.contains("يفحص") -> "يفحص"
        remoteStage.contains("يحفظ") -> "يحفظ"
        remoteStage.contains("يجهز") -> "يجهز"
        queueStarted && (remoteLive || controllerLive) -> "يعمل"
        tasks.isNotEmpty() && doneCount == tasks.size -> "تم"
        else -> "جاهز"
    }

    val accent = when {
        remoteState == "failure" || workerError.isNotBlank() -> Color(0xFFDB5D6A)
        paused -> Violet
        remoteState == "success" -> Moss
        else -> Blue
    }

    Surface(
        modifier = modifier.clickable(onClick = onOpenTasks),
        color = Color(0xF8FFFFFF),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Line),
        shadowElevation = 10.dp
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (queueStarted) {
                Surface(
                    modifier = Modifier
                        .size(36.dp)
                        .clickable(onClick = onTogglePause),
                    color = if (paused) Lavender else BlueSoft,
                    shape = CircleShape
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(Modifier.width(9.dp))
            }

            Column(Modifier.weight(1f)) {
                Text(
                    status,
                    color = Ink,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                val detail = when {
                    workerError.isNotBlank() -> workerError
                    remoteStage.isNotBlank() -> remoteStage
                    queueStarted -> "التنفيذ مستمر"
                    else -> ""
                }

                if (detail.isNotBlank()) {
                    Text(
                        detail,
                        color = Muted,
                        fontSize = 9.sp,
                        maxLines = 1
                    )
                }
            }

            Box(
                Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
        }
    }
}

@Composable
private fun LiveCodePreview(
    code: String,
    now: Long,
    paused: Boolean
) {
    val preview = code.takeLast(6_000)
    var typed by remember { mutableStateOf("") }

    LaunchedEffect(preview, paused) {
        if (paused) return@LaunchedEffect

        var index = if (preview.startsWith(typed)) typed.length else 0
        if (index == 0) typed = ""

        while (index < preview.length) {
            index = (index + 36).coerceAtMost(preview.length)
            typed = preview.take(index)
            delay(14)
        }
    }

    val cursor = if (!paused && (now / 500L) % 2L == 0L) "▌" else ""

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFFFDFEFF),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color(0xFFDDE5FF)),
            shadowElevation = 5.dp
        ) {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(BlueSoft)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(28.dp),
                        color = Color.White,
                        shape = CircleShape
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.Code,
                                contentDescription = null,
                                tint = Blue,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    Text(
                        "الكود المباشر",
                        color = Ink,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Spacer(Modifier.weight(1f))

                    Text(
                        if (paused) "متوقف" else "مباشر",
                        color = if (paused) Violet else Moss,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = typed + cursor,
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
                    color = Color(0xFF24304A),
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Left,
                    maxLines = 22
                )
            }
        }
    }
}

@Composable
private fun AutoExecuteTopControl(
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .wrapContentWidth()
            .clickable { onToggle(!checked) },
        color = Color(0xF8FFFFFF),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            1.dp,
            if (checked) Color(0xFFCBD5FF) else Line
        ),
        shadowElevation = 10.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(22.dp),
                color = if (checked) BlueSoft else Lavender,
                shape = CircleShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        if (checked) "✓" else "○",
                        color = if (checked) Blue else Muted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.width(6.dp))

            Text(
                "تلقائي",
                color = if (checked) Blue else Ink,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun AutoExecuteCheckBox(
    checked: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(16.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (checked) "●" else "○",
            color = if (checked) Blue else Muted,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun AutoExecuteConfirmBanner(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.wrapContentWidth(),
        color = Color(0xFAFFFFFF),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Line),
        shadowElevation = 10.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "تفعيل التنفيذ التلقائي؟",
                color = Ink,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(Modifier.width(9.dp))

            Surface(
                modifier = Modifier
                    .size(28.dp)
                    .clickable(onClick = onCancel),
                color = Lavender,
                shape = CircleShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("×", color = Muted, fontSize = 16.sp)
                }
            }

            Spacer(Modifier.width(5.dp))

            Surface(
                modifier = Modifier
                    .size(28.dp)
                    .clickable(onClick = onConfirm),
                color = Blue,
                shape = CircleShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "✓",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun GitHubConnectCard(
    linking: Boolean,
    status: String,
    oauthAvailable: Boolean,
    onConnect: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = BlueSoft,
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(
            modifier = Modifier.padding(17.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    color = Color.White,
                    shape = CircleShape,
                    shadowElevation = 5.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Link,
                            contentDescription = null,
                            tint = Blue,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        "ربط GitHub",
                        color = Ink,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        if (linking) {
                            status.ifBlank { "جاري الربط" }
                        } else {
                            "اربط حسابك واختر المشروع"
                        },
                        color = Muted,
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(Modifier.height(15.dp))

            if (linking) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White.copy(alpha = 0.82f),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(17.dp),
                            strokeWidth = 2.dp,
                            color = Blue
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(
                            status.ifBlank { "بانتظار الموافقة" },
                            modifier = Modifier.weight(1f),
                            color = Ink,
                            fontSize = 11.sp
                        )
                        Surface(
                            modifier = Modifier
                                .size(28.dp)
                                .clickable(onClick = onCancel),
                            color = Peach,
                            shape = CircleShape
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    "×",
                                    color = Color(0xFFB66A55),
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = oauthAvailable, onClick = onConnect),
                    color = if (oauthAvailable) Blue else LavenderStrong,
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(
                        "ربط الحساب",
                        modifier = Modifier.padding(vertical = 13.dp),
                        color = if (oauthAvailable) Color.White else Muted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyToolCard(title: String, subtitle: String) {
    Surface(
        Modifier.fillMaxWidth(),
        color = SurfaceElevated,
        shape = RoundedCornerShape(3.dp),
        border = BorderStroke(1.dp, Ink.copy(alpha = 0.18f))
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(34.dp),
                color = Lavender,
                shape = RoundedCornerShape(2.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "—",
                        color = Blue,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    title,
                    color = Ink,
                    fontWeight = FontWeight.ExtraBold
                )
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(subtitle, color = Muted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun GitHubLinkDialog(
    status: String,
    userCode: String,
    onOpenGitHub: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        modifier = Modifier.widthIn(max = 300.dp),
        onDismissRequest = {},
        shape = RoundedCornerShape(4.dp),
        title = {
            Text(
                "GitHub",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (userCode.isBlank()) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = Blue
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        status.ifBlank { "جاري الربط…" },
                        color = Muted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Surface(
                        color = BlueSoft,
                        shape = RoundedCornerShape(3.dp),
                        border = BorderStroke(1.dp, Blue.copy(alpha = 0.45f))
                    ) {
                        Text(
                            userCode,
                            modifier = Modifier.padding(
                                horizontal = 16.dp,
                                vertical = 10.dp
                            ),
                            color = Ink,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "أدخل الكود في GitHub",
                        color = Ink,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            if (userCode.isNotBlank()) {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(
                            Context.CLIPBOARD_SERVICE
                        ) as ClipboardManager
                        clipboard.setPrimaryClip(
                            android.content.ClipData.newPlainText(
                                "GitHub code",
                                userCode
                            )
                        )
                        onOpenGitHub()
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("نسخ وفتح", fontSize = 13.sp)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("إلغاء", fontSize = 13.sp)
            }
        }
    )
}

@Composable
private fun UpdateDialog(
    versionName: String,
    onDismiss: () -> Unit,
    onOpenDownload: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تحديث") },
        text = {
            Text("الإصدار " + versionName + " متوفر.")
        },
        confirmButton = {
            TextButton(onClick = onOpenDownload) {
                Text("تنزيل")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("لاحقًا")
            }
        }
    )
}
private fun displayName(context: Context, uri: Uri): String {
    val name = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()
    return name?.takeIf { it.isNotBlank() } ?: "وسائط"
}
