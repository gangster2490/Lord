package de.tiktokshop.buchhaltung.data.importer

import de.tiktokshop.buchhaltung.data.model.EntryType
import de.tiktokshop.buchhaltung.data.xlsx.XlsxParseException
import de.tiktokshop.buchhaltung.data.xlsx.XlsxReader
import de.tiktokshop.buchhaltung.data.xlsx.XlsxRow
import de.tiktokshop.buchhaltung.ocr.AmountParser
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Universeller Tabellen-Importer (§1,2,3,21 der Vereinfachungs-Vorgabe): akzeptiert .xlsx UND
 * .csv, verlangt KEINEN bestimmten Sheet-Namen, durchsucht ALLE Sheets nach einer verwertbaren
 * Kopfzeile und erkennt Einnahme/Ausgabe automatisch an der Spaltensignatur (Income-Spalte ->
 * Einnahme, Expense-Spalte -> Ausgabe). Ein ganz normales Excel/CSV mit nur einer generischen
 * "Betrag"-Spalte (kein Income/Expense-Split) wird per Vorzeichen eingeordnet (negativ =
 * Ausgabe, sonst Einnahme) - das ist nur eine Vermutung, die der Nutzer im Import-Preview für
 * die ganze Datei umschalten kann (siehe [ImportCandidateRow.isAmbiguousType]).
 *
 * TikTok-Earnings-Reports (Kopfzeile mit "Date (UTC+0)" + "Transaction ID" + "Type of
 * earnings") werden NICHT neu geparst, sondern an den bestehenden, bereits gegen echte Reports
 * verifizierten [TikTokExcelParser] delegiert - das bewahrt die bisherige, funktionierende
 * TikTok-Logik (Sparse-Row-Handling, geteilte Transaction-ID/Earning-Type-Paare) unverändert.
 *
 * Wenn für ein Sheet weder Datum noch ein Betragsfeld sicher erkannt werden, wird das Sheet
 * NICHT stillschweigend übersprungen, sondern als nicht "confident" markiert - die UI zeigt
 * dann den "Spalten zuordnen"-Screen (§4) an, statt einen Fehler zu werfen. Das gilt auch dann,
 * wenn ÜBERHAUPT KEINE Spalte automatisch erkannt wurde: solange irgendeine nicht-leere Zeile
 * existiert, wird sie als Rohkopf für die manuelle Zuordnung angeboten (§1/§6 - "Import
 * fehlgeschlagen" darf nicht die erste Reaktion auf eine unbekannte Kopfzeile sein). Ein
 * echter Fehler wird nur ausgelöst, wenn die Datei nach Durchsuchen ALLER Sheets wirklich
 * keine einzige nicht-leere Zeile enthält (§21).
 */
object UniversalSpreadsheetImporter {

    // Reale, "hübsch" formatierte Excel-Exporte (Titelzeile, Disclaimer, Legende, Leerzeilen vor
    // der eigentlichen Tabelle) können die Kopfzeile deutlich nach unten schieben - 15 Zeilen
    // waren dafür zu wenig und haben echte Nutzerdateien fälschlich als "keine Tabellendaten"
    // abgelehnt, obwohl die Kopfzeile nur weiter unten stand.
    private const val HEADER_SEARCH_ROWS = 100
    private val DATE_FORMATS = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("dd.MM.yyyy"),
        DateTimeFormatter.ofPattern("dd.MM.yy"),
        DateTimeFormatter.ofPattern("d.M.yyyy"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
    )

    fun analyze(bytes: ByteArray, filename: String): UniversalImportResult {
        if (bytes.isEmpty()) throw ImportException("„$filename“ ist leer.")
        val xlsx = isXlsx(bytes)

        val sheetsData: Map<String, List<XlsxRow>> = if (xlsx) {
            try {
                XlsxReader.readAllSheets(bytes.inputStream())
            } catch (e: XlsxParseException) {
                throw ImportException("„$filename“ konnte nicht als Tabelle gelesen werden: ${e.message}", e)
            }
        } else {
            val csvRows = CsvReader.read(bytes.inputStream())
            if (csvRows.isEmpty()) throw ImportException("„$filename“ enthält keine lesbaren CSV-Zeilen.")
            mapOf("CSV" to csvRows)
        }

        val sheetAnalyses = mutableListOf<SheetAnalysis>()
        val allRows = mutableListOf<ImportCandidateRow>()
        val allErrors = mutableListOf<ImportRowError>()
        var tikTokSheetHandled = false

        for ((sheetName, rawRows) in sheetsData) {
            if (rawRows.isEmpty()) continue
            val analysis = analyzeSheet(sheetName, rawRows) ?: continue
            sheetAnalyses += analysis

            when {
                xlsx && !tikTokSheetHandled && isTikTokEarningsSheet(analysis.headers) -> {
                    tikTokSheetHandled = true
                    val (rows, errors) = parseAsTikTokReport(bytes, sheetName)
                    allRows += rows
                    allErrors += errors
                }
                analysis.isConfident -> {
                    val (rows, errors) = extractRows(sheetName, rawRows, analysis.headerRowIndex, analysis.columnMapping)
                    allRows += rows
                    allErrors += errors
                }
            }
        }

        if (sheetAnalyses.isEmpty()) {
            // Nur hier: nicht "Kopfzeile nicht erkannt" (dafür gibt es "Spalten zuordnen"),
            // sondern wirklich JEDE Zeile in JEDEM Sheet ist leer.
            throw ImportException(
                "„$filename“ enthält keine Daten (alle Sheets sind leer). " +
                    "Bitte eine Datei mit mindestens einer ausgefüllten Zeile wählen.",
            )
        }

        return UniversalImportResult(filename, sheetAnalyses, allRows, allErrors, sheetsData)
    }

    /**
     * Extrahiert Zeilen für ein Sheet anhand einer Spaltenzuordnung. Wird sowohl automatisch
     * (aus [analyzeSheet]) als auch für die vom Nutzer im "Spalten zuordnen"-Screen manuell
     * überschriebene Zuordnung verwendet (§4 "Vorschau"-Button ruft exakt diese Funktion auf).
     */
    fun extractRows(
        sheetName: String,
        rawRows: List<XlsxRow>,
        headerRowIndex: Int,
        mapping: Map<ColumnRole, Int>,
    ): Pair<List<ImportCandidateRow>, List<ImportRowError>> {
        val dateCol = mapping[ColumnRole.DATE]
        val incomeCol = mapping[ColumnRole.AMOUNT_INCOME]
        val expenseCol = mapping[ColumnRole.AMOUNT_EXPENSE]
        val genericCol = mapping[ColumnRole.AMOUNT_GENERIC]
        val merchantCol = mapping[ColumnRole.MERCHANT]
        val categoryCol = mapping[ColumnRole.CATEGORY]
        val currencyCol = mapping[ColumnRole.CURRENCY]
        val idCol = mapping[ColumnRole.TRANSACTION_ID]
        val businessPercentCol = mapping[ColumnRole.BUSINESS_PERCENT]
        val statusCol = mapping[ColumnRole.STATUS]
        val sourceCol = mapping[ColumnRole.SOURCE]
        val noteCol = mapping[ColumnRole.NOTE]

        if (dateCol == null || (incomeCol == null && expenseCol == null && genericCol == null)) {
            return emptyList<ImportCandidateRow>() to emptyList()
        }

        val rows = mutableListOf<ImportCandidateRow>()
        val errors = mutableListOf<ImportRowError>()

        for (i in headerRowIndex + 1 until rawRows.size) {
            val row = rawRows[i]
            val rowNumber = i + 1
            if (row.all { it.isBlank() }) continue

            val dateText = row.getOrNull(dateCol)?.trim().orEmpty()
            if (dateText.isBlank()) continue

            val date = parseFlexibleDate(dateText)
            if (date == null) {
                errors += ImportRowError(sheetName, rowNumber, "Ungültiges Datum: '$dateText'")
                continue
            }

            val incomeText = incomeCol?.let { row.getOrNull(it)?.trim() }.orEmpty()
            val expenseText = expenseCol?.let { row.getOrNull(it)?.trim() }.orEmpty()
            val genericText = genericCol?.let { row.getOrNull(it)?.trim() }.orEmpty()

            // Explizite Income-/Expense-Spalten sind eindeutig. Eine generische "Betrag"-Spalte
            // (z. B. ein ganz normales Excel mit nur Datum+Betrag, egal ob Einnahmen oder
            // Ausgaben) hat keinen Spalten-Hinweis auf den Typ - zuerst zählt ein negatives
            // Vorzeichen bzw. eine Klammerschreibweise "(12,00)" als Ausgabe; ist der Betrag
            // positiv, hilft der Sheet-Name als zusätzliches Signal (ein Sheet/Reiter namens
            // "Ausgaben_2026" ist eindeutig eine Ausgabenliste, auch ohne eigene Expense-
            // Spalte). Ohne jeden Hinweis gilt "positiv = Einnahme". Das bleibt in jedem Fall
            // nur eine Vermutung (isAmbiguousType = true), die der Nutzer im Import-Preview für
            // die ganze Datei umschalten kann (§3).
            val (type, amountText, isAmbiguous) = when {
                expenseText.isNotBlank() -> Triple(EntryType.EXPENSE, expenseText, false)
                incomeText.isNotBlank() -> Triple(EntryType.INCOME, incomeText, false)
                genericText.isNotBlank() -> {
                    val trimmed = genericText.trim()
                    val looksNegative = trimmed.startsWith("-") || (trimmed.startsWith("(") && trimmed.endsWith(")"))
                    val normalizedSheetName = HeaderMatcher.normalize(sheetName)
                    val guessedType = when {
                        looksNegative -> EntryType.EXPENSE
                        normalizedSheetName.contains("ausgabe") -> EntryType.EXPENSE
                        normalizedSheetName.contains("einnahme") -> EntryType.INCOME
                        else -> EntryType.INCOME
                    }
                    Triple(guessedType, genericText, true)
                }
                else -> Triple(null, "", false)
            }
            if (type == null) {
                if (incomeCol != null || expenseCol != null || genericCol != null) {
                    errors += ImportRowError(sheetName, rowNumber, "Kein Betrag gefunden.")
                }
                continue
            }

            val parsedCents = runCatching { AmountParser.parseSingleAmountToCents(amountText.trim().removeSurrounding("(", ")")) }.getOrNull()
            if (parsedCents == null) {
                errors += ImportRowError(sheetName, rowNumber, "Ungültiger Betrag: '$amountText'")
                continue
            }

            val businessPercentText = businessPercentCol?.let { row.getOrNull(it)?.trim() }
            val businessPercent = businessPercentText?.ifBlank { null }
                ?.replace("%", "")?.trim()?.replace(",", ".")?.toDoubleOrNull()?.toInt()

            rows += ImportCandidateRow(
                type = type,
                date = date,
                amountCents = kotlin.math.abs(parsedCents),
                currency = currencyCol?.let { row.getOrNull(it)?.trim()?.ifBlank { null } } ?: "EUR",
                merchant = merchantCol?.let { row.getOrNull(it)?.trim()?.ifBlank { null } },
                category = categoryCol?.let { row.getOrNull(it)?.trim()?.ifBlank { null } },
                externalTransactionId = idCol?.let { row.getOrNull(it)?.trim()?.ifBlank { null } },
                sourceSheet = sheetName,
                sourceRowNumber = rowNumber,
                isAmbiguousType = isAmbiguous,
                businessPercent = businessPercent,
                status = statusCol?.let { row.getOrNull(it)?.trim()?.ifBlank { null } },
                source = sourceCol?.let { row.getOrNull(it)?.trim()?.ifBlank { null } },
                note = noteCol?.let { row.getOrNull(it)?.trim()?.ifBlank { null } },
            )
        }

        return rows to errors
    }

    /**
     * Sucht über ALLE Kandidatenzeilen hinweg zuerst nach einer VOLLSTÄNDIG sicheren Kopfzeile
     * (Datum + Betrag), bevor auf eine teilweise erkannte Zeile zurückgefallen wird. Das ist
     * wichtig, weil TikTok-Reports vor der echten Kopfzeile Metadatenzeilen wie "Date period"
     * enthalten, die über den "enthält"-Fallback in [HeaderMatcher] fälschlich als DATE-Spalte
     * erkannt würden, wenn die Suche beim ersten Treffer abbrechen würde - die echte, weiter
     * unten stehende Kopfzeile darf dadurch nicht übersehen werden.
     */
    fun analyzeSheet(sheetName: String, rawRows: List<XlsxRow>): SheetAnalysis? {
        var bestPartialMatch: SheetAnalysis? = null
        var firstNonBlankRow: Pair<Int, XlsxRow>? = null

        for (i in 0 until minOf(HEADER_SEARCH_ROWS, rawRows.size)) {
            val candidateHeader = rawRows[i]
            if (candidateHeader.all { it.isBlank() }) continue
            if (firstNonBlankRow == null) firstNonBlankRow = i to candidateHeader

            val mapping = HeaderMatcher.matchColumns(candidateHeader)
            val hasDate = mapping.containsKey(ColumnRole.DATE)
            val hasAmount = mapping.containsKey(ColumnRole.AMOUNT_INCOME) ||
                mapping.containsKey(ColumnRole.AMOUNT_EXPENSE) ||
                mapping.containsKey(ColumnRole.AMOUNT_GENERIC)
            if (hasDate && hasAmount) {
                return SheetAnalysis(sheetName, i, candidateHeader, mapping, isConfident = true)
            }
            if (bestPartialMatch == null && mapping.isNotEmpty()) {
                // Mindestens eine Spalte erkannt (z. B. nur Betrag ohne Datum) - Kandidat für
                // "Spalten zuordnen", aber bewusst NICHT automatisch extrahieren (§4).
                bestPartialMatch = SheetAnalysis(sheetName, i, candidateHeader, mapping, isConfident = false)
            }
        }
        if (bestPartialMatch != null) return bestPartialMatch

        // Selbst wenn KEINE einzige Spalte automatisch erkannt wurde, gilt das Sheet nicht als
        // "keine Tabellendaten" - solange irgendeine nicht-leere Zeile existiert, wird sie als
        // Rohkopf für "Spalten zuordnen" angeboten, damit der Nutzer die Spalten selbst
        // zuordnen kann, statt eine Fehlermeldung zu sehen (§1: nie sofort scheitern lassen).
        // Das Suchfenster für diesen Fallback ist bewusst unbegrenzt (nicht auf
        // HEADER_SEARCH_ROWS beschränkt), damit auch ungewöhnlich viele Titel-/Leerzeilen vor
        // der Tabelle nie zu einem Totalausfall führen.
        val fallbackRow = firstNonBlankRow
            ?: rawRows.withIndex().firstOrNull { (_, row) -> row.any { it.isNotBlank() } }?.let { it.index to it.value }
        return fallbackRow?.let { (idx, header) -> SheetAnalysis(sheetName, idx, header, emptyMap(), isConfident = false) }
    }

    private fun isTikTokEarningsSheet(headers: List<String>): Boolean {
        val normalized = headers.map { HeaderMatcher.normalize(it) }
        return normalized.any { it.contains("date utc") } &&
            normalized.contains("transaction id") &&
            normalized.contains("type of earnings")
    }

    private fun parseAsTikTokReport(
        bytes: ByteArray,
        sheetName: String,
    ): Pair<List<ImportCandidateRow>, List<ImportRowError>> {
        val result = try {
            TikTokExcelParser.parse(bytes.inputStream())
        } catch (e: XlsxParseException) {
            return emptyList<ImportCandidateRow>() to listOf(ImportRowError(sheetName, 0, e.message ?: "TikTok-Report konnte nicht gelesen werden."))
        }
        val rows = result.rows.map { row ->
            ImportCandidateRow(
                type = EntryType.INCOME,
                date = row.transactionDate,
                amountCents = row.grossAmountCents,
                currency = row.currency,
                merchant = row.payer,
                category = row.incomeType,
                externalTransactionId = row.externalTransactionId,
                externalEarningType = row.earningType,
                payer = row.payer,
                payerCountry = row.payerCountry,
                platformExpenseCents = row.platformExpenseCents,
                sourceSheet = sheetName,
                sourceRowNumber = row.rowNumber,
            )
        }
        val errors = result.errors.map { ImportRowError(sheetName, it.rowNumber, it.reason) }
        return rows to errors
    }

    private fun parseFlexibleDate(text: String): LocalDate? {
        for (format in DATE_FORMATS) {
            val parsed = runCatching { LocalDate.parse(text, format) }.getOrNull()
            if (parsed != null) return parsed
        }
        return null
    }

    /** XLSX-Dateien sind ZIP-Archive und beginnen mit der Magic Number "PK". */
    private fun isXlsx(bytes: ByteArray): Boolean =
        bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
}
