package de.tiktokshop.buchhaltung

import android.app.Application
import de.tiktokshop.buchhaltung.data.db.AppDatabase
import de.tiktokshop.buchhaltung.data.receipts.ReceiptStorage
import de.tiktokshop.buchhaltung.data.repository.LedgerRepository
import de.tiktokshop.buchhaltung.export.BackupManager
import de.tiktokshop.buchhaltung.export.ExportManager
import de.tiktokshop.buchhaltung.ocr.TextRecognizerEngine

/**
 * Manuelle Service-Locator-DI (kein Hilt) - für den MVP-Umfang bewusst einfach gehalten.
 * Alle Daten bleiben lokal, keine Cloud-Pflicht (CLAUDE_MASTER_PROMPT.md).
 */
class TiktokBuchhaltungApplication : Application() {

    lateinit var database: AppDatabase
        private set
    lateinit var repository: LedgerRepository
        private set
    lateinit var receiptStorage: ReceiptStorage
        private set
    lateinit var textRecognizer: TextRecognizerEngine
        private set
    lateinit var exportManager: ExportManager
        private set
    lateinit var backupManager: BackupManager
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        repository = LedgerRepository(database)
        receiptStorage = ReceiptStorage(this)
        textRecognizer = TextRecognizerEngine(this)
        exportManager = ExportManager(this)
        backupManager = BackupManager(this, repository)
    }
}
