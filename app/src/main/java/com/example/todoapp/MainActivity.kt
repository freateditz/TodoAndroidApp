package com.example.todoapp

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.todoapp.data.TaskStorage
import com.example.todoapp.model.HistoryItem
import com.example.todoapp.model.TaskItem
import com.example.todoapp.model.TaskListType
import com.example.todoapp.model.TaskPriority
import com.example.todoapp.notification.ReminderScheduler
import com.example.todoapp.ui.theme.ToDoAppTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlinx.coroutines.delay

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
    val context = LocalContext.current
    val storage = remember { TaskStorage(context) }

    val allTasks = remember { mutableStateListOf<TaskItem>() }
    val history = remember { mutableStateListOf<HistoryItem>() }
    var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.Home) }
    var currentTimeMillis by remember { mutableStateOf(System.currentTimeMillis()) }

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

    LaunchedEffect(Unit) {
        while (true) {
            currentTimeMillis = System.currentTimeMillis()
            delay(1_000)
        }
    }

    GradientBackdrop {
        when (val screen = currentScreen) {
            AppScreen.Home -> HomeScreen(
                onOpenList = { currentScreen = AppScreen.List(it) },
                allTasksCount = allTasks.size,
                completedCount = allTasks.count { it.isDone },
                localTimeLabel = formatLocalClock(currentTimeMillis),
                timeZoneLabel = TimeZone.getDefault().id
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
                },
                onDeleteTask = { taskId ->
                    val target = allTasks.firstOrNull { it.id == taskId } ?: return@TaskListScreen
                    allTasks.removeAll { it.id == taskId }
                    storage.saveTasks(allTasks)
                    ReminderScheduler.cancelReminder(context, taskId)

                    history.add(
                        HistoryItem(
                            id = UUID.randomUUID().toString(),
                            taskTitle = target.title,
                            listType = target.listType,
                            actionText = "Deleted task",
                            dayDateLabel = formatHumanDate(System.currentTimeMillis()),
                            timestampMillis = System.currentTimeMillis()
                        )
                    )
                    storage.saveHistory(history)
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ToDoAppTheme {
        GradientBackdrop {
            HomeScreen(
                onOpenList = {},
                allTasksCount = 12,
                completedCount = 7,
                localTimeLabel = "Fri, 25 Apr 2026 09:30:00",
                timeZoneLabel = "Asia/Kolkata"
            )
        }
    }
}

private sealed class AppScreen {
    data object Home : AppScreen()
    data class List(val listType: TaskListType) : AppScreen()
}

private enum class ListSection {
    TASKS,
    HISTORY
}

@Composable
private fun GradientBackdrop(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFBF3E8),
                        Color(0xFFF4E2CE),
                        Color(0xFFEAD8C4)
                    )
                )
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0x66FFFFFF),
                            Color.Transparent
                        ),
                        radius = 900f
                    )
                )
        )
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    onOpenList: (TaskListType) -> Unit,
    allTasksCount: Int,
    completedCount: Int,
    localTimeLabel: String,
    timeZoneLabel: String
) {
    var pickerExpanded by remember { mutableStateOf(false) }
    var quickTarget by remember { mutableStateOf(TaskListType.DAILY) }

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                GlassPanel {
                    Text(
                        text = "ToDo Hub",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF3F2A19)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Warm planner for daily wins, weekly rhythm, and monthly goals.",
                        color = Color(0xFF6A4A34)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Local time: $localTimeLabel",
                        color = Color(0xFF6E4D35),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = "Time zone: $timeZoneLabel",
                        color = Color(0xFF8C6345),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetricPill(label = "All Tasks", value = allTasksCount.toString())
                        MetricPill(label = "Completed", value = completedCount.toString())
                    }
                }
            }

            item {
                SectionTitle("Planning Sections")
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeOptionCard(
                        modifier = Modifier.weight(1f),
                        title = "Daily",
                        subtitle = "Today focus",
                        onClick = { onOpenList(TaskListType.DAILY) }
                    )
                    HomeOptionCard(
                        modifier = Modifier.weight(1f),
                        title = "Weekly",
                        subtitle = "Week strategy",
                        onClick = { onOpenList(TaskListType.WEEKLY) }
                    )
                }
            }

            item {
                HomeOptionCard(
                    modifier = Modifier.fillMaxWidth(),
                    title = "Monthly",
                    subtitle = "Longer horizon planning",
                    onClick = { onOpenList(TaskListType.MONTHLY) }
                )
            }

            item {
                SectionTitle("Quick Open")
            }

            item {
                GlassPanel {
                    Text(
                        text = "Jump to a section with dropdown",
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF4D321E)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    ExposedDropdownMenuBox(
                        expanded = pickerExpanded,
                        onExpandedChange = { pickerExpanded = !pickerExpanded }
                    ) {
                        OutlinedTextField(
                            value = taskListTitle(quickTarget),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("List Type") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = pickerExpanded)
                            },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        DropdownMenu(
                            expanded = pickerExpanded,
                            onDismissRequest = { pickerExpanded = false }
                        ) {
                            TaskListType.entries.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(taskListTitle(type)) },
                                    onClick = {
                                        quickTarget = type
                                        pickerExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { onOpenList(quickTarget) },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFC46F3C),
                            contentColor = Color.White
                        )
                    ) {
                        Text("Open ${taskListTitle(quickTarget)}")
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeOptionCard(
    modifier: Modifier,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEED8C2)),
        shape = RoundedCornerShape(18.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.verticalGradient(
            colors = listOf(Color(0x99FFFFFF), Color(0x33FFFFFF))
        ))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge, color = Color(0xFF412A19))
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF6D4A33))
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Tap to open", style = MaterialTheme.typography.labelMedium, color = Color(0xFFA04F22))
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
    onUpdateTaskStatus: (String, Boolean) -> Unit,
    onDeleteTask: (String) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedSection by remember { mutableStateOf(ListSection.TASKS) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(taskListTitle(listType), color = Color.White)
                        Text(
                            text = taskListSubtitle(listType),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF5F422F)
                        )
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
            if (selectedSection == ListSection.TASKS) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = Color(0xFFC46F3C),
                    contentColor = Color.White
                ) {
                    Text("+")
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                GlassPanel {
                    Text(
                        text = "${tasks.count { it.isDone }}/${tasks.size} completed",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF412A19),
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(taskListDescription(listType), color = Color(0xFF6B4932))
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionToggleButton(
                        modifier = Modifier.weight(1f),
                        selected = selectedSection == ListSection.TASKS,
                        text = "Tasks",
                        onClick = { selectedSection = ListSection.TASKS }
                    )
                    SectionToggleButton(
                        modifier = Modifier.weight(1f),
                        selected = selectedSection == ListSection.HISTORY,
                        text = "History",
                        onClick = { selectedSection = ListSection.HISTORY }
                    )
                }
            }

            item {
                if (selectedSection == ListSection.TASKS) {
                    TaskContent(
                        tasks = tasks,
                        onUpdateTaskStatus = onUpdateTaskStatus,
                        onDeleteTask = onDeleteTask
                    )
                } else {
                    HistoryContent(history = history)
                }
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
private fun SectionToggleButton(
    modifier: Modifier,
    selected: Boolean,
    text: String,
    onClick: () -> Unit
) {
    val bg = if (selected) Color(0xFFC46F3C) else Color(0x55FFFFFF)
    val fg = if (selected) Color.White else Color(0xFF5C3F2D)
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg),
        shape = RoundedCornerShape(14.dp)
    ) {
        Text(text)
    }
}

