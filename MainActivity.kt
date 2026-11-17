package com.example.gamelauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight

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
        // Правая колонка (Время, Погода, Прилы)
        Column(modifier = Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.End) {
            Text("12:59", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("25°", fontSize = 32.sp, color = Color.White)
            Spacer(modifier = Modifier.height(16.dp))
            // Сетка 8 приложений (2х4)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 0 until 4) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.size(48.dp).background(Color.Gray, RoundedCornerShape(12.dp)))
                        Box(modifier = Modifier.size(48.dp).background(Color.Gray, RoundedCornerShape(12.dp)))
                    }
                }
            }
        }
    }
}

@Composable
fun ScreenTwo() {
    Row(modifier = Modifier.fillMaxSize().background(Color(0xFF121212)).padding(16.dp)) {
        // Аватар (1 к 2)
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight().background(Color.DarkGray, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("+ Добавить\nАватар", color = Color.Gray, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        Spacer(modifier = Modifier.width(16.dp))
        // Статы
        Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            StatBox("HP (Пульс)", "99")
            StatBox("MP (Калории)", "10") // Тут потом будет окошко
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