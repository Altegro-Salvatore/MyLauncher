package com.altegro.gamewidgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

// --- ПРОВАЙДЕР КВЕСТОВ ---
class QuestWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_quests)
            
            // Кнопка "+"
            val addIntent = Intent(context, AddQuestActivity::class.java)
            val addPendingIntent = PendingIntent.getActivity(context, 0, addIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.btn_add_quest, addPendingIntent)
            
            // Настройка прокручиваемого списка ListView
            val listIntent = Intent(context, QuestWidgetService::class.java)
            listIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            views.setRemoteAdapter(R.id.quest_list, listIntent)
            
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}

// --- ПРОВАЙДЕР СТАТОВ ---
class StatsWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val prefs = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
        val mp = prefs.getInt("manna_points", 0)
        
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_stats)
            views.setTextViewText(R.id.tv_hp, "HP (Пульс): 99")
            views.setTextViewText(R.id.tv_mp, "MP (Калории): $mp")
            views.setTextViewText(R.id.tv_steps, "Шаги: (в разработке)")
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}

// --- ПРОВАЙДЕР ЧАСОВ ---
class ClockWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_clock)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
