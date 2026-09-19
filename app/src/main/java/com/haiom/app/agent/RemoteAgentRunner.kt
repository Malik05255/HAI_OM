package com.haiom.app.agent

import android.content.Context
import com.haiom.app.model.AgentRunResult
import com.haiom.app.model.CiState
import com.haiom.app.model.EditBatch
import com.haiom.app.model.FileEdit
import com.haiom.app.network.GitHubClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Uses one persistent runtime branch per repository.
 *
 * Conversation is read-only by default. A separate working branch is created
 * only when the user gives an explicit programming/edit command.
 */
class RemoteAgentRunner(
    private val context: Context,
    private val github: GitHubClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun run(
        repositoryUrl: String,
        requirements: String,
        onEvent: (String) -> Unit,
        baseBranchOverride: String? = null,
        onCiProgress: (Long?, String, String) -> Unit = { _, _, _ -> },
        onLiveCode: (String) -> Unit = {},
        autoMerge: Boolean = false
    ): AgentRunResult {
        require(requirements.isNotBlank()) { "اكتب رسالتك" }

        val repo = github.parseRepository(repositoryUrl)
        val defaultBase = github.defaultBranch(repo)
        val base = baseBranchOverride
            ?.takeIf { it.isNotBlank() }
            ?: defaultBase
        val taskId = System.currentTimeMillis().toString()
        val programming = explicitProgrammingCommand(requirements)

        onEvent("تجهيز المحادثة")
        ensureRuntime(repo, base, onEvent)

        val targetBranch = if (programming) {
            "hai-agent/$taskId".also {
                onEvent("تجهيز فرع البرمجة")
                github.createBranch(repo, base, it)
            }
        } else {
            base
        }

        val taskJson = buildJsonObject {
            put("taskId", taskId)
            put("requirements", requirements.take(MAX_REQUIREMENTS_CHARS))
            put("baseBranch", base)
            put("targetBranch", targetBranch)
            put("editAllowed", programming)
            put("repository", repo.owner + "/" + repo.repo)
        }.toString()

        github.applyBatch(
            repo = repo,
            branch = RUNTIME_BRANCH,
            batch = EditBatch(
                summary = "تشغيل HAI OM",
                files = listOf(
                    FileEdit(TASK_PATH, taskJson),
                    FileEdit(CONTROL_PATH, """{"paused":false}""")
                )
            ),
            taskId = "task",
            onEvent = onEvent
        )

        val kickoffSha = github.headSha(repo, RUNTIME_BRANCH)
        onEvent(if (programming) "بدأ التنفيذ" else "جاري الرد")

        val run = coroutineScope {
            var lastLiveCode = ""
            val liveJob = launch {
                while (isActive) {
                    val code = runCatching {
                        github.readFile(
                            repo,
                            RUNTIME_BRANCH,
                            "$LIVE_DIR/$taskId.txt"
                        )?.text.orEmpty()
                    }.getOrDefault("")

                    if (code.isNotBlank() && code != lastLiveCode) {
                        lastLiveCode = code
                        onLiveCode(code)
                    }
                    delay(1_500)
                }
            }

            try {
                github.waitForCi(
                    repo = repo,
                    branch = RUNTIME_BRANCH,
                    headSha = kickoffSha,
                    onEvent = onEvent,
                    workflowName = WORKFLOW_NAME,
                    maxAttempts = 300,
                    onProgress = onCiProgress
                )
            } finally {
                liveJob.cancel()
            }
        }

        when (run.state) {
            CiState.SUCCESS -> Unit
            CiState.FAILURE -> throw IllegalStateException(friendlyFailure(run.logs))
            CiState.NOT_FOUND -> throw IllegalStateException(
                "لم يبدأ HAI OM. تأكد أن GitHub Actions مفعّل لهذا المشروع."
            )
            CiState.PENDING -> throw IllegalStateException("المهمة ما زالت قيد الانتظار")
        }

        val resultPath = "$RESULTS_DIR/$taskId.json"
        val resultFile = github.readFile(repo, RUNTIME_BRANCH, resultPath)
            ?: throw IllegalStateException("اكتمل التشغيل لكن لم تصل النتيجة.")

        val resultObject = runCatching {
            json.parseToJsonElement(resultFile.text).jsonObject
        }.getOrElse {
            throw IllegalStateException("تعذر قراءة نتيجة HAI OM.")
        }

        val mode = resultObject["mode"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val answer = resultObject["answer"]?.jsonPrimitive?.contentOrNull
            ?.trim()
            .orEmpty()

        runCatching {
            github.applyBatch(
                repo = repo,
                branch = RUNTIME_BRANCH,
                batch = EditBatch(
                    summary = "تنظيف نتيجة HAI OM",
                    files = listOf(
                        FileEdit(TASK_PATH, delete = true),
                        FileEdit(resultPath, delete = true),
                        FileEdit("$LIVE_DIR/$taskId.txt", delete = true)
                    )
                ),
                taskId = "runtime-cleanup",
                onEvent = {}
            )
        }

        if (!programming || mode == "answer") {
            onEvent("✓ تم الرد")
            if (programming && targetBranch != base) {
                runCatching { github.deleteBranch(repo, targetBranch) }
            }
            return AgentRunResult(
                branch = base,
                pullRequestUrl = null,
                completedTasks = 1,
                totalTasks = 1,
                answer = answer.ifBlank { "تم." }
            )
        }

        onEvent("تجهيز النتيجة")
        val shortTitle = requirements.lineSequence()
            .firstOrNull()
            .orEmpty()
            .take(70)
            .ifBlank { "تحديث المشروع" }

        val pullRequestUrl = github.createPullRequest(
            repo = repo,
            branch = targetBranch,
            base = base,
            title = "HAI OM: $shortTitle",
            body = buildString {
                appendLine("تم تنفيذ الطلب تلقائيًا بواسطة HAI OM.")
                appendLine()
                appendLine("## الطلب")
                appendLine(requirements.take(3_000))
                appendLine()
                appendLine("تم تشغيل البرمجة والفحص والإصلاح باستخدام OmniRoute ومصادر مجانية فقط.")
            }
        )

        val finalBranch = if (autoMerge && !pullRequestUrl.isNullOrBlank()) {
            onEvent("دمج التعديل في المشروع")
            val merged = github.mergePullRequest(
                repo = repo,
                pullRequestUrl = pullRequestUrl,
                title = "HAI OM: $shortTitle"
            )
            if (!merged) {
                throw IllegalStateException("تم تنفيذ التعديل لكن تعذر دمجه تلقائيًا")
            }
            runCatching { github.deleteBranch(repo, targetBranch) }
            onEvent("✓ تم دمج التعديل")
            base
        } else {
            targetBranch
        }

        onEvent("✓ اكتملت البرمجة")
        return AgentRunResult(
            branch = finalBranch,
            pullRequestUrl = pullRequestUrl,
            completedTasks = 1,
            totalTasks = 1,
            answer = answer.takeIf { it.isNotBlank() }
        )
    }

    private suspend fun ensureRuntime(
        repo: GitHubClient.RepoRef,
        base: String,
        onEvent: (String) -> Unit
    ) {
        val exists = runCatching { github.headSha(repo, RUNTIME_BRANCH) }.isSuccess
        if (!exists) {
            github.createBranch(repo, base, RUNTIME_BRANCH)
        }

        val workflow = asset("remote/hai-om-agent.yml")
        val runner = asset("remote/hai-om-agent.mjs")

        val updates = mutableListOf<FileEdit>()
        val currentWorkflow = runCatching {
            github.readFile(repo, RUNTIME_BRANCH, WORKFLOW_PATH)?.text
        }.getOrNull()
        val currentRunner = runCatching {
            github.readFile(repo, RUNTIME_BRANCH, RUNNER_PATH)?.text
        }.getOrNull()

        if (currentWorkflow != workflow) updates += FileEdit(WORKFLOW_PATH, workflow)
        if (currentRunner != runner) updates += FileEdit(RUNNER_PATH, runner)

        if (updates.isNotEmpty()) {
            onEvent("تجهيز المشغل")
            github.applyBatch(
                repo = repo,
                branch = RUNTIME_BRANCH,
                batch = EditBatch(
                    summary = "مزامنة مشغل HAI OM",
                    files = updates
                ),
                taskId = "runtime-sync",
                onEvent = {}
            )
        }
    }

    private fun explicitProgrammingCommand(text: String): Boolean {
        val value = text.trim()
        if (value.isBlank()) return false

        val directArabic = Regex(
            """(?:^|\s)(?:نفذ|نفّذ|عدل|عدّل|اصلح|أصلح|اضف|أضف|احذف|برمج|ابن|ابني|أنشئ|انشئ|غيّر|غير|طوّر|طور|صمم|ادمج|اربط|حدّث|حدث|طبّق|طبق)(?:\s|$|:|،|,)""",
            RegexOption.IGNORE_CASE
        )
        val phraseArabic = Regex(
            """(?:اكتب\s+(?:الكود|كود)|أعد\s+بناء|اعد\s+بناء|ابي\s+(?:تضيف|تعدل|تصلح|تحذف|تبرمج|تنفذ|تسوي)|أريدك\s+(?:أن\s+)?(?:تضيف|تعدل|تصلح|تحذف|تبرمج|تنفذ|تسوي))""",
            RegexOption.IGNORE_CASE
        )
        val english = Regex(
            """\b(?:implement|modify|edit|fix|add|remove|delete|refactor|build|create|code|program|redesign|merge|apply|update)\b""",
            RegexOption.IGNORE_CASE
        )

        return directArabic.containsMatchIn(value) ||
            phraseArabic.containsMatchIn(value) ||
            english.containsMatchIn(value)
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
                "المشروع لا يسمح لـ HAI OM بالتشغيل التلقائي."

            "resource not accessible by integration" in lower ||
                "403" in lower ->
                "صلاحية GitHub لهذا المشروع غير كافية. افصل GitHub واربطه من جديد."

            "timed out" in lower || "timeout" in lower ->
                "استغرق التنفيذ وقتًا أطول من المسموح. حاول مرة ثانية."

            else -> "تعذر إكمال الطلب. حاول مرة ثانية."
        }
    }

    companion object {
        private const val RUNTIME_BRANCH = "hai-om/runtime"
        private const val WORKFLOW_NAME = "HAI OM Agent"
        private const val WORKFLOW_PATH = ".github/workflows/hai-om-agent.yml"
        private const val RUNNER_PATH = ".hai-om/agent.mjs"
        private const val TASK_PATH = ".hai-om/task.json"
        private const val CONTROL_PATH = ".hai-om/control.json"
        private const val RESULTS_DIR = ".hai-om/results"
        private const val LIVE_DIR = ".hai-om/live"
        private const val MAX_REQUIREMENTS_CHARS = 20_000
    }
}
