package de.tiktokshop.buchhaltung.data.repository

import de.tiktokshop.buchhaltung.data.db.AppDatabase
import de.tiktokshop.buchhaltung.data.model.ExpenseEntry
import de.tiktokshop.buchhaltung.data.model.FrozenBalanceEntry
import de.tiktokshop.buchhaltung.data.model.FrozenBalanceStatusHistory
import de.tiktokshop.buchhaltung.data.model.IncomeEntry
import de.tiktokshop.buchhaltung.data.model.StatusHistory
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

/**
 * Einziger Zugriffspunkt auf die Buchungsdaten. Statusänderungen von Einnahmen
 * schreiben immer einen StatusHistory-Eintrag (Audit-Trail, TAX_LOGIC_DE.md).
 */
class LedgerRepository(private val db: AppDatabase) {

    fun observeIncomes(): Flow<List<IncomeEntry>> = db.incomeDao().observeAll()
    fun observeExpenses(): Flow<List<ExpenseEntry>> = db.expenseDao().observeAll()
    fun observeStatusHistory(incomeEntryId: String): Flow<List<StatusHistory>> =
        db.statusHistoryDao().observeForIncome(incomeEntryId)

    suspend fun getAllIncomes(): List<IncomeEntry> = db.incomeDao().getAll()
    suspend fun getAllExpenses(): List<ExpenseEntry> = db.expenseDao().getAll()
    suspend fun getAllStatusHistory(): List<StatusHistory> = db.statusHistoryDao().getAll()

    suspend fun incomesInRange(from: LocalDate, to: LocalDate): List<IncomeEntry> =
        db.incomeDao().getInRange(from, to)

    suspend fun expensesInRange(from: LocalDate, to: LocalDate): List<ExpenseEntry> =
        db.expenseDao().getInRange(from, to)

    /** Legt eine neue Einnahme an und protokolliert den initialen Status. */
    suspend fun saveNewIncome(entry: IncomeEntry) {
        db.incomeDao().upsert(entry)
        db.statusHistoryDao().insert(
            StatusHistory(
                incomeEntryId = entry.id,
                oldStatus = null,
                newStatus = entry.status,
                changedAt = entry.createdAt,
                note = "Erfassung",
                receiptUri = entry.receiptUris.firstOrNull(),
            ),
        )
    }

    /**
     * Aktualisiert eine bestehende Einnahme. Ändert sich der Status, bleibt der
     * ursprüngliche Eintrag erhalten (TC05) und die Historie wird ergänzt.
     */
    suspend fun updateIncome(previous: IncomeEntry, updated: IncomeEntry, newReceiptUri: String? = null) {
        db.incomeDao().update(updated.copy(updatedAt = Instant.now()))
        if (previous.status != updated.status) {
            db.statusHistoryDao().insert(
                StatusHistory(
                    incomeEntryId = updated.id,
                    oldStatus = previous.status,
                    newStatus = updated.status,
                    note = "Statusänderung",
                    receiptUri = newReceiptUri,
                ),
            )
        }
    }

    suspend fun deleteIncome(entry: IncomeEntry) = db.incomeDao().delete(entry)

    suspend fun saveNewExpense(entry: ExpenseEntry) = db.expenseDao().upsert(entry)

    suspend fun updateExpense(entry: ExpenseEntry) =
        db.expenseDao().update(entry.copy(updatedAt = Instant.now()))

    suspend fun deleteExpense(entry: ExpenseEntry) = db.expenseDao().delete(entry)

    /**
     * Für Restore aus einem Backup (TC08): schreibt Buchung/Historie ohne die
     * synthetische "Erfassung"-Historie von [saveNewIncome] zu erzeugen, damit die
     * ursprüngliche Audit-Historie unverändert übernommen wird.
     */
    suspend fun restoreIncome(entry: IncomeEntry) = db.incomeDao().upsert(entry)

    suspend fun restoreExpense(entry: ExpenseEntry) = db.expenseDao().upsert(entry)

    suspend fun restoreStatusHistory(history: StatusHistory) = db.statusHistoryDao().upsert(history)

    // --- Eingefrorene Auszahlungsbeträge (separat von Einnahmen, siehe FrozenBalanceEntry-Kommentar) ---

    fun observeFrozenBalances(): Flow<List<FrozenBalanceEntry>> = db.frozenBalanceDao().observeAll()
    fun observeFrozenBalanceHistory(frozenBalanceEntryId: String): Flow<List<FrozenBalanceStatusHistory>> =
        db.frozenBalanceStatusHistoryDao().observeForEntry(frozenBalanceEntryId)

    suspend fun getAllFrozenBalances(): List<FrozenBalanceEntry> = db.frozenBalanceDao().getAll()
    suspend fun getAllFrozenBalanceHistory(): List<FrozenBalanceStatusHistory> = db.frozenBalanceStatusHistoryDao().getAll()

    /** Legt einen neuen Frozen-Balance-Eintrag an und protokolliert den initialen Status. */
    suspend fun saveNewFrozenBalance(entry: FrozenBalanceEntry) {
        db.frozenBalanceDao().upsert(entry)
        db.frozenBalanceStatusHistoryDao().insert(
            FrozenBalanceStatusHistory(
                frozenBalanceEntryId = entry.id,
                oldStatus = null,
                newStatus = entry.status,
                changedAt = entry.createdAt,
                note = "Erfassung",
            ),
        )
    }

    /**
     * Aktualisiert einen bestehenden Frozen-Balance-Eintrag (z. B. FROZEN -> AVAILABLE). Der
     * Eintrag bleibt derselbe Datensatz (keine Duplizierung des ursprünglichen Earned-Betrags);
     * bei einer Statusänderung wird die Historie ergänzt.
     */
    suspend fun updateFrozenBalance(previous: FrozenBalanceEntry, updated: FrozenBalanceEntry) {
        db.frozenBalanceDao().update(updated.copy(updatedAt = Instant.now()))
        if (previous.status != updated.status) {
            db.frozenBalanceStatusHistoryDao().insert(
                FrozenBalanceStatusHistory(
                    frozenBalanceEntryId = updated.id,
                    oldStatus = previous.status,
                    newStatus = updated.status,
                    note = "Statusänderung",
                ),
            )
        }
    }

    suspend fun deleteFrozenBalance(entry: FrozenBalanceEntry) = db.frozenBalanceDao().delete(entry)

    suspend fun restoreFrozenBalance(entry: FrozenBalanceEntry) = db.frozenBalanceDao().upsert(entry)

    suspend fun restoreFrozenBalanceHistory(history: FrozenBalanceStatusHistory) =
        db.frozenBalanceStatusHistoryDao().upsert(history)
}
