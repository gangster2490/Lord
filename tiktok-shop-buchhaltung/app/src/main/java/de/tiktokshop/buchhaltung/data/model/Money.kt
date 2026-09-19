package de.tiktokshop.buchhaltung.data.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Geldbeträge werden intern immer als ganzzahlige Cent-Werte gespeichert, damit
 * Rundungsfehler von Fließkommazahlen (z. B. bei Export/Vergleich) ausgeschlossen sind.
 */
typealias Cents = Long

private val GERMAN_LOCALE: Locale = Locale.GERMANY

fun BigDecimal.toCents(): Cents =
    this.setScale(2, RoundingMode.HALF_UP).movePointRight(2).toLong()

fun Cents.toBigDecimal(): BigDecimal =
    BigDecimal(this).movePointLeft(2)

/** Formatiert Cent-Beträge mit deutschem Dezimaltrennzeichen, z. B. 70261L -> "702,61". */
fun Cents.formatGerman(): String {
    val symbols = DecimalFormatSymbols(GERMAN_LOCALE)
    val value = toBigDecimal()
    val sign = if (value.signum() < 0) "-" else ""
    val abs = value.abs()
    val wholePart = abs.toBigInteger().toString()
    val fraction = abs.remainder(BigDecimal.ONE).movePointRight(2).abs().toInt()
    val grouped = wholePart.reversed().chunked(3).joinToString(symbols.groupingSeparator.toString()).reversed()
    return "$sign$grouped${symbols.decimalSeparator}${fraction.toString().padStart(2, '0')}"
}
