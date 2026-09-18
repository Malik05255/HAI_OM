package com.haiom.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.haiom.app.MainViewModel
import com.haiom.app.model.GitHubRepository
import com.haiom.app.network.ChatTurn

private val Canvas = Color(0xFFFCFBFD)
private val SurfaceSoft = Color(0xFFFFFAFD)
private val Lavender = Color(0xFFF3EEF7)
private val LavenderStrong = Color(0xFFE8DFF0)
private val Line = Color(0xFFE7E0E9)
private val Blue = Color(0xFF4E82E9)
private val BlueSoft = Color(0xFFEAF0FF)
private val Ink = Color(0xFF1B181D)
private val Muted = Color(0xFF98929C)

private enum class ToolPanel { PROJECTS, HISTORY, MODELS, SETTINGS }

private data class PickedMedia(val uri: Uri, val name: String)

@Composable
fun HaiOmApp(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var draft by remember { mutableStateOf("") }
    var selectedRepo by remember { mutableStateOf<GitHubRepository?>(null) }
    var dockOpen by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf<ToolPanel?>(null) }
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

    LaunchedEffect(state.repositories) {
        if (selectedRepo == null && state.repositories.isNotEmpty()) {
            selectedRepo = state.repositories.first()
        } else if (selectedRepo != null && state.repositories.none { it.fullName == selectedRepo?.fullName }) {
            selectedRepo = state.repositories.firstOrNull()
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
    LaunchedEffect(state.githubLaunchUrl, state.githubUserCode) {
        val url = state.githubLaunchUrl ?: return@LaunchedEffect
        if (state.githubUserCode.isNotBlank()) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("GitHub", state.githubUserCode))
        }
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        vm.consumeGitHubLaunch()
    }

    state.updateInfo?.let { update ->
        UpdateDialog(
            versionName = update.versionName,
            downloading = state.downloadingUpdate,
            progress = state.updateProgress,
            installUri = state.updateInstallUri,
            onDismiss = vm::dismissUpdate,
            onDownload = vm::downloadUpdate,
            onInstall = { uri ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    !context.packageManager.canRequestPackageInstalls()
                ) {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + context.packageName)
                        )
                    )
                } else {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(
                                Uri.parse(uri),
                                "application/vnd.android.package-archive"
                            )
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                }
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
                ) {
                    if (media.isNotEmpty()) {
                        MediaStrip(media) { media.remove(it) }
                    }
                    CompactComposer(
                        value = draft,
                        onValueChange = { draft = it },
                        enabled = !state.running,
                        onMedia = {
                            mediaPicker.launch(
                                arrayOf("image/*", "video/*", "application/pdf", "text/plain")
                            )
                        },
                        onSend = {
                            if (draft.isBlank() || state.running) return@CompactComposer
                            val attachmentNote = if (media.isEmpty()) "" else
                                "\n\nالمرفقات المختارة: " + media.joinToString(", ") { it.name }
                            vm.runAgent(
                                selectedRepo?.htmlUrl.orEmpty(),
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
                    selectedRepo = selectedRepo
                )
            } else {
                ToolScreen(
                    panel = panel!!,
                    repositories = state.repositories,
                    selectedRepo = selectedRepo,
                    connected = state.hasGitHubToken,
                    login = state.githubLogin,
                    linking = state.githubLinking,
                    logs = state.logs,
                    answer = state.result?.answer,
                    prUrl = state.result?.pullRequestUrl,
                    models = state.rankings.map { it.modelName },
                    omniReady = state.omniReady,
                    checkingUpdate = state.checkingUpdate,
                    onBack = { panel = null },
                    onSelectRepo = {
                        selectedRepo = it
                        panel = null
                    },
                    onConnect = vm::startGitHubLink,
                    onDisconnect = vm::disconnectGitHub,
                    onCheckUpdate = vm::checkForUpdate,
                    onOpenPr = { url ->
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    }
                )
            }

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                if (dockOpen) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.025f))
                            .clickable { dockOpen = false }
                    )
                    ToolDock(
                        modifier = Modifier.align(Alignment.CenterStart),
                        connected = state.hasGitHubToken,
                        onClose = { dockOpen = false },
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
                            panel = ToolPanel.SETTINGS
                            dockOpen = false
                        }
                    )
                } else {
                    DockHandle(
                        Modifier.align(Alignment.CenterStart),
                        onClick = { dockOpen = true }
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
    selectedRepo: GitHubRepository?
) {
    val lastAnswer = history.lastOrNull { it.role == "assistant" }?.text
    val extraAnswer = standaloneAnswer?.takeIf { it.isNotBlank() && it != lastAnswer }
    val empty = history.isEmpty() && extraAnswer == null && !running

    Column(
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(14.dp))
        MinimalHeader(selectedRepo)
        Spacer(Modifier.height(8.dp))

        if (empty) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                EmptyState(Modifier.align(Alignment.Center))
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(top = 18.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                items(history) { MessageBlock(it) }
                extraAnswer?.let { answer ->
                    item { MessageBlock(ChatTurn("assistant", answer)) }
                }
                if (running) {
                    item { RunningLine(programming, progressText) }
                }
            }
        }
    }
}

