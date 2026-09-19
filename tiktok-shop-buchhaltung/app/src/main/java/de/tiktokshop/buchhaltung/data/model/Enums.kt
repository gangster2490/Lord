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
