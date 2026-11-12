package com.roadar.app.data.report

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PotholeReport::class],
    version = 1,
    exportSchema = true
)
abstract class RoadarDatabase : RoomDatabase() {
    abstract fun potholeReportDao(): PotholeReportDao

    companion object {
        @Volatile
        private var instance: RoadarDatabase? = null

        fun getInstance(context: Context): RoadarDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    RoadarDatabase::class.java,
                    "roadar-reports.db"
                ).build().also { instance = it }
            }
    }
}
