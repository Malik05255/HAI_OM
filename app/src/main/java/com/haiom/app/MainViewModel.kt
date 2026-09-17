package com.haiom.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.haiom.app.agent.AgentEngine
import com.haiom.app.model.AgentRunResult
import com.haiom.app.model.FreeProviderRanking
import com.haiom.app.network.GitHubClient
import com.haiom.app.network.OmniRouteClient
import com.haiom.app.security.SecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MainUiState(
    val running: Boolean = false,
    val connecting: Boolean = false,
    val logs: List<String> = emptyList(),
    val result: AgentRunResult? = null,
    val error: String? = null,
    val hasGitHubToken: Boolean = false,
    val omniReady: Boolean = false,
    val strictFreeVerified: Boolean = false,
    val compressionEnabled: Boolean = false,
    val rankings: List<FreeProviderRanking> = emptyList()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val secrets = SecretStore(application)
    private val _state = MutableStateFlow(
        MainUiState(hasGitHubToken = secrets.githubToken().isNotBlank())
    )
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    fun savedOmniRouteUrl(): String = secrets.omniRouteUrl().ifBlank { DEFAULT_OMNIROUTE_URL }

    fun connectOmniRoute(url: String, key: String) {
        if (_state.value.connecting || _state.value.running) return
        persistOmniRoute(url, key)
        val savedUrl = secrets.omniRouteUrl()
        if (savedUrl.isBlank()) {
            _state.update { it.copy(error = "أدخل رابط OmniRoute") }
            return
        }
        _state.update { it.copy(connecting = true, omniReady = false, error = null) }
        viewModelScope.launch {
            try {
                val omni = OmniRouteClient(savedUrl, secrets.omniRouteKey())
                val connectionLogs = mutableListOf<String>()
                omni.verifyAndConfigure { connectionLogs += it }
                val rankings = runCatching { omni.fetchCodingRankings(50) }.getOrDefault(emptyList())
                _state.update {
                    it.copy(
                        connecting = false,
                        omniReady = true,
                        strictFreeVerified = true,
                        compressionEnabled = true,
                        rankings = rankings,
                        logs = (it.logs + connectionLogs).takeLast(120)
                    )
                }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        connecting = false,
                        omniReady = false,
                        strictFreeVerified = false,
                        compressionEnabled = false,
                        error = t.message ?: t.javaClass.simpleName
                    )
                }
            }
        }
    }

    fun runAgent(
        repositoryUrl: String,
        requirements: String,
        githubToken: String,
        omniRouteUrl: String,
        omniRouteKey: String
    ) {
        if (_state.value.running || _state.value.connecting) return
        if (githubToken.isNotBlank()) secrets.saveGitHubToken(githubToken)
        persistOmniRoute(omniRouteUrl, omniRouteKey)

        val token = secrets.githubToken()
        val savedUrl = secrets.omniRouteUrl()
        if (token.isBlank()) {
            _state.update { it.copy(error = "أدخل GitHub fine-grained token أولًا") }
            return
        }
        if (savedUrl.isBlank()) {
            _state.update { it.copy(error = "أدخل رابط OmniRoute أولًا") }
            return
        }

        _state.update {
            it.copy(
                running = true,
                result = null,
                error = null,
                logs = listOf("بدء HAI OM Agent", "AI: OmniRoute فقط", "Route: ${OmniRouteClient.CODING_ROUTE}")
            )
        }
        viewModelScope.launch {
            try {
                val omni = OmniRouteClient(savedUrl, secrets.omniRouteKey())
                omni.verifyAndConfigure(::appendLog)
                val rankings = runCatching { omni.fetchCodingRankings(50) }.getOrDefault(emptyList())
                _state.update {
                    it.copy(
                        omniReady = true,
                        strictFreeVerified = true,
                        compressionEnabled = true,
                        rankings = rankings
                    )
                }

                val github = GitHubClient(token)
                val result = withContext(Dispatchers.IO) {
                    AgentEngine(omni, github).run(repositoryUrl, requirements, ::appendLog)
                }
                _state.update { it.copy(running = false, result = result) }
            } catch (t: Throwable) {
                _state.update { it.copy(running = false, error = t.message ?: t.javaClass.simpleName) }
            }
        }
    }

    private fun persistOmniRoute(url: String, key: String) {
        if (url.isNotBlank()) secrets.saveOmniRouteUrl(url)
        if (key.isNotBlank()) secrets.saveOmniRouteKey(key)
    }

    private fun appendLog(message: String) {
        _state.update { current -> current.copy(logs = (current.logs + message).takeLast(160)) }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    companion object {
        private const val DEFAULT_OMNIROUTE_URL = "http://127.0.0.1:20128"
    }
}
