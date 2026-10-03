package com.personal.calisthenicsguide.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SessionEntity::class,
        SetLogEntity::class,
        ProgressionEntity::class,
        SettingEntity::class,
        ChecklistEntity::class,
        MediaOverrideEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessions(): SessionDao
    abstract fun setLogs(): SetLogDao
    abstract fun progression(): ProgressionDao
    abstract fun settings(): SettingDao
    abstract fun checklist(): ChecklistDao
    abstract fun media(): MediaDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "calisthenics.db").build()
    }
}
