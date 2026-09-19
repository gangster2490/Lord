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
        val sheets = parseXml(workbookXml).getElementsByTagName("sheet")
        return (0 until sheets.length).map { i -> (sheets.item(i) as Element).getAttribute("name") }
    }

    /**
     * Liest ein Sheet nach Namen. Wenn [sheetName] nicht existiert, wird das erste Sheet
     * verwendet, dessen Name nicht wie eine Feldbeschreibungs-/Hilfeseite aussieht (z. B.
     * "Fields explanation"/"Fields explantion") - schützt davor, versehentlich die
     * Erklärungsseite statt der Datenzeilen einzulesen.
     */
    fun readSheet(input: InputStream, sheetName: String): List<XlsxRow> {
        val entries = readZipEntries(input)
        val workbookXml = entries["xl/workbook.xml"]
            ?: throw XlsxParseException("xl/workbook.xml fehlt - keine gültige XLSX-Datei.")
        val relsXml = entries["xl/_rels/workbook.xml.rels"]
            ?: throw XlsxParseException("xl/_rels/workbook.xml.rels fehlt - keine gültige XLSX-Datei.")

        val workbookDoc = parseXml(workbookXml)
        val relId = findSheetRelId(workbookDoc, sheetName)
            ?: findFallbackSheetRelId(workbookDoc)
            ?: throw XlsxParseException("Kein verwendbares Sheet in der Datei gefunden.")

        val relsDoc = parseXml(relsXml)
        val target = findRelationshipTarget(relsDoc, relId)
            ?: throw XlsxParseException("Relationship '$relId' nicht in workbook.xml.rels gefunden.")
        val sheetPath = normalizeTarget(target)

        val sheetXml = entries[sheetPath]
            ?: throw XlsxParseException("Sheet-Datei '$sheetPath' fehlt im Archiv.")
        val sharedStrings = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings) ?: emptyList()

        return parseSheetRows(parseXml(sheetXml), sharedStrings)
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

    private fun parseXml(bytes: ByteArray): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        return try {
            factory.newDocumentBuilder().parse(bytes.inputStream())
        } catch (e: Exception) {
            throw XlsxParseException("XML in der XLSX-Datei konnte nicht gelesen werden: ${e.message}", e)
        }
    }

    private fun findSheetRelId(workbookDoc: Document, sheetName: String): String? {
        val sheets = workbookDoc.getElementsByTagName("sheet")
        for (i in 0 until sheets.length) {
            val el = sheets.item(i) as Element
            if (el.getAttribute("name").equals(sheetName, ignoreCase = true)) {
                return el.getAttribute("r:id").ifBlank { el.getAttribute("id") }.ifBlank { null }
            }
        }
        return null
    }

    private fun findFallbackSheetRelId(workbookDoc: Document): String? {
        val sheets = workbookDoc.getElementsByTagName("sheet")
        for (i in 0 until sheets.length) {
            val el = sheets.item(i) as Element
            val name = el.getAttribute("name")
            if (!name.contains("field", ignoreCase = true) && !name.contains("explan", ignoreCase = true)) {
                return el.getAttribute("r:id").ifBlank { el.getAttribute("id") }.ifBlank { null }
            }
        }
        if (sheets.length == 0) return null
        return (sheets.item(0) as Element).getAttribute("r:id").ifBlank { null }
    }

    private fun findRelationshipTarget(relsDoc: Document, relId: String): String? {
        val rels = relsDoc.getElementsByTagName("Relationship")
        for (i in 0 until rels.length) {
            val el = rels.item(i) as Element
            if (el.getAttribute("Id") == relId) return el.getAttribute("Target")
        }
        return null
    }

    private fun normalizeTarget(target: String): String = when {
        target.startsWith("/xl/") -> target.removePrefix("/")
        target.startsWith("xl/") -> target
        else -> "xl/$target"
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val siNodes = parseXml(bytes).getElementsByTagName("si")
        return (0 until siNodes.length).map { i -> extractText(siNodes.item(i) as Element) }
    }

    /** `<si>` enthält entweder direkt `<t>` oder mehrere Rich-Text-Runs `<r><t>` - alles zusammenfassen. */
    private fun extractText(si: Element): String {
        val tNodes = si.getElementsByTagName("t")
        val sb = StringBuilder()
        for (i in 0 until tNodes.length) sb.append(tNodes.item(i).textContent)
        return sb.toString()
    }

    /**
     * XLSX lässt komplett leere `<row>`-Elemente in der Sheet-XML oft ganz weg (sparse
     * Repräsentation) - "Zeile 5" existiert im XML schlicht nicht, wenn sie leer ist. Die
     * Zeilen dürfen deshalb NICHT nach ihrer Position im XML indiziert werden, sondern müssen
     * anhand ihres `r`-Attributs (1-basierte echte Zeilennummer) einsortiert werden - exakt
     * dieselbe Sparse-Logik wie bei Spalten (siehe `columnIndexFromRef`/`cellsByIndex`).
     */
    private fun parseSheetRows(sheetDoc: Document, sharedStrings: List<String>): List<XlsxRow> {
        val rowNodes = sheetDoc.getElementsByTagName("row")
        val rowsByIndex = sortedMapOf<Int, XlsxRow>()
        var maxRowIndex = -1

        for (i in 0 until rowNodes.length) {
            val rowEl = rowNodes.item(i) as Element
            val rowIndex = (rowEl.getAttribute("r").toIntOrNull() ?: (i + 1)) - 1
            if (rowIndex < 0) continue

            val cellNodes = rowEl.getElementsByTagName("c")
            val cellsByIndex = sortedMapOf<Int, String>()
            var maxColIndex = -1
            for (j in 0 until cellNodes.length) {
                val cellEl = cellNodes.item(j) as Element
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
        "inlineStr" -> cellEl.getElementsByTagName("is").let { nodes ->
            if (nodes.length > 0) extractText(nodes.item(0) as Element) else ""
        }
        else -> childText(cellEl, "v") ?: ""
    }

    private fun childText(parent: Element, tagName: String): String? {
        val nodes = parent.getElementsByTagName(tagName)
        return if (nodes.length > 0) nodes.item(0).textContent else null
    }

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
