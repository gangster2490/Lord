package de.tiktokshop.buchhaltung.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import de.tiktokshop.buchhaltung.data.model.ExpenseEntry
import de.tiktokshop.buchhaltung.data.model.IncomeEntry
import de.tiktokshop.buchhaltung.data.model.MerchantRule
import de.tiktokshop.buchhaltung.data.model.StatusHistory

@Database(
    entities = [IncomeEntry::class, ExpenseEntry::class, StatusHistory::class, MerchantRule::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun incomeDao(): IncomeDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun statusHistoryDao(): StatusHistoryDao
    abstract fun merchantRuleDao(): MerchantRuleDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /** Fügt die "gelernten Regeln"-Tabelle hinzu (Multi-Transaktions-Scan, Rule Learning). */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `merchant_rules` (
                        `merchantKey` TEXT NOT NULL,
                        `entryTypeName` TEXT NOT NULL,
                        `categoryName` TEXT,
                        `incomeType` TEXT,
                        `businessUsePercent` INTEGER,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`merchantKey`, `entryTypeName`)
                    )
                    """.trimIndent(),
                )
            }
        }

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tiktok_buchhaltung.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
