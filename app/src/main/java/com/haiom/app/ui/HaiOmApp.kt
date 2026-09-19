package com.haiom.app.ui

import android.app.Activity
import android.content.Context
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Logout
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.haiom.app.MainViewModel
import com.haiom.app.automation.AutoTaskItem
import com.haiom.app.automation.AutoTaskStatus
import com.haiom.app.model.GitHubRepository
import com.haiom.app.network.ChatTurn
import kotlinx.coroutines.delay

private val Canvas = Color(0xFFF3EFE7)
private val SurfaceSoft = Color(0xFFFFFCF6)
private val Lavender = Color(0xFFEDE7DD)
private val LavenderStrong = Color(0xFFE0D7C9)
private val Line = Color(0xFFCBC1B2)
private val Blue = Color(0xFF3156D3)
private val BlueSoft = Color(0xFFE6EBFF)
private val Ink = Color(0xFF17181B)
private val Muted = Color(0xFF716D66)
private val Violet = Color(0xFFFF6B4A)
private val SurfaceElevated = Color(0xFFFFFCF6)
private val Signal = Color(0xFFFF6B4A)
private val Moss = Color(0xFF52725D)
private val PaperShadow = Color(0x1A151515)

private enum class ToolPanel { PROJECTS, HISTORY, MODELS, SETTINGS }
private enum class SettingsPage { ROOT, GITHUB, AUTOMATION }

private data class PickedMedia(val uri: Uri, val name: String)

