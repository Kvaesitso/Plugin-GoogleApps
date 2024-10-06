package de.mm20.launcher2.plugin.google

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import com.google.api.services.calendar.model.CalendarListEntry
import com.google.api.services.calendar.model.Event
import de.mm20.launcher2.plugin.config.QueryPluginConfig
import de.mm20.launcher2.plugin.config.StorageStrategy
import de.mm20.launcher2.plugin.google.database.Database
import de.mm20.launcher2.plugin.google.database.Database.Companion.invoke
import de.mm20.launcher2.plugin.google.database.entities.CalendarListEntity
import de.mm20.launcher2.plugin.google.database.entities.EventEntity
import de.mm20.launcher2.sdk.PluginState
import de.mm20.launcher2.sdk.base.RefreshParams
import de.mm20.launcher2.sdk.base.SearchParams
import de.mm20.launcher2.sdk.calendar.CalendarEvent
import de.mm20.launcher2.sdk.calendar.CalendarList
import de.mm20.launcher2.sdk.calendar.CalendarProvider
import de.mm20.launcher2.search.calendar.CalendarListType
import de.mm20.launcher2.search.calendar.CalendarQuery
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

class GoogleCalendarSearchProvider : CalendarProvider(
    QueryPluginConfig(
        storageStrategy = StorageStrategy.StoreCopy,
    )
) {
    private lateinit var apiClient: GoogleApiClient
    private lateinit var database: Database
    private lateinit var prefs: SharedPreferences

    override fun onCreate(): Boolean {
        apiClient = GoogleApiClient(context!!)
        database = Database(context!!)
        prefs = context!!.getSharedPreferences("events", Context.MODE_PRIVATE)
        return super.onCreate()
    }

    override suspend fun search(query: CalendarQuery, params: SearchParams): List<CalendarEvent> {
        if (!params.allowNetwork) {
            val lastSynced = prefs.getLong("last_sync", 0)
            if (lastSynced + 1.hours.inWholeMilliseconds < System.currentTimeMillis()) {
                syncCalendars()
            }
            val calendarListIds = database.calendarLists().getAll().map { it.id }.toSet() - query.excludedCalendars.toSet()
            return database.events().search(
                query = query.query,
                start = query.start,
                end = query.end,
                calendarListIds = calendarListIds,
            ).map {
                CalendarEvent(
                    id = "${it.calendarId}/${it.id}",
                    title = it.summary,
                    description = it.description,
                    calendarName = it.calendarListSummary,
                    color = it.calendarListBackgroundColor,
                    location = it.location,
                    startTime = it.start,
                    endTime = it.end,
                    includeTime = it.includeTime,
                    uri = Uri.parse(it.uri),
                    attendees = it.attendees.split("\n"),
                )
            }
        }

        val calendarLists = apiClient.calendarListsList()

        return supervisorScope {
            calendarLists.map { list ->
                async {
                    apiClient.calendarEventsList(
                        calendarId = list.id,
                        timeMin = query.start,
                        timeMax = query.end,
                        q = query.query,
                    ).mapNotNull {
                        it.toCalendarEvent(
                            calendarId = list.id,
                            calendarName = list.summaryOverride ?: list.summary,
                            calendarColor = list.backgroundColor?.substring(1)?.toIntOrNull(16)
                                ?.or(0xFF000000.toInt()),
                        )
                    }
                }
            }.awaitAll().flatten()
        }
    }

    override suspend fun refresh(item: CalendarEvent, params: RefreshParams): CalendarEvent? {
        if (params.lastUpdated + 30.seconds.inWholeMilliseconds > System.currentTimeMillis()) {
            return item
        }
        val (calendarId, id) = item.id.split("/")

        val refreshed = apiClient.calendarEventById(calendarId, id) ?: return null

        val event = refreshed.toCalendarEvent(
            calendarId = calendarId,
            calendarName = item.calendarName,
            calendarColor = item.color,
        ) ?: return null

        val entity = event.toEntity() ?: return null

        database.events().update(entity)

        return event
    }

    override suspend fun getCalendarLists(): List<CalendarList> {
        return apiClient.calendarListsList().mapNotNull {
            CalendarList(
                id = it.id ?: return@mapNotNull null,
                name = it.summaryOverride ?: it.summary,
                color = it.backgroundColor?.substring(1)?.toIntOrNull(16)?.or(0xFF000000.toInt()),
                contentTypes = listOf(CalendarListType.Calendar),
            )
        }
    }

    override suspend fun getPluginState(): PluginState {
        val loginState = apiClient.loginState.firstOrNull()
        if (loginState is LoginState.LoggedIn) {
            return PluginState.Ready(
                text = context!!.getString(
                    R.string.calendar_plugin_state_ready,
                    loginState.displayName
                ),
            )
        }
        return PluginState.SetupRequired(
            setupActivity = Intent(context!!, SettingsActivity::class.java),
            message = context!!.getString(R.string.calendar_plugin_state_setup_required)
        )
    }

    private fun Event.toCalendarEvent(
        calendarId: String?,
        calendarName: String?,
        calendarColor: Int?,
    ): CalendarEvent? {
        val includeTime = when {
            start.dateTime != null && end.dateTime != null -> true
            start.date != null && end.date != null -> false
            else -> return null
        }

        val startOffset = if (start.date != null) {
            ZonedDateTime.ofInstant(
                Instant.ofEpochMilli(start.date.value),
                ZoneId.systemDefault()
            ).offset.totalSeconds * 1000
        } else 0

        val endOffset = if (end.date != null) {
            ZonedDateTime.ofInstant(
                Instant.ofEpochMilli(end.date.value),
                ZoneId.systemDefault()
            ).offset.totalSeconds * 1000
        } else 0

        val startTime =
            start.dateTime?.value ?: start.date?.value?.minus(startOffset) ?: return null
        val endTime = end.dateTime?.value ?: end.date?.value?.minus(endOffset) ?: return null
        return CalendarEvent(
            id = "$calendarId/${id ?: return null}",
            title = summary ?: return null,
            description = description,
            calendarName = calendarName,
            color = calendarColor,
            location = location,
            startTime = startTime,
            endTime = endTime,
            includeTime = includeTime,
            uri = Uri.parse(htmlLink ?: return null),
            attendees = attendees?.mapNotNull { it.displayName } ?: emptyList(),
        )
    }

    private val syncLock = Mutex()
    private suspend fun syncCalendars() {
        if (syncLock.isLocked) return

        Log.d("GoogleCalendarSearchProvider", "Syncing calendars")

        syncLock.withLock {
            val calendarListDao = database.calendarLists()
            val eventDao = database.events()

            val calendarLists = apiClient.calendarListsList().mapNotNull { it.toEntity() }

            val timeMin = System.currentTimeMillis()
            val timeMax = timeMin + 14.days.inWholeMilliseconds

            for (calendarList in calendarLists) {
                val events = apiClient.calendarEventsList(calendarList.id, timeMin = timeMin, timeMax = timeMax)
                    .mapNotNull {
                        it.toCalendarEvent(
                            calendarList.id,
                            calendarList.summary,
                            calendarList.backgroundColor,
                        )?.toEntity()
                    }
                eventDao.replaceAll(calendarList.id, events)
            }

            calendarListDao.replaceAll(calendarLists)

            prefs.edit {
                putLong("last_sync", System.currentTimeMillis())
            }
        }
    }

    private fun CalendarListEntry.toEntity(): CalendarListEntity? {
        return CalendarListEntity(
            id = id ?: return null,
            summary = summaryOverride ?: summary ?: return null,
            backgroundColor = backgroundColor?.substring(1)?.toIntOrNull(16)?.or(0xFF000000.toInt()),
        )
    }

    private fun CalendarEvent.toEntity(): EventEntity? {
        val (calendarId, id) = id.split("/")
        return EventEntity(
            id = id,
            calendarId = calendarId,
            summary = title,
            start = startTime ?: return null,
            end = endTime,
            includeTime = includeTime,
            uri = uri.toString(),
            description = description,
            location = location,
            attendees = attendees.joinToString("\n"),
        )
    }
}