package com.haiom.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.haiom.app.MainViewModel
import com.haiom.app.model.FreeProviderRanking
import com.haiom.app.model.GitHubRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HaiOmApp(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var requirements by remember { mutableStateOf("") }
    var selectedRepo by remember { mutableStateOf<GitHubRepository?>(null) }
    var showProjects by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.repositories) {
        if (selectedRepo == null && state.repositories.isNotEmpty()) selectedRepo = state.repositories.first()
        if (selectedRepo != null && state.repositories.none { it.fullName == selectedRepo?.fullName }) {
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
        AlertDialog(
            onDismissRequest = { if (!state.downloadingUpdate) vm.dismissUpdate() },
            title = {
                Text(if (state.updateInstallUri == null) "تحديث جديد" else "جاهز للتثبيت")
            },
            text = {
                Text(
                    when {
                        state.downloadingUpdate -> "جاري تنزيل التحديث ${state.updateProgress}%"
                        state.updateInstallUri != null -> "تم تنزيل الإصدار ${update.versionName}"
                        else -> "الإصدار ${update.versionName} متوفر"
                    }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !state.downloadingUpdate,
                    onClick = {
                        val installUri = state.updateInstallUri
                        if (installUri == null) {
                            vm.downloadUpdate()
                        } else if (
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                            !context.packageManager.canRequestPackageInstalls()
                        ) {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        } else {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(
                                        Uri.parse(installUri),
                                        "application/vnd.android.package-archive"
                                    )
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        }
                    }
                ) {
                    Text(
                        when {
                            state.downloadingUpdate -> "جاري التنزيل…"
                            state.updateInstallUri != null -> "تثبيت"
                            else -> "تنزيل وتثبيت"
                        }
                    )
                }
            },
            dismissButton = {
                if (!state.downloadingUpdate) {
                    TextButton(onClick = vm::dismissUpdate) { Text("لاحقًا") }
                }
            }
        )
    }

    if (showProjects) {
        ModalBottomSheet(onDismissRequest = { showProjects = false }) {
            Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 28.dp)) {
                Text("اختر المشروع", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                if (state.repositories.isEmpty()) {
                    EmptyCard("ما فيه مشاريع")
                } else {
                    state.repositories.take(50).forEach { repo ->
                        ProjectRow(repo, selectedRepo?.fullName == repo.fullName) {
                            selectedRepo = repo
                            showProjects = false
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                val items = listOf(
                    Triple("الرئيسية", Icons.Outlined.Home, 0),
                    Triple("المشاريع", Icons.Outlined.Folder, 1),
                    Triple("النماذج", Icons.Outlined.AutoAwesome, 2),
                    Triple("النشاط", Icons.Outlined.TaskAlt, 3)
                )
                items.forEach { (label, icon, index) ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(icon, null) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { padding ->
        when (tab) {
            0 -> HomeScreen(
                modifier = Modifier.padding(padding),
                state = state,
                selectedRepo = selectedRepo,
                requirements = requirements,
                onRequirements = { requirements = it },
                onPickProject = {
                    if (state.hasGitHubToken) showProjects = true else vm.startGitHubLink()
                },
                onRun = { vm.runAgent(selectedRepo?.htmlUrl.orEmpty(), requirements) },
                onConnectGitHub = vm::startGitHubLink,
                onCheckUpdate = vm::checkForUpdate,
                checkingUpdate = state.checkingUpdate
            )
            1 -> ProjectsScreen(
                modifier = Modifier.padding(padding),
                login = state.githubLogin,
                connected = state.hasGitHubToken,
                linking = state.githubLinking,
                repositories = state.repositories,
                selectedRepo = selectedRepo,
                onSelect = { selectedRepo = it; tab = 0 },
                onRefresh = vm::bootstrap,
                onConnect = vm::startGitHubLink,
                onDisconnect = vm::disconnectGitHub
            )
            2 -> ModelsScreen(Modifier.padding(padding), state.rankings, state.omniReady)
            else -> ActivityScreen(
                Modifier.padding(padding),
                state.logs,
                state.running,
                state.result?.pullRequestUrl,
                state.result?.answer
            )
        }
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier,
    state: com.haiom.app.MainUiState,
    selectedRepo: GitHubRepository?,
    requirements: String,
    onRequirements: (String) -> Unit,
    onPickProject: () -> Unit,
    onRun: () -> Unit,
    onConnectGitHub: () -> Unit,
    onCheckUpdate: () -> Unit,
    checkingUpdate: Boolean
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AppMark()
                Spacer(Modifier.size(12.dp))
                Text(
                    "HAI OM",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black
                )
                StatusPill(state.omniReady && state.hasGitHubToken)
            }
        }

        item {
            OutlinedButton(
                onClick = onCheckUpdate,
                enabled = !checkingUpdate,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Outlined.Refresh, null)
                Spacer(Modifier.size(8.dp))
                Text(if (checkingUpdate) "جاري البحث…" else "البحث عن تحديث", fontWeight = FontWeight.Bold)
            }
        }

        if (!state.hasGitHubToken) {
            item {
                Button(
                    onClick = onConnectGitHub,
                    enabled = !state.githubLinking,
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    if (state.githubLinking) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(10.dp))
                        Text("بانتظار الموافقة…", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Outlined.Link, null)
                        Spacer(Modifier.size(8.dp))
                        Text("ربط GitHub", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Text("المشروع", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp))
            Card(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onPickProject),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Folder, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(12.dp))
                    Text(
                        selectedRepo?.fullName?.substringAfter('/') ?: "اختر مشروع",
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold
                    )
                    if (selectedRepo != null) {
                        Text("تغيير", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = requirements,
                onValueChange = onRequirements,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("اكتب رسالتك") },
                minLines = 5,
                maxLines = 12,
                shape = RoundedCornerShape(20.dp)
            )
        }

        item {
            Button(
                onClick = onRun,
                enabled = !state.running &&
                    !state.connecting &&
                    !state.githubLinking &&
                    requirements.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                if (state.running) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(10.dp))
                    Text(
                        if (state.programming) state.logs.lastOrNull() ?: "جاري البرمجة…"
                        else "جاري الرد…",
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(Icons.Outlined.PlayArrow, null)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        if (vm.isProgrammingRequest(requirements)) "ابدأ البرمجة" else "إرسال",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        state.result?.answer?.takeIf { it.isNotBlank() }?.let { answer ->
            item {
                Text("النتيجة", fontWeight = FontWeight.Bold)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Text(
                        answer,
                        modifier = Modifier.padding(18.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        item { Spacer(Modifier.height(18.dp)) }
    }
}

@Composable
private fun ProjectsScreen(
    modifier: Modifier,
    login: String,
    connected: Boolean,
    linking: Boolean,
    repositories: List<GitHubRepository>,
    selectedRepo: GitHubRepository?,
    onSelect: (GitHubRepository) -> Unit,
    onRefresh: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Spacer(Modifier.height(16.dp))
            Text("مشاريعي", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(12.dp))
        }

        if (!connected) {
            item {
                Button(
                    onClick = onConnect,
                    enabled = !linking,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    if (linking) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                        Text("بانتظار الموافقة…")
                    } else {
                        Text("ربط GitHub", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.AccountCircle, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(8.dp))
                    Text(login.ifBlank { "GitHub" }, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Icon(
                        Icons.Outlined.Refresh,
                        "تحديث",
                        modifier = Modifier.clickable(onClick = onRefresh)
                    )
                }
            }

            if (repositories.isEmpty()) {
                item { EmptyCard("ما فيه مشاريع") }
            } else {
                itemsIndexed(repositories) { _, repo ->
                    ProjectRow(repo, selectedRepo?.fullName == repo.fullName) { onSelect(repo) }
                }
            }

            item {
                OutlinedButton(
                    onClick = onDisconnect,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("فصل GitHub")
                }
            }
        }

        item { Spacer(Modifier.height(18.dp)) }
    }
}

@Composable
private fun ModelsScreen(modifier: Modifier, rankings: List<FreeProviderRanking>, ready: Boolean) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Spacer(Modifier.height(16.dp))
            Text("النماذج", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
        }
        if (!ready) {
            item { EmptyCard("اربط GitHub") }
        } else if (rankings.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Code, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.size(12.dp))
                        Text("تلقائي", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Text(
                                "الأقوى المتاح",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        } else {
            itemsIndexed(rankings.take(20)) { index, model -> ModelRow(index, model) }
        }
        item { Spacer(Modifier.height(18.dp)) }
    }
}

@Composable
private fun ActivityScreen(
    modifier: Modifier,
    logs: List<String>,
    running: Boolean,
    prUrl: String?,
    answer: String?
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Spacer(Modifier.height(16.dp))
            Text("النشاط", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
        }
        if (logs.isEmpty()) {
            item { EmptyCard("ما فيه مهام") }
        } else {
            itemsIndexed(logs.reversed()) { index, log ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier.padding(top = 6.dp)
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == 0 && running) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline
                            )
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(
                        log,
                        modifier = Modifier.weight(1f),
                        fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
        if (!answer.isNullOrBlank()) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Text(
                        answer,
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
        if (!prUrl.isNullOrBlank()) {
            item { EmptyCard("✓ اكتملت المهمة") }
        }
        item { Spacer(Modifier.height(18.dp)) }
    }
}

@Composable
private fun AppMark() {
    Box(
        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(15.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {}
        Text("H", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun StatusPill(ready: Boolean) {
    Card(
        shape = RoundedCornerShape(50),
        colors = CardDefaults.cardColors(
            containerColor = if (ready) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Text(
            if (ready) "جاهز" else "غير جاهز",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ProjectRow(repo: GitHubRepository, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Folder, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(12.dp))
            Text(repo.fullName.substringAfter('/'), modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            if (repo.isPrivate) Text("خاص", style = MaterialTheme.typography.labelSmall)
            if (selected) {
                Spacer(Modifier.size(8.dp))
                Icon(Icons.Outlined.TaskAlt, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun ModelRow(index: Int, model: FreeProviderRanking) {
    val badge = when (index) {
        0 -> "الأقوى"
        1 -> "الثاني"
        2 -> "الثالث"
        else -> "#${index + 1}"
    }
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Code, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(12.dp))
            Text(model.modelName, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Text(
                    badge,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun EmptyCard(text: String) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Text(
            text,
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
