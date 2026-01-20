package com.example.smartcalendar.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.smartcalendar.data.model.ICalEvent
import com.example.smartcalendar.data.model.LocalCalendar
import com.example.smartcalendar.data.model.SyncStatus

/**
 * Room database for SmartCalendar app.
 * Stores calendars and events locally with sync support.
 */
@Database(
    entities = [LocalCalendar::class, ICalEvent::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun calendarDao(): CalendarDao
    abstract fun eventDao(): EventDao

    companion object {
        private const val DATABASE_NAME = "smartcalendar.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
            .fallbackToDestructiveMigration()
            .build()
        }
    }
}

/**
 * Type converters for Room to handle custom types.
 */
class Converters {
    @TypeConverter
    fun fromSyncStatus(status: SyncStatus): String = status.name

    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus = SyncStatus.valueOf(value)
}
