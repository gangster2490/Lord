package de.tiktokshop.buchhaltung.data.repository

import android.app.Application
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import de.tiktokshop.buchhaltung.data.db.AppDatabase
import de.tiktokshop.buchhaltung.data.importer.ImportFileStorage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Integrationstest gegen eine echte (In-Memory) Room-Datenbank und synthetische, aber
 * strukturell echten TikTok-Earnings-Reports nachgebildete Test-Dateien (siehe
 * app/src/test/resources/tiktok_reports/ - anonymisiert, keine echten Finanzdaten im Repo).
 * Deckt genau die in §34/§35 geforderten Szenarien ab: "import January report" (hier: Fixture
 * A), "import same report twice" (keine Dubletten - inkl. der Transaction-ID-mit-zwei-
 * Earning-Types-Eigenheit), "import Jan+Feb files" (hier: Fixture A + B, keine
 * Überschneidung).
 *
 * `application = Application::class` weist Robolectric an, eine schlichte Android-Application
 * zu starten statt der echten [de.tiktokshop.buchhaltung.TiktokBuchhaltungApplication] - diese
 * initialisiert in `onCreate()` u. a. ML Kit, was unter Robolectric ohne Play-Services-Shadow
 * fehlschlägt und für diesen reinen Datenbank-/Import-Test auch nicht gebraucht wird.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ImportRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: ImportRepository
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ImportRepository(context, database, ImportFileStorage(context))
        tempDir = File(context.cacheDir, "xlsx_test_${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        database.close()
        tempDir.deleteRecursively()
    }

    private fun uriForFixture(fixtureFile: String): Uri {
        val resourceStream = requireNotNull(javaClass.classLoader?.getResourceAsStream("tiktok_reports/$fixtureFile"))
        val target = File(tempDir, fixtureFile)
        resourceStream.use { input -> target.outputStream().use { input.copyTo(it) } }
        return Uri.fromFile(target)
    }

    @Test
    fun `importing fixture A creates 8 income entries (one row skipped for blank Income)`() = runTest {
        val filename = "fixture_month_a.xlsx"
        val preview = repository.preview(uriForFixture(filename), filename)

        assertThat(preview.newTransactionCount).isEqualTo(8)
        assertThat(preview.duplicateCount).isEqualTo(0)
        assertThat(preview.parseErrors).hasSize(1)

        val batch = repository.commit(preview)

        assertThat(batch.newTransactionCount).isEqualTo(8)
        val stored = database.incomeDao().getAll()
        assertThat(stored).hasSize(8)
        assertThat(stored.sumOf { it.amountCents }).isEqualTo(1806L)

        // Die geteilte Transaction ID (TXN-A-003) darf NICHT kollabiert worden sein - beide
        // Earning-Types (Seller bonus + Standard commission) müssen als eigene Zeilen da sein.
        val sharedIdEntries = stored.filter { it.externalTransactionId == "TXN-A-003" }
        assertThat(sharedIdEntries).hasSize(2)
        assertThat(sharedIdEntries.map { it.externalEarningType })
            .containsExactly("Seller bonus", "Standard commission")
    }

    @Test
    fun `importing the same fixture twice does not create duplicates`() = runTest {
        val filename = "fixture_month_a.xlsx"

        repository.commit(repository.preview(uriForFixture(filename), filename))
        assertThat(database.incomeDao().getAll()).hasSize(8)

        // Zweiter Import derselben Datei: Vorschau muss 0 neue / 8 bereits vorhandene zeigen.
        val secondPreview = repository.preview(uriForFixture(filename), filename)
        assertThat(secondPreview.newTransactionCount).isEqualTo(0)
        assertThat(secondPreview.duplicateCount).isEqualTo(8)

        val secondBatch = repository.commit(secondPreview)
        assertThat(secondBatch.newTransactionCount).isEqualTo(0)

        // Keine Dubletten in der Datenbank - immer noch genau 8 Einnahmen.
        assertThat(database.incomeDao().getAll()).hasSize(8)
        // Aber beide Importversuche werden protokolliert (Import-Verlauf, §8).
        assertThat(database.importBatchDao().observeAll().first()).hasSize(2)
        assertThat(database.sourceDocumentDao().getAll()).hasSize(1) // dieselbe Datei -> nur 1 SourceDocument
    }

    @Test
    fun `importing fixture A then fixture B keeps both without cross-contamination`() = runTest {
        val fileA = "fixture_month_a.xlsx"
        val fileB = "fixture_month_b.xlsx"

        val batchA = repository.commit(repository.preview(uriForFixture(fileA), fileA))
        assertThat(batchA.newTransactionCount).isEqualTo(8)
        assertThat(database.incomeDao().getAll()).hasSize(8)

        val previewB = repository.preview(uriForFixture(fileB), fileB)
        assertThat(previewB.duplicateCount).isEqualTo(0)
        assertThat(previewB.newTransactionCount).isEqualTo(3)

        val batchB = repository.commit(previewB)
        assertThat(batchB.newTransactionCount).isEqualTo(3)

        val all = database.incomeDao().getAll()
        assertThat(all).hasSize(8 + 3)
        assertThat(all.sumOf { it.amountCents }).isEqualTo(1806L + 666L)
    }
}
