package de.tiktokshop.buchhaltung.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Eine TikTok-/Plattform-Provision. `status` und `amountCents` (angezeigte Provision)
 * sind bewusst getrennt von `paidOutAmountCents` (tatsächlich ausgezahlt) - siehe
 * TAX_LOGIC_DE.md: eine angezeigte Provision darf nie automatisch als Auszahlung gelten.
 */
@Entity(tableName = "income_entries")
data class IncomeEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val date: LocalDate,
    val platform: String,
    val incomeType: String,
    val amountCents: Cents,
    val currency: String = "EUR",
    val status: IncomeStatus,
    val payoutDate: LocalDate? = null,
    val paidOutAmountCents: Cents? = null,
    val note: String? = null,
    val receiptUris: List<String> = emptyList(),
    val ocrRawText: String? = null,
    val ocrConfidence: Float? = null,
    val confirmed: Boolean = false,
    val imageHash: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)

/**
 * Eine Betriebsausgabe. `businessAmountCents` ist der anrechenbare Arbeitswert
 * (grossAmountCents * businessUsePercent / 100), siehe TAX_LOGIC_DE.md Beispiel.
 */
@Entity(tableName = "expense_entries")
data class ExpenseEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val date: LocalDate,
    val merchant: String? = null,
    val category: ExpenseCategory,
    val grossAmountCents: Cents,
    val currency: String = "EUR",
    val vatAmountCents: Cents? = null,
    val businessUsePercent: Int = 100,
    val businessAmountCents: Cents,
    val paymentMethod: String? = null,
    val note: String? = null,
    val receiptUris: List<String> = emptyList(),
    val ocrRawText: String? = null,
    val ocrConfidence: Float? = null,
    val confirmed: Boolean = false,
    val imageHash: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
) {
    companion object {
        fun computeBusinessAmountCents(grossAmountCents: Cents, businessUsePercent: Int): Cents =
            Math.round(grossAmountCents * businessUsePercent / 100.0)
    }
}

/** Audit-Trail für Statusänderungen einer Provision (TAX_LOGIC_DE.md, TC05). */
@Entity(tableName = "status_history")
data class StatusHistory(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val incomeEntryId: String,
    val oldStatus: IncomeStatus?,
    val newStatus: IncomeStatus,
    val changedAt: Instant = Instant.now(),
    val note: String? = null,
    val receiptUri: String? = null,
)
