package de.mm20.launcher2.plugin.google.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasklists")
data class TasklistEntity(
    @PrimaryKey val id: String,
    val title: String,
    val updated: Long,
)