package de.tiktokshop.buchhaltung.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import de.tiktokshop.buchhaltung.data.model.ExpenseEntry
import de.tiktokshop.buchhaltung.data.model.ImportBatch
import de.tiktokshop.buchhaltung.data.model.IncomeEntry
import de.tiktokshop.buchhaltung.data.model.MerchantRule
import de.tiktokshop.buchhaltung.data.model.SourceDocument
import de.tiktokshop.buchhaltung.data.model.StatusHistory

@Database(
    entities = [
        IncomeEntry::class,
        ExpenseEntry::class,
        StatusHistory::class,
        MerchantRule::class,
        SourceDocument::class,
        ImportBatch::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun incomeDao(): IncomeDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun statusHistoryDao(): StatusHistoryDao
    abstract fun merchantRuleDao(): MerchantRuleDao
    abstract fun sourceDocumentDao(): SourceDocumentDao
    abstract fun importBatchDao(): ImportBatchDao

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

        /**
         * TikTok-Excel-Import (§7-9): neue Spalten auf income_entries/expense_entries,
         * Unique-Index für die Dublettenprüfung per TikTok Transaction ID + Earning-Type (§8,
         * siehe Entity-Kommentar zu [de.tiktokshop.buchhaltung.data.model.IncomeEntry] für die
         * per echten Reports verifizierte Begründung für die Kombination statt nur
         * Transaction ID), sowie die Tabellen für importierte Quelldateien und den
         * Import-Verlauf. Alte Daten des Nutzers bleiben vollständig erhalten (§21) - keine
         * destruktive Migration.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE income_entries ADD COLUMN externalTransactionId TEXT")
                db.execSQL("ALTER TABLE income_entries ADD COLUMN externalEarningType TEXT")
                db.execSQL("ALTER TABLE income_entries ADD COLUMN payer TEXT")
                db.execSQL("ALTER TABLE income_entries ADD COLUMN payerCountry TEXT")
                db.execSQL("ALTER TABLE income_entries ADD COLUMN platformExpenseCents INTEGER")
                db.execSQL(
                    "ALTER TABLE income_entries ADD COLUMN activityPhase TEXT NOT NULL DEFAULT 'REGULAR_BUSINESS'",
                )
                db.execSQL("ALTER TABLE income_entries ADD COLUMN taxRelevant INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE income_entries ADD COLUMN sourceDocumentId TEXT")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_income_entries_externalTransactionId_externalEarningType` " +
                        "ON income_entries (externalTransactionId, externalEarningType)",
                )

                db.execSQL(
                    "ALTER TABLE expense_entries ADD COLUMN activityPhase TEXT NOT NULL DEFAULT 'REGULAR_BUSINESS'",
                )
                db.execSQL("ALTER TABLE expense_entries ADD COLUMN taxRelevant INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE expense_entries ADD COLUMN sourceDocumentId TEXT")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `source_documents` (
                        `id` TEXT NOT NULL,
                        `filename` TEXT NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `importDate` INTEGER NOT NULL,
                        `originalUri` TEXT,
                        `localBackupPath` TEXT NOT NULL,
                        `hash` TEXT NOT NULL,
                        `sourceType` TEXT NOT NULL,
                        `notes` TEXT,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `import_batches` (
                        `id` TEXT NOT NULL,
                        `sourceDocumentId` TEXT NOT NULL,
                        `filename` TEXT NOT NULL,
                        `month` TEXT,
                        `importDate` INTEGER NOT NULL,
                        `rowCount` INTEGER NOT NULL,
                        `newTransactionCount` INTEGER NOT NULL,
                        `duplicateCount` INTEGER NOT NULL,
                        `totalIncomeCents` INTEGER NOT NULL,
                        `sourceHash` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
            }
        }

        /**
         * Universeller Import (§1-6 der Vereinfachungs-Vorgabe): `import_batches` bekommt eine
         * eigene Ausgaben-Summe, damit der Import-Verlauf auch generische Excel/CSV-
         * Ausgabenimporte korrekt zusammenfasst (§20) - vorher gab es nur `totalIncomeCents`.
         * Rein additiv (neue Spalte mit DEFAULT 0), keine bestehenden Daten werden verändert
         * oder gelöscht.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE import_batches ADD COLUMN totalExpenseCents INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tiktok_buchhaltung.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { instance = it }
            }
    }
}
