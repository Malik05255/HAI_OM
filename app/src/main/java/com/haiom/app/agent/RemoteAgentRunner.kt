package com.haiom.app.agent

import android.content.Context
import com.haiom.app.model.AgentRunResult
import com.haiom.app.model.CiState
import com.haiom.app.model.EditBatch
import com.haiom.app.model.FileEdit
import com.haiom.app.network.GitHubClient
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Runs OmniRoute and the autonomous coding loop inside GitHub Actions.
 *
 * Nothing AI-related has to run on the Android device. The app only creates an
 * ephemeral branch, injects the runner into that branch, waits for the named
 * workflow, removes the runner files, then opens a PR containing only the
 * requested project changes.
 */
class RemoteAgentRunner(
    private val context: Context,
    private val github: GitHubClient
) {
    suspend fun run(
        repositoryUrl: String,
        requirements: String,
        onEvent: (String) -> Unit
    ): AgentRunResult {
        require(requirements.isNotBlank()) { "اكتب المطلوب" }

        val repo = github.parseRepository(repositoryUrl)
        val base = github.defaultBranch(repo)
        val branch = "hai-agent/" + System.currentTimeMillis()

        onEvent("تجهيز المهمة")
        github.createBranch(repo, base, branch)

        val workflow = asset("remote/hai-om-agent.yml")
        val runner = asset("remote/hai-om-agent.mjs")
        val taskJson = buildJsonObject {
            put("requirements", requirements.take(MAX_REQUIREMENTS_CHARS))
            put("baseBranch", base)
            put("branch", branch)
            put("repository", repo.owner + "/" + repo.repo)
        }.toString()

        val bootstrap = EditBatch(
            summary = "تجهيز تنفيذ HAI OM السحابي",
            files = listOf(
                FileEdit(WORKFLOW_PATH, workflow),
                FileEdit(RUNNER_PATH, runner),
                FileEdit(TASK_PATH, taskJson)
            )
        )
        github.applyBatch(repo, branch, bootstrap, "remote-bootstrap", onEvent)

        val kickoffSha = github.headSha(repo, branch)
        onEvent("بدأ التنفيذ")

        val run = github.waitForCi(
            repo = repo,
            branch = branch,
            headSha = kickoffSha,
            onEvent = onEvent,
            workflowName = WORKFLOW_NAME
        )

        when (run.state) {
            CiState.SUCCESS -> onEvent("✓ اكتمل التنفيذ")
            CiState.FAILURE -> throw IllegalStateException(friendlyFailure(run.logs))
            CiState.NOT_FOUND -> throw IllegalStateException(
                "لم يبدأ التنفيذ. تأكد أن GitHub Actions مفعّل لهذا المشروع."
            )
            CiState.PENDING -> throw IllegalStateException("التنفيذ ما زال قيد الانتظار")
        }

        onEvent("تنظيف ملفات التشغيل")
        val cleanup = EditBatch(
            summary = "إزالة ملفات تشغيل HAI OM المؤقتة",
            files = listOf(
                FileEdit(WORKFLOW_PATH, delete = true),
                FileEdit(RUNNER_PATH, delete = true),
                FileEdit(TASK_PATH, delete = true)
            )
        )
        github.applyBatch(repo, branch, cleanup, "remote-cleanup", onEvent)

        onEvent("تجهيز النتيجة")
        val shortTitle = requirements.lineSequence()
            .firstOrNull()
            .orEmpty()
            .take(70)
            .ifBlank { "تحديث المشروع" }

        val pullRequestUrl = github.createPullRequest(
            repo = repo,
            branch = branch,
            base = base,
            title = "HAI OM: " + shortTitle,
            body = buildString {
                appendLine("تم تنفيذ الطلب تلقائيًا بواسطة HAI OM.")
                appendLine()
                appendLine("## الطلب")
                appendLine(requirements.take(3_000))
                appendLine()
                appendLine("تم تشغيل البرمجة والفحص والإصلاح داخل GitHub Actions باستخدام OmniRoute ومصادر مجانية فقط.")
            }
        )

        return AgentRunResult(
            branch = branch,
            pullRequestUrl = pullRequestUrl,
            completedTasks = 1,
            totalTasks = 1
        )
    }

    private fun asset(path: String): String =
        context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun friendlyFailure(logs: String): String {
        val lower = logs.lowercase()
        return when {
            "الموديلات المجانية غير متاحة" in logs ||
                "no eligible" in lower ||
                "no candidate" in lower ->
                "الموديلات المجانية مشغولة أو غير متاحة الآن. حاول مرة ثانية بعد قليل."

            "workflow" in lower && "permission" in lower ->
                "المشروع لا يسمح للتطبيق بتشغيل التعديلات تلقائيًا."

            "resource not accessible by integration" in lower ||
                "403" in lower ->
                "صلاحية GitHub لهذا المشروع غير كافية. افصل GitHub واربطه من جديد."

            "timed out" in lower || "timeout" in lower ->
                "استغرق التنفيذ وقتًا أطول من المسموح. حاول مرة ثانية."

            else -> "تعذر إكمال المهمة أثناء التنفيذ. حاول مرة ثانية."
        }
    }

    companion object {
        private const val WORKFLOW_NAME = "HAI OM Agent"
        private const val WORKFLOW_PATH = ".github/workflows/hai-om-agent.yml"
        private const val RUNNER_PATH = ".hai-om/agent.mjs"
        private const val TASK_PATH = ".hai-om/task.json"
        private const val MAX_REQUIREMENTS_CHARS = 20_000
    }
}
