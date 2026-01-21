package com.example.smartcalendar.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.smartcalendar.data.model.ICalEvent
import com.example.smartcalendar.data.model.InputType
import com.example.smartcalendar.data.model.LocalCalendar
import com.example.smartcalendar.data.model.PendingEvent
import com.example.smartcalendar.data.model.PendingStatus
import com.example.smartcalendar.data.model.SyncStatus

/**
 * Room database for SmartCalendar app.
 * Stores calendars and events locally with sync support.
 */
@Database(
    entities = [LocalCalendar::class, ICalEvent::class, PendingEvent::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun calendarDao(): CalendarDao
    abstract fun eventDao(): EventDao
    abstract fun pendingEventDao(): PendingEventDao

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

    @TypeConverter
    fun fromPendingStatus(status: PendingStatus): String = status.name

    @TypeConverter
    fun toPendingStatus(value: String): PendingStatus = PendingStatus.valueOf(value)

    @TypeConverter
    fun fromInputType(type: InputType): String = type.name

    @TypeConverter
    fun toInputType(value: String): InputType = InputType.valueOf(value)
}
