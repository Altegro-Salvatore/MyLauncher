package com.altegro.gamewidgets

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class TaskItem(val id: String, val title: String)

object TaskManager {
    private const val PREFS_NAME = "game_widget_prefs"
    private const val KEY_TASKS = "tasks_list"

    fun getTasks(context: Context): List<TaskItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_TASKS, "[]") ?: "[]"
        val list = mutableListOf<TaskItem>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(TaskItem(obj.getString("id"), obj.getString("title")))
            }
        } catch (e: Exception) {}
        return list
    }

    fun addTask(context: Context, title: String) {
        val tasks = getTasks(context).toMutableList()
        tasks.add(TaskItem(UUID.randomUUID().toString(), title))
        saveTasks(context, tasks)
    }

    fun removeTask(context: Context, id: String) {
        val tasks = getTasks(context).toMutableList()
        tasks.removeAll { it.id == id }
        saveTasks(context, tasks)
    }

    private fun saveTasks(context: Context, tasks: List<TaskItem>) {
        val jsonArray = JSONArray()
        tasks.forEach {
            val obj = JSONObject()
            obj.put("id", it.id)
            obj.put("title", it.title)
            jsonArray.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_TASKS, jsonArray.toString()).apply()
    }
}
