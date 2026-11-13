package com.roadar.app.data.report

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `firebase_sync_state` (
                `id` INTEGER NOT NULL,
                `ownerUid` TEXT,
                `lastSuccessfulSyncUtcMillis` INTEGER,
                `lastErrorCode` TEXT,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `pending_report_deletions` (
                `reportId` TEXT NOT NULL,
                `queuedAtUtcMillis` INTEGER NOT NULL,
                PRIMARY KEY(`reportId`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS " +
                "`index_pending_report_deletions_queuedAtUtcMillis` " +
                "ON `pending_report_deletions` (`queuedAtUtcMillis`)"
        )
        db.execSQL(
            "INSERT OR IGNORE INTO `firebase_sync_state` " +
                "(`id`, `ownerUid`, `lastSuccessfulSyncUtcMillis`, `lastErrorCode`) " +
                "VALUES (1, NULL, NULL, NULL)"
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `pothole_reports` " +
                "ADD COLUMN `syncEligible` INTEGER NOT NULL DEFAULT 0"
        )
    }
}

@Database(
    entities = [
        PotholeReport::class,
        FirebaseSyncState::class,
        PendingReportDeletion::class
    ],
    version = 3,
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
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}
