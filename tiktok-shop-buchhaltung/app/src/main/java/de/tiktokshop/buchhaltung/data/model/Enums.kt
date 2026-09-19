package de.tiktokshop.buchhaltung.data.model

/** Art einer Buchung. */
enum class EntryType {
    INCOME,
    EXPENSE,
}

/**
 * Status einer TikTok-Provision. Der Zeitpunkt der tatsächlichen Verfügungsmacht
 * (AVAILABLE/PAID_OUT) ist steuerlich relevant, nicht der Anzeigezeitpunkt in der
 * Plattform (ACCRUED). Siehe TAX_LOGIC_DE.md.
 */
enum class IncomeStatus(val label: String) {
    ACCRUED("Angezeigt"),
    FROZEN("Eingefroren"),
    AVAILABLE("Verfügbar"),
    PAID_OUT("Ausgezahlt"),
    REVERSED("Storniert"),
    REFUNDED("Rückerstattet"),
}

/**
 * Dokumentierte Phase der Tätigkeit (Steuer-Arbeitsstand): Januar-Mai 2026 gilt als Test-
 * und Vorbereitungsphase, ab Gewerbeanmeldung als reguläre Tätigkeit. Rein dokumentarisch -
 * verändert keine Beträge, wird aber im Steuer-Arbeitsstand-Export mit ausgewiesen.
 */
enum class ActivityPhase(val label: String) {
    TEST_PREPARATION("Test- und Vorbereitungsphase"),
    REGULAR_BUSINESS("Reguläre Tätigkeit"),
    ;

    companion object {
        /** Gewerbeanmeldung dokumentiert ab Juni 2026 - siehe Steuerübersicht_2026_Arbeitsstand. */
        private val REGULAR_BUSINESS_START: java.time.LocalDate = java.time.LocalDate.of(2026, 6, 1)

        fun forDate(date: java.time.LocalDate): ActivityPhase =
            if (date.isBefore(REGULAR_BUSINESS_START)) TEST_PREPARATION else REGULAR_BUSINESS
    }
}

enum class ExpenseCategory(val label: String) {
    VIDEO_PRODUCTS("Produkte für Videos"),
    VIDEO_EDITING_SOFTWARE("Video-Software"),
    SHIPPING("Versandkosten"),
    SOFTWARE_SUBSCRIPTIONS("Software / Abos"),
    AI_SERVICES("AI-Dienste"),
    PHONE_INTERNET("Telefon / Internet"),
    CAMERA_AUDIO_LIGHTING("Kamera / Mikrofon / Licht / Stativ"),
    COMPUTER_PHONE_ACCESSORIES("Computer / Smartphone / Zubehör"),
    ADVERTISING("Werbung"),
    TRAVEL("Fahrtkosten"),
    OFFICE_SUPPLIES("Bürobedarf"),
    FEES("Gebühren"),
    OTHER("Sonstige Betriebsausgaben"),
}

/** Art eines importierten/hochgeladenen [de.tiktokshop.buchhaltung.data.model.SourceDocument]. */
enum class SourceDocumentType(val label: String) {
    TIKTOK_EXCEL("TikTok Earnings Report"),
    RECEIPT_IMAGE("Beleg / Screenshot"),
    TAX_DOCUMENT("Steuerunterlage"),
    OTHER("Sonstiges"),
}
