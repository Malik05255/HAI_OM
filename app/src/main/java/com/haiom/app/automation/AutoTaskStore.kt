package com.haiom.app.automation

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class AutoTaskStatus {
    WAITING,
    RUNNING,
    SUCCESS,
    FAILED
}

@Serializable
data class AutoTaskItem(
    val id: String,
    val title: String,
    val requirements: String,
    val order: Int,
    val status: AutoTaskStatus = AutoTaskStatus.WAITING,
    val result: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class AutoQueueSnapshot(
    val tasks: List<AutoTaskItem> = emptyList(),
    val started: Boolean = false,
    val awaitingConfirmation: Boolean = false,
    val events: List<String> = emptyList()
)

class AutoTaskStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _state = MutableStateFlow(read())
    val state: StateFlow<AutoQueueSnapshot> = _state.asStateFlow()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY_STATE) {
            _state.value = read()
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun snapshot(): AutoQueueSnapshot = _state.value

    fun replaceWaitingTasks(tasks: List<AutoTaskItem>) {
        val current = snapshot()
        val fixed = current.tasks
            .filter { it.status != AutoTaskStatus.WAITING }
            .sortedBy { it.order }

        val baseOrder = fixed.maxOfOrNull { it.order } ?: 0
        val waiting = tasks.mapIndexed { index, task ->
            task.copy(
                order = baseOrder + index + 1,
                status = AutoTaskStatus.WAITING,
                updatedAt = System.currentTimeMillis()
            )
        }

        write(current.copy(tasks = fixed + waiting))
    }

    fun setAwaitingConfirmation(value: Boolean) {
        write(snapshot().copy(awaitingConfirmation = value))
    }

    fun setStarted(value: Boolean) {
        val current = snapshot()
        write(
            current.copy(
                started = value,
                awaitingConfirmation = if (value) false else current.awaitingConfirmation
            )
        )
    }

    fun recoverInterrupted() {
        val current = snapshot()
        val recovered = current.tasks.map {
            if (it.status == AutoTaskStatus.RUNNING) {
                it.copy(
                    status = AutoTaskStatus.WAITING,
                    updatedAt = System.currentTimeMillis()
                )
            } else {
                it
            }
        }
        write(current.copy(tasks = recovered))
    }

    fun nextWaiting(): AutoTaskItem? =
        snapshot().tasks
            .filter { it.status == AutoTaskStatus.WAITING }
            .minByOrNull { it.order }

    fun updateTask(
        id: String,
        status: AutoTaskStatus,
        result: String = ""
    ) {
        val current = snapshot()
        val updated = current.tasks.map { task ->
            if (task.id == id) {
                task.copy(
                    status = status,
                    result = result.take(2_000),
                    updatedAt = System.currentTimeMillis()
                )
            } else {
                task
            }
        }
        write(current.copy(tasks = updated))
    }

    fun addEvent(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        val current = snapshot()
        val next = (current.events + clean)
            .fold(mutableListOf<String>()) { acc, item ->
                if (acc.lastOrNull() != item) acc += item
                acc
            }
            .takeLast(40)
        write(current.copy(events = next))
    }

    fun clearEvents() {
        write(snapshot().copy(events = emptyList()))
    }

    fun clearAll() {
        write(AutoQueueSnapshot())
    }

    private fun read(): AutoQueueSnapshot {
        val raw = prefs.getString(KEY_STATE, null) ?: return AutoQueueSnapshot()
        return runCatching {
            json.decodeFromString<AutoQueueSnapshot>(raw)
        }.getOrDefault(AutoQueueSnapshot())
    }

    private fun write(value: AutoQueueSnapshot) {
        _state.value = value
        prefs.edit().putString(KEY_STATE, json.encodeToString(value)).apply()
    }

    companion object {
        private const val PREFS = "hai_om_auto_tasks"
        private const val KEY_STATE = "queue_state"
    }
}
