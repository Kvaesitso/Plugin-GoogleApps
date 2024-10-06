package de.mm20.launcher2.plugin.google.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import de.mm20.launcher2.plugin.google.database.entities.CalendarListEntity

@Dao
interface CalendarListsDao {
    @Insert
    suspend fun insertAll(calendarLists: List<CalendarListEntity>)

    @Query("DELETE FROM calendar_lists")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(calendarLists: List<CalendarListEntity>) {
        deleteAll()
        insertAll(calendarLists)
    }

    @Query("SELECT * FROM calendar_lists")
    suspend fun getAll(): List<CalendarListEntity>
}