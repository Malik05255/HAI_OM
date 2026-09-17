package com.haiom.app.model

import kotlinx.serialization.Serializable

@Serializable
data class FreeCodingModel(
    val id: String,
    val displayName: String,
    val provider: String,
    val endpoint: String,
    val codingPriority: Int,
    val auth: AuthMode = AuthMode.NONE,
    val note: String = ""
)

@Serializable
enum class AuthMode { NONE, OPTIONAL_FREE_KEY }

@Serializable
data class AgentPlan(val tasks: List<AgentTask>)

@Serializable
data class AgentTask(
    val id: String,
    val title: String,
    val objective: String,
    val acceptance: List<String> = emptyList()
)

@Serializable
data class EditBatch(
    val summary: String,
    val files: List<FileEdit>
)

@Serializable
data class FileEdit(
    val path: String,
    val content: String = "",
    val delete: Boolean = false
)

data class AgentRunResult(
    val branch: String,
    val pullRequestUrl: String?,
    val completedTasks: Int,
    val totalTasks: Int
)

enum class CiState { PENDING, SUCCESS, FAILURE, NOT_FOUND }

data class CiResult(
    val state: CiState,
    val runId: Long? = null,
    val logs: String = ""
)

data class RepoSnapshot(
    val owner: String,
    val repo: String,
    val defaultBranch: String,
    val branch: String,
    val context: String
)
