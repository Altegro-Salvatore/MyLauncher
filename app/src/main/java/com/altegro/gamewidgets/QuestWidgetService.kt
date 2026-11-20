package com.altegro.gamewidgets

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService

class QuestWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return QuestFactory(applicationContext)
    }
}

class QuestFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {
    private var tasks: List<TaskItem> = emptyList()

    override fun onCreate() {}
    
    override fun onDataSetChanged() {
        tasks = TaskManager.getTasks(context)
    }
    
    override fun onDestroy() {}
    
    override fun getCount(): Int = tasks.size
    
    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.quest_item)
        val task = tasks[position]
        views.setTextViewText(R.id.quest_title, task.title)
        return views
    }
    
    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
}
