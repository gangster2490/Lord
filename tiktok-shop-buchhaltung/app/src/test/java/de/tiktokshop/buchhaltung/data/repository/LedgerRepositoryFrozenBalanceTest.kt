package de.tiktokshop.buchhaltung.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import de.tiktokshop.buchhaltung.data.db.AppDatabase
import de.tiktokshop.buchhaltung.data.model.FrozenBalanceEntry
import de.tiktokshop.buchhaltung.data.model.FrozenBalanceStatus
import de.tiktokshop.buchhaltung.data.model.IncomeEntry
import de.tiktokshop.buchhaltung.data.model.IncomeStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Integrationstest gegen eine echte (In-Memory) Room-Datenbank für die separate
 * Frozen-Balance-Erfassung (§ "Eingefrorenen Betrag erfassen"). Deckt insbesondere Punkt 5-9 der
 * Anforderung ab: mehrere Frozen-Einträge müssen möglich sein, Statusänderungen
 * (FROZEN -> AVAILABLE -> PAID_OUT, FROZEN -> REVERSED) dürfen weder die ursprüngliche
 * Einnahme noch den Frozen-Balance-Eintrag selbst duplizieren, und die Statushistorie wird
 * lückenlos protokolliert.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LedgerRepositoryFrozenBalanceTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: LedgerRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = LedgerRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun frozenEntry(status: FrozenBalanceStatus = FrozenBalanceStatus.FROZEN, amountCents: Long = 70261L) =
        FrozenBalanceEntry(
            date = LocalDate.of(2026, 9, 19),
            amountCents = amountCents,
            platform = "TikTok Shop",
            status = status,
            note = "Auszahlung laut TikTok gesperrt",
        )

    @Test
    fun `saving a frozen balance entry never touches income_entries`() = runTest {
        repository.saveNewIncome(
            IncomeEntry(
                date = LocalDate.of(2026, 1, 15),
                platform = "TikTok Shop",
                incomeType = "Provision",
                amountCents = 197697L,
                status = IncomeStatus.ACCRUED,
            ),
        )
        repository.saveNewFrozenBalance(frozenEntry())

        assertThat(repository.getAllIncomes()).hasSize(1)
        assertThat(repository.getAllIncomes().single().amountCents).isEqualTo(197697L)
        assertThat(repository.getAllFrozenBalances()).hasSize(1)
    }

    @Test
    fun `multiple frozen balance entries can coexist`() = runTest {
        repository.saveNewFrozenBalance(frozenEntry(amountCents = 70261L))
        repository.saveNewFrozenBalance(frozenEntry(amountCents = 5000L))

        assertThat(repository.getAllFrozenBalances()).hasSize(2)
        assertThat(repository.getAllFrozenBalances().sumOf { it.amountCents }).isEqualTo(75261L)
    }

    @Test
    fun `status transitions FROZEN to AVAILABLE to PAID_OUT keep a single row and record history`() = runTest {
        val entry = frozenEntry(status = FrozenBalanceStatus.FROZEN)
        repository.saveNewFrozenBalance(entry)

        val afterAvailable = entry.copy(status = FrozenBalanceStatus.AVAILABLE)
        repository.updateFrozenBalance(entry, afterAvailable)

        val afterPaidOut = afterAvailable.copy(status = FrozenBalanceStatus.PAID_OUT)
        repository.updateFrozenBalance(afterAvailable, afterPaidOut)

        val allEntries = repository.getAllFrozenBalances()
        assertThat(allEntries).hasSize(1)
        assertThat(allEntries.single().status).isEqualTo(FrozenBalanceStatus.PAID_OUT)
        assertThat(allEntries.single().amountCents).isEqualTo(70261L)

        val history = repository.getAllFrozenBalanceHistory().sortedBy { it.changedAt }
        assertThat(history).hasSize(3)
        assertThat(history.map { it.newStatus }).containsExactly(
            FrozenBalanceStatus.FROZEN,
            FrozenBalanceStatus.AVAILABLE,
            FrozenBalanceStatus.PAID_OUT,
        ).inOrder()
    }

    @Test
    fun `a reversal (FROZEN to REVERSED) keeps a single row`() = runTest {
        val entry = frozenEntry(status = FrozenBalanceStatus.FROZEN)
        repository.saveNewFrozenBalance(entry)

        repository.updateFrozenBalance(entry, entry.copy(status = FrozenBalanceStatus.REVERSED))

        val allEntries = repository.getAllFrozenBalances()
        assertThat(allEntries).hasSize(1)
        assertThat(allEntries.single().status).isEqualTo(FrozenBalanceStatus.REVERSED)
    }

    @Test
    fun `deleting a frozen balance entry never deletes the underlying income`() = runTest {
        repository.saveNewIncome(
            IncomeEntry(
                date = LocalDate.of(2026, 1, 15),
                platform = "TikTok Shop",
                incomeType = "Provision",
                amountCents = 197697L,
                status = IncomeStatus.ACCRUED,
            ),
        )
        val entry = frozenEntry()
        repository.saveNewFrozenBalance(entry)
        repository.deleteFrozenBalance(entry)

        assertThat(repository.getAllFrozenBalances()).isEmpty()
        assertThat(repository.getAllIncomes()).hasSize(1)
    }
}
