package de.tiktokshop.buchhaltung.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Eine TikTok-/Plattform-Provision. `status` und `amountCents` (angezeigte Provision)
 * sind bewusst getrennt von `paidOutAmountCents` (tatsächlich ausgezahlt) - siehe
 * TAX_LOGIC_DE.md: eine angezeigte Provision darf nie automatisch als Auszahlung gelten.
 *
 * `externalTransactionId` ist die TikTok "Transaction ID" aus dem Earnings-Report-Excel.
 * WICHTIG (per echten Reports verifiziert): dieselbe Transaction ID kann in EINER Datei
 * mehrfach vorkommen, z. B. einmal als "Seller bonus" und einmal als "Standard commission"
 * für denselben zugrunde liegenden Verkauf - beides sind eigenständige, echte Einnahmen. Die
 * Dublettenprüfung darf sich deshalb NICHT allein auf `externalTransactionId` stützen (das
 * würde eine der beiden Zeilen beim Import stillschweigend verwerfen), sondern auf die
 * Kombination aus `externalTransactionId` + `externalEarningType` (§8 - UNIQUE INDEX auf
 * beide Spalten zusammen; mehrere NULL-Kombinationen sind in SQLite erlaubt, betrifft also
 * nicht manuell erfasste Einnahmen ohne Transaction ID).
 */
@Entity(
    tableName = "income_entries",
    indices = [Index(value = ["externalTransactionId", "externalEarningType"], unique = true)],
)
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
    /** TikTok "Transaction ID" aus dem Earnings-Report - Grundlage der Dublettenprüfung (zusammen mit [externalEarningType]). */
    val externalTransactionId: String? = null,
    /** Rohes TikTok "Type of earnings" (z. B. "Standard commission", "Seller bonus") - siehe Klassenkommentar. */
    val externalEarningType: String? = null,
    /** TikTok "Payer" / "Payer country" aus dem Earnings-Report (nur bei Excel-Import gesetzt). */
    val payer: String? = null,
    val payerCountry: String? = null,
    /** TikTok "Expense"-Spalte: vom Payout abgezogener Betrag (z. B. Gebühren), nicht mit Ausgaben verwechseln. */
    val platformExpenseCents: Cents? = null,
    val activityPhase: ActivityPhase = ActivityPhase.REGULAR_BUSINESS,
    /** Entspricht der "Berücksichtigt"-Spalte in der Steuerübersicht - false = bewusst ausgeklammert. */
    val taxRelevant: Boolean = true,
    /** Verweist auf das importierte Excel/den Beleg, aus dem dieser Eintrag stammt (siehe [SourceDocument]). */
    val sourceDocumentId: String? = null,
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
    val activityPhase: ActivityPhase = ActivityPhase.REGULAR_BUSINESS,
    /** Entspricht der "Berücksichtigt"-Spalte in der Steuerübersicht - false = bewusst ausgeklammert. */
    val taxRelevant: Boolean = true,
    /** Verweist auf das importierte Excel/den Beleg, aus dem dieser Eintrag stammt (siehe [SourceDocument]). */
    val sourceDocumentId: String? = null,
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
