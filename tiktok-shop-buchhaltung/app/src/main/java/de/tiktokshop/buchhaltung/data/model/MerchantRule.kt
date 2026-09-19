package de.tiktokshop.buchhaltung.data.model

import androidx.room.Entity
import java.time.Instant

/**
 * Eine vom Nutzer bestätigte Zuordnung "Händler -> Kategorie/Einnahmeart/Geschäftsanteil",
 * die beim nächsten Scan automatisch vorgeschlagen wird ("ChatGPT Plus -> AI-Dienste -> 100%
 * geschäftlich"). `merchantKey` ist der normalisierte (getrimmte, kleingeschriebene)
 * Händlername; `entryTypeName` ist [EntryType.name], da derselbe Händlername theoretisch in
 * beiden Kontexten auftauchen könnte.
 */
@Entity(tableName = "merchant_rules", primaryKeys = ["merchantKey", "entryTypeName"])
data class MerchantRule(
    val merchantKey: String,
    val entryTypeName: String,
    val categoryName: String?,
    val incomeType: String?,
    val businessUsePercent: Int?,
    val updatedAt: Instant = Instant.now(),
)
