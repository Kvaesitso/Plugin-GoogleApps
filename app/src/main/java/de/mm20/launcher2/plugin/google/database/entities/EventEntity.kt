package de.mm20.launcher2.plugin.google.database.entities

import androidx.room.Entity

@Entity(tableName = "events", primaryKeys = ["id", "calendarId"])
data class EventEntity(
    val id: String,
    val calendarId: String,
    val summary: String,
    val start: Long,
    val end: Long,
    val includeTime: Boolean,
    val uri: String,
    val description: String?,
    val location: String?,
    val attendees: String,
)