@Composable
private fun MinimalHeader(selectedRepo: GitHubRepository?) {
    Surface(
        modifier = Modifier.widthIn(min = 96.dp, max = 210.dp),
        color = SurfaceSoft,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Line)
    ) {
        Column(
            Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("H AI", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            selectedRepo?.let {
                Text(
                    it.fullName.substringAfter('/'),
                    color = Muted,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier.padding(horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("H · AI", color = Color(0xFFE1E7F7), fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(22.dp))
        Text("كيف أساعدك؟", color = Ink, fontSize = 21.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(7.dp))
        Text("ابدأ من شريط الكتابة بالأسفل", color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun MessageBlock(turn: ChatTurn) {
    val user = turn.role == "user"
    val fence = 96.toChar().toString().repeat(3)
    val codeLike = turn.text.contains(fence)

    if (user) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.82f),
                color = Lavender,
                shape = RoundedCornerShape(20.dp, 20.dp, 7.dp, 20.dp)
            ) {
                Text(
                    turn.text,
                    Modifier.padding(horizontal = 15.dp, vertical = 12.dp),
                    color = Ink,
                    fontSize = 15.sp,
                    lineHeight = 23.sp
                )
            }
        }
    } else {
        Column(Modifier.fillMaxWidth()) {
            Text("H AI", color = Blue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            if (codeLike) {
                Surface(
                    Modifier.fillMaxWidth(),
                    color = Color(0xFFF7F5F8),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Line)
                ) {
                    Text(
                        turn.text.replace(fence, ""),
                        Modifier.padding(14.dp),
                        color = Color(0xFF3B3840),
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else {
                Text(turn.text, color = Ink, fontSize = 15.sp, lineHeight = 24.sp)
            }
        }
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
                .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            color = Color(0xFFF8F4FA),
            shape = RoundedCornerShape(29.dp),
            border = BorderStroke(1.dp, LavenderStrong)
        ) {
            Row(
                Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledIconButton(
                    onClick = onSend,
                    enabled = enabled && value.isNotBlank(),
                    modifier = Modifier.size(if (compact) 40.dp else 42.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color(0xFF66616A),
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFFE7E1E9),
                        disabledContentColor = Color(0xFF9C96A0)
                    )
                ) {
                    Icon(Icons.Outlined.ArrowUpward, "إرسال", Modifier.size(22.dp))
                }

                Spacer(Modifier.width(6.dp))
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        enabled = enabled,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 6.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = Ink,
                            fontSize = if (compact) 16.sp else 17.sp
                        ),
                        cursorBrush = SolidColor(Blue),
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = { if (value.isNotBlank() && enabled) onSend() }
                        ),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (value.isBlank()) {
                                    Text(
                                        "اكتب هنا…",
                                        color = Muted,
                                        fontSize = if (compact) 16.sp else 17.sp
                                    )
                                }
                                inner()
                            }
                        }
                    )
                }
                Spacer(Modifier.width(6.dp))
                FilledIconButton(
                    onClick = onMedia,
                    enabled = enabled,
                    modifier = Modifier.size(if (compact) 40.dp else 42.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Blue,
                        contentColor = Color.White,
                        disabledContainerColor = Blue.copy(alpha = 0.38f),
                        disabledContentColor = Color.White.copy(alpha = 0.7f)
                    )
                ) {
                    Icon(Icons.Outlined.Add, "إدراج وسائط", Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun MediaStrip(media: List<PickedMedia>, onRemove: (PickedMedia) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        media.take(2).forEach { item ->
            Surface(
                modifier = Modifier.weight(1f),
                color = BlueSoft,
                shape = RoundedCornerShape(13.dp)
            ) {
                Row(
                    Modifier.padding(start = 10.dp, end = 3.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        item.name,
                        Modifier.weight(1f),
                        color = Color(0xFF5D6270),
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                    IconButton(onClick = { onRemove(item) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Outlined.Close, "إزالة", Modifier.size(16.dp), tint = Color(0xFF666B76))
                    }
                }
            }
        }
    }
}

@Composable
private fun DockHandle(modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.width(19.dp).height(76.dp).clickable(onClick = onClick),
        color = Blue,
        shape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp),
        shadowElevation = 2.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.MoreVert, "الأدوات", tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ToolDock(
    modifier: Modifier,
    connected: Boolean,
    onClose: () -> Unit,
    onNewChat: () -> Unit,
    onProjects: () -> Unit,
    onHistory: () -> Unit,
    onModels: () -> Unit,
    onSettings: () -> Unit
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.width(66.dp),
            color = SurfaceSoft,
            shape = RoundedCornerShape(topEnd = 32.dp, bottomEnd = 32.dp),
            border = BorderStroke(1.dp, Line),
            shadowElevation = 8.dp
        ) {
            Column(
                Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                DockIcon(Icons.Outlined.ChatBubbleOutline, "دردشة جديدة", onNewChat)
                DockIcon(Icons.Outlined.FolderOpen, "المشاريع", onProjects)
                DockIcon(Icons.Outlined.History, "السجل", onHistory)
                DockIcon(Icons.Outlined.AutoAwesome, "النماذج", onModels)
                HorizontalDivider(Modifier.width(34.dp), color = Line)
                DockIcon(Icons.Outlined.Settings, if (connected) "الإعدادات" else "الربط", onSettings)
            }
        }
        Surface(
            modifier = Modifier.width(18.dp).height(70.dp).clickable(onClick = onClose),
            color = Blue,
            shape = RoundedCornerShape(topEnd = 11.dp, bottomEnd = 11.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.MoreVert, "إغلاق", tint = Color.White, modifier = Modifier.size(15.dp))
            }
        }
    }
}

@Composable
private fun DockIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.size(43.dp),
        color = Color.White,
        shape = CircleShape,
        border = BorderStroke(1.dp, Line)
    ) {
        IconButton(onClick = onClick) {
            Icon(icon, label, tint = Ink, modifier = Modifier.size(23.dp))
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
    logs: List<String>,
    answer: String?,
    prUrl: String?,
    models: List<String>,
    omniReady: Boolean,
    checkingUpdate: Boolean,
    onBack: () -> Unit,
    onSelectRepo: (GitHubRepository) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onCheckUpdate: () -> Unit,
    onOpenPr: (String) -> Unit
) {
    Column(
        Modifier.fillMaxSize().background(Canvas)
            .statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = 18.dp)
    ) {
        ToolHeader(
            when (panel) {
                ToolPanel.PROJECTS -> "المشاريع"
                ToolPanel.HISTORY -> "السجل والتعديلات"
                ToolPanel.MODELS -> "النماذج"
                ToolPanel.SETTINGS -> "الإعدادات"
            },
            onBack
        )
        Spacer(Modifier.height(18.dp))
        when (panel) {
            ToolPanel.PROJECTS -> ProjectsContent(
                repositories, selectedRepo, connected, linking, onSelectRepo, onConnect
            )
            ToolPanel.HISTORY -> HistoryContent(logs, answer, prUrl, onOpenPr)
            ToolPanel.MODELS -> ModelsContent(models, omniReady)
            ToolPanel.SETTINGS -> SettingsContent(
                connected, login, linking, checkingUpdate,
                onConnect, onDisconnect, onCheckUpdate
            )
        }
    }
}

