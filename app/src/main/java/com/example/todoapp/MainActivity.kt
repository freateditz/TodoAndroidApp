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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
            ToDoAppRoot()
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
    var use24HourFormat by remember { mutableStateOf(storage.loadUse24HourFormat()) }
    var reminderVibrationEnabled by remember { mutableStateOf(storage.loadReminderVibrationEnabled()) }
    var defaultPriority by remember { mutableStateOf(storage.loadDefaultPriority()) }
    var darkThemeEnabled by remember { mutableStateOf(storage.loadDarkThemeEnabled()) }
    var uiStylePreset by remember { mutableStateOf(parseUiStylePreset(storage.loadUiStylePresetName())) }
    var activeDayKey by remember { mutableStateOf(formatDateKey(System.currentTimeMillis())) }

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
        history.addAll(storage.loadHistory().filter { it.actionText == "Done" || it.actionText == "Not done" })
        archivePreviousDayDailyTasks(allTasks, history, storage)
        activeDayKey = formatDateKey(System.currentTimeMillis())
    }

    LaunchedEffect(Unit) {
        while (true) {
            currentTimeMillis = System.currentTimeMillis()

            val latestDay = formatDateKey(currentTimeMillis)
            if (latestDay != activeDayKey) {
                archivePreviousDayDailyTasks(allTasks, history, storage)
                activeDayKey = latestDay
            }

            delay(1_000)
        }
    }

    ToDoAppTheme(darkTheme = darkThemeEnabled) {
        CompositionLocalProvider(LocalUiStylePreset provides uiStylePreset) {
            GradientBackdrop {
                when (val screen = currentScreen) {
                    AppScreen.Home -> HomeScreen(
                        onOpenList = { currentScreen = AppScreen.List(it) },
                        onOpenSettings = { currentScreen = AppScreen.Settings },
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
                        use24HourFormat = use24HourFormat,
                        defaultPriority = defaultPriority,
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
                        },
                        onDeleteTask = { taskId ->
                            allTasks.removeAll { it.id == taskId }
                            storage.saveTasks(allTasks)
                            ReminderScheduler.cancelReminder(context, taskId)
                        }
                    )

                    AppScreen.Settings -> SettingsScreen(
                        darkThemeEnabled = darkThemeEnabled,
                        uiStylePreset = uiStylePreset,
                        use24HourFormat = use24HourFormat,
                        reminderVibrationEnabled = reminderVibrationEnabled,
                        defaultPriority = defaultPriority,
                        onBack = { currentScreen = AppScreen.Home },
                        onDarkThemeChanged = {
                            darkThemeEnabled = it
                            storage.saveDarkThemeEnabled(it)
                        },
                        onUiStylePresetChanged = {
                            uiStylePreset = it
                            storage.saveUiStylePresetName(it.name)
                        },
                        onUse24HourFormatChanged = {
                            use24HourFormat = it
                            storage.saveUse24HourFormat(it)
                        },
                        onReminderVibrationChanged = {
                            reminderVibrationEnabled = it
                            storage.saveReminderVibrationEnabled(it)
                        },
                        onDefaultPriorityChanged = {
                            defaultPriority = it
                            storage.saveDefaultPriority(it)
                        }
                    )
                }
            }
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
                onOpenSettings = {},
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
    data object Settings : AppScreen()
}

private enum class ListSection {
    TASKS,
    HISTORY
}

private enum class UiStylePreset {
    MINIMAL,
    BOLD
}

private val LocalUiStylePreset = staticCompositionLocalOf { UiStylePreset.MINIMAL }