@Composable
fun HaiOmApp(
    vm: MainViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }
    var draft by remember { mutableStateOf("") }
    val selectedRepo = state.selectedRepository
    var dockOpen by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf<ToolPanel?>(null) }
    var showAutoExecuteConfirm by remember { mutableStateOf(false) }
    var showAutoExecuteControl by remember { mutableStateOf(false) }
    var openAutomationSettings by remember { mutableStateOf(false) }
    var lastHomeBackAt by remember { mutableStateOf(0L) }
    var runtimeNow by remember { mutableStateOf(System.currentTimeMillis()) }
    val media = remember { mutableStateListOf<PickedMedia>() }

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

    LaunchedEffect(showAutoExecuteControl) {
        if (showAutoExecuteControl) {
            delay(2_000)
            showAutoExecuteControl = false
        }
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
            showAutoExecuteControl -> showAutoExecuteControl = false
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

            if (panel == null) {
                // نقطة لمس مخفية فقط في أعلى يمين الشاشة.
                val autoExecuteTouchSource = remember {
                    MutableInteractionSource()
                }

                Box(
                    modifier = Modifier
                        .align(AbsoluteAlignment.TopRight)
                        .statusBarsPadding()
                        .padding(top = 0.dp, end = 0.dp)
                        .size(80.dp)
                        .clickable(
                            interactionSource = autoExecuteTouchSource,
                            indication = null
                        ) {
                            haptics.performHapticFeedback(
                                HapticFeedbackType.TextHandleMove
                            )
                            showAutoExecuteControl = true
                        }
                )

                AnimatedVisibility(
                    visible = showAutoExecuteControl,
                    modifier = Modifier
                        .align(AbsoluteAlignment.TopRight)
                        .statusBarsPadding()
                        .padding(top = 3.dp, end = 8.dp),
                    enter = slideInVertically(
                        initialOffsetY = { -it },
                        animationSpec = tween(170)
                    ) + fadeIn(animationSpec = tween(130)),
                    exit = slideOutVertically(
                        targetOffsetY = { -it },
                        animationSpec = tween(160)
                    ) + fadeOut(animationSpec = tween(110))
                ) {
                    AutoExecuteTopControl(
                        checked = state.autoExecuteEnabled,
                        onToggle = { enabled ->
                            if (enabled) {
                                showAutoExecuteConfirm = true
                            } else {
                                vm.setAutoExecuteEnabled(false)
                            }
                            showAutoExecuteControl = false
                        }
                    )
                }
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
                        .align(AbsoluteAlignment.TopRight)
                        .statusBarsPadding()
                        .padding(top = 46.dp, end = 8.dp)
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
                        onToggle = { dockOpen = !dockOpen },
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
            .padding(horizontal = 18.dp)
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
                verticalArrangement = Arrangement.spacedBy(18.dp)
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
        Surface(
            modifier = Modifier
                .width(150.dp)
                .height(84.dp),
            color = SurfaceElevated,
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(2.dp, Ink),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "HAI / 01",
                    color = Blue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.8.sp
                )
                Text(
                    "واجهة عمل ذكية",
                    color = Ink,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black
                )
                Box(
                    Modifier
                        .width(54.dp)
                        .height(4.dp)
                        .background(Signal)
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "ابدأ من هنا",
            color = Ink,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "محادثة، برمجة، أو تنفيذ مشروع",
            color = Muted,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun MessageBlock(turn: ChatTurn) {
    val user = turn.role == "user"
    val fence = 96.toChar().toString().repeat(3)
    val codeLike = turn.text.contains(fence)
    val messageText = if (codeLike) turn.text.replace(fence, "") else turn.text

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (user) {
            AbsoluteAlignment.CenterRight
        } else {
            AbsoluteAlignment.CenterLeft
        }
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(if (user) 0.80f else 0.90f),
            color = if (user) Color(0xFFFFF3EE) else SurfaceElevated,
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(
                1.dp,
                if (user) Signal.copy(alpha = 0.55f) else Ink.copy(alpha = 0.22f)
            ),
            shadowElevation = if (user) 2.dp else 5.dp
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (user) Color(0xFFFFE7DF) else Color(0xFFE9EDFF))
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(24.dp),
                        color = if (user) Signal else Blue,
                        shape = RoundedCornerShape(2.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (user) Icons.Outlined.AccountCircle else Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (user) "أنت" else "HAI",
                        color = Ink,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (user) "USER" else "ASSISTANT",
                        color = Muted,
                        fontSize = 8.sp,
                        letterSpacing = 1.2.sp
                    )
                }

                CompositionLocalProvider(
                    LocalLayoutDirection provides if (codeLike) {
                        LayoutDirection.Ltr
                    } else {
                        LayoutDirection.Rtl
                    }
                ) {
                    Text(
                        messageText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 13.dp, vertical = 12.dp),
                        color = Ink,
                        fontSize = if (codeLike) 12.sp else 15.sp,
                        lineHeight = if (codeLike) 18.sp else 23.sp,
                        fontFamily = if (codeLike) FontFamily.Monospace else FontFamily.Default,
                        textAlign = if (codeLike) TextAlign.Left else TextAlign.Right
                    )
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
    onSend: () -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Surface(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(enabled = enabled && value.isNotBlank(), onClick = onSend),
                color = if (enabled && value.isNotBlank()) Ink else LavenderStrong,
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, Ink)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.ArrowUpward,
                        contentDescription = "إرسال",
                        tint = if (enabled && value.isNotBlank()) Color.White else Muted,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Surface(
                modifier = Modifier.weight(1f),
                color = SurfaceElevated,
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, Ink.copy(alpha = 0.28f)),
                shadowElevation = 6.dp
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        enabled = enabled,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
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
                                        "اكتب إلى HAI",
                                        color = Muted,
                                        fontSize = 14.sp
                                    )
                                }
                                inner()
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Surface(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(enabled = enabled, onClick = onMedia),
                color = Signal,
                shape = RoundedCornerShape(24.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = "إضافة",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
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
    onToggle: () -> Unit,
    onNewChat: () -> Unit,
    onProjects: () -> Unit,
    onHistory: () -> Unit,
    onModels: () -> Unit,
    onSettings: () -> Unit
) {
    val panelWidth = 178.dp
    val handleWidth = 28.dp
    val panelOffset by animateDpAsState(
        targetValue = if (open) 0.dp else -panelWidth,
        animationSpec = tween(durationMillis = 260),
        label = "editorialRail"
    )
    val handleOffset by animateDpAsState(
        targetValue = if (open) panelWidth else 0.dp,
        animationSpec = tween(durationMillis = 260),
        label = "editorialHandle"
    )

    Box(
        modifier = modifier
            .width(panelWidth + handleWidth)
            .height(300.dp)
    ) {
        Surface(
            modifier = Modifier
                .width(panelWidth)
                .offset(x = panelOffset),
            color = SurfaceElevated,
            shape = RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp),
            border = BorderStroke(1.dp, Ink.copy(alpha = 0.25f)),
            shadowElevation = 12.dp
        ) {
            Column(Modifier.padding(10.dp)) {
                EditorialDockItem("01", Icons.Outlined.ChatBubbleOutline, "محادثة جديدة", onNewChat)
                EditorialDockItem("02", Icons.Outlined.FolderOpen, "المشاريع", onProjects)
                EditorialDockItem("03", Icons.Outlined.History, "السجل", onHistory)
                EditorialDockItem("04", Icons.Outlined.Code, "النماذج", onModels)
                EditorialDockItem(
                    "05",
                    if (connected) Icons.Outlined.Settings else Icons.Outlined.Link,
                    if (connected) "الإعدادات" else "ربط GitHub",
                    onSettings
                )
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = handleOffset)
                .width(handleWidth)
                .height(92.dp)
                .clickable(onClick = onToggle),
            color = Ink,
            shape = RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    if (open) "×" else "MENU",
                    color = Color.White,
                    fontSize = if (open) 18.sp else 8.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
private fun EditorialDockItem(
    index: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            index,
            color = Signal,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            icon,
            contentDescription = label,
            tint = Blue,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(9.dp))
        Text(
            label,
            color = Ink,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
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
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .size(42.dp)
                    .clickable(onClick = onBack),
                color = Ink,
                shape = RoundedCornerShape(3.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.ArrowBack,
                        contentDescription = "رجوع",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Text(
                "HAI / PANEL",
                color = Signal,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.6.sp
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            title,
            color = Ink,
            fontSize = 30.sp,
            fontWeight = FontWeight.Black
        )

        Spacer(Modifier.height(8.dp))

        Row {
            Box(
                Modifier
                    .width(58.dp)
                    .height(4.dp)
                    .background(Blue)
            )
            Box(
                Modifier
                    .width(24.dp)
                    .height(4.dp)
                    .background(Signal)
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
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "PROJECT INDEX",
                color = Blue,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.5.sp
            )
            Spacer(Modifier.weight(1f))
            Surface(
                color = Ink,
                shape = RoundedCornerShape(2.dp)
            ) {
                Text(
                    "${repositories.size}",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(12.dp))

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
                    shape = RoundedCornerShape(3.dp),
                    border = BorderStroke(
                        if (pending) 2.dp else 1.dp,
                        if (pending) Blue else Ink.copy(alpha = 0.18f)
                    ),
                    shadowElevation = if (pending) 5.dp else 1.dp
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(42.dp),
                            color = if (pending) Blue else Lavender,
                            shape = RoundedCornerShape(2.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Outlined.FolderOpen,
                                    contentDescription = null,
                                    tint = if (pending) Color.White else Ink,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Spacer(Modifier.width(11.dp))

                        Column(Modifier.weight(1f)) {
                            Text(
                                repo.fullName.substringAfter('/'),
                                color = Ink,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                repo.fullName.substringBefore('/'),
                                color = Muted,
                                fontSize = 10.sp,
                                letterSpacing = 0.8.sp
                            )
                        }

                        Box(
                            Modifier
                                .width(4.dp)
                                .height(36.dp)
                                .background(if (pending) Signal else Line)
                        )
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
            color = if (pendingRepository != null) Ink else LavenderStrong,
            shape = RoundedCornerShape(3.dp)
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = if (pendingRepository != null) Color.White else Muted,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    "اعتماد المشروع",
                    color = if (pendingRepository != null) Color.White else Muted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "ENTER",
                    color = if (pendingRepository != null) Signal else Muted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.3.sp
                )
            }
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
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        items(logs.reversed().take(30)) { log ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Blue)
                    )
                    Box(
                        Modifier
                            .width(1.dp)
                            .height(54.dp)
                            .background(Line)
                    )
                }

                Spacer(Modifier.width(10.dp))

                Column(
                    Modifier
                        .weight(1f)
                        .padding(bottom = 10.dp)
                ) {
                    Text(
                        "EVENT",
                        color = Signal,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.4.sp
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        log,
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
                    color = Color(0xFFFFF3EE),
                    shape = RoundedCornerShape(3.dp),
                    border = BorderStroke(1.dp, Signal.copy(alpha = 0.4f))
                ) {
                    Column(Modifier.padding(13.dp)) {
                        Text(
                            "RESULT",
                            color = Signal,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.3.sp
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
            color = Blue,
            shape = RoundedCornerShape(3.dp)
        ) {
            Row(
                Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Code,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "فتح آخر تعديل",
                    color = Color.White,
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
        color = if (ready) Color(0xFFEAF3EC) else Lavender,
        shape = RoundedCornerShape(3.dp),
        border = BorderStroke(1.dp, Ink.copy(alpha = 0.18f))
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = if (ready) Moss else Muted,
                modifier = Modifier.size(21.dp)
            )
            Spacer(Modifier.width(9.dp))
            Column {
                Text(
                    if (ready) "المحرك جاهز" else "المحرك غير جاهز",
                    color = Ink,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp
                )
                Text(
                    if (ready) "ONLINE" else "OFFLINE",
                    color = if (ready) Moss else Muted,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.3.sp
                )
            }
        }
    }

    Spacer(Modifier.height(14.dp))

    models.take(12).forEachIndexed { index, model ->
        Surface(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            color = SurfaceElevated,
            shape = RoundedCornerShape(3.dp),
            border = BorderStroke(1.dp, Ink.copy(alpha = 0.16f))
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(30.dp),
                    color = if (index % 2 == 0) Blue else Signal,
                    shape = RoundedCornerShape(2.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "${(index + 1).toString().padStart(2, '0')}",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    model,
                    Modifier.weight(1f),
                    color = Ink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    Icons.Outlined.Code,
                    contentDescription = null,
                    tint = Muted,
                    modifier = Modifier.size(17.dp)
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
        Text(
            "CONTROL BOARD",
            color = Signal,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.6.sp
        )

        Spacer(Modifier.height(10.dp))

        SettingsTile(
            index = "01",
            icon = Icons.Outlined.Link,
            title = "GitHub",
            subtitle = "الحساب والمشروع النشط",
            accent = Blue,
            onClick = onOpenGitHub
        )

        Spacer(Modifier.height(10.dp))

        SettingsTile(
            index = "02",
            icon = Icons.Outlined.AutoAwesome,
            title = "التنفيذ التلقائي",
            subtitle = "الطابور والحالة والتحكم",
            accent = Signal,
            onClick = onOpenAutomation
        )

        Spacer(Modifier.weight(1f))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !checkingUpdate, onClick = onCheckUpdate),
            color = Ink,
            shape = RoundedCornerShape(3.dp)
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = null,
                    tint = if (checkingUpdate) Muted else Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    if (checkingUpdate) "جاري البحث…" else "البحث عن تحديث",
                    color = if (checkingUpdate) Muted else Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "UPDATE",
                    color = Signal,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.2.sp
                )
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SettingsTile(
    index: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = SurfaceElevated,
        shape = RoundedCornerShape(3.dp),
        border = BorderStroke(1.dp, Ink.copy(alpha = 0.18f)),
        shadowElevation = 3.dp
    ) {
        Row(
            Modifier.padding(13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                index,
                color = accent,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp
            )

            Spacer(Modifier.width(9.dp))

            Surface(
                modifier = Modifier.size(40.dp),
                color = accent,
                shape = RoundedCornerShape(2.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = title,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.width(11.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    color = Muted,
                    fontSize = 10.sp
                )
            }

            Text(
                "→",
                color = Ink,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
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
            color = Ink,
            shape = RoundedCornerShape(3.dp)
        ) {
            Column(Modifier.padding(15.dp)) {
                Text(
                    "GITHUB ID",
                    color = Signal,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.4.sp
                )
                Spacer(Modifier.height(7.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.AccountCircle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            login.ifBlank { "GitHub" },
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 19.sp
                        )
                        Text(
                            "${repositoryCount} مستودع",
                            color = Color.White.copy(alpha = 0.68f),
                            fontSize = 10.sp
                        )
                    }
                    Text("●", color = Color(0xFF7FD6A6), fontSize = 12.sp)
                }

                Spacer(Modifier.height(15.dp))

                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.18f))
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    "المشروع النشط",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 9.sp
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    selectedRepo?.fullName ?: "لم يتم اختيار مشروع",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenProjects),
            color = BlueSoft,
            shape = RoundedCornerShape(3.dp),
            border = BorderStroke(1.dp, Blue.copy(alpha = 0.45f))
        ) {
            Row(
                Modifier.padding(13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.FolderOpen,
                    contentDescription = null,
                    tint = Blue,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    if (selectedRepo == null) "اختيار مشروع" else "تغيير المشروع",
                    color = Ink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                Text("→", color = Blue, fontSize = 18.sp)
            }
        }

        Spacer(Modifier.height(10.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onDisconnect),
            color = Color(0xFFFFECE7),
            shape = RoundedCornerShape(3.dp),
            border = BorderStroke(1.dp, Signal.copy(alpha = 0.42f))
        ) {
            Row(
                Modifier.padding(13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Logout,
                    contentDescription = null,
                    tint = Signal,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(Modifier.width(9.dp))
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
                color = SurfaceElevated,
                shape = RoundedCornerShape(3.dp),
                border = BorderStroke(1.dp, Ink.copy(alpha = 0.16f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = Blue,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "لا توجد مهام",
                            color = Ink,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "AUTO QUEUE / EMPTY",
                            color = Muted,
                            fontSize = 8.sp,
                            letterSpacing = 1.2.sp
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tasks.sortedBy { it.order }, key = { it.id }) { task ->
                val staleRunning =
                    task.status == AutoTaskStatus.RUNNING &&
                        !remoteLive &&
                        remoteState !in setOf("", "waiting") &&
                        !controllerLive

                val statusText = when {
                    staleRunning -> "STOP"
                    task.status == AutoTaskStatus.WAITING -> "WAIT"
                    task.status == AutoTaskStatus.RUNNING && paused -> "PAUSE"
                    task.status == AutoTaskStatus.RUNNING -> "RUN"
                    task.status == AutoTaskStatus.SUCCESS -> "DONE"
                    else -> "FAIL"
                }

                val statusColor = when {
                    staleRunning -> Muted
                    task.status == AutoTaskStatus.RUNNING -> Blue
                    task.status == AutoTaskStatus.SUCCESS -> Moss
                    task.status == AutoTaskStatus.FAILED -> Signal
                    else -> Color(0xFF9A7B2E)
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = SurfaceElevated,
                    shape = RoundedCornerShape(3.dp),
                    border = BorderStroke(
                        if (task.status == AutoTaskStatus.RUNNING && remoteLive) 2.dp else 1.dp,
                        if (task.status == AutoTaskStatus.RUNNING && remoteLive) Blue
                        else Ink.copy(alpha = 0.16f)
                    )
                ) {
                    Row(
                        Modifier.padding(11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier
                                .width(38.dp)
                                .height(46.dp),
                            color = if (
                                task.status == AutoTaskStatus.RUNNING && remoteLive
                            ) Blue else Lavender,
                            shape = RoundedCornerShape(2.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    "${task.order.toString().padStart(2, '0')}",
                                    color = if (
                                        task.status == AutoTaskStatus.RUNNING && remoteLive
                                    ) Color.White else Ink,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    statusText,
                                    color = if (
                                        task.status == AutoTaskStatus.RUNNING && remoteLive
                                    ) Color.White.copy(alpha = 0.75f) else statusColor,
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                )
                            }
                        }

                        Spacer(Modifier.width(10.dp))

                        Column(Modifier.weight(1f)) {
                            Text(
                                task.title,
                                color = Ink,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(Modifier.height(3.dp))
                            Box(
                                Modifier
                                    .width(
                                        when (statusText) {
                                            "DONE" -> 70.dp
                                            "RUN" -> 52.dp
                                            "FAIL" -> 38.dp
                                            else -> 28.dp
                                        }
                                    )
                                    .height(3.dp)
                                    .background(statusColor)
                            )
                        }

                        Text(
                            when (statusText) {
                                "RUN" -> "↻"
                                "DONE" -> "✓"
                                "FAIL" -> "×"
                                "PAUSE" -> "Ⅱ"
                                else -> "·"
                            },
                            color = statusColor,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
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
        paused -> "PAUSED"
        remoteState == "failure" -> "ERROR"
        remoteState == "success" -> "DONE"
        remoteState == "queued" || remoteState == "waiting" -> "QUEUED"
        remoteState == "not_found" -> "IDLE"
        workerError.isNotBlank() -> "STOPPED"
        remoteStage.contains("يصلح") || remoteStage.contains("إصلاح") -> "REPAIR"
        remoteStage.contains("يفحص") -> "VERIFY"
        remoteStage.contains("يحفظ") -> "SAVE"
        remoteStage.contains("يجهز") -> "PREP"
        queueStarted && (remoteLive || controllerLive) -> "RUN"
        tasks.isNotEmpty() && doneCount == tasks.size -> "DONE"
        else -> "IDLE"
    }

    val accent = when {
        status == "ERROR" || status == "STOPPED" -> Signal
        status == "DONE" -> Moss
        paused -> Color(0xFF9A7B2E)
        else -> Blue
    }

    Surface(
        modifier = modifier.clickable(onClick = onOpenTasks),
        color = Ink,
        shape = RoundedCornerShape(3.dp),
        shadowElevation = 5.dp
    ) {
        Row(
            Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (queueStarted) {
                Surface(
                    modifier = Modifier
                        .size(30.dp)
                        .clickable(onClick = onTogglePause),
                    color = accent,
                    shape = RoundedCornerShape(2.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
            }

            Column(Modifier.weight(1f)) {
                Text(
                    status,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.3.sp
                )

                val detail = when {
                    paused -> "متوقف مؤقتًا"
                    workerError.isNotBlank() -> workerError
                    remoteStage.isNotBlank() -> remoteStage
                    queueStarted -> "المهمة قيد التنفيذ"
                    else -> ""
                }

                if (detail.isNotBlank()) {
                    Text(
                        detail,
                        color = Color.White.copy(alpha = 0.68f),
                        fontSize = 9.sp,
                        maxLines = 1
                    )
                }
            }

            Box(
                Modifier
                    .width(4.dp)
                    .height(34.dp)
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
            color = Color(0xFF17181B),
            shape = RoundedCornerShape(3.dp),
            border = BorderStroke(2.dp, Blue)
        ) {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Blue)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Code,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "LIVE CODE",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (paused) "PAUSE" else "STREAM",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = typed + cursor,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                    color = Color(0xFFF0F3FF),
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
        color = if (checked) Ink else SurfaceElevated,
        shape = RoundedCornerShape(3.dp),
        border = BorderStroke(1.dp, Ink),
        shadowElevation = 7.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(18.dp),
                color = if (checked) Signal else Lavender,
                shape = RoundedCornerShape(2.dp),
                border = BorderStroke(1.dp, if (checked) Signal else Line)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        if (checked) "✓" else "",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            Text(
                "AUTO",
                color = if (checked) Color.White else Ink,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.4.sp
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
        color = Ink,
        shape = RoundedCornerShape(3.dp),
        shadowElevation = 10.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "تفعيل التنفيذ التلقائي؟",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(10.dp))
            Surface(
                modifier = Modifier
                    .size(27.dp)
                    .clickable(onClick = onCancel),
                color = Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(2.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("×", color = Color.White, fontSize = 17.sp)
                }
            }
            Spacer(Modifier.width(5.dp))
            Surface(
                modifier = Modifier
                    .size(27.dp)
                    .clickable(onClick = onConfirm),
                color = Signal,
                shape = RoundedCornerShape(2.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "✓",
                        color = Color.White,
                        fontSize = 15.sp,
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
        color = SurfaceElevated,
        shape = RoundedCornerShape(3.dp),
        border = BorderStroke(2.dp, Ink)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Ink)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Link,
                    contentDescription = null,
                    tint = Signal,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "GITHUB / CONNECT",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp
                )
            }

            Column(Modifier.padding(14.dp)) {
                Text(
                    if (linking) {
                        status.ifBlank { "جاري الربط" }
                    } else if (oauthAvailable) {
                        "اربط حسابك لبدء العمل على المشاريع"
                    } else {
                        "الربط غير متاح"
                    },
                    color = Ink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(12.dp))

                if (linking) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(17.dp),
                            strokeWidth = 2.dp,
                            color = Blue
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(
                            "بانتظار GitHub",
                            modifier = Modifier.weight(1f),
                            color = Muted,
                            fontSize = 11.sp
                        )
                        Surface(
                            modifier = Modifier
                                .size(28.dp)
                                .clickable(onClick = onCancel),
                            color = Signal,
                            shape = RoundedCornerShape(2.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("×", color = Color.White, fontSize = 18.sp)
                            }
                        }
                    }
                } else {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = oauthAvailable, onClick = onConnect),
                        color = if (oauthAvailable) Blue else LavenderStrong,
                        shape = RoundedCornerShape(2.dp)
                    ) {
                        Row(
                            Modifier.padding(11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "ربط الحساب",
                                color = if (oauthAvailable) Color.White else Muted,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "OPEN",
                                color = if (oauthAvailable) Color.White.copy(alpha = 0.72f) else Muted,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.2.sp
                            )
                        }
                    }
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
