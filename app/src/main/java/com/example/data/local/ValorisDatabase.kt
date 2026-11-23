package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        HoldingEntity::class,
        CachedQuoteEntity::class,
        FxRateEntity::class,
        NetWorthSnapshotEntity::class,
        TransactionEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class ValorisDatabase : RoomDatabase() {
    abstract fun holdingDao(): HoldingDao
    abstract fun quoteDao(): QuoteDao
    abstract fun fxRateDao(): FxRateDao
    abstract fun snapshotDao(): SnapshotDao
    abstract fun transactionDao(): TransactionDao

    companion object {
        @Volatile
        private var INSTANCE: ValorisDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE holdings ADD COLUMN buyDateMillis INTEGER")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cached_quotes ADD COLUMN extendedPriceNative REAL")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `transactions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `holdingId` INTEGER,
                        `symbol` TEXT NOT NULL,
                        `assetName` TEXT NOT NULL,
                        `assetClass` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `quantity` REAL NOT NULL,
                        `unitType` TEXT NOT NULL,
                        `unitPriceNative` REAL NOT NULL,
                        `totalAmountNative` REAL NOT NULL,
                        `nativeCurrency` TEXT NOT NULL,
                        `realizedPnlNative` REAL,
                        `depositedToCash` INTEGER NOT NULL DEFAULT 0,
                        `timestampMillis` INTEGER NOT NULL,
                        `note` TEXT NOT NULL DEFAULT ''
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_timestampMillis` ON `transactions` (`timestampMillis`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_symbol` ON `transactions` (`symbol`)")
            }
        }

        fun getInstance(context: Context): ValorisDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ValorisDatabase::class.java,
                    "valoris_portfolio.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
