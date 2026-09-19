package de.tiktokshop.buchhaltung.data.repository

import android.app.Application
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import de.tiktokshop.buchhaltung.data.db.AppDatabase
import de.tiktokshop.buchhaltung.data.importer.ImportFileStorage
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Deckt den NEUEN universellen Import-Pfad ab (§1-6 der Vereinfachungs-Vorgabe:
 * `previewUniversal`/`commitUniversal`, der Produktionscode hinter "Datei / Beleg
 * importieren") - dieselben Szenarien wie [ImportRepositoryTest] (das den älteren,
 * TikTok-only `preview`/`commit`-Pfad testet), plus generische CSV-Ausgabenimporte, deren
 * Dublettenprüfung ohne Transaction ID auskommen muss (§6: Datum+Händler+Betrag+Kategorie).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class UniversalImportRepositoryTest {

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
        tempDir = File(context.cacheDir, "universal_import_test_${System.nanoTime()}").apply { mkdirs() }
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

    private fun uriForCsv(filename: String, content: String): Uri {
        val target = File(tempDir, filename)
        target.writeText(content)
        return Uri.fromFile(target)
    }

    @Test
    fun `previewUniversal and commitUniversal import fixture A as 8 income entries`() = runTest {
        val filename = "fixture_month_a.xlsx"
        val preview = repository.previewUniversal(uriForFixture(filename), filename)

        assertThat(preview.newRows).hasSize(8)
        assertThat(preview.duplicateCount).isEqualTo(0)

        val batch = repository.commitUniversal(preview)
        assertThat(batch.newTransactionCount).isEqualTo(8)
        assertThat(database.incomeDao().getAll()).hasSize(8)
    }

    @Test
    fun `repeated universal import of the same TikTok file creates 0 new duplicates`() = runTest {
        val filename = "fixture_month_a.xlsx"
        repository.commitUniversal(repository.previewUniversal(uriForFixture(filename), filename))
        assertThat(database.incomeDao().getAll()).hasSize(8)

        val secondPreview = repository.previewUniversal(uriForFixture(filename), filename)
        assertThat(secondPreview.newRows).isEmpty()
        assertThat(secondPreview.duplicateCount).isEqualTo(8)

        val secondBatch = repository.commitUniversal(secondPreview)
        assertThat(secondBatch.newTransactionCount).isEqualTo(0)
        assertThat(database.incomeDao().getAll()).hasSize(8) // keine Dubletten
    }

    @Test
    fun `same Transaction ID with two different earning types are both saved via the universal path`() = runTest {
        val filename = "fixture_month_a.xlsx"
        val batch = repository.commitUniversal(repository.previewUniversal(uriForFixture(filename), filename))
        assertThat(batch.newTransactionCount).isEqualTo(8)

        val sharedIdEntries = database.incomeDao().getAll().filter { it.externalTransactionId == "TXN-A-003" }
        assertThat(sharedIdEntries).hasSize(2)
        assertThat(sharedIdEntries.map { it.externalEarningType }).containsExactly("Seller bonus", "Standard commission")
    }

    @Test
    fun `generic expense CSV is imported and a repeated import creates 0 duplicates`() = runTest {
        val csv = """
            Datum;Händler;Betrag;Kategorie
            03.01.2026;CapCut;9,99;Software
            15.02.2026;DHL;4,50;Versand
        """.trimIndent()
        val filename = "ausgaben.csv"

        val firstPreview = repository.previewUniversal(uriForCsv(filename, csv), filename)
        assertThat(firstPreview.newExpenseCount).isEqualTo(2)
        assertThat(firstPreview.duplicateCount).isEqualTo(0)

        val firstBatch = repository.commitUniversal(firstPreview)
        assertThat(firstBatch.newTransactionCount).isEqualTo(2)
        assertThat(database.expenseDao().getAll()).hasSize(2)

        // Erneuter Import derselben Ausgabenliste (z. B. aus Versehen zweimal ausgewählt):
        // Dublettenprüfung über Datum+Händler+Betrag+Kategorie muss greifen, obwohl keine
        // Transaction ID vorhanden ist.
        val secondPreview = repository.previewUniversal(uriForCsv(filename, csv), filename)
        assertThat(secondPreview.newExpenseCount).isEqualTo(0)
        assertThat(secondPreview.duplicateCount).isEqualTo(2)

        repository.commitUniversal(secondPreview)
        assertThat(database.expenseDao().getAll()).hasSize(2) // keine Dubletten
    }
}
