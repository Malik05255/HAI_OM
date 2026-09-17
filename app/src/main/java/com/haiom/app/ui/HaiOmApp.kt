package com.haiom.app.ui

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
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.TaskAlt
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
    var tab by remember { mutableIntStateOf(0) }
    var requirements by remember { mutableStateOf("") }
    var selectedRepo by remember { mutableStateOf<GitHubRepository?>(null) }
    var showProjects by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.repositories) {
        if (selectedRepo == null && state.repositories.isNotEmpty()) selectedRepo = state.repositories.first()
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); vm.clearError() }
    }

    if (showProjects) {
        ModalBottomSheet(onDismissRequest = { showProjects = false }) {
            Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 28.dp)) {
                Text("اختر المشروع", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                if (state.repositories.isEmpty()) {
                    EmptyCard("ما فيه مشاريع ظاهرة الآن")
                } else {
                    state.repositories.take(30).forEach { repo ->
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
                onPickProject = { showProjects = true },
                onRun = { vm.runAgent(selectedRepo?.htmlUrl.orEmpty(), requirements) },
                onRefresh = vm::bootstrap
            )
            1 -> ProjectsScreen(
                modifier = Modifier.padding(padding),
                login = state.githubLogin,
                connected = state.hasGitHubToken,
                repositories = state.repositories,
                selectedRepo = selectedRepo,
                onSelect = { selectedRepo = it; tab = 0 },
                onRefresh = vm::bootstrap
            )
            2 -> ModelsScreen(Modifier.padding(padding), state.rankings, state.omniReady)
            else -> ActivityScreen(Modifier.padding(padding), state.logs, state.running, state.result?.pullRequestUrl)
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
    onRefresh: () -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AppMark()
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("HAI OM", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text("مساعدك للبرمجة", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusPill(state.omniReady)
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("وش تبغى أسوي؟", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("اكتب المطلوب وأنا أكمل الباقي.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Column(Modifier.weight(1f)) {
                        Text(selectedRepo?.fullName?.substringAfter('/') ?: "اختر مشروع", fontWeight = FontWeight.SemiBold)
                        selectedRepo?.fullName?.substringBefore('/')?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text("تغيير", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }

        item {
            OutlinedTextField(
                value = requirements,
                onValueChange = onRequirements,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("اكتب المطلوب") },
                placeholder = { Text("مثال: سو لي صفحة تسجيل دخول مرتبة وأصلح أي أخطاء") },
                minLines = 5,
                maxLines = 10,
                shape = RoundedCornerShape(20.dp)
            )
        }

        item {
            Button(
                onClick = onRun,
                enabled = !state.running && !state.connecting && selectedRepo != null && requirements.isNotBlank() && state.hasGitHubToken,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                if (state.running) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(10.dp))
                    Text("جاري العمل…", fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Outlined.PlayArrow, null)
                    Spacer(Modifier.size(8.dp))
                    Text("ابدأ", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (!state.hasGitHubToken) {
            item {
                SimpleNotice(
                    title = "اربط GitHub مرة واحدة",
                    subtitle = "بعدها مشاريعك تظهر هنا تلقائيًا.",
                    action = "تحديث",
                    onAction = onRefresh
                )
            }
        } else if (!state.omniReady && !state.connecting) {
            item {
                SimpleNotice("الخدمة غير جاهزة", "حاول مرة ثانية.", "إعادة المحاولة", onRefresh)
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
    repositories: List<GitHubRepository>,
    selectedRepo: GitHubRepository?,
    onSelect: (GitHubRepository) -> Unit,
    onRefresh: () -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Spacer(Modifier.height(16.dp))
            Text("مشاريعي", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AccountCircle, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(8.dp))
                Text(if (connected) login.ifBlank { "GitHub متصل" } else "GitHub غير مربوط")
                Spacer(Modifier.weight(1f))
                Icon(Icons.Outlined.Refresh, "تحديث", modifier = Modifier.clickable(onClick = onRefresh))
            }
            Spacer(Modifier.height(8.dp))
        }
        if (repositories.isEmpty()) {
            item { EmptyCard(if (connected) "ما لقيت مشاريع" else "اربط GitHub أولًا") }
        } else {
            itemsIndexed(repositories) { _, repo ->
                ProjectRow(repo, selectedRepo?.fullName == repo.fullName) { onSelect(repo) }
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
            Text("نماذج البرمجة", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text("يتم اختيار الأفضل تلقائيًا", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
        }
        if (!ready || rankings.isEmpty()) {
            item { EmptyCard("يظهر الترتيب تلقائيًا عند الجاهزية") }
        } else {
            itemsIndexed(rankings.take(20)) { index, model -> ModelRow(index, model) }
        }
        item { Spacer(Modifier.height(18.dp)) }
    }
}

@Composable
private fun ActivityScreen(modifier: Modifier, logs: List<String>, running: Boolean, prUrl: String?) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Spacer(Modifier.height(16.dp))
            Text("النشاط", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text(if (running) "المهمة تعمل الآن" else "آخر ما تم", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
        }
        if (logs.isEmpty()) {
            item { EmptyCard("ما فيه مهام حتى الآن") }
        } else {
            itemsIndexed(logs.reversed()) { index, log ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier.padding(top = 5.dp).size(9.dp).clip(CircleShape)
                            .then(Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.fillMaxSize().clip(CircleShape).clickable(enabled = false) {})
                    }
                    Spacer(Modifier.size(10.dp))
                    Text(log, modifier = Modifier.weight(1f), fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Normal)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
        if (!prUrl.isNullOrBlank()) {
            item { SimpleNotice("اكتملت المهمة", "تم تجهيز التغييرات للمراجعة.", null, null) }
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
            containerColor = if (ready) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Text(if (ready) "جاهز" else "يتصل…", modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), fontWeight = FontWeight.Bold)
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
            Column(Modifier.weight(1f)) {
                Text(repo.fullName.substringAfter('/'), fontWeight = FontWeight.SemiBold)
                Text(repo.fullName.substringBefore('/'), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (repo.isPrivate) Text("خاص", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Code, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(model.modelName, fontWeight = FontWeight.SemiBold)
                Text(model.providerName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Text(badge, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun EmptyCard(text: String) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Text(text, modifier = Modifier.fillMaxWidth().padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SimpleNotice(title: String, subtitle: String, action: String?, onAction: (() -> Unit)?) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (action != null && onAction != null) {
                OutlinedButton(onClick = onAction) { Text(action) }
            }
        }
    }
}
