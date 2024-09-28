package de.mm20.launcher2.plugin.google.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import de.mm20.launcher2.plugin.google.database.entities.TaskEntity

@Dao
interface TasksDao {
    @Insert
    suspend fun insertAll(tasks: List<TaskEntity>)

    @Query("DELETE FROM tasks WHERE tasklistId = :tasklistId")
    suspend fun deleteAll(tasklistId: String)

    @Transaction
    suspend fun replaceAll(tasklistId: String, tasks: List<TaskEntity>) {
        deleteAll(tasklistId)
        insertAll(tasks)
    }

    @Update
    suspend fun update(task: TaskEntity)

    @Query("SELECT * FROM tasks WHERE tasklistId IN (:tasklistIds)" +
            " AND (:query IS NULL OR title LIKE '%' || :query || '%')" +
            " AND (:start IS NULL OR :start <= due)" +
            " AND (:end IS NULL OR :end > due)" +
            " ORDER BY due ASC")
    suspend fun search(
        query: String?,
        start: Long?,
        end: Long?,
        tasklistIds: Set<String>,
    ): List<TaskEntity>
}