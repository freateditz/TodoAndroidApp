package com.example.todoapp.model

enum class TaskListType {
    DAILY,
    WEEKLY,
    MONTHLY
}

enum class TaskPriority {
    LOW,
    MEDIUM,
    HIGH
}

data class TaskItem(
    val id: String,
    val title: String,
    val description: String,
    val listType: TaskListType,
    val priority: TaskPriority,
    val scheduleText: String,
    val reminderText: String,
    val reminderAtMillis: Long,
    val createdDateKey: String,
    val isDone: Boolean
)

data class HistoryItem(
    val id: String,
    val taskTitle: String,
    val listType: TaskListType,
    val actionText: String,
    val dayDateLabel: String,
    val timestampMillis: Long
)
