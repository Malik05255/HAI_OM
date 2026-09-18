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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.haiom.app.MainViewModel
import com.haiom.app.model.GitHubRepository
import com.haiom.app.network.ChatTurn

private val PageBackground = Color(0xFFFCFBFE)
private val SoftLavender = Color(0xFFF1ECF5)
private val SoftLavenderBorder = Color(0xFFE2DBE7)
private val RailBlue = Color(0xFF2F7AE9)
private val MutedText = Color(0xFF96929B)
private val Ink = Color(0xFF17151A)

private enum class PanelSheet {
    PROJECTS,
    HISTORY,
    MODELS,
    SETTINGS
}

private data class PickedMedia(
    val uri: Uri,
    val name: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HaiOmApp(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    var draft by remember { mutableStateOf("") }
    var selectedRepo by remember { mutableStateOf<GitHubRepository?>(null) }
    var railOpen by remember { mutableStateOf(false) }
    var activeSheet by remember { mutableStateOf<PanelSheet?>(null) }
    val media = remember { mutableStateListOf<PickedMedia>() }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
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
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
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
                if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
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

    when (activeSheet) {
        PanelSheet.PROJECTS -> {
            ProjectsSheet(
                repositories = state.repositories,
                selectedRepo = selectedRepo,
                connected = state.hasGitHubToken,
                linking = state.githubLinking,
                onDismiss = { activeSheet = null },
                onSelect = {
                    selectedRepo = it
                    activeSheet = null
                },
                onConnect = vm::startGitHubLink
            )
        }
        PanelSheet.HISTORY -> {
            HistorySheet(
                logs = state.logs,
                running = state.running,
                answer = state.result?.answer,
                prUrl = state.result?.pullRequestUrl,
                onDismiss = { activeSheet = null },
                onOpenPr = { url ->
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                }
            )
        }
        PanelSheet.MODELS -> {
            ModelsSheet(
                models = state.rankings.map { it.modelName },
                ready = state.omniReady,
                onDismiss = { activeSheet = null }
            )
        }
        PanelSheet.SETTINGS -> {
            SettingsSheet(
                connected = state.hasGitHubToken,
                login = state.githubLogin,
                linking = state.githubLinking,
                checkingUpdate = state.checkingUpdate,
                onDismiss = { activeSheet = null },
                onConnect = vm::startGitHubLink,
                onDisconnect = vm::disconnectGitHub,
                onCheckUpdate = vm::checkForUpdate
            )
        }
        null -> Unit
    }

    Scaffold(
        containerColor = PageBackground,
        snackbarHost = { SnackbarHost(snackbar) }
    ) { scaffoldPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PageBackground)
                .padding(scaffoldPadding)
        ) {
            ChatHome(
                modifier = Modifier.fillMaxSize(),
                stateHistory = state.chatHistory,
                standaloneAnswer = state.result?.answer,
                running = state.running,
                programming = state.programming,
                progressText = state.logs.lastOrNull(),
                draft = draft,
                onDraftChange = { draft = it },
                media = media,
                onRemoveMedia = { media.remove(it) },
                onPickMedia = {
                    mediaPicker.launch(
                        arrayOf(
                            "image/*",
                            "video/*",
                            "application/pdf",
                            "text/plain"
                        )
                    )
                },
                onSend = {
                    if (draft.isBlank() || state.running) return@ChatHome
                    val attachmentNote = if (media.isEmpty()) {
                        ""
                    } else {
                        "\n\nالمرفقات المختارة: " + media.joinToString(", ") { it.name }
                    }
                    vm.runAgent(
                        selectedRepo?.htmlUrl.orEmpty(),
                        draft.trim() + attachmentNote
                    )
                    draft = ""
                    media.clear()
                }
            )

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Box(Modifier.fillMaxSize()) {
                    if (railOpen) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.04f))
                                .clickable { railOpen = false }
                        )
                        SideRail(
                            modifier = Modifier.align(Alignment.CenterStart),
                            connected = state.hasGitHubToken,
                            onClose = { railOpen = false },
                            onNewChat = {
                                vm.clearChat()
                                draft = ""
                                media.clear()
                                railOpen = false
                            },
                            onProjects = {
                                activeSheet = PanelSheet.PROJECTS
                                railOpen = false
                            },
                            onHistory = {
                                activeSheet = PanelSheet.HISTORY
                                railOpen = false
                            },
                            onModels = {
                                activeSheet = PanelSheet.MODELS
                                railOpen = false
                            },
                            onSettings = {
                                activeSheet = PanelSheet.SETTINGS
                                railOpen = false
                            }
                        )
                    } else {
                        RailHandle(
                            modifier = Modifier.align(Alignment.CenterStart),
                            onClick = { railOpen = true }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatHome(
    modifier: Modifier,
    stateHistory: List<ChatTurn>,
    standaloneAnswer: String?,
    running: Boolean,
    programming: Boolean,
    progressText: String?,
    draft: String,
    onDraftChange: (String) -> Unit,
    media: List<PickedMedia>,
    onRemoveMedia: (PickedMedia) -> Unit,
    onPickMedia: () -> Unit,
    onSend: () -> Unit
) {
    val lastHistoryAnswer = stateHistory.lastOrNull { it.role == "assistant" }?.text
    val extraAnswer = standaloneAnswer?.takeIf {
        it.isNotBlank() && it != lastHistoryAnswer
    }
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0

    BoxWithConstraints(
        modifier = modifier
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        val availableWidth = maxWidth
        val availableHeight = maxHeight
        val narrow = availableWidth < 360.dp
        val veryWide = availableWidth >= 520.dp
        val short = availableHeight < 680.dp
        val keyboardLayout = imeVisible || availableHeight < 460.dp
        val horizontalPadding = when {
            narrow -> 10.dp
            veryWide -> 26.dp
            else -> 16.dp
        }
        val showTitle = !keyboardLayout && availableHeight >= 560.dp
        val showEmptyHero = stateHistory.isEmpty() &&
            extraAnswer == null &&
            !running &&
            !keyboardLayout

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding)
        ) {
            if (showTitle) {
                Spacer(Modifier.height(if (short) 10.dp else 16.dp))
                Surface(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = Color(0xFFFFFCFF),
                    shape = RoundedCornerShape(25.dp),
                    border = BorderStroke(1.dp, SoftLavenderBorder)
                ) {
                    Text(
                        text = "دردشة",
                        modifier = Modifier.padding(
                            horizontal = if (narrow) 24.dp else 30.dp,
                            vertical = if (short) 8.dp else 10.dp
                        ),
                        color = Color(0xFF423C46),
                        fontSize = if (narrow) 17.sp else 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (showEmptyHero) {
                    EmptyConversation(
                        modifier = Modifier.align(Alignment.Center),
                        availableWidth = availableWidth,
                        availableHeight = availableHeight
                    )
                } else if (stateHistory.isNotEmpty() || extraAnswer != null || running) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                top = if (keyboardLayout) 4.dp else 14.dp,
                                bottom = 8.dp
                            ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(stateHistory) { turn ->
                            ChatBubble(turn)
                        }
                        extraAnswer?.let { answer ->
                            item {
                                ChatBubble(ChatTurn(role = "assistant", text = answer))
                            }
                        }
                        if (running) {
                            item {
                                WorkingBubble(
                                    programming = programming,
                                    progressText = progressText
                                )
                            }
                        }
                    }
                }
            }

            if (media.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((media.size.coerceAtMost(if (keyboardLayout) 1 else 2) * 42).dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    items(media) { item ->
                        MediaRow(item, onRemoveMedia)
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            Composer(
                value = draft,
                onValueChange = onDraftChange,
                enabled = !running,
                compact = narrow || short || keyboardLayout,
                onPickMedia = onPickMedia,
                onSend = onSend
            )
            Spacer(Modifier.height(if (keyboardLayout) 4.dp else 8.dp))
        }
    }
}

@Composable
private fun EmptyConversation(
    modifier: Modifier = Modifier,
    availableWidth: androidx.compose.ui.unit.Dp,
    availableHeight: androidx.compose.ui.unit.Dp
) {
    val narrow = availableWidth < 360.dp
    val short = availableHeight < 700.dp
    val logoSize = when {
        short && narrow -> 76.dp
        short -> 84.dp
        narrow -> 88.dp
        else -> 100.dp
    }
    val gap = when {
        short -> 30.dp
        availableHeight > 850.dp -> 48.dp
        else -> 38.dp
    }

    Column(
        modifier = modifier.padding(horizontal = if (narrow) 12.dp else 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(logoSize)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Transparent,
                shape = CircleShape,
                border = BorderStroke(1.dp, Color(0xFFE8ECFA))
            ) {}
            Text(
                "H AI",
                color = Color(0xFFDCE4FB),
                fontWeight = FontWeight.Bold,
                fontSize = if (short) 23.sp else 27.sp
            )
        }

        Spacer(Modifier.height(gap))

        Text(
            "مرحبًا، كيف أساعدك اليوم؟",
            color = Ink,
            fontSize = when {
                narrow -> 19.sp
                short -> 20.sp
                else -> 22.sp
            },
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun ChatBubble(turn: ChatTurn) {
    val user = turn.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (user) Arrangement.Start else Arrangement.End
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.88f),
            color = if (user) Color(0xFFF0E8F7) else Color.White,
            shape = RoundedCornerShape(
                topStart = 22.dp,
                topEnd = 22.dp,
                bottomStart = if (user) 22.dp else 6.dp,
                bottomEnd = if (user) 6.dp else 22.dp
            ),
            border = BorderStroke(
                1.dp,
                if (user) Color(0xFFE2D7EC) else Color(0xFFEAE7EC)
            )
        ) {
            Text(
                text = turn.text,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                color = Ink,
                fontSize = 16.sp,
                lineHeight = 24.sp
            )
        }
    }
}

@Composable
private fun WorkingBubble(
    programming: Boolean,
    progressText: String?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color(0xFFEAE7EC))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = RailBlue
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    if (programming) progressText ?: "جاري تنفيذ المطلوب…" else "جاري الرد…",
                    color = Color(0xFF5B5660)
                )
            }
        }
    }
}

