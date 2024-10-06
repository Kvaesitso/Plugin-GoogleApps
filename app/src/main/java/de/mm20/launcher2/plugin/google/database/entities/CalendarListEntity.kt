package de.mm20.launcher2.plugin.google.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calendar_lists")
data class CalendarListEntity(
    @PrimaryKey val id: String,
    val summary: String,
    val backgroundColor: Int?,
)
