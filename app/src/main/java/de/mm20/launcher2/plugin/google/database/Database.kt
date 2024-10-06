package de.mm20.launcher2.plugin.google.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import de.mm20.launcher2.plugin.google.database.dao.CalendarListsDao
import de.mm20.launcher2.plugin.google.database.dao.EventsDao
import de.mm20.launcher2.plugin.google.database.dao.TasklistsDao
import de.mm20.launcher2.plugin.google.database.dao.TasksDao
import de.mm20.launcher2.plugin.google.database.entities.CalendarListEntity
import de.mm20.launcher2.plugin.google.database.entities.EventEntity
import de.mm20.launcher2.plugin.google.database.entities.EventWithCalendarListEntity
import de.mm20.launcher2.plugin.google.database.entities.TaskEntity
import de.mm20.launcher2.plugin.google.database.entities.TaskWithTasklistEntity
import de.mm20.launcher2.plugin.google.database.entities.TasklistEntity

@androidx.room.Database(
    entities = [TasklistEntity::class, TaskEntity::class, CalendarListEntity::class, EventEntity::class],
    views = [TaskWithTasklistEntity::class, EventWithCalendarListEntity::class],
    version = 1
)
abstract class Database : RoomDatabase() {
    abstract fun tasks(): TasksDao
    abstract fun tasklists(): TasklistsDao
    abstract fun events(): EventsDao
    abstract fun calendarLists(): CalendarListsDao

    companion object {
        private lateinit var instance: Database
        operator fun invoke(context: Context): Database {
            if (!::instance.isInitialized) {
                instance = Room.databaseBuilder<Database>(
                    context.applicationContext,
                    "offline_data"
                ).build()
            }
            return instance
        }
    }
}