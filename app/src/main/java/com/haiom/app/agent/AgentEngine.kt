package com.haiom.app.agent

import com.haiom.app.model.AgentPlan
import com.haiom.app.model.AgentRunResult
import com.haiom.app.model.AgentTask
import com.haiom.app.model.CiState
import com.haiom.app.model.EditBatch
import com.haiom.app.network.GitHubClient
import com.haiom.app.network.OmniRouteClient
import kotlinx.serialization.json.Json

class AgentEngine(
    private val omniRoute: OmniRouteClient,
    private val github: GitHubClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun run(repositoryUrl: String, requirements: String, onEvent: (String) -> Unit): AgentRunResult {
        require(requirements.isNotBlank()) { "اكتب مواصفات البرنامج أو التعديل المطلوب" }
        val repo = github.parseRepository(repositoryUrl)
        val base = github.defaultBranch(repo)
        val branch = "hai-agent/${System.currentTimeMillis()}"
        onEvent("قراءة المستودع ${repo.owner}/${repo.repo}")
        val initialContext = github.buildContext(repo, base)
        require(initialContext.isNotBlank()) { "لم أجد ملفات نصية قابلة للتحليل في المستودع" }

        onEvent("إنشاء خطة التنفيذ")
        val planCompletion = omniRoute.complete(PLANNER_SYSTEM, plannerPrompt(requirements, initialContext))
        onEvent("المخطط عبر OmniRoute: ${planCompletion.modelLabel}")
        val plan = decode<AgentPlan>(planCompletion.text)
        val tasks = plan.tasks.take(20)
        require(tasks.isNotEmpty()) { "النموذج لم ينشئ مهام تنفيذ" }

        github.createBranch(repo, base, branch)
        onEvent("فرع العمل: $branch")

        var completed = 0
        for ((index, task) in tasks.withIndex()) {
            onEvent("المهمة ${index + 1}/${tasks.size}: ${task.title}")
            executeTask(repo, branch, requirements, task, onEvent)
            completed++
            onEvent("✓ اكتملت: ${task.title}")
        }

        val prUrl = github.createPullRequest(
            repo = repo,
            branch = branch,
            base = base,
            title = "HAI Agent: ${tasks.first().title.take(60)}",
            body = buildString {
                appendLine("تم إنشاء هذا التغيير تلقائيًا بواسطة HAI OM.")
                appendLine()
                appendLine("## الطلب")
                appendLine(requirements.take(3000))
                appendLine()
                appendLine("## المهام المنفذة")
                tasks.forEach { appendLine("- ${it.title}") }
                appendLine()
                appendLine("السياسة: OmniRoute auto/coding + strict zero-cost + RTK/Caveman compression + CI self-healing.")
            }
        )
        onEvent(prUrl?.let { "تم إنشاء Pull Request: $it" } ?: "اكتمل التنفيذ")
        return AgentRunResult(branch, prUrl, completed, tasks.size)
    }

    private suspend fun executeTask(
        repo: GitHubClient.RepoRef,
        branch: String,
        requirements: String,
        task: AgentTask,
        onEvent: (String) -> Unit
    ) {
        var context = github.buildContext(repo, branch)
        val editCompletion = omniRoute.complete(EDITOR_SYSTEM, editorPrompt(requirements, task, context))
        onEvent("تنفيذ عبر OmniRoute: ${editCompletion.modelLabel}")
        val batch = decode<EditBatch>(editCompletion.text)
        require(batch.files.isNotEmpty()) { "المهمة لم تنتج أي تعديلات" }
        github.applyBatch(repo, branch, batch, task.id, onEvent)

        var head = github.headSha(repo, branch)
        var ci = github.waitForCi(repo, branch, head, onEvent)
        if (ci.state == CiState.NOT_FOUND) {
            onEvent("لا يوجد GitHub Actions لهذه المهمة؛ الانتقال بعد الفحص البنيوي")
            return
        }

        var attempt = 0
        while (ci.state == CiState.FAILURE && attempt < MAX_FIX_ATTEMPTS) {
            attempt++
            onEvent("فشل CI — محاولة إصلاح $attempt/$MAX_FIX_ATTEMPTS")
            context = github.buildContext(repo, branch)
            val fix = omniRoute.complete(
                system = FIXER_SYSTEM,
                user = fixerPrompt(requirements, task, context, attempt),
                toolContext = ci.logs.takeLast(80_000)
            )
            onEvent("الإصلاح عبر OmniRoute: ${fix.modelLabel}")
            val fixBatch = decode<EditBatch>(fix.text)
            require(fixBatch.files.isNotEmpty()) { "نموذج الإصلاح لم ينتج تغييرات" }
            github.applyBatch(repo, branch, fixBatch, "${task.id}-fix$attempt", onEvent)
            head = github.headSha(repo, branch)
            ci = github.waitForCi(repo, branch, head, onEvent)
        }
        if (ci.state == CiState.FAILURE) error("تعذر إصلاح CI بعد $MAX_FIX_ATTEMPTS محاولات: ${task.title}")
        if (ci.state == CiState.SUCCESS) onEvent("✓ CI ناجح")
    }

    private inline fun <reified T> decode(raw: String): T {
        val cleaned = raw.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()
        val first = cleaned.indexOf('{')
        val last = cleaned.lastIndexOf('}')
        require(first >= 0 && last > first) { "استجابة النموذج ليست JSON صالحًا" }
        return json.decodeFromString(cleaned.substring(first, last + 1))
    }

    private fun plannerPrompt(requirements: String, context: String) = """
        USER REQUIREMENTS:
        $requirements

        REPOSITORY CONTEXT:
        $context

        Return ONLY JSON in this exact shape:
        {"tasks":[{"id":"t1","title":"short title","objective":"precise implementation objective","acceptance":["testable condition"]}]}

        Break work into ordered, independently testable tasks. Maximum 12 tasks. Do not add paid services.
    """.trimIndent()

    private fun editorPrompt(requirements: String, task: AgentTask, context: String) = """
        GLOBAL REQUIREMENTS:
        $requirements

        CURRENT TASK:
        id=${task.id}
        title=${task.title}
        objective=${task.objective}
        acceptance=${task.acceptance.joinToString(" | ")}

        CURRENT REPOSITORY CONTEXT:
        $context

        Produce the complete file edits required for THIS TASK ONLY.
        Return ONLY JSON:
        {"summary":"what changed","files":[{"path":"relative/path","content":"COMPLETE FILE CONTENT","delete":false}]}
        For deletion use {"path":"relative/path","content":"","delete":true}.
        Never use placeholders, ellipses, TODOs, secrets, paid APIs, or paths outside the repository.
    """.trimIndent()

    private fun fixerPrompt(requirements: String, task: AgentTask, context: String, attempt: Int) = """
        You are repairing a failed CI build after an automated code edit.
        Attempt: $attempt
        Global requirements: $requirements
        Task: ${task.title} — ${task.objective}

        REPOSITORY CONTEXT:
        $context

        The CI/build log is attached separately as a tool message so OmniRoute can apply RTK compression to it.

        Identify the root cause and return ONLY the minimum complete file replacements needed to fix it:
        {"summary":"root cause and fix","files":[{"path":"relative/path","content":"COMPLETE FILE CONTENT","delete":false}]}
        Do not weaken tests merely to make CI green. Do not add paid services.
    """.trimIndent()

    companion object {
        private const val MAX_FIX_ATTEMPTS = 5
        private const val PLANNER_SYSTEM = "You are a senior software architect. Plan repository changes conservatively, test-first, and free-only. Output strict JSON only."
        private const val EDITOR_SYSTEM = "You are a senior autonomous coding agent. Make production-ready repository edits. Preserve existing behavior unless the task requires change. Output strict JSON only."
        private const val FIXER_SYSTEM = "You are a build/debugging specialist. Use CI evidence to repair the actual root cause with the smallest safe change. Output strict JSON only."
    }
}
