package de.tiktokshop.buchhaltung.data.xlsx

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/** Eine Tabellenzeile als 0-indexierte Liste von Zellwerten (leere Zellen = ""). */
typealias XlsxRow = List<String>

class XlsxParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Minimaler, Android-kompatibler XLSX-Reader für OOXML SpreadsheetML - bewusst ohne Apache
 * POI: `poi-ooxml` zieht AWT-/ImageIO-Klassen nach, die auf Android nicht existieren, und ist
 * eine häufige Quelle von Laufzeitabstürzen (`NoClassDefFoundError`). Diese Implementierung
 * liest das XLSX-ZIP direkt (Workbook-Relationships, Shared Strings, Sheet-XML) mit den in
 * Java/Android eingebauten `javax.xml.parsers`/`org.w3c.dom`-APIs. Unterstützt genau das, was
 * TikTok-Earnings-Reports enthalten: mehrere benannte Sheets, Shared Strings, Inline-Strings,
 * leere Zellen. Keine Formelauswertung, kein Styling.
 */
object XlsxReader {

    fun listSheetNames(input: InputStream): List<String> {
        val entries = readZipEntries(input)
        val workbookXml = entries["xl/workbook.xml"]
            ?: throw XlsxParseException("xl/workbook.xml fehlt - keine gültige XLSX-Datei.")
        val sheets = parseXml(workbookXml).elementsByLocalName("sheet")
        return sheets.map { it.getAttribute("name") }
    }

    /**
     * Liest ein Sheet nach Namen. Wenn [sheetName] nicht existiert, wird das erste Sheet
     * verwendet, dessen Name nicht wie eine Feldbeschreibungs-/Hilfeseite aussieht (z. B.
     * "Fields explanation"/"Fields explantion") - schützt davor, versehentlich die
     * Erklärungsseite statt der Datenzeilen einzulesen.
     */
    fun readSheet(input: InputStream, sheetName: String): List<XlsxRow> {
        val workbook = openWorkbook(input)
        val name = workbook.sheetNames.firstOrNull { it.equals(sheetName, ignoreCase = true) }
            ?: workbook.sheetNames.firstOrNull { !it.contains("field", true) && !it.contains("explan", true) }
            ?: workbook.sheetNames.firstOrNull()
            ?: throw XlsxParseException("Kein verwendbares Sheet in der Datei gefunden.")
        return workbook.readSheet(name)
    }

    /**
     * Liest ALLE Sheets einer Arbeitsmappe in einem Durchgang (für den
     * `UniversalSpreadsheetImporter`, der nicht auf einen bestimmten Sheet-Namen angewiesen
     * ist, sondern jedes Sheet nach verwertbaren Kopfzeilen durchsucht, §2).
     */
    fun readAllSheets(input: InputStream): Map<String, List<XlsxRow>> {
        val workbook = openWorkbook(input)
        return workbook.sheetNames.associateWith { name -> workbook.readSheet(name) }
    }

    private fun openWorkbook(input: InputStream): OpenWorkbook {
        val entries = readZipEntries(input)
        val workbookXml = entries["xl/workbook.xml"]
            ?: throw XlsxParseException("xl/workbook.xml fehlt - keine gültige XLSX-Datei.")
        val relsXml = entries["xl/_rels/workbook.xml.rels"]
            ?: throw XlsxParseException("xl/_rels/workbook.xml.rels fehlt - keine gültige XLSX-Datei.")

        val workbookDoc = parseXml(workbookXml)
        val relsDoc = parseXml(relsXml)
        val sharedStrings = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings) ?: emptyList()

        val sheetNameToRelId = workbookDoc.elementsByLocalName("sheet").mapNotNull { el ->
            val name = el.getAttribute("name")
            val relId = el.getAttribute("r:id").ifBlank { el.getAttribute("id") }
            if (name.isBlank() || relId.isBlank()) null else name to relId
        }

