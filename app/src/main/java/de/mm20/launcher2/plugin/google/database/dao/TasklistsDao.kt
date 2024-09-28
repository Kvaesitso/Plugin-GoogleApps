package de.mm20.launcher2.plugin.google.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import de.mm20.launcher2.plugin.google.database.entities.TasklistEntity

@Dao
interface TasklistsDao {
    @Insert
    suspend fun insertAll(tasklists: List<TasklistEntity>)

    @Query("DELETE FROM tasklists")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(tasklists: List<TasklistEntity>) {
        deleteAll()
        insertAll(tasklists)
    }

    @Query("SELECT * FROM tasklists")
    suspend fun getAll(): List<TasklistEntity>
}