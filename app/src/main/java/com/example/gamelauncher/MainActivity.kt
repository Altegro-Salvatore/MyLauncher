package com.example.gamelauncher

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

// Цвета D&D
val ColorBg = Color(0xFF1A1512)
val ColorCardBg = Color(0xFF2C231E)
val ColorGold = Color(0xFFC59B4B)
val ColorParchment = Color(0xFFE8D5B5)
val ColorHp = Color(0xFFD9534F)
val ColorMp = Color(0xFF5BC0DE)

data class AppInfo(val name: String, val packageName: String, val icon: Drawable)
data class TaskItem(val id: String, val title: String, val category: String, val isDone: Boolean = false)

fun saveTasks(context: Context, tasks: List<TaskItem>) {
    val jsonArray = JSONArray()
    tasks.forEach {
        val jsonObj = JSONObject()
        jsonObj.put("id", it.id); jsonObj.put("title", it.title); jsonObj.put("category", it.category); jsonObj.put("isDone", it.isDone)
        jsonArray.put(jsonObj)
    }
    context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE).edit().putString("tasks_list", jsonArray.toString()).apply()
}

fun loadTasks(context: Context): List<TaskItem> {
    val prefs = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
    val list = mutableListOf<TaskItem>()
    try {
        val jsonArray = JSONArray(prefs.getString("tasks_list", "[]") ?: "[]")
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            list.add(TaskItem(obj.getString("id"), obj.getString("title"), obj.optString("category", "Minor"), obj.optBoolean("isDone", false)))
        }
    } catch (e: Exception) {}
    return list
}

fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
    return mode == AppOpsManager.MODE_ALLOWED
}

// ИСПРАВЛЕННЫЙ ПОИСК ПРИЛОЖЕНИЙ
fun getFrequentApps(context: Context): List<AppInfo> {
    val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val endTime = System.currentTimeMillis()
    val beginTime = endTime - 1000L * 60 * 60 * 24 * 7 // За 7 дней
    val usageEvents = usageStatsManager.queryEvents(beginTime, endTime)
    val event = UsageEvents.Event()
    val launchCounts = mutableMapOf<String, Int>()
    val pm = context.packageManager

    // Считаем только реальные открытия приложений
    while (usageEvents.hasNextEvent()) {
        usageEvents.getNextEvent(event)
        if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
            val pkg = event.packageName
            if (pkg != context.packageName && pkg != "com.android.systemui") {
                if (pm.getLaunchIntentForPackage(pkg) != null) {
                    launchCounts[pkg] = launchCounts.getOrDefault(pkg, 0) + 1
                }
            }
        }
    }

    return launchCounts.entries
        .sortedByDescending { it.value }
        .mapNotNull {
            try {
                val appInfo = pm.getApplicationInfo(it.key, 0)
                AppInfo(pm.getApplicationLabel(appInfo).toString(), it.key, pm.getApplicationIcon(it.key))
            } catch (e: Exception) { null }
        }
        .distinctBy { it.packageName }
        .take(8)
}

@OptIn(ExperimentalFoundationApi::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val pagerState = rememberPagerState(pageCount = { 2 })
            HorizontalPager(state = pagerState) { page ->
                if (page == 0) ScreenOne() else ScreenTwo()
            }
        }
    }
}

