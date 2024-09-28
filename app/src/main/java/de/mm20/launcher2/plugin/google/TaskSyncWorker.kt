package de.mm20.launcher2.plugin.google

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.api.client.util.DateTime
import com.google.api.services.tasks.model.Task
import com.google.api.services.tasks.model.TaskList
import de.mm20.launcher2.plugin.google.database.Database
import de.mm20.launcher2.plugin.google.database.entities.TaskEntity
import de.mm20.launcher2.plugin.google.database.entities.TasklistEntity
import kotlin.time.Duration.Companion.days

class TaskSyncWorker(
    appContext: Context, workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val googleApiClient = GoogleApiClient(this.applicationContext)
        val database = Database(this.applicationContext)

        val tasklistDao = database.tasklists()
        val taskDao = database.tasks()

        val oldTasklists = tasklistDao.getAll()

        val tasklists = googleApiClient.tasklistsList().mapNotNull { it.toEntity() }

        val updatedTasklists = tasklists.filter {
            oldTasklists.find { old -> old.id == it.id }?.let { old -> old.updated < it.updated }
                ?: true
        }

        val dueMin = System.currentTimeMillis()
        val dueMax = dueMin + 365.days.inWholeMilliseconds

        for (tasklist in updatedTasklists) {
            val tasks = googleApiClient.tasksList(tasklist.id, dueMin, dueMax)
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

        return Result.success()
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