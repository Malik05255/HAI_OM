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

private val Canvas = Color(0xFFF7F8F4)
private val SurfaceSoft = Color(0xFFFFFFFF)
private val Lavender = Color(0xFFEEF3EC)
private val LavenderStrong = Color(0xFFE3EBE5)
private val Line = Color(0xFFD9E2DD)
private val Blue = Color(0xFF1F8A70)
private val BlueSoft = Color(0xFFE5F4EF)
private val Ink = Color(0xFF17211D)
private val Muted = Color(0xFF78837D)
private val Violet = Color(0xFFD97757)
private val SurfaceElevated = Color(0xFFFFFFFF)

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
        modifier = modifier.padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(72.dp),
            color = SurfaceElevated,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, Line),
            shadowElevation = 10.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    "H",
                    color = Blue,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "HAI",
            color = Ink,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "اكتب أي شيء",
            color = Muted,
            fontSize = 13.sp
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
            modifier = Modifier.fillMaxWidth(
                if (user) 0.82f else 0.88f
            ),
            color = if (user) Color(0xFFF1F4F1) else SurfaceElevated,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(
                1.dp,
                if (user) Color(0xFFD5E1DA) else Line
            )
        ) {
            Row(
                Modifier.padding(vertical = 12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    Modifier
                        .width(3.dp)
                        .height(if (codeLike) 54.dp else 34.dp)
                        .background(if (user) Violet else Blue)
                )
                Spacer(Modifier.width(11.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 13.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    CompositionLocalProvider(
                        LocalLayoutDirection provides LayoutDirection.Rtl
                    ) {
                        Text(
                            if (user) "YOU" else "HAI",
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF3F3A36),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.2.sp,
                            textAlign = TextAlign.Right
                        )

                        Spacer(Modifier.height(6.dp))

                        Text(
                            messageText,
                            modifier = Modifier.fillMaxWidth(),
                            color = Ink,
                            fontSize = if (codeLike) 12.sp else 15.sp,
                            lineHeight = if (codeLike) 18.sp else 23.sp,
                            fontFamily = if (codeLike) {
                                FontFamily.Monospace
                            } else {
                                FontFamily.Default
                            },
                            textAlign = TextAlign.Right
                        )
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
    onSend: () -> Unit
) {
    val c = LocalConfiguration.current
    val compact = c.screenWidthDp < 360 || c.screenHeightDp < 680

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            color = SurfaceElevated,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, Line),
            shadowElevation = 12.dp
        ) {
            Row(
                Modifier.padding(horizontal = 7.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier
                        .size(if (compact) 38.dp else 40.dp)
                        .clickable(
                            enabled = enabled && value.isNotBlank(),
                            onClick = onSend
                        ),
                    color = if (enabled && value.isNotBlank()) Blue else Line,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "↑",
                            color = if (enabled && value.isNotBlank()) Canvas else Muted,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        enabled = enabled,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = Ink,
                            fontSize = if (compact) 15.sp else 16.sp
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
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (value.isBlank()) {
                                    Text(
                                        "رسالة إلى HAI",
                                        color = Muted,
                                        fontSize = if (compact) 14.sp else 15.sp
                                    )
                                }
                                inner()
                            }
                        }
                    )
                }

                Spacer(Modifier.width(6.dp))

                Surface(
                    modifier = Modifier
                        .size(if (compact) 38.dp else 40.dp)
                        .clickable(enabled = enabled, onClick = onMedia),
                    color = BlueSoft,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFB9DED2))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "+",
                            color = Blue,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
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
    val panelWidth = 64.dp
    val handleWidth = 22.dp
    val panelOffset by animateDpAsState(
        targetValue = if (open) 0.dp else -panelWidth,
        animationSpec = tween(durationMillis = 240),
        label = "commandRail"
    )
    val handleOffset by animateDpAsState(
        targetValue = if (open) panelWidth else 0.dp,
        animationSpec = tween(durationMillis = 240),
        label = "commandHandle"
    )

    Box(
        modifier = modifier
            .width(panelWidth + handleWidth)
            .height(310.dp)
    ) {
        Surface(
            modifier = Modifier
                .width(panelWidth)
                .offset(x = panelOffset),
            color = Color(0xFFF7FAF8),
            shape = RoundedCornerShape(
                topEnd = 18.dp,
                bottomEnd = 18.dp
            ),
            border = BorderStroke(1.dp, Line),
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier.padding(
                    vertical = 12.dp,
                    horizontal = 8.dp
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CommandGlyph("+", "دردشة جديدة", onNewChat)
                CommandGlyph("□", "المشاريع", onProjects)
                CommandGlyph("↺", "السجل", onHistory)
                CommandGlyph("✦", "النماذج", onModels)
                Box(
                    Modifier
                        .width(24.dp)
                        .height(1.dp)
                        .background(Line)
                )
                CommandGlyph(
                    if (connected) "≡" else "◎",
                    if (connected) "الإعدادات" else "الربط",
                    onSettings
                )
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = handleOffset)
                .width(handleWidth)
                .height(74.dp)
                .clickable(onClick = onToggle),
            color = Color(0xFFEAF2ED),
            shape = RoundedCornerShape(
                topEnd = 12.dp,
                bottomEnd = 12.dp
            ),
            border = BorderStroke(1.dp, Line)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    if (open) "‹" else "›",
                    color = Color(0xFF6A5145),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun CommandGlyph(
    glyph: String,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(42.dp)
            .clickable(onClick = onClick),
        color = SurfaceElevated,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Line)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                glyph,
                color = Ink,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold
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
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier
                .size(38.dp)
                .clickable(onClick = onBack),
            color = SurfaceElevated,
            shape = RoundedCornerShape(11.dp),
            border = BorderStroke(1.dp, Line)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("‹", color = Blue, fontSize = 26.sp)
            }
        }

        Spacer(Modifier.width(12.dp))

        Column {
            Text(
                title,
                color = Ink,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(3.dp))
            Box(
                Modifier
                    .width(28.dp)
                    .height(2.dp)
                    .background(Blue)
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
            verticalAlignment = Alignment.Bottom
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "WORKSPACE",
                    color = Blue,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Text(
                    "اختر المشروع النشط",
                    color = Ink,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                "${repositories.size}",
                color = Muted,
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(repositories) { repo ->
                val pending = pendingFullName == repo.fullName
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { pendingFullName = repo.fullName },
                    color = if (pending) BlueSoft else SurfaceElevated,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        1.dp,
                        if (pending) Blue.copy(alpha = 0.55f) else Line
                    )
                ) {
                    Row(
                        Modifier.padding(13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(36.dp),
                            color = if (pending) Blue else Color(0xFFE4ECE7),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    repo.fullName.substringAfter('/').take(1).uppercase(),
                                    color = if (pending) Canvas else Blue,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                repo.fullName.substringAfter('/'),
                                color = Ink,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                repo.fullName.substringBefore('/'),
                                color = Muted,
                                fontSize = 10.sp
                            )
                        }
                        Text(
                            if (pending) "●" else "○",
                            color = if (pending) Blue else Muted,
                            fontSize = 16.sp
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
            color = if (pendingRepository != null) Blue else Line,
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                "اعتماد المشروع",
                modifier = Modifier.padding(vertical = 13.dp),
                color = if (pendingRepository != null) Canvas else Muted,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
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
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        items(logs.reversed()) { log ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Blue)
                )
                Spacer(Modifier.width(10.dp))
                Surface(
                    modifier = Modifier.weight(1f),
                    color = SurfaceElevated,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Line)
                ) {
                    Text(
                        log,
                        modifier = Modifier.padding(11.dp),
                        color = Ink,
                        fontSize = 12.sp
                    )
                }
            }
        }

        answer?.takeIf { it.isNotBlank() }?.let { text ->
            item {
                Surface(
                    Modifier.fillMaxWidth(),
                    color = BlueSoft,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFFB9DED2))
                ) {
                    Text(
                        text,
                        Modifier.padding(14.dp),
                        color = Ink,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }

    if (!prUrl.isNullOrBlank()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenPr(prUrl) },
            color = SurfaceElevated,
            shape = RoundedCornerShape(13.dp),
            border = BorderStroke(1.dp, Line)
        ) {
            Text(
                "فتح آخر تعديل  ↗",
                modifier = Modifier.padding(12.dp),
                color = Blue,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ModelsContent(models: List<String>, ready: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (ready) BlueSoft else SurfaceElevated,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (ready) Color(0xFFB9DED2) else Line)
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (ready) "●" else "○",
                color = if (ready) Blue else Muted,
                fontSize = 16.sp
            )
            Spacer(Modifier.width(9.dp))
            Text(
                if (ready) "المحرك جاهز" else "المحرك غير جاهز",
                color = Ink,
                fontWeight = FontWeight.SemiBold
            )
        }
    }

    if (models.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        models.take(12).forEachIndexed { index, model ->
            Surface(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 7.dp),
                color = SurfaceElevated,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Line)
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "#${index + 1}",
                        color = Blue,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        model,
                        Modifier.weight(1f),
                        color = Ink,
                        fontSize = 13.sp
                    )
                    Text("✦", color = Violet, fontSize = 14.sp)
                }
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
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsTile(
            glyph = "◎",
            title = "GitHub",
            subtitle = "الحساب والمشروع النشط",
            onClick = onOpenGitHub
        )

        SettingsTile(
            glyph = "⚡",
            title = "التنفيذ",
            subtitle = "المهام والحالة التلقائية",
            onClick = onOpenAutomation
        )

        Spacer(Modifier.weight(1f))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !checkingUpdate, onClick = onCheckUpdate),
            color = SurfaceElevated,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Line)
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (checkingUpdate) "…" else "↻",
                    color = Blue,
                    fontSize = 20.sp
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    if (checkingUpdate) "جاري البحث" else "البحث عن تحديث",
                    color = Ink,
                    fontSize = 14.sp
                )
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun SettingsTile(
    glyph: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = SurfaceElevated,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Line)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                color = BlueSoft,
                shape = RoundedCornerShape(13.dp),
                border = BorderStroke(1.dp, Color(0xFFA9D8C9))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        glyph,
                        color = Blue,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = Ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    subtitle,
                    color = Muted,
                    fontSize = 11.sp
                )
            }
            Text("›", color = Muted, fontSize = 20.sp)
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
            color = SurfaceElevated,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Line)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(46.dp),
                        color = BlueSoft,
                        shape = RoundedCornerShape(13.dp),
                        border = BorderStroke(1.dp, Color(0xFFA9D8C9))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "@",
                                color = Blue,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(Modifier.weight(1f)) {
                        Text(
                            login.ifBlank { "GitHub" },
                            color = Ink,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            "${repositoryCount} مستودع",
                            color = Muted,
                            fontSize = 11.sp
                        )
                    }

                    Text("●", color = Blue, fontSize = 13.sp)
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    "المشروع النشط",
                    color = Muted,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    selectedRepo?.fullName ?: "لم يتم اختيار مشروع",
                    color = if (selectedRepo == null) Muted else Ink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        SettingsTile(
            glyph = "□",
            title = if (selectedRepo == null) "اختيار مشروع" else "تغيير المشروع",
            subtitle = selectedRepo?.fullName?.substringAfter('/') ?: "حدد مساحة العمل",
            onClick = onOpenProjects
        )

        Spacer(Modifier.height(10.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onDisconnect),
            color = Color(0xFFFFF1EF),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color(0xFFF4C5BD))
        ) {
            Row(
                Modifier.padding(13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("×", color = Color(0xFFD85F50), fontSize = 20.sp)
                Spacer(Modifier.width(10.dp))
                Text(
                    "فصل الحساب",
                    color = Color(0xFFB74E42),
                    fontSize = 13.sp
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
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("◇", color = Blue, fontSize = 30.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("لا توجد مهام", color = Muted, fontSize = 13.sp)
                }
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
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
                    task.status == AutoTaskStatus.SUCCESS -> Color(0xFF28A47D)
                    task.status == AutoTaskStatus.FAILED -> Color(0xFFD85F50)
                    else -> Violet
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (
                        task.status == AutoTaskStatus.RUNNING && remoteLive
                    ) BlueSoft else SurfaceElevated,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        1.dp,
                        if (
                            task.status == AutoTaskStatus.RUNNING && remoteLive
                        ) Blue.copy(alpha = 0.55f) else Line
                    )
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(34.dp),
                            color = Color(0xFFF7FAF8),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Line)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    "${task.order}",
                                    color = statusColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(Modifier.width(10.dp))

                        Column(Modifier.weight(1f)) {
                            Text(
                                task.title,
                                color = Ink,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                statusText,
                                color = statusColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.1.sp
                            )
                        }

                        Text(
                            when (statusText) {
                                "RUN" -> "●"
                                "DONE" -> "✓"
                                "FAIL" -> "×"
                                "PAUSE" -> "Ⅱ"
                                else -> "○"
                            },
                            color = statusColor,
                            fontSize = 16.sp
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

    Surface(
        modifier = modifier.clickable(onClick = onOpenTasks),
        color = SurfaceElevated,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            if (remoteLive && !paused) Blue.copy(alpha = 0.55f) else Line
        )
    ) {
        Row(
            Modifier.padding(
                start = 5.dp,
                end = 11.dp,
                top = 7.dp,
                bottom = 7.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (queueStarted) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clickable(onClick = onTogglePause),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (paused) "▶" else "Ⅱ",
                        color = Blue,
                        fontSize = if (paused) 15.sp else 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(6.dp))
            }

            Column(Modifier.weight(1f)) {
                Text(
                    status,
                    color = if (remoteLive && !paused) Blue else Ink,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
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
                        color = Muted,
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                }
            }

            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            paused -> Violet
                            remoteLive -> Blue
                            status == "DONE" -> Color(0xFF28A47D)
                            else -> Muted
                        }
                    )
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
            color = Color(0xFFF5F8F6),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color(0xFFC4DCD3))
        ) {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFEAF1ED))
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "LIVE",
                        color = Blue,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp
                    )
                    Spacer(Modifier.weight(1f))
                    Text("●", color = Color(0xFF28A47D), fontSize = 9.sp)
                }
                Text(
                    text = typed + cursor,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                    color = Color(0xFF16362D),
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
        color = Color(0xF2FFFFFF),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (checked) Blue.copy(alpha = 0.55f) else Line
        ),
        shadowElevation = 10.dp
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = 9.dp,
                vertical = 6.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (checked) "●" else "○",
                color = if (checked) Blue else Muted,
                fontSize = 12.sp
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "AUTO",
                color = if (checked) Blue else Ink,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
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
        color = SurfaceElevated,
        shape = RoundedCornerShape(13.dp),
        border = BorderStroke(1.dp, Line),
        shadowElevation = 12.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "تفعيل AUTO؟",
                color = Ink,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "×",
                modifier = Modifier.clickable(onClick = onCancel),
                color = Muted,
                fontSize = 18.sp
            )
            Spacer(Modifier.width(9.dp))
            Text(
                "✓",
                modifier = Modifier.clickable(onClick = onConfirm),
                color = Blue,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
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
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Line)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    color = BlueSoft,
                    shape = RoundedCornerShape(13.dp),
                    border = BorderStroke(1.dp, Color(0xFFA9D8C9))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "@",
                            color = Blue,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        "GitHub",
                        color = Ink,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        if (linking) {
                            status.ifBlank { "جاري الربط" }
                        } else if (oauthAvailable) {
                            "ربط مساحة العمل"
                        } else {
                            "غير متاح"
                        },
                        color = Muted,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            if (linking) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Blue
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        status.ifBlank { "بانتظار الموافقة" },
                        modifier = Modifier.weight(1f),
                        color = Ink,
                        fontSize = 12.sp
                    )
                    Text(
                        "×",
                        modifier = Modifier.clickable(onClick = onCancel),
                        color = Muted,
                        fontSize = 18.sp
                    )
                }
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = oauthAvailable, onClick = onConnect),
                    color = if (oauthAvailable) Blue else Line,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "ربط الحساب",
                        modifier = Modifier.padding(vertical = 11.dp),
                        color = if (oauthAvailable) Canvas else Muted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyToolCard(title: String, subtitle: String) {
    Surface(Modifier.fillMaxWidth(), color = Lavender, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(15.dp)) {
            Text(title, color = Ink, fontWeight = FontWeight.SemiBold)
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(subtitle, color = Muted, fontSize = 12.sp)
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
        shape = RoundedCornerShape(20.dp),
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
                        color = Lavender,
                        shape = RoundedCornerShape(12.dp)
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
