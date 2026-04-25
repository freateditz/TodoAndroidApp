package com.example.todoapp.data

import android.content.Context
import com.example.todoapp.model.HistoryItem
import com.example.todoapp.model.TaskItem
import com.example.todoapp.model.TaskListType
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
            items.add(
                TaskItem(
                    id = obj.getString("id"),
                    title = obj.getString("title"),
                    description = obj.optString("description", ""),
                    listType = TaskListType.valueOf(obj.getString("listType")),
                    scheduleText = obj.getString("scheduleText"),
                    reminderText = obj.getString("reminderText"),
                    reminderAtMillis = obj.optLong("reminderAtMillis", 0L),
                    createdDateKey = obj.optString("createdDateKey", ""),
                    isDone = obj.optBoolean("isDone", false)
                )
            )
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
            items.add(
                HistoryItem(
                    id = obj.getString("id"),
                    taskTitle = obj.getString("taskTitle"),
                    listType = TaskListType.valueOf(obj.getString("listType")),
                    actionText = obj.getString("actionText"),
                    dayDateLabel = obj.getString("dayDateLabel"),
                    timestampMillis = obj.optLong("timestampMillis", 0L)
                )
            )
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

    companion object {
        private const val PREF_NAME = "todo_local_store"
        private const val KEY_TASKS = "tasks"
        private const val KEY_HISTORY = "history"
    }
}
