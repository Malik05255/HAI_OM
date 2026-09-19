package com.haiom.app.automation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.haiom.app.agent.RemoteAgentRunner
import com.haiom.app.network.GitHubAppLinker
import com.haiom.app.network.GitHubClient
import com.haiom.app.security.SecretStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AutoTaskWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    private val store = AutoTaskStore(appContext)
    private val secrets = SecretStore(appContext)

    override suspend fun doWork(): Result = coroutineScope {
        store.recoverInterrupted()

        if (!store.snapshot().started) {
            store.markWorkerStopped("متوقف")
            return@coroutineScope Result.success()
        }

        store.markWorkerStarting()

        try {
            val fullName = secrets.selectedRepositoryFullName()
            if (fullName.isBlank()) {
                store.addEvent("اختر مشروعًا")
                store.setStarted(false)
                store.markWorkerStopped("متوقف", "لم يتم اختيار مشروع")
                return@coroutineScope Result.failure()
            }

            val token = resolveGitHubToken()
            if (token.isNullOrBlank()) {
                store.addEvent("اربط GitHub")
                store.setStarted(false)
                store.markWorkerStopped("متوقف", "GitHub غير مربوط")
                return@coroutineScope Result.failure()
            }

            setForeground(createForegroundInfo("تنفيذ المهام"))
            store.markWorkerRunning()

            val heartbeatJob = launch {
                while (isActive) {
                    delay(3_000)
                    store.heartbeat()
                }
            }

            try {
                val github = GitHubClient(token)
                val runner = RemoteAgentRunner(applicationContext, github)
                val repositoryUrl = "https://github.com/$fullName"
                var workingBranch = store.snapshot().tasks
                    .filter {
                        it.status == AutoTaskStatus.SUCCESS &&
                            it.branch.isNotBlank()
                    }
                    .maxByOrNull { it.order }
                    ?.branch

                while (store.snapshot().started) {
                    val task = store.nextWaiting() ?: break

                    store.updateTask(task.id, AutoTaskStatus.RUNNING)
                    store.clearRemoteExecution(fullName)
                    store.addEvent("${task.order}. ${task.title} — تجهيز التنفيذ")
                    store.heartbeat("${task.order}. ${task.title}")
                    setForeground(createForegroundInfo(task.title))

                    try {
                        val result = runner.run(
                            repositoryUrl = repositoryUrl,
                            requirements = buildString {
                                appendLine(
                                    "نفّذ هذه المهمة بالكامل على المشروع المحدد فقط."
                                )
                                appendLine(
                                    "أصلح أي خطأ يظهر واختبر النتيجة قبل الانتقال للمهمة التالية."
                                )
                                appendLine()
                                append(task.requirements)
                            },
                            onEvent = { event ->
                                val clean = event.trim()
                                store.addEvent("${task.order}. $clean")
                                store.heartbeat(clean.take(100))
                            },
                            baseBranchOverride = workingBranch,
                            onCiProgress = { runId, state, stage ->
                                store.updateRemoteExecution(
                                    repository = fullName,
                                    runId = runId,
                                    state = state,
                                    stage = stage
                                )
                                val visibleId =
                                    runId?.takeIf { it > 0L }?.let { " #$it" }.orEmpty()
                                store.addEvent(
                                    "${task.order}. GitHub Actions$visibleId — $stage"
                                )
                                store.heartbeat(stage)
                            }
                        )

                        workingBranch =
                            result.branch.ifBlank { workingBranch.orEmpty() }

                        store.updateTask(
                            id = task.id,
                            status = AutoTaskStatus.SUCCESS,
                            result = result.answer.orEmpty(),
                            branch = result.branch
                        )
                        store.updateRemoteExecution(
                            repository = fullName,
                            runId = store.snapshot().remoteRunId.takeIf { it > 0L },
                            state = "success",
                            stage = "اكتمل GitHub Actions"
                        )
                        store.addEvent("${task.order}. ${task.title} — تم")
                        store.heartbeat("تمت المهمة ${task.order}")
                    } catch (cancelled: CancellationException) {
                        store.updateTask(task.id, AutoTaskStatus.WAITING)
                        throw cancelled
                    } catch (t: Throwable) {
                        store.updateTask(
                            id = task.id,
                            status = AutoTaskStatus.FAILED,
                            result = t.message.orEmpty()
                        )
                        store.updateRemoteExecution(
                            repository = fullName,
                            runId = store.snapshot().remoteRunId.takeIf { it > 0L },
                            state = "failure",
                            stage = t.message?.take(120).orEmpty()
                                .ifBlank { "فشل التنفيذ" }
                        )
                        store.addEvent("${task.order}. ${task.title} — فشل")
                        store.heartbeat("فشل المهمة ${task.order}")
                    }
                }

                store.setStarted(false)
                store.addEvent("اكتملت المهام")
                store.markWorkerStopped("اكتملت")
                Result.success()
            } finally {
                heartbeatJob.cancel()
            }
        } catch (_: CancellationException) {
            store.markWorkerStopped("متوقف")
            Result.retry()
        } catch (t: Throwable) {
            store.snapshot().tasks
                .firstOrNull { it.status == AutoTaskStatus.RUNNING }
                ?.let { runningTask ->
                    store.updateTask(
                        runningTask.id,
                        AutoTaskStatus.FAILED,
                        t.message.orEmpty()
                    )
                }

            store.setStarted(false)
            store.addEvent("توقف التنفيذ")
            store.markWorkerStopped(
                message = "توقف",
                error = t.message ?: t::class.java.simpleName
            )
            Result.failure()
        }
    }

    private suspend fun resolveGitHubToken(): String? {
        secrets.githubToken().takeIf { it.isNotBlank() }?.let { return it }

        val appId = secrets.githubAppId() ?: return null
        val installationId = secrets.githubInstallationId() ?: return null
        val key = secrets.githubAppPrivateKey()
        if (key.isBlank()) return null

        return runCatching {
            GitHubAppLinker().installationToken(
                appId = appId,
                privateKeyPem = key,
                installationId = installationId
            )
        }.getOrNull()
    }

    private fun createForegroundInfo(text: String): ForegroundInfo {
        val manager = applicationContext.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "HAI OM",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("HAI OM")
            .setContentText(text.take(80))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    companion object {
        private const val UNIQUE_WORK = "hai_om_auto_queue"
        private const val CHANNEL_ID = "hai_om_auto_tasks"
        private const val NOTIFICATION_ID = 1401

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<AutoTaskWorker>().build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(
                    UNIQUE_WORK,
                    ExistingWorkPolicy.KEEP,
                    request
                )
        }
    }
}
