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

data class AppInfo(val name: String, val packageName: String, val icon: Drawable)
data class TaskItem(val id: String, val title: String, val isDone: Boolean)

fun saveTasks(context: Context, tasks: List<TaskItem>) {
    val jsonArray = JSONArray()
    tasks.forEach {
        val jsonObj = JSONObject()
        jsonObj.put("id", it.id)
        jsonObj.put("title", it.title)
        jsonObj.put("isDone", it.isDone)
        jsonArray.put(jsonObj)
    }
    context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
        .edit().putString("tasks_list", jsonArray.toString()).apply()
}

fun loadTasks(context: Context): List<TaskItem> {
    val prefs = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
    val jsonStr = prefs.getString("tasks_list", "[]") ?: "[]"
    val list = mutableListOf<TaskItem>()
    try {
        val jsonArray = JSONArray(jsonStr)
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            list.add(TaskItem(obj.getString("id"), obj.getString("title"), obj.getBoolean("isDone")))
        }
    } catch (e: Exception) {}
    return list
}

fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    // Вот тут была ошибка, теперь AppOpsManager
    val mode = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
    return mode == AppOpsManager.MODE_ALLOWED
}

fun getFrequentApps(context: Context): List<AppInfo> {
    val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val endTime = System.currentTimeMillis()
    val beginTime = endTime - 1000L * 60 * 60 * 24 * 7
    val usageEvents = usageStatsManager.queryEvents(beginTime, endTime)
    val event = UsageEvents.Event()
    val launchCounts = mutableMapOf<String, Int>()

    while (usageEvents.hasNextEvent()) {
        usageEvents.getNextEvent(event)
        if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED || event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
            val pkg = event.packageName
            if (pkg != context.packageName) {
                launchCounts[pkg] = launchCounts.getOrDefault(pkg, 0) + 1
            }
        }
    }

    val pm = context.packageManager
    return launchCounts.entries
        .sortedByDescending { it.value }
        .filter { pm.getLaunchIntentForPackage(it.key) != null }
        .mapNotNull {
            try {
                val appInfo = pm.getApplicationInfo(it.key, 0)
                val name = pm.getApplicationLabel(appInfo).toString()
                val icon = pm.getApplicationIcon(it.key)
                AppInfo(name, it.key, icon)
            } catch (e: Exception) { null }
        }
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
    var inputTask by remember { mutableStateOf("") }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Новая задача") },
            text = {
                OutlinedTextField(
                    value = inputTask,
                    onValueChange = { inputTask = it },
                    label = { Text("Что нужно сделать?") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (inputTask.isNotBlank()) {
                        val newTask = TaskItem(UUID.randomUUID().toString(), inputTask, false)
                        tasks = tasks + newTask
                        saveTasks(context, tasks)
                    }
                    inputTask = ""
                    showAddDialog = false
                }) { Text("Добавить") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Отмена") }
            }
        )
    }

    LaunchedEffect(Unit) {
        while (true) {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            currentTime = sdf.format(Date())
            delay(1000)
        }
    }

    LaunchedEffect(hasPermission) {
        while (true) {
            if (hasPermission) apps = getFrequentApps(context)
            delay(10000)
        }
    }

    LaunchedEffect(Unit) { hasPermission = hasUsageStatsPermission(context) }

    Row(modifier = Modifier.fillMaxSize().background(Color(0xFF1E1E1E)).padding(16.dp)) {
        // ЛЕВАЯ КОЛОНКА - СПИСОК ЗАДАЧ
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Box(
                    modifier = Modifier.size(36.dp).background(Color.DarkGray, CircleShape)
                        .clickable { showAddDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text("+", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text("Глобальные", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tasks) { task ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .border(2.dp, if (task.isDone) Color.Green else Color.Gray, CircleShape)
                                .background(if (task.isDone) Color.Green else Color.Transparent, CircleShape)
                                .clickable {
                                    val updatedTasks = tasks.map {
                                        if (it.id == task.id) it.copy(isDone = !it.isDone) else it
                                    }
                                    tasks = updatedTasks
                                    saveTasks(context, updatedTasks)
                                }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = task.title,
                            color = if (task.isDone) Color.Gray else Color.White,
                            textDecoration = if (task.isDone) TextDecoration.LineThrough else null,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // ПРАВАЯ КОЛОНКА
        Column(modifier = Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.End) {
            Text(currentTime, fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("25°", fontSize = 32.sp, color = Color.White)
            Spacer(modifier = Modifier.height(16.dp))
            
            if (!hasPermission) {
                Button(onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Разрешить\nприложения", textAlign = TextAlign.Center, fontSize = 12.sp)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (i in 0 until 4) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            for (j in 0 until 2) {
                                val index = i * 2 + j
                                if (index < apps.size) {
                                    val app = apps[index]
                                    Box(
                                        modifier = Modifier.size(56.dp).background(Color(0xFF333333), RoundedCornerShape(14.dp))
                                            .clickable { context.startActivity(context.packageManager.getLaunchIntentForPackage(app.packageName)) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(bitmap = app.icon.toBitmap(100, 100).asImageBitmap(), contentDescription = app.name, modifier = Modifier.size(44.dp))
                                    }
                                } else {
                                    Box(modifier = Modifier.size(56.dp).background(Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(14.dp)))
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
fun ScreenTwo() {
    val context = LocalContext.current
    var showMannaDialog by remember { mutableStateOf(false) }
    var inputManna by remember { mutableStateOf("") }
    
    val prefs = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
    var mannaPoints by remember { mutableStateOf(prefs.getInt("manna_points", 0)) }

    if (showMannaDialog) {
        AlertDialog(
            onDismissRequest = { showMannaDialog = false },
            title = { Text("Добавить калории (MP)") },
            text = {
                OutlinedTextField(
                    value = inputManna,
                    onValueChange = { if (it.all { char -> char.isDigit() }) inputManna = it },
                    label = { Text("Сколько калорий?") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val calories = inputManna.toIntOrNull() ?: 0
                    mannaPoints += calories
                    prefs.edit().putInt("manna_points", mannaPoints).apply()
                    inputManna = ""
                    showMannaDialog = false
                }) { Text("Добавить") }
            },
            dismissButton = {
                TextButton(onClick = { showMannaDialog = false }) { Text("Отмена") }
            }
        )
    }

    Row(modifier = Modifier.fillMaxSize().background(Color(0xFF121212)).padding(16.dp)) {
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight().background(Color.DarkGray, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("+ Добавить\nАватар", color = Color.Gray, textAlign = TextAlign.Center)
        }
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            StatBox("HP (Пульс)", "99")
            
            Column(modifier = Modifier.clickable { showMannaDialog = true }) {
                Text("MP (Калории)", color = Color.LightGray, fontSize = 12.sp)
                Text("$mannaPoints", color = if (mannaPoints < 0) Color.Red else Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }

            StatBox("Отдых", "6ч 12мин")
            
            Column {
                Text("Шаги за сегодня", color = Color.LightGray, fontSize = 12.sp)
                Text("Нет доступа", color = Color.Gray, fontSize = 20.sp)
            }
        }
    }
}

@Composable
fun StatBox(title: String, value: String) {
    Column {
        Text(title, color = Color.LightGray, fontSize = 12.sp)
        Text(value, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}