@Composable
private fun GradientBackdrop(content: @Composable () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    val darkMode = colorScheme.background.luminance() < 0.5f
    val stylePreset = LocalUiStylePreset.current
    val gradientStops = if (stylePreset == UiStylePreset.BOLD) {
        if (darkMode) {
            listOf(Color(0xFF111129), Color(0xFF2A1841), Color(0xFF39214D))
        } else {
            listOf(Color(0xFFFFF2D8), Color(0xFFFFDFC7), Color(0xFFFFD0C4))
        }
    } else {
        if (darkMode) {
            listOf(Color(0xFF111A24), Color(0xFF182739), Color(0xFF1F3347))
        } else {
            listOf(Color(0xFFF7F0E8), Color(0xFFF0E2D2), Color(0xFFEADCCB))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = gradientStops
                )
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            if (stylePreset == UiStylePreset.BOLD) {
                                if (darkMode) Color(0x33FFFFFF) else Color(0x7AFFFFFF)
                            } else {
                                if (darkMode) Color(0x22FFFFFF) else Color(0x66FFFFFF)
                            },
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
    onOpenSettings: () -> Unit,
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
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Momentum dashboard: plan, focus, complete.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Local time: $localTimeLabel",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = "Time zone: $timeZoneLabel",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        color = MaterialTheme.colorScheme.onSurface
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
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("Open ${taskListTitle(quickTarget)}")
                    }
                }
            }

            item {
                HomeOptionCard(
                    modifier = Modifier.fillMaxWidth(),
                    title = "Settings",
                    subtitle = "Time format, vibration, default priority",
                    onClick = onOpenSettings
                )
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
    val stylePreset = LocalUiStylePreset.current
    val shape = if (stylePreset == UiStylePreset.BOLD) RoundedCornerShape(14.dp) else RoundedCornerShape(22.dp)
    val container = if (stylePreset == UiStylePreset.BOLD) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = modifier
            .clip(shape)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = container),
        shape = shape,
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.verticalGradient(
            colors = if (stylePreset == UiStylePreset.BOLD) {
                listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f))
            } else {
                listOf(Color(0x88FFFFFF), Color(0x22FFFFFF))
            }
        ))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Tap to open", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskListScreen(
    listType: TaskListType,
    tasks: List<TaskItem>,
    history: List<HistoryItem>,
    use24HourFormat: Boolean,
    defaultPriority: TaskPriority,
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
                        Text(taskListTitle(listType), color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            text = taskListSubtitle(listType),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
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
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(taskListDescription(listType), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            use24HourFormat = use24HourFormat,
            defaultPriority = defaultPriority,
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
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
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
                    containerColor = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (selected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.verticalGradient(listOf(Color(0xAAFFFFFF), Color(0x44FFFFFF)))
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        task.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    PriorityChip(task.priority)
                    if (task.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(task.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Schedule: ${task.scheduleText}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Reminder: ${task.reminderText}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            Text(if (task.isDone) "Done" else "In progress", color = MaterialTheme.colorScheme.onSurface)
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
    val doneHistory = history.filter { it.actionText == "Done" }
    val notDoneHistory = history.filter { it.actionText == "Not done" }

    if (doneHistory.isEmpty() && notDoneHistory.isEmpty()) {
        GlassPanel {
            Text(
                text = "No history found yet.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (doneHistory.isNotEmpty()) {
            SectionTitle("Done (${doneHistory.size})")
        }
        doneHistory.forEach { entry ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.20f)),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.verticalGradient(listOf(Color(0xAAFFFFFF), Color(0x44FFFFFF)))
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        entry.taskTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(entry.dayDateLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (notDoneHistory.isNotEmpty()) {
            SectionTitle("Not Done (${notDoneHistory.size})")
        }
        notDoneHistory.forEach { entry ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.16f)),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.verticalGradient(listOf(Color(0xAAFFFFFF), Color(0x44FFFFFF)))
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        entry.taskTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(entry.dayDateLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTaskDialog(
    listType: TaskListType,
    use24HourFormat: Boolean,
    defaultPriority: TaskPriority,
    onDismiss: () -> Unit,
    onConfirm: (TaskItem) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priority by remember(defaultPriority) { mutableStateOf(defaultPriority) }

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
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        title = { Text("Add Task", color = MaterialTheme.colorScheme.onSurface) },
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
                                onTimeSelected = { dailyTime = it },
                                use24HourFormat = use24HourFormat
                            )
                        }
                        item {
                            TimePickerField(
                                label = "Reminder time",
                                timeText = dailyReminderTime,
                                onTimeSelected = { dailyReminderTime = it },
                                use24HourFormat = use24HourFormat
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
                                onTimeSelected = { weeklyTime = it },
                                use24HourFormat = use24HourFormat
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
                                onTimeSelected = { weeklyReminderTime = it },
                                use24HourFormat = use24HourFormat
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
                                onTimeSelected = { monthlyTime = it },
                                use24HourFormat = use24HourFormat
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
                                onTimeSelected = { monthlyReminderTime = it },
                                use24HourFormat = use24HourFormat
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = "All reminders use your current local timezone (${TimeZone.getDefault().id}) and ${if (use24HourFormat) "24h" else "12h AM/PM"} format.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    onTimeSelected: (String) -> Unit,
    use24HourFormat: Boolean
) {
    val context = LocalContext.current
    OutlinedTextField(
        value = timeText,
        onValueChange = {},
        readOnly = true,
        singleLine = true,
        label = { Text(label) },
        placeholder = { Text(if (use24HourFormat) "HH:mm" else "hh:mm AM/PM") },
        trailingIcon = {
            TextButton(
                onClick = {
                    val now = Calendar.getInstance()
                    TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            val output = if (use24HourFormat) {
                                String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
                            } else {
                                formatTimeAmPm(hour, minute)
                            }
                            onTimeSelected(output)
                        },
                        now.get(Calendar.HOUR_OF_DAY),
                        now.get(Calendar.MINUTE),
                        use24HourFormat
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    darkThemeEnabled: Boolean,
    uiStylePreset: UiStylePreset,
    use24HourFormat: Boolean,
    reminderVibrationEnabled: Boolean,
    defaultPriority: TaskPriority,
    onBack: () -> Unit,
    onDarkThemeChanged: (Boolean) -> Unit,
    onUiStylePresetChanged: (UiStylePreset) -> Unit,
    onUse24HourFormatChanged: (Boolean) -> Unit,
    onReminderVibrationChanged: (Boolean) -> Unit,
    onDefaultPriorityChanged: (TaskPriority) -> Unit
) {
    var priorityExpanded by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = MaterialTheme.colorScheme.onSurface) },
                actions = {
                    TextButton(onClick = onBack) {
                        Text("Home")
                    }
                }
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                GlassPanel {
                    SectionTitle("Appearance")
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            if (darkThemeEnabled) "Dark mode" else "Light mode",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        androidx.compose.material3.Switch(
                            checked = darkThemeEnabled,
                            onCheckedChange = onDarkThemeChanged
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Style Preset",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onUiStylePresetChanged(UiStylePreset.MINIMAL) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (uiStylePreset == UiStylePreset.MINIMAL) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (uiStylePreset == UiStylePreset.MINIMAL) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Minimal")
                        }
                        Button(
                            onClick = { onUiStylePresetChanged(UiStylePreset.BOLD) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (uiStylePreset == UiStylePreset.BOLD) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (uiStylePreset == UiStylePreset.BOLD) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Bold")
                        }
                    }
                }
            }

            item {
                GlassPanel {
                    SectionTitle("Time Format")
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onUse24HourFormatChanged(false) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!use24HourFormat) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (!use24HourFormat) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("12h AM/PM")
                        }
                        Button(
                            onClick = { onUse24HourFormatChanged(true) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (use24HourFormat) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (use24HourFormat) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("24h")
                        }
                    }
                }
            }

            item {
                GlassPanel {
                    SectionTitle("Reminder Vibration")
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            if (reminderVibrationEnabled) "Enabled" else "Disabled",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        androidx.compose.material3.Switch(
                            checked = reminderVibrationEnabled,
                            onCheckedChange = onReminderVibrationChanged
                        )
                    }
                }
            }

            item {
                GlassPanel {
                    SectionTitle("Default Priority")
                    Spacer(modifier = Modifier.height(6.dp))
                    ExposedDropdownMenuBox(
                        expanded = priorityExpanded,
                        onExpandedChange = { priorityExpanded = !priorityExpanded }
                    ) {
                        OutlinedTextField(
                            value = defaultPriority.name.lowercase().replaceFirstChar { it.uppercase() },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Priority") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = priorityExpanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        DropdownMenu(
                            expanded = priorityExpanded,
                            onDismissRequest = { priorityExpanded = false }
                        ) {
                            TaskPriority.entries.forEach { priority ->
                                DropdownMenuItem(
                                    text = { Text(priority.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                    onClick = {
                                        onDefaultPriorityChanged(priority)
                                        priorityExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val stylePreset = LocalUiStylePreset.current
    val cornerRadius = if (stylePreset == UiStylePreset.BOLD) 14.dp else 26.dp
    val backgroundColor = if (stylePreset == UiStylePreset.BOLD) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.90f)
    }
    val borderColor = if (stylePreset == UiStylePreset.BOLD) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius))
            .background(backgroundColor)
            .border(
                width = 1.2.dp,
                color = borderColor,
                shape = RoundedCornerShape(cornerRadius)
            )
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content
    )
}

@Composable
private fun MetricPill(label: String, value: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = value, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.onSurface,
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

private fun parseUiStylePreset(raw: String): UiStylePreset {
    return UiStylePreset.entries.firstOrNull { it.name == raw } ?: UiStylePreset.MINIMAL
}

private fun archivePreviousDayDailyTasks(
    allTasks: MutableList<TaskItem>,
    history: MutableList<HistoryItem>,
    storage: TaskStorage
) {
    val todayKey = formatDateKey(System.currentTimeMillis())
    val oldDailyTasks = allTasks.filter {
        it.listType == TaskListType.DAILY && it.createdDateKey != todayKey
    }

    if (oldDailyTasks.isEmpty()) return

    val updatedHistory = history.toMutableList()
    oldDailyTasks.forEach { task ->
        updatedHistory.add(
            HistoryItem(
                id = UUID.randomUUID().toString(),
                taskTitle = task.title,
                listType = TaskListType.DAILY,
                actionText = if (task.isDone) "Done" else "Not done",
                dayDateLabel = task.createdDateKey,
                timestampMillis = System.currentTimeMillis()
            )
        )
    }

    val remaining = allTasks.filterNot { it in oldDailyTasks }
    allTasks.clear()
    allTasks.addAll(remaining)

    history.clear()
    history.addAll(updatedHistory.filter { it.actionText == "Done" || it.actionText == "Not done" })

    storage.saveTasks(allTasks)
    storage.saveHistory(history)
}
