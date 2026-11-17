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

// НОВАЯ ЦВЕТОВАЯ ПАЛИТРА (по референсам)
val ColorBg = Color(0xFF1A1512)         // Темное дерево/кожа
val ColorCardBg = Color(0xFF2C231E)     // Темный пергамент
val ColorGold = Color(0xFFC59B4B)       // Золотой акцент
val ColorParchment = Color(0xFFE8D5B5)  // Светлый пергамент
val ColorTextDark = Color(0xFF3A2A1A)   // Темный текст для пергамента
val ColorHp = Color(0xFFD9534F)         // Красный (кровь)
val ColorMp = Color(0xFF5BC0DE)         // Голубой (мана)

data class AppInfo(val name: String, val packageName: String, val icon: Drawable)
data class TaskItem(val id: String, val title: String, val category: String)

fun saveTasks(context: Context, tasks: List<TaskItem>) {
    val jsonArray = JSONArray()
    tasks.forEach {
        val jsonObj = JSONObject()
        jsonObj.put("id", it.id)
        jsonObj.put("title", it.title)
        jsonObj.put("category", it.category)
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
            list.add(TaskItem(obj.getString("id"), obj.getString("title"), obj.optString("category", "Minor")))
        }
    } catch (e: Exception) {}
    return list
}

fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
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
            if (pkg != context.packageName) launchCounts[pkg] = launchCounts.getOrDefault(pkg, 0) + 1
        }
    }

    val pm = context.packageManager
    return launchCounts.entries
        .sortedByDescending { it.value }
        .filter { pm.getLaunchIntentForPackage(it.key) != null }
        .mapNotNull {
            try {
                val appInfo = pm.getApplicationInfo(it.key, 0)
                AppInfo(pm.getApplicationLabel(appInfo).toString(), it.key, pm.getApplicationIcon(it.key))
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
    var currentCategory by remember { mutableStateOf("Major") }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = ColorCardBg,
            title = { Text("Новая задача", color = ColorGold, fontFamily = FontFamily.Serif) },
            text = {
                OutlinedTextField(
                    value = inputTask,
                    onValueChange = { inputTask = it },
                    label = { Text("Что нужно сделать?", color = ColorParchment) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = ColorParchment,
                        unfocusedTextColor = ColorParchment,
                        focusedContainerColor = ColorBg,
                        unfocusedContainerColor = ColorBg,
                        focusedIndicatorColor = ColorGold,
                        unfocusedIndicatorColor = ColorGold.copy(alpha = 0.5f)
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (inputTask.isNotBlank()) {
                        tasks = tasks + TaskItem(UUID.randomUUID().toString(), inputTask, currentCategory)
                        saveTasks(context, tasks)
                    }
                    inputTask = ""
                    showAddDialog = false
                }) { Text("Добавить", color = ColorGold, fontFamily = FontFamily.Serif) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Отмена", color = ColorParchment) }
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

    val onTaskDone: (TaskItem) -> Unit = { task ->
        tasks = tasks.filter { it.id != task.id }
        saveTasks(context, tasks)
    }

    Row(modifier = Modifier.fillMaxSize().background(ColorBg).padding(16.dp)) {
        
        // ЛЕВАЯ КОЛОНКА - КВЕСТЫ В СТИЛЕ D&D
        Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                QuestListCard("Глобальные", tasks.filter { it.category == "Major" }, { currentCategory = "Major"; showAddDialog = true }, onTaskDone)
            }

            Box(modifier = Modifier.weight(1.2f).fillMaxWidth()) {
                QuestListCard("Средние", tasks.filter { it.category == "Medium" }, { currentCategory = "Medium"; showAddDialog = true }, onTaskDone)
            }

            Box(modifier = Modifier.weight(2f).fillMaxWidth()) {
                QuestListCard("Мелкие", tasks.filter { it.category == "Minor" }, { currentCategory = "Minor"; showAddDialog = true }, onTaskDone)
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // ПРАВАЯ КОЛОНКА - ВРЕМЯ, ПОГОДА, ПРИЛОЖЕНИЯ
        Column(modifier = Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.End) {
            
            // Карточка времени (Пергамент)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ColorParchment, RoundedCornerShape(4.dp))
                    .border(2.dp, ColorGold, RoundedCornerShape(4.dp))
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Year 1 Spring 12", fontSize = 12.sp, fontFamily = FontFamily.Serif, color = ColorTextDark)
                    Text(currentTime, fontSize = 42.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = ColorTextDark)
                }
            }

            // Погода (просто текст пока)
            Text("25°", fontSize = 32.sp, fontFamily = FontFamily.Serif, color = ColorParchment, modifier = Modifier.padding(vertical = 12.dp))
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (!hasPermission) {
                Button(
                    onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }, 
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ColorCardBg)
                ) {
                    Text("Разрешить\nприложения", textAlign = TextAlign.Center, fontSize = 12.sp, color = ColorGold, fontFamily = FontFamily.Serif)
                }
            } else {
                // Сетка приложений (как созвездия/артефакты)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (i in 0 until 4) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            for (j in 0 until 2) {
                                val index = i * 2 + j
                                if (index < apps.size) {
                                    val app = apps[index]
                                    Box(
                                        modifier = Modifier.size(56.dp)
                                            .background(ColorCardBg, RoundedCornerShape(8.dp))
                                            .border(1.dp, ColorGold.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                            .clickable { context.startActivity(context.packageManager.getLaunchIntentForPackage(app.packageName)) },
                                        contentAlignment = Alignment.Center
                                    ) {
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorCardBg, RoundedCornerShape(6.dp))
            .border(1.dp, ColorGold, RoundedCornerShape(6.dp)) // Золотая рамка как на карточках
            .padding(12.dp)
    ) {
        // Заголовок карточки
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Box(
                modifier = Modifier.size(22.dp).border(1.dp, ColorGold, CircleShape).clickable { onAddClick() },
                contentAlignment = Alignment.Center
            ) {
                Text("+", color = ColorGold, fontSize = 16.sp, fontFamily = FontFamily.Serif)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(title, color = ColorGold, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
        }

        // Список задач с квадратными чекбоксами (как в D&D)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(tasks) { task ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .border(1.dp, ColorGold, RoundedCornerShape(2.dp))
                            .background(ColorBg, RoundedCornerShape(2.dp))
                            .clickable { onTaskDone(task) }
                    )
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
            onDismissRequest = { showMannaDialog = false },
            containerColor = ColorCardBg,
            title = { Text("Добавить калории (MP)", color = ColorMp, fontFamily = FontFamily.Serif) },
            text = {
                OutlinedTextField(
                    value = inputManna,
                    onValueChange = { if (it.all { char -> char.isDigit() }) inputManna = it },
                    label = { Text("Сколько калорий?", color = ColorParchment) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = ColorParchment,
                        unfocusedTextColor = ColorParchment,
                        focusedContainerColor = ColorBg,
                        unfocusedContainerColor = ColorBg,
                        focusedIndicatorColor = ColorMp,
                        unfocusedIndicatorColor = ColorMp.copy(alpha = 0.5f)
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val calories = inputManna.toIntOrNull() ?: 0
                    mannaPoints += calories
                    prefs.edit().putInt("manna_points", mannaPoints).apply()
                    inputManna = ""
                    showMannaDialog = false
                }) { Text("Добавить", color = ColorMp, fontFamily = FontFamily.Serif) }
            },
            dismissButton = {
                TextButton(onClick = { showMannaDialog = false }) { Text("Отмена", color = ColorParchment) }
            }
        )
    }

    Row(modifier = Modifier.fillMaxSize().background(ColorBg).padding(16.dp)) {
        // Аватар (Карточка персонажа)
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight()
                .background(ColorCardBg, RoundedCornerShape(8.dp))
                .border(2.dp, ColorGold, RoundedCornerShape(8.dp)), // Толстая золотая рамка
            contentAlignment = Alignment.Center
        ) {
            Text("+ Добавить\nАватар", color = ColorParchment.copy(alpha = 0.5f), textAlign = TextAlign.Center, fontFamily = FontFamily.Serif)
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Статы
        Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            
            // HP (Красный)
            Column {
                Text("HP (Пульс)", color = ColorParchment, fontSize = 12.sp, fontFamily = FontFamily.Serif)
                Text("99", color = ColorHp, fontSize = 32.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
            }
            
            // MP (Синий, кликабельный)
            Column(modifier = Modifier.clickable { showMannaDialog = true }) {
                Text("MP (Калории)", color = ColorParchment, fontSize = 12.sp, fontFamily = FontFamily.Serif)
                Text("$mannaPoints", color = if (mannaPoints < 0) Color.Red else ColorMp, fontSize = 32.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
            }

            // Отдых
            Column {
                Text("Отдых", color = ColorParchment, fontSize = 12.sp, fontFamily = FontFamily.Serif)
                Text("6ч 12мин", color = ColorParchment, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
            }
            
            // Шаги
            Column {
                Text("Шаги за сегодня", color = ColorParchment, fontSize = 12.sp, fontFamily = FontFamily.Serif)
                Text("Нет доступа", color = ColorParchment.copy(alpha = 0.5f), fontSize = 20.sp, fontFamily = FontFamily.Serif)
            }
        }
    }
}
