package de.mm20.launcher2.plugin.google.database.entities

import androidx.room.DatabaseView

@DatabaseView(
    viewName = "events_with_calendar_lists",
    value = "SELECT events.*, calendar_lists.summary AS calendarListSummary, calendar_lists.backgroundColor AS calendarListBackgroundColor FROM events " +
            "LEFT JOIN calendar_lists ON events.calendarId = calendar_lists.id",
)
data class EventWithCalendarListEntity(
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
    val calendarListSummary: String,
    val calendarListBackgroundColor: Int?,
)
