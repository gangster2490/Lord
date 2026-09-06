package de.spardirekt.clipforge.data.model

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

class HistoryEntryTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun oldArchiveJsonWithoutNewFieldsStillDecodes() {
        val raw = """
            [{
              "id": "legacy",
              "createdAt": 1,
              "platformId": "tiktok_shop",
              "lengthSeconds": 8,
              "formulaId": "hook_demo_cta",
              "languageId": "ru",
              "productName": "Jar",
              "ad": {}
            }]
        """.trimIndent()
        val decoded = json.decodeFromString<List<HistoryEntry>>(raw)
        val entry = decoded.single()
        assertThat(entry.styleId).isEqualTo(VisualStyle.CINEMATIC.id)
        assertThat(entry.wish).isEmpty()
        assertThat(entry.photoUris).isEmpty()
        assertThat(entry.ad.product.name).isEqualTo("Не распознано")
    }

    @Test
    fun roundTripKeepsWishAndPhotos() {
        val entry = HistoryEntry(
            id = "new",
            createdAt = 2,
            platformId = Platform.REELS.id,
            lengthSeconds = 15,
            formulaId = AdFormula.UGC.id,
            languageId = AdLanguage.DE.id,
            styleId = VisualStyle.UGC_RAW.id,
            productName = "Creme",
            wish = "am See",
            photoUris = listOf("file:///photos/a.jpg"),
            ad = AdPackage(cta = "Produkt im Profil"),
        )
        val encoded = json.encodeToString(listOf(entry))
        val decoded = json.decodeFromString<List<HistoryEntry>>(encoded).single()
        assertThat(decoded.styleId).isEqualTo(VisualStyle.UGC_RAW.id)
        assertThat(decoded.wish).isEqualTo("am See")
        assertThat(decoded.photoUris).containsExactly("file:///photos/a.jpg")
        assertThat(decoded.ad.cta).isEqualTo("Produkt im Profil")
    }
}
