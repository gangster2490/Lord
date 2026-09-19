package de.tiktokshop.buchhaltung.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Status eines eingefrorenen/zurückgehaltenen Auszahlungsbetrags. Bewusst eine eigene Enum,
 * getrennt von [IncomeStatus]: eine [FrozenBalanceEntry] klassifiziert nur nachträglich, wie
 * viel eines bereits als Earned gezählten Betrags aktuell zurückgehalten/verfügbar/ausgezahlt
 * ist - sie hat keine der einnahmen-spezifischen Zustände (ACCRUED/REFUNDED).
 */
enum class FrozenBalanceStatus(val label: String) {
    FROZEN("Eingefroren"),
    AVAILABLE("Verfügbar"),
    PAID_OUT("Ausgezahlt"),
    REVERSED("Storniert"),
}

/**
 * Ein vom TikTok-Auszahlungsstatus zurückgehaltener/eingefrorener Betrag (z. B. "Payout on
 * hold"). WICHTIG: Dieser Betrag ist bereits Teil der angezeigten Einnahmen ([IncomeEntry] /
 * "Earned") - eine FrozenBalanceEntry erzeugt NIEMALS eine zusätzliche Einnahme und verändert
 * NIE Einnahmen, Ausgaben oder das Vorläufige Ergebnis ([de.tiktokshop.buchhaltung.domain.DashboardCalculator]
 * bleibt komplett unberührt von dieser Entity). Sie klassifiziert ausschließlich, welcher
 * Anteil der bereits erfassten Provisionen sich aktuell in welcher Auszahlungsphase befindet
 * (FROZEN -> AVAILABLE -> PAID_OUT, oder FROZEN -> REVERSED bei Rücknahme) - siehe
 * [de.tiktokshop.buchhaltung.domain.FrozenBalanceCalculator].
 */
@Entity(tableName = "frozen_balance_entries")
data class FrozenBalanceEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val date: LocalDate,
    val amountCents: Cents,
    val currency: String = "EUR",
    val platform: String = "TikTok Shop",
    val status: FrozenBalanceStatus = FrozenBalanceStatus.FROZEN,
    val note: String? = null,
    /** Screenshot/Beleg als Nachweis der Sperrung (Kamera/Galerie) - siehe [ReceiptStorage]. */
    val receiptUris: List<String> = emptyList(),
    /** Verweist optional auf ein importiertes Dokument (siehe [SourceDocument]). */
    val sourceDocumentId: String? = null,
    /** Optionaler freier Bezug zu Zeitraum/Import, z. B. "Januar 2026" oder ein Import-Batch. */
    val periodReference: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)

/** Audit-Trail für Statusänderungen eines [FrozenBalanceEntry] (analog [StatusHistory]). */
@Entity(tableName = "frozen_balance_status_history")
data class FrozenBalanceStatusHistory(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val frozenBalanceEntryId: String,
    val oldStatus: FrozenBalanceStatus?,
    val newStatus: FrozenBalanceStatus,
    val changedAt: Instant = Instant.now(),
    val note: String? = null,
)