        return OpenWorkbook(entries, relsDoc, sharedStrings, sheetNameToRelId)
    }

    private class OpenWorkbook(
        private val entries: Map<String, ByteArray>,
        private val relsDoc: Document,
        private val sharedStrings: List<String>,
        private val sheetNameToRelId: List<Pair<String, String>>,
    ) {
        val sheetNames: List<String> get() = sheetNameToRelId.map { it.first }

        fun readSheet(name: String): List<XlsxRow> {
            val relId = sheetNameToRelId.firstOrNull { it.first == name }?.second
                ?: throw XlsxParseException("Sheet '$name' nicht gefunden.")
            val target = findRelationshipTarget(relsDoc, relId)
                ?: throw XlsxParseException("Relationship '$relId' nicht in workbook.xml.rels gefunden.")
            val sheetPath = normalizeTarget(target)
            val sheetXml = entries[sheetPath]
                ?: throw XlsxParseException("Sheet-Datei '$sheetPath' fehlt im Archiv.")
            return parseSheetRows(parseXml(sheetXml), sharedStrings)
        }
    }

    private fun readZipEntries(input: InputStream): Map<String, ByteArray> {
        val map = mutableMapOf<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    map[entry.name] = zip.readBytes()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        if (map.isEmpty()) throw XlsxParseException("Datei ist leer oder kein gültiges XLSX (ZIP-Archiv).")
        return map
    }

    /**
     * Namespace-AWARE (wichtig!): OOXML-Dateien unterschiedlicher Erzeuger-Tools schreiben die
     * SpreadsheetML-Elemente teils mit explizitem Namespace-Präfix (z. B. `<x:row>` mit
     * `xmlns:x="http://schemas.openxmlformats.org/spreadsheetml/2006/main"`), teils ohne
     * Präfix über die Default-Namespace-Deklaration (`<row xmlns="...">`, so wie es z. B.
     * openpyxl erzeugt). Mit `isNamespaceAware = false` UND einem literalen Tag-Namen wie
     * `getElementsByTagName("row")` werden präfixierte Elemente NIE gefunden, weil der
     * Parser dann den Tag-Namen wörtlich inklusive Präfix vergleicht ("x:row" != "row") -
     * das hat reale, korrekt formatierte Excel-Dateien mit Präfix komplett zum Scheitern
     * gebracht ("keine Tabellendaten", obwohl die Datei eine normale Tabelle enthielt). Mit
     * Namespace-Awareness + [elementsByLocalName] (Namespace-Wildcard `"*"`) funktionieren
     * beide Schreibweisen gleichermaßen.
     */
    private fun parseXml(bytes: ByteArray): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        return try {
            factory.newDocumentBuilder().parse(bytes.inputStream())
        } catch (e: Exception) {
            throw XlsxParseException("XML in der XLSX-Datei konnte nicht gelesen werden: ${e.message}", e)
        }
    }

    /** Findet Elemente anhand ihres lokalen Namens, unabhängig von einem eventuellen Namespace-Präfix. */
    private fun Document.elementsByLocalName(localName: String): List<Element> {
        val nodes = getElementsByTagNameNS("*", localName)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun Element.elementsByLocalName(localName: String): List<Element> {
        val nodes = getElementsByTagNameNS("*", localName)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun findRelationshipTarget(relsDoc: Document, relId: String): String? {
        val rels = relsDoc.elementsByLocalName("Relationship")
        return rels.firstOrNull { it.getAttribute("Id") == relId }?.getAttribute("Target")
    }

    private fun normalizeTarget(target: String): String = when {
        target.startsWith("/xl/") -> target.removePrefix("/")
        target.startsWith("xl/") -> target
        else -> "xl/$target"
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        return parseXml(bytes).elementsByLocalName("si").map { extractText(it) }
    }

    /** `<si>` enthält entweder direkt `<t>` oder mehrere Rich-Text-Runs `<r><t>` - alles zusammenfassen. */
    private fun extractText(si: Element): String {
        val tNodes = si.elementsByLocalName("t")
        return tNodes.joinToString("") { it.textContent }
    }

    /**
     * XLSX lässt komplett leere `<row>`-Elemente in der Sheet-XML oft ganz weg (sparse
     * Repräsentation) - "Zeile 5" existiert im XML schlicht nicht, wenn sie leer ist. Die
     * Zeilen dürfen deshalb NICHT nach ihrer Position im XML indiziert werden, sondern müssen
     * anhand ihres `r`-Attributs (1-basierte echte Zeilennummer) einsortiert werden - exakt
     * dieselbe Sparse-Logik wie bei Spalten (siehe `columnIndexFromRef`/`cellsByIndex`).
     */
    private fun parseSheetRows(sheetDoc: Document, sharedStrings: List<String>): List<XlsxRow> {
        val rowNodes = sheetDoc.elementsByLocalName("row")
        val rowsByIndex = sortedMapOf<Int, XlsxRow>()
        var maxRowIndex = -1

        for ((i, rowEl) in rowNodes.withIndex()) {
            val rowIndex = (rowEl.getAttribute("r").toIntOrNull() ?: (i + 1)) - 1
            if (rowIndex < 0) continue

            val cellNodes = rowEl.elementsByLocalName("c")
            val cellsByIndex = sortedMapOf<Int, String>()
            var maxColIndex = -1
            for (cellEl in cellNodes) {
                val colIndex = columnIndexFromRef(cellEl.getAttribute("r"))
                if (colIndex < 0) continue
                cellsByIndex[colIndex] = extractCellValue(cellEl, sharedStrings)
                if (colIndex > maxColIndex) maxColIndex = colIndex
            }
            val row = MutableList(maxColIndex + 1) { "" }
            cellsByIndex.forEach { (idx, value) -> row[idx] = value }
            rowsByIndex[rowIndex] = row
            if (rowIndex > maxRowIndex) maxRowIndex = rowIndex
        }

        val rows = MutableList<XlsxRow>(maxRowIndex + 1) { emptyList() }
        rowsByIndex.forEach { (idx, row) -> rows[idx] = row }
        return rows
    }

    private fun extractCellValue(cellEl: Element, sharedStrings: List<String>): String = when (cellEl.getAttribute("t")) {
        "s" -> childText(cellEl, "v")?.toIntOrNull()?.let { sharedStrings.getOrNull(it) } ?: ""
        "inlineStr" -> cellEl.elementsByLocalName("is").firstOrNull()?.let { extractText(it) } ?: ""
        else -> childText(cellEl, "v") ?: ""
    }

    private fun childText(parent: Element, tagName: String): String? =
        parent.elementsByLocalName(tagName).firstOrNull()?.textContent

    /** "C7" -> Spaltenindex 2 (0-basiert). Unterstützt A-Z, AA-ZZ, ... */
    internal fun columnIndexFromRef(ref: String): Int {
        var index = 0
        for (char in ref) {
            if (!char.isLetter()) break
            index = index * 26 + (char.uppercaseChar() - 'A' + 1)
        }
        return index - 1
    }
}
