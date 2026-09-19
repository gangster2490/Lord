package de.tiktokshop.buchhaltung.ocr

/**
 * Gruppiert OCR-Zeilen zu visuell zusammengehörigen Blöcken (im Idealfall: eine Transaktion
 * pro Block) anhand des vertikalen Abstands zwischen Zeilen. Reine Layout-Heuristik ohne
 * Textverständnis - die Felderkennung pro Block übernimmt [TransactionFieldExtractor].
 *
 * Android-unabhängig (arbeitet nur auf [RecognizedLine]-Koordinaten), damit die
 * Gruppierungslogik ohne Emulator/Gerät mit JUnit getestet werden kann.
 */
object TransactionGrouper {

    /**
     * Zeilen, deren Lücke zueinander größer ist als (Median-Zeilenhöhe * dieser Faktor),
     * gehören zu unterschiedlichen Transaktionen. Ermittelt empirisch anhand typischer
     * Listenlayouts (TikTok Shop / Google Play "Budget & Verlauf"): innerhalb einer
     * Transaktion liegen Zeilen eng beieinander, zwischen Transaktionen ist die Lücke
     * spürbar größer (Listenpadding).
     */
    private const val GAP_MULTIPLIER = 1.6
    private const val MIN_GAP_THRESHOLD_PX = 8

    fun group(lines: List<RecognizedLine>): List<List<RecognizedLine>> {
        if (lines.isEmpty()) return emptyList()
        val sorted = lines.sortedBy { it.top }

        val heights = sorted.map { it.height }.sorted()
        val medianHeight = heights[heights.size / 2]
        val gapThreshold = (medianHeight * GAP_MULTIPLIER).toInt().coerceAtLeast(MIN_GAP_THRESHOLD_PX)

        val groups = mutableListOf<MutableList<RecognizedLine>>()
        var current = mutableListOf(sorted.first())

        for (i in 1 until sorted.size) {
            val previous = sorted[i - 1]
            val line = sorted[i]
            val gap = line.top - previous.bottom
            if (gap > gapThreshold) {
                groups += current
                current = mutableListOf(line)
            } else {
                current += line
            }
        }
        groups += current
        return groups
    }
}
