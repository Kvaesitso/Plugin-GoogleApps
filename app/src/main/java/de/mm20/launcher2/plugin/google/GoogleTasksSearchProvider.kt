package de.mm20.launcher2.plugin.google

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import com.google.api.client.util.DateTime
import com.google.api.services.tasks.model.Task
import com.google.api.services.tasks.model.TaskList
import de.mm20.launcher2.plugin.config.QueryPluginConfig
import de.mm20.launcher2.plugin.config.StorageStrategy
import de.mm20.launcher2.plugin.google.database.Database
import de.mm20.launcher2.plugin.google.database.entities.TaskEntity
import de.mm20.launcher2.plugin.google.database.entities.TasklistEntity
import de.mm20.launcher2.sdk.PluginState
import de.mm20.launcher2.sdk.base.RefreshParams
import de.mm20.launcher2.sdk.base.SearchParams
import de.mm20.launcher2.sdk.calendar.CalendarEvent
import de.mm20.launcher2.sdk.calendar.CalendarList
import de.mm20.launcher2.sdk.calendar.CalendarProvider
import de.mm20.launcher2.search.calendar.CalendarListType
import de.mm20.launcher2.search.calendar.CalendarQuery
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.collections.map
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class GoogleTasksSearchProvider : CalendarProvider(
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
        prefs = context!!.getSharedPreferences("tasks", Context.MODE_PRIVATE)
        return super.onCreate()
    }

    override suspend fun search(query: CalendarQuery, params: SearchParams): List<CalendarEvent> {
        val lastSynced = prefs.getLong("last_sync", 0)

        if (params.allowNetwork && lastSynced + 1.minutes.inWholeMilliseconds < System.currentTimeMillis()
            || lastSynced + 1.hours.inWholeMilliseconds < System.currentTimeMillis()
        ) {
            syncTasks()
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
        if (params.lastUpdated + 30.seconds.inWholeMilliseconds > System.currentTimeMillis()) {
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

    private val syncLock = Mutex()
    private suspend fun syncTasks() {

        if (syncLock.isLocked) return

        Log.d("GoogleTasksSearchProvider", "Syncing tasks")

        syncLock.withLock {
            val tasklistDao = database.tasklists()
            val taskDao = database.tasks()

            val oldTasklists = tasklistDao.getAll()

            val tasklists = apiClient.tasklistsList().mapNotNull { it.toEntity() }

            val updatedTasklists = tasklists.filter {
                oldTasklists.find { old -> old.id == it.id }
                    ?.let { old -> old.updated < it.updated }
                    ?: true
            }

            val dueMin = System.currentTimeMillis()
            val dueMax = dueMin + 365.days.inWholeMilliseconds

            for (tasklist in updatedTasklists) {
                val tasks = apiClient.tasksList(tasklist.id, dueMin, dueMax)
                    .mapNotNull {
                        it.toEntity(tasklist)
                    }
                taskDao.replaceAll(tasklist.id, tasks)
            }

            tasklistDao.replaceAll(
                tasklists.map {
                    TasklistEntity(
                        id = it.id,
                        title = it.title,
                        updated = it.updated
                    )
                }
            )
        }

        prefs.edit {
            putLong("last_sync", System.currentTimeMillis())
        }
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


    private fun TaskList.toEntity(): TasklistEntity? {
        return TasklistEntity(
            id = id ?: return null,
            title = title ?: return null,
            updated = DateTime(updated ?: return null).value
        )
    }

    private fun Task.toEntity(tasklist: TasklistEntity): TaskEntity? {
        return TaskEntity(
            id = id ?: return null,
            tasklistId = tasklist.id,
            title = title ?: return null,
            notes = notes,
            due = DateTime(due ?: return null).value,
            tasklistName = tasklist.title,
            webViewLink = webViewLink ?: return null,
            completed = completed != null
        )
    }
}