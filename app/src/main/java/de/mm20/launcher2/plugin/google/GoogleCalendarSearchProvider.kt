package de.mm20.launcher2.plugin.google

import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.api.services.calendar.model.CalendarListEntry
import com.google.api.services.calendar.model.Event
import de.mm20.launcher2.plugin.config.QueryPluginConfig
import de.mm20.launcher2.plugin.config.StorageStrategy
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
import kotlin.time.Duration.Companion.seconds

class GoogleCalendarSearchProvider : CalendarProvider(
    QueryPluginConfig(
        storageStrategy = StorageStrategy.StoreCopy,
    )
) {
    private lateinit var apiClient: GoogleApiClient

    override fun onCreate(): Boolean {
        apiClient = GoogleApiClient(context!!)
        return super.onCreate()
    }

    override suspend fun search(query: CalendarQuery, params: SearchParams): List<CalendarEvent> {
        if (!params.allowNetwork) return emptyList()

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
                        it.toCalendarEvent(list)
                    }
                }
            }.awaitAll().flatten()
        }
    }

    override suspend fun refresh(item: CalendarEvent, params: RefreshParams): CalendarEvent? {
        if (params.lastUpdated + 5.seconds.inWholeMilliseconds > System.currentTimeMillis()) {
            return item
        }
        val (calendarId, id) = item.id.split("/")
        return apiClient.calendarEventById(calendarId, id)?.toCalendarEvent(
            CalendarListEntry()
                .setId(calendarId)
                .setSummary(item.calendarName)
                .setBackgroundColor(item.color?.toString(16))
        )
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

    private fun Event.toCalendarEvent(calendarList: CalendarListEntry): CalendarEvent? {
        val includeTime = when {
            start.dateTime != null && end.dateTime != null -> true
            start.date != null && end.date != null -> false
            else -> return null
        }
        val startTime = start.dateTime?.value ?: start.date?.value ?: return null
        val endTime = end.dateTime?.value ?: end.date?.value ?: return null
        return CalendarEvent(
            id = "${calendarList.id}/${id ?: return null}",
            title = summary ?: return null,
            description = description,
            calendarName = calendarList.summary,
            color = calendarList.backgroundColor?.substring(1)?.toIntOrNull(16)?.or(0xFF000000.toInt()),
            location = location,
            startTime = startTime,
            endTime = endTime,
            includeTime = includeTime,
            uri = Uri.parse(htmlLink ?: return null),
            attendees = attendees?.mapNotNull { it.displayName } ?: emptyList(),
        )
    }
}