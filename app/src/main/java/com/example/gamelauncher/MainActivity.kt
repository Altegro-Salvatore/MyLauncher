package com.example.gamelauncher

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AppInfo(val name: String, val packageName: String, val icon: Drawable)

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
    var dailySteps by remember { mutableStateOf(0) }
    val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    
    val prefs: SharedPreferences = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)

    var hasActivityPermission by remember { 
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED) 
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        hasActivityPermission = isGranted
    }
    
    LaunchedEffect(Unit) {
        if (!hasActivityPermission) permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
    }

    DisposableEffect(hasActivityPermission) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
                    val totalSensorSteps = event.values[0].toInt()
                    val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    
                    val savedDate = prefs.getString("step_date", "")
                    val savedBaseline = prefs.getInt("step_baseline", -1)

                    if (savedDate != todayDate || savedBaseline == -1 || totalSensorSteps < savedBaseline) {
                        prefs.edit()
                            .putString("step_date", todayDate)
                            .putInt("step_baseline", totalSensorSteps)
                            .apply()
                        dailySteps = 0
                    } else {
                        dailySteps = totalSensorSteps - savedBaseline
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }

        if (hasActivityPermission && stepSensor != null) {
            sensorManager.registerListener(listener, stepSensor, SensorManager.SENSOR_DELAY_UI)
        }

        onDispose { sensorManager.unregisterListener(listener) }
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
            StatBox("MP (Калории)", "10")
            StatBox("Отдых", "6ч 12мин")
            
            // Блок с шагами
            Column {
                Text("Шаги за сегодня", color = Color.LightGray, fontSize = 12.sp)
                if (hasActivityPermission && stepSensor != null) {
                    Text("$dailySteps", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                } else {
                    // Если нет разрешения - показываем кнопку, которая ведет в настройки
                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            intent.data = Uri.parse("package:" + context.packageName)
                            context.startActivity(intent)
                        },
                        modifier = Modifier.padding(top = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                    ) {
                        Text("Разрешить", color = Color.White, fontSize = 14.sp)
                    }
                }
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
