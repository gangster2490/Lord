package com.rob.aikeyboard.text

/**
 * TikTok mode: puts an invisible zero-width space after the first letter of words that start with a
 * listed stem ("цен" → цена, цены; "куп" → купить), so keyword filters that match plain text no longer
 * see the word while it still reads the same. There is no guarantee any given platform ignores it.
 */
object WordMasker {
    const val ZWSP = '​'

    const val DEFAULT_STEMS = "цен, куп, продаж, доставк, заказ, оплат, скидк, налич, прайс, стоим, предоплат, " +
        "preis, kauf, bestell, liefer, versand, zahl, rabatt, verfügbar, kosten, " +
        "price, buy, order, ship, deliver, payment, discount, cost, sale"

    fun parse(list: String): List<String> =
        list.split(',', ';', '\n').map { it.trim().lowercase() }.filter { it.length >= 3 }

    fun mask(word: String, stems: List<String>): String {
        if (word.length < 3 || word.indexOf(ZWSP) >= 0) return word
        val low = word.lowercase()
        if (stems.none { low.startsWith(it) }) return word
        return word.substring(0, 1) + ZWSP + word.substring(1)
    }
}