@Composable
private fun TaskContent(
    tasks: List<TaskItem>,
    onUpdateTaskStatus: (String, Boolean) -> Unit,
    onDeleteTask: (String) -> Unit
) {
    var filter by remember { mutableStateOf("All") }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("All", "Pending", "Done").forEach { item ->
            val selected = filter == item
            Button(
                onClick = { filter = item },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selected) Color(0xFFA55B2F) else Color(0x66FFFFFF),
                    contentColor = if (selected) Color.White else Color(0xFF5C3F2D)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(item)
            }
        }
    }
    Spacer(modifier = Modifier.height(10.dp))

    val visibleTasks = when (filter) {
        "Pending" -> tasks.filterNot { it.isDone }
        "Done" -> tasks.filter { it.isDone }
        else -> tasks
    }

    if (visibleTasks.isEmpty()) {
        GlassPanel {
            Text(
                text = if (tasks.isEmpty()) "No tasks yet. Tap + to add your first task." else "No tasks in this filter.",
                color = Color(0xFF5E412F),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        visibleTasks.forEach { task ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF2DFC9)),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.verticalGradient(listOf(Color(0xAAFFFFFF), Color(0x44FFFFFF)))
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        task.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF412A19)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    PriorityChip(task.priority)
                    if (task.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(task.description, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF644633))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Schedule: ${task.scheduleText}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF8A6144))
                    Text("Reminder: ${task.reminderText}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF8A6144))
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = task.isDone,
                                onCheckedChange = { checked -> onUpdateTaskStatus(task.id, checked) }
                            )
                            Text(if (task.isDone) "Done" else "In progress", color = Color(0xFF4E3424))
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = { onDeleteTask(task.id) }) {
                            Text("Delete", color = Color(0xFFB3302B))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryContent(history: List<HistoryItem>) {
    if (history.isEmpty()) {
        GlassPanel {
            Text(
                text = "No history found yet.",
                color = Color(0xFFE9F3FF),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        history.forEach { entry ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF2DFC9)),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.verticalGradient(listOf(Color(0xAAFFFFFF), Color(0x44FFFFFF)))
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        entry.taskTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF412A19)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(entry.actionText, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF664733))
                    Text(entry.dayDateLabel, style = MaterialTheme.typography.bodySmall, color = Color(0xFF8A6144))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTaskDialog(
    listType: TaskListType,
    onDismiss: () -> Unit,
    onConfirm: (TaskItem) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf(TaskPriority.MEDIUM) }

    var dailyTime by remember { mutableStateOf("") }
    var dailyReminderTime by remember { mutableStateOf("") }

    var weeklyDay by remember { mutableStateOf("Monday") }
    var weeklyTime by remember { mutableStateOf("") }
    var weeklyReminderDay by remember { mutableStateOf("Monday") }
    var weeklyReminderTime by remember { mutableStateOf("") }

    var monthlyDate by remember { mutableStateOf("") }
    var monthlyTime by remember { mutableStateOf("") }
    var monthlyReminderDate by remember { mutableStateOf("") }
    var monthlyReminderTime by remember { mutableStateOf("") }

    var validationMessage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFFF5E7D7),
        tonalElevation = 6.dp,
        title = { Text("Add Task", color = Color(0xFF412A19)) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                item {
                    SectionTitle("Task Details")
                }
                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Task name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    PriorityDropdownField(
                        selectedPriority = priority,
                        onPrioritySelected = { priority = it }
                    )
                }

                when (listType) {
                    TaskListType.DAILY -> {
                        item { SectionTitle("Schedule") }
                        item {
                            TimePickerField(
                                label = "Task time",
                                timeText = dailyTime,
                                onTimeSelected = { dailyTime = it }
                            )
                        }
                        item {
                            TimePickerField(
                                label = "Reminder time",
                                timeText = dailyReminderTime,
                                onTimeSelected = { dailyReminderTime = it }
                            )
                        }
                    }

                    TaskListType.WEEKLY -> {
                        item { SectionTitle("Schedule") }
                        item {
                            DayDropdownField(
                                label = "Task day",
                                selectedDay = weeklyDay,
                                onDaySelected = { weeklyDay = it }
                            )
                        }
                        item {
                            TimePickerField(
                                label = "Task time",
                                timeText = weeklyTime,
                                onTimeSelected = { weeklyTime = it }
                            )
                        }
                        item { SectionTitle("Reminder") }
                        item {
                            DayDropdownField(
                                label = "Reminder day",
                                selectedDay = weeklyReminderDay,
                                onDaySelected = { weeklyReminderDay = it }
                            )
                        }
                        item {
                            TimePickerField(
                                label = "Reminder time",
                                timeText = weeklyReminderTime,
                                onTimeSelected = { weeklyReminderTime = it }
                            )
                        }
                    }

                    TaskListType.MONTHLY -> {
                        item { SectionTitle("Schedule") }
                        item {
                            DatePickerField(
                                label = "Task date",
                                dateText = monthlyDate,
                                onDateSelected = { monthlyDate = it }
                            )
                        }
                        item {
                            TimePickerField(
                                label = "Task time",
                                timeText = monthlyTime,
                                onTimeSelected = { monthlyTime = it }
                            )
                        }
                        item { SectionTitle("Reminder") }
                        item {
                            DatePickerField(
                                label = "Reminder date",
                                dateText = monthlyReminderDate,
                                onDateSelected = { monthlyReminderDate = it }
                            )
                        }
                        item {
                            TimePickerField(
                                label = "Reminder time",
                                timeText = monthlyReminderTime,
                                onTimeSelected = { monthlyReminderTime = it }
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = "All reminders use your current local timezone (${TimeZone.getDefault().id}).",
                        color = Color(0xFF7D573D),
                        style = MaterialTheme.typography.labelSmall
                    )
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
            Button(
                onClick = {
                    val built = buildTaskFromInputs(
                        listType = listType,
                        title = title,
                        description = description,
                        priority = priority,
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
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFC46F3C),
                    contentColor = Color.White
                )
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF6D4B34))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PriorityDropdownField(
    selectedPriority: TaskPriority,
    onPrioritySelected: (TaskPriority) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedPriority.name.lowercase().replaceFirstChar { it.uppercase() },
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("Priority") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            TaskPriority.entries.forEach { priority ->
                DropdownMenuItem(
                    text = { Text(priority.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    onClick = {
                        onPrioritySelected(priority)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun TimePickerField(
    label: String,
    timeText: String,
    onTimeSelected: (String) -> Unit
) {
    val context = LocalContext.current
    OutlinedTextField(
        value = timeText,
        onValueChange = {},
        readOnly = true,
        singleLine = true,
        label = { Text(label) },
        placeholder = { Text("hh:mm AM/PM") },
        trailingIcon = {
            TextButton(
                onClick = {
                    val now = Calendar.getInstance()
                    TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            onTimeSelected(formatTimeAmPm(hour, minute))
                        },
                        now.get(Calendar.HOUR_OF_DAY),
                        now.get(Calendar.MINUTE),
                        false
                    ).show()
                }
            ) {
                Text("Pick")
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun DatePickerField(
    label: String,
    dateText: String,
    onDateSelected: (String) -> Unit
) {
    val context = LocalContext.current
    OutlinedTextField(
        value = dateText,
        onValueChange = {},
        readOnly = true,
        singleLine = true,
        label = { Text(label) },
        placeholder = { Text("dd/MM/yyyy") },
        trailingIcon = {
            TextButton(
                onClick = {
                    val now = Calendar.getInstance()
                    DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            onDateSelected(
                                String.format(
                                    Locale.getDefault(),
                                    "%02d/%02d/%04d",
                                    dayOfMonth,
                                    month + 1,
                                    year
                                )
                            )
                        },
                        now.get(Calendar.YEAR),
                        now.get(Calendar.MONTH),
                        now.get(Calendar.DAY_OF_MONTH)
                    ).show()
                }
            ) {
                Text("Pick")
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayDropdownField(
    label: String,
    selectedDay: String,
    onDaySelected: (String) -> Unit
) {
    val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedDay,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            days.forEach { day ->
                DropdownMenuItem(
                    text = { Text(day) },
                    onClick = {
                        onDaySelected(day)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun GlassPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xCCFFF6EA))
            .border(
                width = 1.dp,
                color = Color(0x33A86A45),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content
    )
}

@Composable
private fun MetricPill(label: String, value: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(Color(0xFFEBCFB0))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = Color(0xFF6A4B36), style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = value, color = Color(0xFF3F2A19), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        color = Color(0xFF4C3321),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold
    )
}

private fun taskListTitle(type: TaskListType): String {
    return when (type) {
        TaskListType.DAILY -> "Daily"
        TaskListType.WEEKLY -> "Weekly"
        TaskListType.MONTHLY -> "Monthly"
    }
}

private fun taskListSubtitle(type: TaskListType): String {
    return when (type) {
        TaskListType.DAILY -> "Today action plan"
        TaskListType.WEEKLY -> "Week roadmap"
        TaskListType.MONTHLY -> "Month overview"
    }
}

private fun taskListDescription(type: TaskListType): String {
    return when (type) {
        TaskListType.DAILY -> "Time-box your day with focused reminders."
        TaskListType.WEEKLY -> "Plan recurring priorities by weekday."
        TaskListType.MONTHLY -> "Track bigger goals and milestones."
    }
}

private fun buildTaskFromInputs(
    listType: TaskListType,
    title: String,
    description: String,
    priority: TaskPriority,
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
                priority = priority,
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
                priority = priority,
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
                priority = priority,
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
    val (hour, minute) = parseHourMinute(timeText) ?: return null
    val zone = TimeZone.getDefault()
    val now = Calendar.getInstance(zone)

    val scheduled = Calendar.getInstance(zone).apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
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
    val (hour, minute) = parseHourMinute(timeText) ?: return null
    val zone = TimeZone.getDefault()

    val scheduled = Calendar.getInstance(zone).apply {
        set(Calendar.DAY_OF_WEEK, targetDay)
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    if (scheduled.before(Calendar.getInstance(zone))) {
        scheduled.add(Calendar.WEEK_OF_YEAR, 1)
    }
    return scheduled.timeInMillis
}

private fun parseDateTime(dateText: String, timeText: String): Long? {
    val patterns = listOf("dd/MM/yyyy hh:mm a", "dd/MM/yyyy HH:mm")
    patterns.forEach { pattern ->
        val parser = SimpleDateFormat(pattern, Locale.getDefault()).apply {
            isLenient = false
            timeZone = TimeZone.getDefault()
        }
        val parsed = runCatching { parser.parse("${dateText.trim()} ${timeText.trim()}") }.getOrNull()
        if (parsed != null) return parsed.time
    }
    return null
}

private fun parseHourMinute(timeText: String): Pair<Int, Int>? {
    val clean = timeText.trim()
    val ampmParser = SimpleDateFormat("hh:mm a", Locale.getDefault()).apply {
        isLenient = false
        timeZone = TimeZone.getDefault()
    }
    val ampmParsed = runCatching { ampmParser.parse(clean.uppercase(Locale.getDefault())) }.getOrNull()
    if (ampmParsed != null) {
        val cal = Calendar.getInstance(TimeZone.getDefault()).apply { time = ampmParsed }
        return cal.get(Calendar.HOUR_OF_DAY) to cal.get(Calendar.MINUTE)
    }

    val match = Regex("^([01]\\d|2[0-3]):([0-5]\\d)$").matchEntire(clean) ?: return null
    val hour = match.groupValues[1].toIntOrNull() ?: return null
    val minute = match.groupValues[2].toIntOrNull() ?: return null
    return hour to minute
}

private fun formatTimeAmPm(hour: Int, minute: Int): String {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
    }
    return SimpleDateFormat("hh:mm a", Locale.getDefault()).format(calendar.time)
}

@Composable
private fun PriorityChip(priority: TaskPriority) {
    val (bg, text) = when (priority) {
        TaskPriority.LOW -> Color(0xFFCCEFD8) to Color(0xFF195B30)
        TaskPriority.MEDIUM -> Color(0xFFF9E3B3) to Color(0xFF7B4D00)
        TaskPriority.HIGH -> Color(0xFFF8C7C7) to Color(0xFF8A1F1F)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = "${priority.name.lowercase().replaceFirstChar { it.uppercase() }} Priority",
            color = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
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
    return SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault()).apply {
        timeZone = TimeZone.getDefault()
    }.format(Date(timeMillis))
}

private fun formatHumanDate(timeMillis: Long): String {
    return SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault()).apply {
        timeZone = TimeZone.getDefault()
    }.format(Date(timeMillis))
}

private fun formatLocalClock(timeMillis: Long): String {
    return SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss", Locale.getDefault()).apply {
        timeZone = TimeZone.getDefault()
    }.format(Date(timeMillis))
}
