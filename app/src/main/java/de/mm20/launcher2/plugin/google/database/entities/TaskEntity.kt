package de.mm20.launcher2.plugin.google.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks", primaryKeys = ["id", "tasklistId"])
data class TaskEntity(
    val id: String,
    val tasklistId: String,
    val title: String,
    val notes: String?,
    val tasklistName: String?,
    val due: Long,
    val webViewLink: String,
    val completed: Boolean,
)