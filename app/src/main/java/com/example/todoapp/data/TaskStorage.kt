package com.example.todoapp.data

import android.content.Context
import com.example.todoapp.model.HistoryItem
import com.example.todoapp.model.TaskItem
import com.example.todoapp.model.TaskListType
import com.example.todoapp.model.TaskPriority
import org.json.JSONArray
import org.json.JSONObject

class TaskStorage(context: Context) {

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun loadTasks(): MutableList<TaskItem> {
        val raw = prefs.getString(KEY_TASKS, "[]") ?: "[]"
        val jsonArray = JSONArray(raw)
        val items = mutableListOf<TaskItem>()
        for (index in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(index)
            val parsed = runCatching {
                val listType = TaskListType.entries.firstOrNull { it.name == obj.optString("listType") }
                    ?: return@runCatching null
                val priority = TaskPriority.entries.firstOrNull { it.name == obj.optString("priority") }
                    ?: TaskPriority.MEDIUM
                TaskItem(
                    id = obj.optString("id"),
                    title = obj.optString("title"),
                    description = obj.optString("description", ""),
                    listType = listType,
                    priority = priority,
                    scheduleText = obj.optString("scheduleText", ""),
                    reminderText = obj.optString("reminderText", ""),
                    reminderAtMillis = obj.optLong("reminderAtMillis", 0L),
                    createdDateKey = obj.optString("createdDateKey", ""),
                    isDone = obj.optBoolean("isDone", false)
                )
            }.getOrNull()

            if (parsed != null && parsed.id.isNotBlank() && parsed.title.isNotBlank()) {
                items.add(parsed)
            }
        }
        return items
    }

    fun saveTasks(tasks: List<TaskItem>) {
        val jsonArray = JSONArray()
        tasks.forEach { task ->
            val obj = JSONObject()
                .put("id", task.id)
                .put("title", task.title)
                .put("description", task.description)
                .put("listType", task.listType.name)
                .put("priority", task.priority.name)
                .put("scheduleText", task.scheduleText)
                .put("reminderText", task.reminderText)
                .put("reminderAtMillis", task.reminderAtMillis)
                .put("createdDateKey", task.createdDateKey)
                .put("isDone", task.isDone)
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_TASKS, jsonArray.toString()).apply()
    }

    fun loadHistory(): MutableList<HistoryItem> {
        val raw = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
        val jsonArray = JSONArray(raw)
        val items = mutableListOf<HistoryItem>()
        for (index in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(index)
            val parsed = runCatching {
                val listType = TaskListType.entries.firstOrNull { it.name == obj.optString("listType") }
                    ?: return@runCatching null
                HistoryItem(
                    id = obj.optString("id"),
                    taskTitle = obj.optString("taskTitle"),
                    listType = listType,
                    actionText = obj.optString("actionText"),
                    dayDateLabel = obj.optString("dayDateLabel"),
                    timestampMillis = obj.optLong("timestampMillis", 0L)
                )
            }.getOrNull()

            if (parsed != null && parsed.id.isNotBlank() && parsed.taskTitle.isNotBlank()) {
                items.add(parsed)
            }
        }
        return items
    }

    fun saveHistory(history: List<HistoryItem>) {
        val jsonArray = JSONArray()
        history.forEach { entry ->
            val obj = JSONObject()
                .put("id", entry.id)
                .put("taskTitle", entry.taskTitle)
                .put("listType", entry.listType.name)
                .put("actionText", entry.actionText)
                .put("dayDateLabel", entry.dayDateLabel)
                .put("timestampMillis", entry.timestampMillis)
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_HISTORY, jsonArray.toString()).apply()
    }

    fun loadUse24HourFormat(): Boolean {
        return prefs.getBoolean(KEY_USE_24_HOUR, false)
    }

    fun saveUse24HourFormat(value: Boolean) {
        prefs.edit().putBoolean(KEY_USE_24_HOUR, value).apply()
    }

    fun loadReminderVibrationEnabled(): Boolean {
        return prefs.getBoolean(KEY_REMINDER_VIBRATION, true)
    }

    fun saveReminderVibrationEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_REMINDER_VIBRATION, value).apply()
    }

    fun loadDefaultPriority(): TaskPriority {
        val raw = prefs.getString(KEY_DEFAULT_PRIORITY, TaskPriority.MEDIUM.name).orEmpty()
        return TaskPriority.entries.firstOrNull { it.name == raw } ?: TaskPriority.MEDIUM
    }

    fun saveDefaultPriority(priority: TaskPriority) {
        prefs.edit().putString(KEY_DEFAULT_PRIORITY, priority.name).apply()
    }

    fun loadDarkThemeEnabled(): Boolean {
        return prefs.getBoolean(KEY_DARK_THEME, false)
    }

    fun saveDarkThemeEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_THEME, value).apply()
    }

    fun loadUiStylePresetName(): String {
        return prefs.getString(KEY_UI_STYLE_PRESET, "MINIMAL").orEmpty()
    }

    fun saveUiStylePresetName(value: String) {
        prefs.edit().putString(KEY_UI_STYLE_PRESET, value).apply()
    }

    companion object {
        private const val PREF_NAME = "todo_local_store"
        private const val KEY_TASKS = "tasks"
        private const val KEY_HISTORY = "history"
        private const val KEY_USE_24_HOUR = "use_24_hour"
        private const val KEY_REMINDER_VIBRATION = "reminder_vibration"
        private const val KEY_DEFAULT_PRIORITY = "default_priority"
        private const val KEY_DARK_THEME = "dark_theme"
        private const val KEY_UI_STYLE_PRESET = "ui_style_preset"
    }
}
