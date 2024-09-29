package de.mm20.launcher2.plugin.google.database

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import de.mm20.launcher2.plugin.google.database.dao.TasklistsDao
import de.mm20.launcher2.plugin.google.database.dao.TasksDao
import de.mm20.launcher2.plugin.google.database.entities.TaskEntity
import de.mm20.launcher2.plugin.google.database.entities.TasklistEntity

@androidx.room.Database(entities = [TasklistEntity::class, TaskEntity::class], version = 1)
abstract class Database : RoomDatabase() {
    abstract fun tasks(): TasksDao
    abstract fun tasklists(): TasklistsDao

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