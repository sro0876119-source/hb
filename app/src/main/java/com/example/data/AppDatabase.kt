package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.sqlcipher.database.SupportFactory

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Customer::class, LoanCycle::class, WeeklyPayment::class, 
        EditLog::class, CashBalanceLog::class
    ],
    version = 17,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun collectionDao(): CollectionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        @Volatile
        private var IN_MEMORY_INSTANCE: AppDatabase? = null

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add upiNameAlias column to customers table
                db.execSQL("ALTER TABLE customers ADD COLUMN upiNameAlias TEXT NOT NULL DEFAULT ''")
                // Add upiTxnId column to weekly_payments table
                db.execSQL("ALTER TABLE weekly_payments ADD COLUMN upiTxnId TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE customers ADD COLUMN preferredLanguage TEXT NOT NULL DEFAULT 'English'")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE customers ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE loan_cycles ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE weekly_payments ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE audit_logs ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE customers ADD COLUMN lastModified INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE customers ADD COLUMN syncedLastSavedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE loan_cycles ADD COLUMN lastModified INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE weekly_payments ADD COLUMN lastModified INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE customers ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE'")
                db.execSQL("ALTER TABLE weekly_payments ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE'")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE audit_logs RENAME TO edit_logs")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE loan_cycles ADD COLUMN deduction REAL NOT NULL DEFAULT 0.0")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `cash_balance_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `date` INTEGER NOT NULL, 
                        `actualCash` REAL NOT NULL, 
                        `systemCash` REAL NOT NULL, 
                        `collectionAmount` REAL NOT NULL, 
                        `disbursalAmount` REAL NOT NULL, 
                        `expenses` REAL NOT NULL, 
                        `notes` TEXT NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // version 13 to 14 placeholder
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE weekly_payments ADD COLUMN timeVerificationStatus TEXT NOT NULL DEFAULT 'VERIFIED'")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE customers ADD COLUMN phone2 TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_weekly_payments_loanCycleId_status_paymentDate` ON `weekly_payments` (`loanCycleId`, `status`, `paymentDate`)")
                db.execSQL("DROP TABLE IF EXISTS `microfinance_customers`")
                db.execSQL("DROP TABLE IF EXISTS `microfinance_transactions`")
            }
        }


        fun getDatabase(
            context: Context, 
            isDemoMode: Boolean, 
            isReadOnlyUser: Boolean,
            dbPasswordBytes: ByteArray
        ): AppDatabase {
            if (isDemoMode) {
                IN_MEMORY_INSTANCE?.let { return it }
            } else {
                INSTANCE?.let { return it }
            }

            val appContext = context.applicationContext ?: context
            val sqlCipherLoaded = try {
                net.sqlcipher.database.SQLiteDatabase.loadLibs(appContext)
                true
            } catch (t: Throwable) {
                android.util.Log.w("AppDatabase", "SQLCipher native libs not loaded, falling back to framework SQLite", t)
                false
            }

            if (isDemoMode) {
                return IN_MEMORY_INSTANCE ?: synchronized(this) {
                    IN_MEMORY_INSTANCE ?: Room.inMemoryDatabaseBuilder(
                        appContext,
                        AppDatabase::class.java
                    )
                    .fallbackToDestructiveMigration(true)
                    .build().also { IN_MEMORY_INSTANCE = it }
                }
            }

            val dbName = "weekly_finance_collection_db"
            val legacyUserCacheDbName = "weekly_finance_user_cache_db"

            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    // If data was previously written to weekly_finance_user_cache_db before role switch, preserve it
                    try {
                        val primaryDbFile = appContext.getDatabasePath(dbName)
                        val legacyDbFile = appContext.getDatabasePath(legacyUserCacheDbName)
                        if ((!primaryDbFile.exists() || primaryDbFile.length() == 0L) && legacyDbFile.exists() && legacyDbFile.length() > 0L) {
                            primaryDbFile.parentFile?.mkdirs()
                            legacyDbFile.copyTo(primaryDbFile, overwrite = true)
                            val legacyWal = java.io.File(legacyDbFile.path + "-wal")
                            if (legacyWal.exists()) {
                                legacyWal.copyTo(java.io.File(primaryDbFile.path + "-wal"), overwrite = true)
                            }
                            val legacyShm = java.io.File(legacyDbFile.path + "-shm")
                            if (legacyShm.exists()) {
                                legacyShm.copyTo(java.io.File(primaryDbFile.path + "-shm"), overwrite = true)
                            }
                        }
                    } catch (e: Throwable) {
                        android.util.Log.w("AppDatabase", "Legacy DB migration check warning", e)
                    }

                    fun buildRoomDb(useEncryptedFactory: Boolean): AppDatabase {
                        val builder = Room.databaseBuilder(
                            appContext,
                            AppDatabase::class.java,
                            dbName
                        )
                        if (useEncryptedFactory && sqlCipherLoaded) {
                            val factory = SupportFactory(dbPasswordBytes.clone(), null, false)
                            builder.openHelperFactory(factory)
                        }
                        return builder
                            .addMigrations(
                                MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, 
                                MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, 
                                MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
                                MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16,
                                MIGRATION_16_17
                            )
                            .fallbackToDestructiveMigration(true)
                            .fallbackToDestructiveMigrationOnDowngrade(true)
                            .build()
                    }

                    val candidate = try {
                        val db = buildRoomDb(useEncryptedFactory = sqlCipherLoaded)
                        // Verify the database can actually open and query sqlite_master without crashing
                        db.openHelper.writableDatabase
                        db
                    } catch (firstErr: Throwable) {
                        android.util.Log.e("AppDatabase", "Encrypted DB open failed, attempting unencrypted fallback or clean recovery", firstErr)
                        try {
                            val unencryptedDb = buildRoomDb(useEncryptedFactory = false)
                            unencryptedDb.openHelper.writableDatabase
                            unencryptedDb
                        } catch (secondErr: Throwable) {
                            android.util.Log.e("AppDatabase", "Database file corrupted, recreating clean database", secondErr)
                            try {
                                appContext.deleteDatabase(dbName)
                                appContext.deleteDatabase(legacyUserCacheDbName)
                            } catch (_: Throwable) {}
                            try {
                                val recoveredDb = buildRoomDb(useEncryptedFactory = sqlCipherLoaded)
                                recoveredDb.openHelper.writableDatabase
                                recoveredDb
                            } catch (_: Throwable) {
                                buildRoomDb(useEncryptedFactory = false)
                            }
                        }
                    }

                    INSTANCE = candidate
                    candidate
                }
            }
        }

        fun resetDatabaseInstances() {
            synchronized(this) {
                IN_MEMORY_INSTANCE = null
                // Keep INSTANCE alive so active ViewModels and Repositories never hold a closed or orphaned database reference.
            }
        }
    }
}

