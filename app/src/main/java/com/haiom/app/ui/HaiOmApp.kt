package com.haiom.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.haiom.app.MainViewModel
import com.haiom.app.model.FreeCodingModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HaiOmApp(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var repo by remember { mutableStateOf("") }
    var requirements by remember { mutableStateOf("") }
    var githubToken by remember { mutableStateOf("") }
    var providerKey by remember { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); vm.clearError() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("HAI OM", fontWeight = FontWeight.Bold)
                        Text("وكيل برمجة مجاني فقط", style = MaterialTheme.typography.labelMedium)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.Security, null)
                            Text("Free-only صارم", fontWeight = FontWeight.Bold)
                        }
                        Text("لا توجد شاشة دفع ولا نماذج مدفوعة. التوجيه ينتقل تلقائيًا بين النماذج المجانية المسموحة فقط.")
                    }
                }
            }

            item { SectionTitle("النماذج — الأقوى للبرمجة أولًا") }
            itemsIndexed(vm.models) { index, model -> ModelRow(index + 1, model) }

            item {
                Spacer(Modifier.height(4.dp))
                SectionTitle("GitHub Agent")
                OutlinedTextField(
                    value = githubToken,
                    onValueChange = { githubToken = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (state.hasGitHubToken) "GitHub token محفوظ — اتركه فارغًا" else "GitHub fine-grained token") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.AccountCircle, null) }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = providerKey,
                    onValueChange = { providerKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (state.hasOptionalProviderKey) "مفتاح Pollinations محفوظ (اختياري)" else "Pollinations key اختياري لرفع الحدود") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = repo,
                    onValueChange = { repo = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("رابط المستودع") },
                    placeholder = { Text("https://github.com/owner/repository") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = requirements,
                    onValueChange = { requirements = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("مواصفات البرنامج أو التعديل") },
                    minLines = 5,
                    maxLines = 12
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { vm.runAgent(repo, requirements, githubToken, providerKey) },
                    enabled = !state.running && repo.isNotBlank() && requirements.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (state.running) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(22.dp))
                        Text("   يعمل تلقائيًا…")
                    } else {
                        Icon(Icons.Outlined.PlayArrow, null)
                        Text("  ابدأ التنفيذ التلقائي")
                    }
                }
            }

            if (state.logs.isNotEmpty()) {
                item {
                    SectionTitle("سجل التنفيذ")
                    Card(shape = RoundedCornerShape(16.dp)) {
                        SelectionContainer {
                            Text(state.logs.joinToString("\n"), modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            state.result?.let { result ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("اكتمل التنفيذ", fontWeight = FontWeight.Bold)
                            Text("المهام: ${result.completedTasks}/${result.totalTasks}")
                            Text("الفرع: ${result.branch}")
                            result.pullRequestUrl?.let { Text("Pull Request: $it") }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun ModelRow(rank: Int, model: FreeCodingModel) {
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Outlined.Code, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text("$rank. ${model.displayName}", fontWeight = FontWeight.SemiBold)
                Text("${model.provider} • ${model.note}", style = MaterialTheme.typography.bodySmall)
            }
            Text("مجاني", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
        }
        HorizontalDivider()
    }
}
