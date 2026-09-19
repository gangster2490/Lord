package de.tiktokshop.buchhaltung

import android.app.Application
import de.tiktokshop.buchhaltung.ai.AiVisionProvider
import de.tiktokshop.buchhaltung.ai.BackendAiVisionProvider
import de.tiktokshop.buchhaltung.ai.BackendConfigStore
import de.tiktokshop.buchhaltung.data.db.AppDatabase
import de.tiktokshop.buchhaltung.data.importer.ImportFileStorage
import de.tiktokshop.buchhaltung.data.receipts.ReceiptStorage
import de.tiktokshop.buchhaltung.data.repository.ImportRepository
import de.tiktokshop.buchhaltung.data.repository.LedgerRepository
import de.tiktokshop.buchhaltung.data.repository.RuleLearningRepository
import de.tiktokshop.buchhaltung.export.BackupManager
import de.tiktokshop.buchhaltung.export.ExportManager
import de.tiktokshop.buchhaltung.ocr.TextRecognizerEngine
import de.tiktokshop.buchhaltung.scan.MultiTransactionExtractor

/**
 * Manuelle Service-Locator-DI (kein Hilt) - für den MVP-Umfang bewusst einfach gehalten.
 * Alle Daten bleiben lokal, keine Cloud-Pflicht (CLAUDE_MASTER_PROMPT.md). Der AI-Vision-
 * Fallback ist die einzige Ausnahme und ruft ausschließlich den selbst konfigurierten
 * Backend-Proxy auf - nie direkt einen AI-Anbieter, nie mit einem im APK verbauten Key.
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
    lateinit var backendConfigStore: BackendConfigStore
        private set
    lateinit var aiVisionProvider: AiVisionProvider
        private set
    lateinit var ruleLearningRepository: RuleLearningRepository
        private set
    lateinit var multiTransactionExtractor: MultiTransactionExtractor
        private set
    lateinit var importFileStorage: ImportFileStorage
        private set
    lateinit var importRepository: ImportRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        repository = LedgerRepository(database)
        receiptStorage = ReceiptStorage(this)
        textRecognizer = TextRecognizerEngine(this)
        exportManager = ExportManager(this)
        backupManager = BackupManager(this, repository)

        backendConfigStore = BackendConfigStore(this)
        aiVisionProvider = BackendAiVisionProvider(this, backendConfigStore)
        ruleLearningRepository = RuleLearningRepository(database.merchantRuleDao())
        multiTransactionExtractor = MultiTransactionExtractor(textRecognizer, aiVisionProvider, ruleLearningRepository)

        importFileStorage = ImportFileStorage(this)
        importRepository = ImportRepository(this, database, importFileStorage)
    }
}