@Composable
private fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    compact: Boolean,
    onPickMedia: () -> Unit,
    onSend: () -> Unit
) {
    val buttonSize = if (compact) 42.dp else 46.dp
    val verticalPadding = if (compact) 5.dp else 6.dp
    val inputFont = if (compact) 17.sp else 18.sp

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFFFAF7FB),
            shape = RoundedCornerShape(if (compact) 30.dp else 34.dp),
            border = BorderStroke(1.dp, SoftLavenderBorder)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 7.dp, vertical = verticalPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledIconButton(
                    onClick = onSend,
                    enabled = enabled && value.isNotBlank(),
                    modifier = Modifier.size(buttonSize),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (value.isNotBlank() && enabled) Color(0xFF5A5864) else Color(0xFFE6E1E8),
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFFE6E1E8),
                        disabledContentColor = Color(0xFF98939C)
                    )
                ) {
                    Icon(
                        Icons.Outlined.ArrowUpward,
                        "إرسال",
                        modifier = Modifier.size(if (compact) 23.dp else 25.dp)
                    )
                }

                Spacer(Modifier.width(if (compact) 5.dp else 7.dp))

                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        enabled = enabled,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 7.dp, vertical = if (compact) 7.dp else 8.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = Ink,
                            fontSize = inputFont
                        ),
                        cursorBrush = SolidColor(RailBlue),
                        maxLines = if (compact) 3 else 5,
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
                                        "اطرح سؤالاً…",
                                        color = MutedText,
                                        fontSize = inputFont
                                    )
                                }
                                inner()
                            }
                        }
                    )
                }

                Spacer(Modifier.width(if (compact) 5.dp else 7.dp))

                FilledIconButton(
                    onClick = onPickMedia,
                    enabled = enabled,
                    modifier = Modifier.size(buttonSize),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = RailBlue,
                        contentColor = Color.White,
                        disabledContainerColor = RailBlue.copy(alpha = 0.4f),
                        disabledContentColor = Color.White.copy(alpha = 0.7f)
                    )
                ) {
                    Icon(
                        Icons.Outlined.Add,
                        "إدراج وسائط",
                        modifier = Modifier.size(if (compact) 25.dp else 27.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaRow(
    item: PickedMedia,
    onRemove: (PickedMedia) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SoftLavender,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "مرفق: " + item.name,
                modifier = Modifier.weight(1f),
                color = Color(0xFF5F5864),
                maxLines = 1
            )
            IconButton(
                onClick = { onRemove(item) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Outlined.Close,
                    "إزالة",
                    tint = Color(0xFF6A626D)
                )
            }
        }
    }
}

