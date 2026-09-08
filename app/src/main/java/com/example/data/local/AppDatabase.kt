package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        EventEntity::class,
        CoursePackageEntity::class,
        OfflineSyncQueueEntity::class,
        AuditLogEntity::class,
        ChatMessageEntity::class,
        UserAccountEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun coursePackageDao(): CoursePackageDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun auditDao(): AuditDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun userAccountDao(): UserAccountDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Real v2 -> v3 migration:
         *  - creates the `user_accounts` table (Google sign-in profile)
         *  - adds the `prevHash` column that turns the audit log into a
         *    tamper-evident hash chain
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `user_accounts` (" +
                        "`userId` TEXT NOT NULL, " +
                        "`displayName` TEXT NOT NULL, " +
                        "`email` TEXT NOT NULL, " +
                        "`photoUrl` TEXT, " +
                        "`active` INTEGER NOT NULL, " +
                        "`updatedAtMs` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`userId`))"
                )
                db.execSQL("ALTER TABLE `audit_log` ADD COLUMN `prevHash` TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "olevel_runtime.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    // v1 was a pre-release development database (no integrity
                    // guarantees); dropping it is acceptable and documented.
                    .fallbackToDestructiveMigrationFrom(1)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