@Composable
private fun ToolHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(42.dp)) {
            Icon(Icons.Outlined.ArrowBack, "رجوع", tint = Ink)
        }
        Spacer(Modifier.width(8.dp))
        Text(title, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ProjectsContent(
    repositories: List<GitHubRepository>,
    selectedRepo: GitHubRepository?,
    connected: Boolean,
    linking: Boolean,
    onSelect: (GitHubRepository) -> Unit,
    onConnect: () -> Unit
) {
    if (!connected) {
        EmptyToolCard("GitHub غير مربوط", "اربط حسابك حتى تظهر المشاريع هنا.")
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onConnect,
            enabled = !linking,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(Icons.Outlined.Link, null)
            Spacer(Modifier.width(8.dp))
            Text(if (linking) "بانتظار الموافقة…" else "ربط GitHub")
        }
        return
    }
    if (repositories.isEmpty()) {
        EmptyToolCard("لا توجد مشاريع", "لا توجد مستودعات ظاهرة حاليًا.")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        items(repositories.take(60)) { repo ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onSelect(repo) },
                color = if (selectedRepo?.fullName == repo.fullName) Lavender else Color.White,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Line)
            ) {
                Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.FolderOpen, null, tint = Blue)
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(repo.fullName.substringAfter('/'), color = Ink, fontWeight = FontWeight.SemiBold)
                        Text(repo.fullName.substringBefore('/'), color = Muted, fontSize = 11.sp)
                    }
                    if (selectedRepo?.fullName == repo.fullName) {
                        Icon(Icons.Outlined.CheckCircle, null, tint = Blue)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryContent(
    logs: List<String>,
    answer: String?,
    prUrl: String?,
    onOpenPr: (String) -> Unit
) {
    if (logs.isEmpty() && answer.isNullOrBlank()) {
        EmptyToolCard("السجل فارغ", "ستظهر هنا خطوات التنفيذ والتعديلات.")
        return
    }
    LazyColumn(
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(logs.reversed()) { log ->
            Surface(
                Modifier.fillMaxWidth(),
                color = Color.White,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Line)
            ) {
                Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(Blue))
                    Spacer(Modifier.width(10.dp))
                    Text(log, color = Ink, fontSize = 13.sp)
                }
            }
        }
        answer?.takeIf { it.isNotBlank() }?.let { text ->
            item {
                Surface(
                    Modifier.fillMaxWidth(),
                    color = Lavender,
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(text, Modifier.padding(15.dp), color = Ink, fontSize = 14.sp)
                }
            }
        }
    }
    if (!prUrl.isNullOrBlank()) {
        OutlinedButton(
            onClick = { onOpenPr(prUrl) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(Icons.Outlined.Code, null)
            Spacer(Modifier.width(8.dp))
            Text("فتح آخر تعديل")
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun ModelsContent(models: List<String>, ready: Boolean) {
    EmptyToolCard(
        if (ready) "المسار المجاني جاهز" else "المسار غير جاهز",
        "اختيار النموذج يتم تلقائيًا حسب التوفر."
    )
    if (models.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        models.take(12).forEachIndexed { index, model ->
            Surface(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                color = Color.White,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Line)
            ) {
                Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AutoAwesome, null, tint = Blue)
                    Spacer(Modifier.width(10.dp))
                    Text(model, Modifier.weight(1f), color = Ink)
                    Text("#" + (index + 1), color = Muted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun SettingsContent(
    connected: Boolean,
    login: String,
    linking: Boolean,
    checkingUpdate: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onCheckUpdate: () -> Unit
) {
    Surface(Modifier.fillMaxWidth(), color = Lavender, shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.AccountCircle, null, tint = Blue, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (connected) login.ifBlank { "GitHub" } else "GitHub غير مربوط",
                    color = Ink,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (connected) "المشاريع متاحة للتنفيذ" else "الربط مطلوب للبرمجة",
                    color = Muted,
                    fontSize = 12.sp
                )
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    if (connected) {
        OutlinedButton(
            onClick = onDisconnect,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(Icons.Outlined.Logout, null)
            Spacer(Modifier.width(8.dp))
            Text("فصل GitHub")
        }
    } else {
        Button(
            onClick = onConnect,
            enabled = !linking,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(Icons.Outlined.Link, null)
            Spacer(Modifier.width(8.dp))
            Text(if (linking) "بانتظار الموافقة…" else "ربط GitHub")
        }
    }
    Spacer(Modifier.height(10.dp))
    OutlinedButton(
        onClick = onCheckUpdate,
        enabled = !checkingUpdate,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        if (checkingUpdate) {
            CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Outlined.Refresh, null)
        }
        Spacer(Modifier.width(8.dp))
        Text(if (checkingUpdate) "جاري البحث…" else "البحث عن تحديث")
    }
}

@Composable
private fun EmptyToolCard(title: String, subtitle: String) {
    Surface(Modifier.fillMaxWidth(), color = Lavender, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(17.dp)) {
            Text(title, color = Ink, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun UpdateDialog(
    versionName: String,
    downloading: Boolean,
    progress: Int,
    installUri: String?,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onInstall: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        title = { Text(if (installUri == null) "تحديث جديد" else "جاهز للتثبيت") },
        text = {
            Text(
                when {
                    downloading -> "جاري تنزيل التحديث " + progress + "%"
                    installUri != null -> "تم تنزيل الإصدار " + versionName
                    else -> "الإصدار " + versionName + " متوفر"
                }
            )
        },
        confirmButton = {
            TextButton(
                enabled = !downloading,
                onClick = { if (installUri == null) onDownload() else onInstall(installUri) }
            ) {
                Text(
                    when {
                        downloading -> "جاري التنزيل…"
                        installUri != null -> "تثبيت"
                        else -> "تنزيل وتثبيت"
                    }
                )
            }
        },
        dismissButton = {
            if (!downloading) {
                TextButton(onClick = onDismiss) { Text("لاحقًا") }
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
