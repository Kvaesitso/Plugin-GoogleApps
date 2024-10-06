package de.mm20.launcher2.plugin.google.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import de.mm20.launcher2.plugin.google.database.entities.EventEntity
import de.mm20.launcher2.plugin.google.database.entities.EventWithCalendarListEntity
import de.mm20.launcher2.plugin.google.database.entities.TaskEntity
import de.mm20.launcher2.plugin.google.database.entities.TaskWithTasklistEntity

@Dao
interface EventsDao {
    @Insert
    suspend fun insertAll(events: List<EventEntity>)

    @Query("DELETE FROM events WHERE calendarId = :calendarListId")
    suspend fun deleteAll(calendarListId: String)

    @Transaction
    suspend fun replaceAll(calendarListId: String, events: List<EventEntity>) {
        deleteAll(calendarListId)
        insertAll(events)
    }

    @Update
    suspend fun update(task: EventEntity)

    @Query("SELECT * FROM events_with_calendar_lists WHERE calendarId IN (:calendarListIds)" +
            " AND (:query IS NULL OR summary LIKE '%' || :query || '%')" +
            " AND (:start IS NULL OR :start <= `end`)" +
            " AND (:end IS NULL OR :end > start)" +
            " ORDER BY start ASC")
    suspend fun search(
        query: String?,
        start: Long?,
        end: Long?,
        calendarListIds: Set<String>,
    ): List<EventWithCalendarListEntity>
}