package de.tiktokshop.buchhaltung.data.importer

import de.tiktokshop.buchhaltung.data.model.Cents
import java.time.LocalDate

/**
 * Eine geparste Zeile aus einem TikTok-Shop-Earnings-Report - noch keine [de.tiktokshop.buchhaltung.data.model.IncomeEntry],
 * da sie erst nach Dublettenprüfung/Bestätigung als solche gespeichert wird (§7-9).
 */
data class TikTokEarningsRow(
    val rowNumber: Int,
    val transactionDate: LocalDate,
    val externalTransactionId: String,
    val earningType: String,
    val incomeType: String,
    val currency: String,
    val grossAmountCents: Cents,
    val platformExpenseCents: Cents?,
    val payer: String?,
    val payerCountry: String?,
)

/** Eine Zeile, die nicht verarbeitet werden konnte (z. B. Datum/Betrag fehlt) - wird dem Nutzer angezeigt statt stillschweigend übersprungen. */
data class TikTokEarningsRowError(val rowNumber: Int, val reason: String)

data class TikTokEarningsParseResult(
    val period: String?,
    val creatorName: String?,
    val rows: List<TikTokEarningsRow>,
    val errors: List<TikTokEarningsRowError>,
)