@Composable
private fun RailHandle(
    modifier: Modifier,
    onClick: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val short = configuration.screenHeightDp < 700
    val narrow = configuration.screenWidthDp < 360
    val handleWidth = if (narrow) 21.dp else 23.dp
    val handleHeight = if (short) 70.dp else 82.dp

    Surface(
        modifier = modifier
            .width(handleWidth)
            .height(handleHeight)
            .clickable(onClick = onClick),
        color = RailBlue,
        shape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp),
        shadowElevation = 2.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Outlined.MoreVert,
                "فتح الأدوات",
                tint = Color.White,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

@Composable
private fun SideRail(
    modifier: Modifier,
    connected: Boolean,
    onClose: () -> Unit,
    onNewChat: () -> Unit,
    onProjects: () -> Unit,
    onHistory: () -> Unit,
    onModels: () -> Unit,
    onSettings: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp
    val screenWidth = configuration.screenWidthDp
    val narrow = screenWidth < 360
    val railWidth = if (narrow) 70.dp else 76.dp
    val railHeight = (screenHeight * 0.50f).dp.coerceIn(300.dp, 410.dp)
    val handleWidth = if (narrow) 21.dp else 23.dp
    val handleHeight = if (screenHeight < 700) 70.dp else 82.dp

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier
                .width(railWidth)
                .height(railHeight),
            color = Color(0xFFFFFBFF),
            shape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
            border = BorderStroke(1.dp, Color(0xFFE4DEE7)),
            shadowElevation = 7.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = if (screenHeight < 700) 8.dp else 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                RailIconButton(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    label = "دردشة جديدة",
                    onClick = onNewChat
                )
                RailIconButton(
                    icon = Icons.Outlined.FolderOpen,
                    label = "المشاريع",
                    onClick = onProjects
                )
                RailIconButton(
                    icon = Icons.Outlined.History,
                    label = "السجل والتعديلات",
                    onClick = onHistory
                )
                RailIconButton(
                    icon = Icons.Outlined.AutoAwesome,
                    label = "النماذج",
                    onClick = onModels
                )
                Box(
                    modifier = Modifier
                        .width(if (narrow) 42.dp else 48.dp)
                        .height(1.dp)
                        .background(Color(0xFFDAD3DD))
                )
                RailIconButton(
                    icon = Icons.Outlined.Settings,
                    label = if (connected) "الإعدادات" else "الربط والإعدادات",
                    onClick = onSettings
                )
            }
        }

        Surface(
            modifier = Modifier
                .width(handleWidth)
                .height(handleHeight)
                .clickable(onClick = onClose),
            color = RailBlue,
            shape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.MoreVert,
                    "إغلاق الأدوات",
                    tint = Color.White,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

@Composable
private fun RailIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val compact = configuration.screenHeightDp < 700 || configuration.screenWidthDp < 360
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(if (compact) 45.dp else 50.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Ink,
            modifier = Modifier.size(if (compact) 25.dp else 28.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectsSheet(
    repositories: List<GitHubRepository>,
    selectedRepo: GitHubRepository?,
    connected: Boolean,
    linking: Boolean,
    onDismiss: () -> Unit,
    onSelect: (GitHubRepository) -> Unit,
    onConnect: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFFFFFBFF)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                "المشاريع",
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                color = Ink
            )
            Spacer(Modifier.height(12.dp))

            if (!connected) {
                Text(
                    "اربط GitHub حتى تظهر مشاريعك هنا.",
                    color = MutedText
                )
                Spacer(Modifier.height(14.dp))
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
            } else if (repositories.isEmpty()) {
                SheetMessage("لا توجد مشاريع ظاهرة حاليًا.")
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(repositories.take(50)) { repo ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(repo) },
                            color = if (selectedRepo?.fullName == repo.fullName) SoftLavender else Color.White,
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, Color(0xFFE8E2EA))
                        ) {
                            Row(
                                Modifier.padding(15.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.FolderOpen,
                                    null,
                                    tint = RailBlue
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    repo.fullName.substringAfter('/'),
                                    modifier = Modifier.weight(1f),
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (selectedRepo?.fullName == repo.fullName) {
                                    Icon(
                                        Icons.Outlined.CheckCircle,
                                        null,
                                        tint = RailBlue
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistorySheet(
    logs: List<String>,
    running: Boolean,
    answer: String?,
    prUrl: String?,
    onDismiss: () -> Unit,
    onOpenPr: (String) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFFFFFBFF)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                "السجل والتعديلات",
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                color = Ink
            )
            Spacer(Modifier.height(10.dp))

            if (logs.isEmpty() && answer.isNullOrBlank()) {
                SheetMessage("لا توجد مهام سابقة في هذه الجلسة.")
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
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
                                    .background(if (running) RailBlue else Color(0xFFA7A1AA))
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                log,
                                modifier = Modifier.weight(1f),
                                color = Ink
                            )
                        }
                        HorizontalDivider(color = Color(0xFFEDE8EF))
                    }
                    answer?.takeIf { it.isNotBlank() }?.let {
                        item {
                            Surface(
                                color = SoftLavender,
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Text(
                                    it,
                                    modifier = Modifier.padding(15.dp),
                                    color = Ink
                                )
                            }
                        }
                    }
                }
            }

            if (!prUrl.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { onOpenPr(prUrl) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.Outlined.Code, null)
                    Spacer(Modifier.width(8.dp))
                    Text("فتح آخر تعديل")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelsSheet(
    models: List<String>,
    ready: Boolean,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFFFFFBFF)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                "النماذج",
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                color = Ink
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (ready) "المسار المجاني جاهز." else "المسار المجاني غير جاهز بعد.",
                color = MutedText
            )
            Spacer(Modifier.height(14.dp))

            if (models.isEmpty()) {
                SheetMessage("يتم اختيار النموذج المجاني تلقائيًا حسب التوفر.")
            } else {
                models.take(12).forEachIndexed { index, name ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        color = Color.White,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFFEAE5EC))
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.AutoAwesome,
                                null,
                                tint = RailBlue
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                name,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "#" + (index + 1),
                                color = MutedText
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    connected: Boolean,
    login: String,
    linking: Boolean,
    checkingUpdate: Boolean,
    onDismiss: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onCheckUpdate: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFFFFFBFF)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                "الإعدادات",
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                color = Ink
            )
            Spacer(Modifier.height(16.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = SoftLavender,
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.AccountCircle,
                        null,
                        tint = RailBlue,
                        modifier = Modifier.size(30.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (connected) login.ifBlank { "GitHub" } else "GitHub غير مربوط",
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (connected) "المشاريع متاحة للتنفيذ" else "اربط الحساب لاستخدام البرمجة",
                            color = MutedText,
                            fontSize = 13.sp
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
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Outlined.Refresh, null)
                }
                Spacer(Modifier.width(8.dp))
                Text(if (checkingUpdate) "جاري البحث…" else "البحث عن تحديث")
            }
        }
    }
}

@Composable
private fun SheetMessage(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SoftLavender,
        shape = RoundedCornerShape(18.dp)
    ) {
        Text(
            text,
            modifier = Modifier.padding(16.dp),
            color = Color(0xFF615A65)
        )
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
        onDismissRequest = {
            if (!downloading) onDismiss()
        },
        title = {
            Text(if (installUri == null) "تحديث جديد" else "جاهز للتثبيت")
        },
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
                onClick = {
                    if (installUri == null) onDownload() else onInstall(installUri)
                }
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
                TextButton(onClick = onDismiss) {
                    Text("لاحقًا")
                }
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
