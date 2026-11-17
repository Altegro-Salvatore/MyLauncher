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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay

data class AppInfo(val name: String, val packageName: String, val icon: Drawable, val launchCount: Int)

fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
    return mode == AppOpsManager.MODE_ALLOWED
}

fun getFrequentApps(context: Context): List<AppInfo> {
    val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val endTime = System.currentTimeMillis()
    val beginTime = endTime - 1000L * 60 * 60 * 24 * 7 // 7 дней назад
    
    // Получаем все события запусков
    val usageEvents = usageStatsManager.queryEvents(beginTime, endTime)
    val event = UsageEvents.Event()
    val launchCounts = mutableMapOf<String, Int>()

    // Считаем, сколько раз каждое приложение выходило на передний план
    while (usageEvents.hasNextEvent()) {
        usageEvents.getNextEvent(event)
        // ACTIVITY_RESUMED (или MOVE_TO_FOREGROUND) = 1
        if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED || event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
            val pkg = event.packageName
            if (pkg != context.packageName) { // Исключаем наш лаунчер
                launchCounts[pkg] = launchCounts.getOrDefault(pkg, 0) + 1
            }
        }
    }

    val pm = context.packageManager
    
    // Сортируем по количеству запусков и берем топ-8
    return launchCounts.entries
        .sortedByDescending { it.value }
        .filter { pm.getLaunchIntentForPackage(it.key) != null } // Только те, что можно открыть
        .mapNotNull {
            try {
                val appInfo = pm.getApplicationInfo(it.key, 0)
                val name = pm.getApplicationLabel(appInfo).toString()
                val icon = pm.getApplicationIcon(it.key)
                AppInfo(name, it.key, icon, it.value)
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

    LaunchedEffect(hasPermission) {
        while (true) {
            if (hasPermission) {
                apps = getFrequentApps(context)
            }
            // Обновляем раз в 10 секунд (чтобы не грузить процессор постоянно)
            delay(10000) 
        }
    }

    LaunchedEffect(Unit) {
        hasPermission = hasUsageStatsPermission(context)
    }

    Row(modifier = Modifier.fillMaxSize().background(Color(0xFF1E1E1E)).padding(16.dp)) {
        // Левая колонка (Квесты)
        Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color.DarkGray, RoundedCornerShape(16.dp))) {
                Text("Крупный квест", modifier = Modifier.align(Alignment.Center), color = Color.White)
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color.Gray, RoundedCornerShape(16.dp))) {
                Text("Средний квест", modifier = Modifier.align(Alignment.Center), color = Color.White)
            }
            Box(modifier = Modifier.weight(2f).fillMaxWidth().background(Color(0xFF2C2C2C), RoundedCornerShape(16.dp))) {
                Text("Мелкие квесты (список)", modifier = Modifier.align(Alignment.Center), color = Color.White)
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Правая колонка
        Column(modifier = Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.End) {
            Text("12:59", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("25°", fontSize = 32.sp, color = Color.White)
            Spacer(modifier = Modifier.height(16.dp))
            
            if (!hasPermission) {
                Button(
                    onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
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
                                        modifier = Modifier
                                            .size(56.dp)
                                            .background(Color(0xFF333333), RoundedCornerShape(14.dp))
                                            .clickable {
                                                val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                                                context.startActivity(launchIntent)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            bitmap = app.icon.toBitmap(100, 100).asImageBitmap(),
                                            contentDescription = app.name,
                                            modifier = Modifier.size(44.dp)
                                        )
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
            StatBox("MP (Калории)", "10")
            StatBox("Отдых", "6ч 12мин")
            StatBox("Шаги", "5224")
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
