package de.mm20.launcher2.plugin.google

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import com.google.api.client.util.DateTime
import com.google.api.services.tasks.model.Task
import com.google.api.services.tasks.model.TaskList
import de.mm20.launcher2.plugin.config.QueryPluginConfig
import de.mm20.launcher2.plugin.config.StorageStrategy
import de.mm20.launcher2.plugin.google.database.Database
import de.mm20.launcher2.plugin.google.database.entities.TaskEntity
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

class GoogleTasksSearchProvider : CalendarProvider(
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
        if (params.allowNetwork) {
            val operation = WorkManager.getInstance(context!!).enqueueUniqueWork(
                "sync_tasks",
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<TaskSyncWorker>().build()
            )

            try {
                operation.await()
            } catch (e: Exception) {
                Log.e("GoogleTasksSearchProvider", "Failed to sync tasks", e)
            }
        }

        val database = Database(context!!)

        val tasklistIds =
            database.tasklists().getAll().map { it.id }.toSet() - query.excludedCalendars.toSet()

        return database.tasks().search(
            query = query.query,
            start = query.start,
            end = query.end,
            tasklistIds = tasklistIds,
        ).map {
            CalendarEvent(
                id = "${it.tasklistId}/${it.id}",
                title = it.title,
                isCompleted = it.completed,
                calendarName = it.tasklistName,
                uri = Uri.parse(it.webViewLink),
                includeTime = false,
                endTime = it.due,
                startTime = null,
                description = it.notes,
            )
        }
    }

    override suspend fun refresh(item: CalendarEvent, params: RefreshParams): CalendarEvent? {
        Log.d("MM20", "Refresh task ${item}, $params")
        if (params.lastUpdated + 5.seconds.inWholeMilliseconds > System.currentTimeMillis()) {
            return item
        }
        val (tasklistId, id) = item.id.split("/")

        val refreshed = apiClient.taskById(tasklistId, id) ?: return null

        val database = Database(context!!)

        database.tasks().update(
            TaskEntity(
                id = refreshed.id ?: return null,
                tasklistId = tasklistId,
                title = refreshed.title ?: return null,
                notes = refreshed.notes,
                due = DateTime(refreshed.due).value,
                tasklistName = item.calendarName,
                webViewLink = refreshed.webViewLink ?: return null,
                completed = refreshed.completed != null,
            )
        )

        return apiClient.taskById(tasklistId, id)?.toCalendarEvent(
            TaskList().setId(tasklistId).setTitle(item.calendarName)
        )
    }

    override suspend fun getCalendarLists(): List<CalendarList> {
        return apiClient.tasklistsList().mapNotNull {
            CalendarList(
                id = it.id ?: return@mapNotNull null,
                name = it.title,
                contentTypes = listOf(CalendarListType.Tasks),
            )
        }
    }

    override suspend fun getPluginState(): PluginState {
        val loginState = apiClient.loginState.firstOrNull()
        if (loginState is LoginState.LoggedIn) {
            return PluginState.Ready(
                text = context!!.getString(
                    R.string.tasks_plugin_state_ready,
                    loginState.displayName
                ),
            )
        }
        return PluginState.SetupRequired(
            setupActivity = Intent(context!!, SettingsActivity::class.java),
            message = context!!.getString(R.string.tasks_plugin_state_setup_required)
        )
    }

    private fun Task.toCalendarEvent(tasklist: TaskList): CalendarEvent? {
        return CalendarEvent(
            id = "${tasklist.id}/${id ?: return null}",
            title = title ?: return null,
            description = notes,
            calendarName = tasklist.title,
            startTime = null,
            endTime = DateTime(due).value,
            includeTime = false,
            uri = Uri.parse(webViewLink ?: return null),
            isCompleted = completed != null
        )
    }
}