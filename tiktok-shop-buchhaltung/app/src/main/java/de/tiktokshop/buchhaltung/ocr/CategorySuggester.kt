package de.tiktokshop.buchhaltung.ocr

import de.tiktokshop.buchhaltung.data.model.ExpenseCategory

/** Schlägt eine Ausgabenkategorie anhand des Händlernamens/OCR-Texts vor (OCR_RULES.md). Nur ein Vorschlag, nie final. */
object CategorySuggester {

    private val RULES: List<Pair<Regex, ExpenseCategory>> = listOf(
        Regex("elevenlabs|openai|anthropic|chatgpt|midjourney|runway", RegexOption.IGNORE_CASE) to ExpenseCategory.AI_SERVICES,
        Regex("capcut|canva|adobe|premiere|final cut|software|abo|subscription", RegexOption.IGNORE_CASE) to ExpenseCategory.SOFTWARE_SUBSCRIPTIONS,
        Regex("dhl|hermes|dpd|ups|fedex|versand|porto|label", RegexOption.IGNORE_CASE) to ExpenseCategory.SHIPPING,
        Regex("mikrofon|kamera|stativ|licht|ringlicht|objektiv|rode|neewer", RegexOption.IGNORE_CASE) to ExpenseCategory.CAMERA_AUDIO_LIGHTING,
        Regex("telekom|vodafone|o2|internet|mobilfunk|handyvertrag", RegexOption.IGNORE_CASE) to ExpenseCategory.PHONE_INTERNET,
        Regex("apple|samsung|mediamarkt|saturn|notebook|laptop|smartphone|zubehör|ladekabel", RegexOption.IGNORE_CASE) to ExpenseCategory.COMPUTER_PHONE_ACCESSORIES,
        Regex("werbung|ads|meta ads|tiktok ads|google ads|promotion", RegexOption.IGNORE_CASE) to ExpenseCategory.ADVERTISING,
        Regex("tankstelle|bahn|db |fahrkarte|parken|taxi|uber", RegexOption.IGNORE_CASE) to ExpenseCategory.TRAVEL,
        Regex("büro|papier|drucker|ordner|toner", RegexOption.IGNORE_CASE) to ExpenseCategory.OFFICE_SUPPLIES,
        Regex("gebühr|kontoführung|paypal-gebühr|fee", RegexOption.IGNORE_CASE) to ExpenseCategory.FEES,
    )

    /** Liefert null, wenn keine Regel greift (Nutzer wählt dann manuell, statt "Sonstige" zu erzwingen). */
    fun suggest(merchantOrText: String?): ExpenseCategory? {
        if (merchantOrText.isNullOrBlank()) return null
        return RULES.firstOrNull { (pattern, _) -> pattern.containsMatchIn(merchantOrText) }?.second
    }
}
