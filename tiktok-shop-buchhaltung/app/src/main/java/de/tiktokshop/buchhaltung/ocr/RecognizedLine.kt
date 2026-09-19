package de.tiktokshop.buchhaltung.ocr

/**
 * Eine OCR-Zeile mit ihrer vertikalen Position im Bild. Android-unabhängig (nur Int-Koordinaten,
 * keine android.graphics.Rect), damit Gruppierungslogik ohne Instrumentierung testbar ist.
 */
data class RecognizedLine(
    val text: String,
    val top: Int,
    val bottom: Int,
    val left: Int,
) {
    val verticalCenter: Int get() = (top + bottom) / 2
    val height: Int get() = (bottom - top).coerceAtLeast(1)
}

/** Ganzer strukturierter OCR-Output eines Belegs: alle Zeilen + der volle Rohtext. */
data class RecognizedDocument(
    val lines: List<RecognizedLine>,
    val rawText: String,
)