@Composable
fun ScreenOne() {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var hasPermission by remember { mutableStateOf(hasUsageStatsPermission(context)) }
    var currentTime by remember { mutableStateOf("") }

    var tasks by remember { mutableStateOf(loadTasks(context)) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) } // Для истории
    var inputTask by remember { mutableStateOf("") }
    var currentCategory by remember { mutableStateOf("Major") }

    // Окно добавления
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false }, containerColor = ColorCardBg,
            title = { Text("Новая задача", color = ColorGold, fontFamily = FontFamily.Serif) },
            text = {
                OutlinedTextField(
                    value = inputTask, onValueChange = { inputTask = it }, label = { Text("Что сделать?", color = ColorParchment) },
                    singleLine = true, colors = TextFieldDefaults.colors(focusedTextColor = ColorParchment, unfocusedTextColor = ColorParchment, focusedContainerColor = ColorBg, unfocusedContainerColor = ColorBg, focusedIndicatorColor = ColorGold, unfocusedIndicatorColor = ColorGold.copy(alpha = 0.5f))
                )
            },
            confirmButton = { TextButton(onClick = { if (inputTask.isNotBlank()) { tasks = tasks + TaskItem(UUID.randomUUID().toString(), inputTask, currentCategory); saveTasks(context, tasks) }; inputTask = ""; showAddDialog = false }) { Text("Добавить", color = ColorGold) } },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Отмена", color = ColorParchment) } }
        )
    }

    // Окно истории
    if (showHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false }, containerColor = ColorCardBg,
            title = { Text("История квестов", color = ColorGold, fontFamily = FontFamily.Serif) },
            text = {
                LazyColumn {
                    val doneTasks = tasks.filter { it.isDone }
                    if (doneTasks.isEmpty()) {
                        item { Text("Пока нет выполненных квестов.", color = ColorParchment) }
                    } else {
                        items(doneTasks) { task ->
                            Text("- ${task.title}", color = ColorParchment, textDecoration = TextDecoration.LineThrough, modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showHistoryDialog = false }) { Text("Закрыть", color = ColorGold) } },
            dismissButton = { TextButton(onClick = { tasks = tasks.filter { !it.isDone }; saveTasks(context, tasks); showHistoryDialog = false }) { Text("Очистить", color = ColorHp) } }
        )
    }

    LaunchedEffect(Unit) { while (true) { currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()); delay(1000) } }
    LaunchedEffect(hasPermission) { while (true) { if (hasPermission) apps = getFrequentApps(context); delay(5000) } }
    LaunchedEffect(Unit) { hasPermission = hasUsageStatsPermission(context) }

    // При выполнении переносим в историю (isDone = true)
    val onTaskDone: (TaskItem) -> Unit = { task ->
        tasks = tasks.map { if (it.id == task.id) it.copy(isDone = true) else it }
        saveTasks(context, tasks)
    }

    Row(modifier = Modifier.fillMaxSize().background(ColorBg).padding(16.dp)) {
        
        // ЛЕВАЯ КОЛОНКА - КВЕСТЫ
        Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                QuestListCard("Глобальные", tasks.filter { it.category == "Major" && !it.isDone }, { currentCategory = "Major"; showAddDialog = true }, onTaskDone)
            }
            Box(modifier = Modifier.weight(1.2f).fillMaxWidth()) {
                QuestListCard("Средние", tasks.filter { it.category == "Medium" && !it.isDone }, { currentCategory = "Medium"; showAddDialog = true }, onTaskDone)
            }
            Box(modifier = Modifier.weight(2f).fillMaxWidth()) {
                QuestListCard("Мелкие", tasks.filter { it.category == "Minor" && !it.isDone }, { currentCategory = "Minor"; showAddDialog = true }, onTaskDone)
            }
            
            // Кнопка ИСТОРИИ
            Button(
                onClick = { showHistoryDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = ColorCardBg)
            ) {
                Text("История квестов", color = ColorGold, fontFamily = FontFamily.Serif)
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // ПРАВАЯ КОЛОНКА
        Column(modifier = Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.End) {
            
            Box(modifier = Modifier.fillMaxWidth().background(ColorCardBg, RoundedCornerShape(4.dp)).border(2.dp, ColorGold, RoundedCornerShape(4.dp)).padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                Text(currentTime, fontSize = 42.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = ColorParchment)
            }

            Text("25°", fontSize = 32.sp, fontFamily = FontFamily.Serif, color = ColorParchment, modifier = Modifier.padding(vertical = 12.dp))
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (!hasPermission) {
                Button(onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = ColorCardBg)) {
                    Text("Разрешить\nприложения", textAlign = TextAlign.Center, fontSize = 12.sp, color = ColorGold)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (i in 0 until 4) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            for (j in 0 until 2) {
                                val index = i * 2 + j
                                if (index < apps.size) {
                                    val app = apps[index]
                                    Box(modifier = Modifier.size(56.dp).background(ColorCardBg, RoundedCornerShape(8.dp)).border(1.dp, ColorGold.copy(alpha=0.5f), RoundedCornerShape(8.dp)).clickable { context.startActivity(context.packageManager.getLaunchIntentForPackage(app.packageName)) }, contentAlignment = Alignment.Center) {
                                        Image(bitmap = app.icon.toBitmap(100, 100).asImageBitmap(), contentDescription = app.name, modifier = Modifier.size(40.dp))
                                    }
                                } else {
                                    Box(modifier = Modifier.size(56.dp).background(ColorCardBg.copy(alpha = 0.3f), RoundedCornerShape(8.dp)))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuestListCard(title: String, tasks: List<TaskItem>, onAddClick: () -> Unit, onTaskDone: (TaskItem) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(ColorCardBg, RoundedCornerShape(4.dp)).border(1.dp, ColorGold, RoundedCornerShape(4.dp)).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Box(modifier = Modifier.size(20.dp).border(1.dp, ColorGold, CircleShape).clickable { onAddClick() }, contentAlignment = Alignment.Center) {
                Text("+", color = ColorGold, fontSize = 14.sp, fontFamily = FontFamily.Serif)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(title, color = ColorGold, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(tasks) { task ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.size(16.dp).border(1.dp, ColorGold, RoundedCornerShape(2.dp)).clickable { onTaskDone(task) })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(task.title, color = ColorParchment, fontSize = 13.sp, fontFamily = FontFamily.Serif)
                }
            }
        }
    }
}

@Composable
fun ScreenTwo() {
    val context = LocalContext.current
    var showMannaDialog by remember { mutableStateOf(false) }
    var inputManna by remember { mutableStateOf("") }
    val prefs = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
    var mannaPoints by remember { mutableStateOf(prefs.getInt("manna_points", 0)) }

    if (showMannaDialog) {
        AlertDialog(
            onDismissRequest = { showMannaDialog = false }, containerColor = ColorCardBg,
            title = { Text("Добавить MP", color = ColorMp, fontFamily = FontFamily.Serif) },
            text = { OutlinedTextField(value = inputManna, onValueChange = { if (it.all { char -> char.isDigit() }) inputManna = it }, label = { Text("Сколько калорий?", color = ColorParchment) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true) },
            confirmButton = { TextButton(onClick = { val calories = inputManna.toIntOrNull() ?: 0; mannaPoints += calories; prefs.edit().putInt("manna_points", mannaPoints).apply(); inputManna = ""; showMannaDialog = false }) { Text("Добавить", color = ColorMp) } },
            dismissButton = { TextButton(onClick = { showMannaDialog = false }) { Text("Отмена", color = ColorParchment) } }
        )
    }

    Row(modifier = Modifier.fillMaxSize().background(ColorBg).padding(16.dp)) {
        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(ColorCardBg, RoundedCornerShape(8.dp)).border(2.dp, ColorGold, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Text("+ Добавить\nАватар", color = ColorParchment.copy(alpha = 0.5f), textAlign = TextAlign.Center, fontFamily = FontFamily.Serif)
        }
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column {
                Text("HP (Пульс)", color = ColorParchment, fontSize = 12.sp, fontFamily = FontFamily.Serif)
                Text("99", color = ColorHp, fontSize = 32.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
            }
            
            Column(modifier = Modifier.clickable { showMannaDialog = true }) {
                Text("MP (Калории)", color = ColorParchment, fontSize = 12.sp, fontFamily = FontFamily.Serif)
                Text("$mannaPoints", color = if (mannaPoints < 0) Color.Red else ColorMp, fontSize = 32.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
            }

            Column {
                Text("Отдых", color = ColorParchment, fontSize = 12.sp, fontFamily = FontFamily.Serif)
                Text("6ч 12мин", color = ColorParchment, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
            }
            
            Column {
                Text("Шаги за сегодня", color = ColorParchment, fontSize = 12.sp, fontFamily = FontFamily.Serif)
                Text("Нет доступа", color = ColorParchment.copy(alpha = 0.5f), fontSize = 20.sp, fontFamily = FontFamily.Serif)
            }
        }
    }
}
