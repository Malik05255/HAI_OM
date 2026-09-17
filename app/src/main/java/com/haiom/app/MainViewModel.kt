package com.haiom.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.haiom.app.agent.AgentEngine
import com.haiom.app.data.FreeModelCatalog
import com.haiom.app.model.AgentRunResult
import com.haiom.app.network.FreeModelRouter
import com.haiom.app.network.GitHubClient
import com.haiom.app.security.SecretStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val running: Boolean = false,
    val logs: List<String> = emptyList(),
    val result: AgentRunResult? = null,
    val error: String? = null,
    val hasGitHubToken: Boolean = false,
    val hasOptionalProviderKey: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val secrets = SecretStore(application)
    private val _state = MutableStateFlow(
        MainUiState(
            hasGitHubToken = secrets.githubToken().isNotBlank(),
            hasOptionalProviderKey = secrets.pollinationsKey().isNotBlank()
        )
    )
    val state: StateFlow<MainUiState> = _state.asStateFlow()
    val models = FreeModelCatalog.models

    fun runAgent(repositoryUrl: String, requirements: String, githubToken: String, providerKey: String) {
        if (_state.value.running) return
        if (githubToken.isNotBlank()) secrets.saveGitHubToken(githubToken)
        if (providerKey.isNotBlank()) secrets.savePollinationsKey(providerKey)
        val token = secrets.githubToken()
        if (token.isBlank()) {
            _state.update { it.copy(error = "أدخل GitHub fine-grained token أولًا") }
            return
        }
        _state.value = MainUiState(
            running = true,
            logs = listOf("بدء HAI OM Agent"),
            hasGitHubToken = true,
            hasOptionalProviderKey = secrets.pollinationsKey().isNotBlank()
        )
        viewModelScope.launch {
            try {
                val router = FreeModelRouter(secrets)
                val github = GitHubClient(token)
                val result = AgentEngine(router, github).run(repositoryUrl, requirements) { message ->
                    _state.update { current -> current.copy(logs = (current.logs + message).takeLast(120)) }
                }
                _state.update { it.copy(running = false, result = result) }
            } catch (t: Throwable) {
                _state.update { it.copy(running = false, error = t.message ?: t.javaClass.simpleName) }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}
