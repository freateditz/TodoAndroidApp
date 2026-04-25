package com.example.todoapp

import android.Manifest
import android.os.Bundle
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.todoapp.data.TaskStorage
import com.example.todoapp.model.HistoryItem
import com.example.todoapp.model.TaskItem
import com.example.todoapp.model.TaskListType
import com.example.todoapp.notification.ReminderScheduler
import com.example.todoapp.ui.theme.ToDoAppTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ToDoAppTheme {
                ToDoAppRoot()
            }
        }
    }
}

@Composable
fun ToDoAppRoot() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val storage = remember { TaskStorage(context) }

    val allTasks = remember { mutableStateListOf<TaskItem>() }
    val history = remember { mutableStateListOf<HistoryItem>() }
    var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.Home) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {}
    )

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        allTasks.clear()
        allTasks.addAll(storage.loadTasks())
        history.clear()
        history.addAll(storage.loadHistory())

        // Automatically archive previous-day daily tasks into history buckets.
        val todayKey = formatDateKey(System.currentTimeMillis())
        val oldDailyTasks = allTasks.filter {
            it.listType == TaskListType.DAILY && it.createdDateKey != todayKey
        }
        if (oldDailyTasks.isNotEmpty()) {
            val updatedHistory = history.toMutableList()
            oldDailyTasks.forEach { task ->
                updatedHistory.add(
                    HistoryItem(
                        id = UUID.randomUUID().toString(),
                        taskTitle = task.title,
                        listType = TaskListType.DAILY,
                        actionText = if (task.isDone) "Completed" else "Not completed",
                        dayDateLabel = task.createdDateKey,
                        timestampMillis = System.currentTimeMillis()
                    )
                )
            }

            val remaining = allTasks.filterNot { it in oldDailyTasks }
            allTasks.clear()
            allTasks.addAll(remaining)

            history.clear()
            history.addAll(updatedHistory)

            storage.saveTasks(allTasks)
            storage.saveHistory(history)
        }
    }

    when (val screen = currentScreen) {
        AppScreen.Home -> HomeScreen(
            onOpenDaily = { currentScreen = AppScreen.List(TaskListType.DAILY) },
            onOpenWeekly = { currentScreen = AppScreen.List(TaskListType.WEEKLY) },
            onOpenMonthly = { currentScreen = AppScreen.List(TaskListType.MONTHLY) }
        )

        is AppScreen.List -> TaskListScreen(
            listType = screen.listType,
            tasks = allTasks.filter { it.listType == screen.listType },
            history = history.filter { it.listType == screen.listType }
                .sortedByDescending { it.timestampMillis },
            onBack = { currentScreen = AppScreen.Home },
            onAddTask = { newTask ->
                allTasks.add(newTask)
                storage.saveTasks(allTasks)
                ReminderScheduler.scheduleReminder(context, newTask)
            },
            onUpdateTaskStatus = { taskId, done ->
                val updated = allTasks.map { task ->
                    if (task.id == taskId) task.copy(isDone = done) else task
                }
                allTasks.clear()
                allTasks.addAll(updated)
                storage.saveTasks(allTasks)

                val changedTask = updated.firstOrNull { it.id == taskId } ?: return@TaskListScreen
                val entry = HistoryItem(
                    id = UUID.randomUUID().toString(),
                    taskTitle = changedTask.title,
                    listType = changedTask.listType,
                    actionText = if (done) "Marked done" else "Marked not done",
                    dayDateLabel = formatHumanDate(System.currentTimeMillis()),
                    timestampMillis = System.currentTimeMillis()
                )
                history.add(entry)
                storage.saveHistory(history)
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ToDoAppTheme {
        HomeScreen({}, {}, {})
    }
}

private sealed class AppScreen {
    data object Home : AppScreen()
    data class List(val listType: TaskListType) : AppScreen()
}

@Composable
private fun HomeScreen(
    onOpenDaily: () -> Unit,
    onOpenWeekly: () -> Unit,
    onOpenMonthly: () -> Unit
) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(20.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "ToDoApp",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Choose your list type",
                style = MaterialTheme.typography.bodyLarge
            )
            HomeOptionCard(title = "Daily List", subtitle = "Tasks for today", onClick = onOpenDaily)
            HomeOptionCard(title = "Weekly List", subtitle = "Tasks for this week", onClick = onOpenWeekly)
            HomeOptionCard(title = "Monthly List", subtitle = "Plan your month", onClick = onOpenMonthly)
        }
    }
}

