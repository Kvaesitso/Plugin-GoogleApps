package de.mm20.launcher2.plugin.google.database.entities

import androidx.room.DatabaseView

@DatabaseView(
    viewName = "tasks_with_tasklist",
    value = """
        SELECT tasks.id, tasks.tasklistId, tasklists.title AS tasklistName, tasks.title, tasks.notes, tasks.due, tasks.webViewLink, tasks.completed
        FROM tasks
        LEFT JOIN tasklists ON tasks.tasklistId = tasklists.id
    """,
)
data class TaskWithTasklistEntity(
    val id: String,
    val tasklistId: String,
    val tasklistName: String?,
    val title: String,
    val notes: String?,
    val due: Long,
    val webViewLink: String,
    val completed: Boolean,
)