@Composable
private fun HomeOptionCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskListScreen(
    listType: TaskListType,
    tasks: List<TaskItem>,
    history: List<HistoryItem>,
    onBack: () -> Unit,
    onAddTask: (TaskItem) -> Unit,
    onUpdateTaskStatus: (String, Boolean) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(initialValue = androidx.compose.material3.DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = {
                    showHistory = false
                    scope.launch { drawerState.close() }
                }) {
                    Text("Task List")
                }
                TextButton(onClick = {
                    showHistory = true
                    scope.launch { drawerState.close() }
                }) {
                    Text("History")
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            when (listType) {
                                TaskListType.DAILY -> "Daily Tasks"
                                TaskListType.WEEKLY -> "Weekly Tasks"
                                TaskListType.MONTHLY -> "Monthly Tasks"
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Text("=", style = MaterialTheme.typography.titleLarge)
                        }
                    },
                    actions = {
                        TextButton(onClick = onBack) {
                            Text("Home")
                        }
                    }
                )
            },
            floatingActionButton = {
                if (!showHistory) {
                    FloatingActionButton(onClick = { showAddDialog = true }) {
                        Text("+")
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            if (showHistory) {
                HistoryContent(
                    history = history,
                    modifier = Modifier.padding(innerPadding)
                )
            } else {
                TaskContent(
                    tasks = tasks,
                    modifier = Modifier.padding(innerPadding),
                    onUpdateTaskStatus = onUpdateTaskStatus
                )
            }
        }
    }

    if (showAddDialog) {
        AddTaskDialog(
            listType = listType,
            onDismiss = { showAddDialog = false },
            onConfirm = { task ->
                onAddTask(task)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun TaskContent(
    tasks: List<TaskItem>,
    modifier: Modifier,
    onUpdateTaskStatus: (String, Boolean) -> Unit
) {
    if (tasks.isEmpty()) {
        Column(modifier = modifier.padding(20.dp)) {
            Text("No tasks yet. Tap + to add one.")
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(tasks) { task ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(task.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (task.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(task.description, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("When: ${task.scheduleText}", style = MaterialTheme.typography.bodySmall)
                    Text("Reminder: ${task.reminderText}", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Checkbox(
                            checked = task.isDone,
                            onCheckedChange = { checked -> onUpdateTaskStatus(task.id, checked) }
                        )
                        Text(if (task.isDone) "Done" else "Not done", modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryContent(history: List<HistoryItem>, modifier: Modifier) {
    if (history.isEmpty()) {
        Column(modifier = modifier.padding(20.dp)) {
            Text("No history found yet.")
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(history) { entry ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(entry.taskTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(entry.actionText, style = MaterialTheme.typography.bodyMedium)
                    Text(entry.dayDateLabel, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun AddTaskDialog(
    listType: TaskListType,
    onDismiss: () -> Unit,
    onConfirm: (TaskItem) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    var dailyTime by remember { mutableStateOf("") }
    var dailyReminderTime by remember { mutableStateOf("") }

    var weeklyDay by remember { mutableStateOf("") }
    var weeklyTime by remember { mutableStateOf("") }
    var weeklyReminderDay by remember { mutableStateOf("") }
    var weeklyReminderTime by remember { mutableStateOf("") }

    var monthlyDate by remember { mutableStateOf("") }
    var monthlyTime by remember { mutableStateOf("") }
    var monthlyReminderDate by remember { mutableStateOf("") }
    var monthlyReminderTime by remember { mutableStateOf("") }

    var validationMessage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Task") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Task name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                when (listType) {
                    TaskListType.DAILY -> {
                        item {
                            OutlinedTextField(
                                value = dailyTime,
                                onValueChange = { dailyTime = it },
                                label = { Text("Task time (HH:mm)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = dailyReminderTime,
                                onValueChange = { dailyReminderTime = it },
                                label = { Text("Reminder time (HH:mm)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    TaskListType.WEEKLY -> {
                        item {
                            OutlinedTextField(
                                value = weeklyDay,
                                onValueChange = { weeklyDay = it },
                                label = { Text("Task day (Mon/Tue/...)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = weeklyTime,
                                onValueChange = { weeklyTime = it },
                                label = { Text("Task time (HH:mm)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = weeklyReminderDay,
                                onValueChange = { weeklyReminderDay = it },
                                label = { Text("Reminder day (Mon/Tue/...)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = weeklyReminderTime,
                                onValueChange = { weeklyReminderTime = it },
                                label = { Text("Reminder time (HH:mm)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    TaskListType.MONTHLY -> {
                        item {
                            OutlinedTextField(
                                value = monthlyDate,
                                onValueChange = { monthlyDate = it },
                                label = { Text("Task date (dd/MM/yyyy)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = monthlyTime,
                                onValueChange = { monthlyTime = it },
                                label = { Text("Task time (HH:mm)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = monthlyReminderDate,
                                onValueChange = { monthlyReminderDate = it },
                                label = { Text("Reminder date (dd/MM/yyyy)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = monthlyReminderTime,
                                onValueChange = { monthlyReminderTime = it },
                                label = { Text("Reminder time (HH:mm)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (validationMessage.isNotBlank()) {
                    item {
                        Text(validationMessage, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val built = buildTaskFromInputs(
                    listType = listType,
                    title = title,
                    description = description,
                    dailyTime = dailyTime,
                    dailyReminderTime = dailyReminderTime,
                    weeklyDay = weeklyDay,
                    weeklyTime = weeklyTime,
                    weeklyReminderDay = weeklyReminderDay,
                    weeklyReminderTime = weeklyReminderTime,
                    monthlyDate = monthlyDate,
                    monthlyTime = monthlyTime,
                    monthlyReminderDate = monthlyReminderDate,
                    monthlyReminderTime = monthlyReminderTime
                )
                if (built == null) {
                    validationMessage = "Please fill all required fields with valid format."
                } else {
                    onConfirm(built)
                }
            }) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun buildTaskFromInputs(
    listType: TaskListType,
    title: String,
    description: String,
    dailyTime: String,
    dailyReminderTime: String,
    weeklyDay: String,
    weeklyTime: String,
    weeklyReminderDay: String,
    weeklyReminderTime: String,
    monthlyDate: String,
    monthlyTime: String,
    monthlyReminderDate: String,
    monthlyReminderTime: String
): TaskItem? {
    if (title.isBlank()) return null

    val createdDateKey = formatDateKey(System.currentTimeMillis())
    return when (listType) {
        TaskListType.DAILY -> {
            val reminderMillis = parseTodayTime(dailyReminderTime) ?: return null
            if (dailyTime.isBlank()) return null
            TaskItem(
                id = UUID.randomUUID().toString(),
                title = title.trim(),
                description = description.trim(),
                listType = listType,
                scheduleText = "Today at ${dailyTime.trim()}",
                reminderText = "Today at ${dailyReminderTime.trim()}",
                reminderAtMillis = reminderMillis,
                createdDateKey = createdDateKey,
                isDone = false
            )
        }

        TaskListType.WEEKLY -> {
            if (weeklyDay.isBlank() || weeklyTime.isBlank() || weeklyReminderDay.isBlank() || weeklyReminderTime.isBlank()) {
                return null
            }
            val reminderMillis = parseWeeklyReminder(weeklyReminderDay, weeklyReminderTime) ?: return null
            TaskItem(
                id = UUID.randomUUID().toString(),
                title = title.trim(),
                description = description.trim(),
                listType = listType,
                scheduleText = "${weeklyDay.trim()} ${weeklyTime.trim()}",
                reminderText = "${weeklyReminderDay.trim()} ${weeklyReminderTime.trim()}",
                reminderAtMillis = reminderMillis,
                createdDateKey = createdDateKey,
                isDone = false
            )
        }

        TaskListType.MONTHLY -> {
            if (monthlyDate.isBlank() || monthlyTime.isBlank() || monthlyReminderDate.isBlank() || monthlyReminderTime.isBlank()) {
                return null
            }
            val reminderMillis = parseDateTime(monthlyReminderDate, monthlyReminderTime) ?: return null
            TaskItem(
                id = UUID.randomUUID().toString(),
                title = title.trim(),
                description = description.trim(),
                listType = listType,
                scheduleText = "${monthlyDate.trim()} ${monthlyTime.trim()}",
                reminderText = "${monthlyReminderDate.trim()} ${monthlyReminderTime.trim()}",
                reminderAtMillis = reminderMillis,
                createdDateKey = createdDateKey,
                isDone = false
            )
        }
    }
}

private fun parseTodayTime(timeText: String): Long? {
    val parser = SimpleDateFormat("HH:mm", Locale.getDefault())
    val timeDate = parser.parse(timeText.trim()) ?: return null
    val now = Calendar.getInstance()
    val parsed = Calendar.getInstance().apply { time = timeDate }

    val scheduled = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, parsed.get(Calendar.HOUR_OF_DAY))
        set(Calendar.MINUTE, parsed.get(Calendar.MINUTE))
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    if (scheduled.before(now)) {
        scheduled.add(Calendar.DAY_OF_YEAR, 1)
    }
    return scheduled.timeInMillis
}

private fun parseWeeklyReminder(dayText: String, timeText: String): Long? {
    val targetDay = mapWeekDay(dayText) ?: return null
    val parsedTime = SimpleDateFormat("HH:mm", Locale.getDefault()).parse(timeText.trim()) ?: return null
    val parsedCal = Calendar.getInstance().apply { time = parsedTime }

    val scheduled = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_WEEK, targetDay)
        set(Calendar.HOUR_OF_DAY, parsedCal.get(Calendar.HOUR_OF_DAY))
        set(Calendar.MINUTE, parsedCal.get(Calendar.MINUTE))
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    if (scheduled.before(Calendar.getInstance())) {
        scheduled.add(Calendar.WEEK_OF_YEAR, 1)
    }
    return scheduled.timeInMillis
}

private fun parseDateTime(dateText: String, timeText: String): Long? {
    val parser = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    val date = parser.parse("${dateText.trim()} ${timeText.trim()}") ?: return null
    return date.time
}

private fun mapWeekDay(dayText: String): Int? {
    return when (dayText.trim().lowercase(Locale.getDefault())) {
        "sun", "sunday" -> Calendar.SUNDAY
        "mon", "monday" -> Calendar.MONDAY
        "tue", "tuesday" -> Calendar.TUESDAY
        "wed", "wednesday" -> Calendar.WEDNESDAY
        "thu", "thursday" -> Calendar.THURSDAY
        "fri", "friday" -> Calendar.FRIDAY
        "sat", "saturday" -> Calendar.SATURDAY
        else -> null
    }
}

private fun formatDateKey(timeMillis: Long): String {
    return SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault()).format(Date(timeMillis))
}

private fun formatHumanDate(timeMillis: Long): String {
    return SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault()).format(Date(timeMillis))
}