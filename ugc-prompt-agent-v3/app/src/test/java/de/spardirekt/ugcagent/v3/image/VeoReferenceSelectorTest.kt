package de.spardirekt.ugcagent.v3.image

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VeoReferenceSelectorTest {
    @Test
    fun prefersCleanFullProductAsFirstFrame() {
        val shot = candidate("shot", 0, 1080, 2400, 40_000, filename = "listing-screenshot.png")
        val hero = candidate("hero", 1, 1200, 1600, 180_000, filename = "product-hero.jpg")
        val support = candidate("side", 2, 1100, 1400, 150_000, filename = "product-side.jpg")
        val extra = candidate("top", 3, 1080, 1350, 148_000, filename = "product-top.jpg")
        val picks = VeoReferenceSelector.select(listOf(shot, hero, support, extra))
        assertEquals("hero", picks.first().id)
        assertEquals("FIRST_FRAME", picks.first().role)
        assertEquals("FIRST FRAME", picks.first().label)
        assertTrue(picks.none { it.id == "shot" })
        assertTrue(picks.size in 3..5)
    }

    @Test
    fun rejectsDuplicatesTextCardsPackagingAndConflicts() {
        val hero = candidate("hero", 0, 1200, 1600, 180_000, filename = "product.jpg")
        val dup = candidate("dup", 1, 1200, 1600, 181_000, filename = "product-copy.jpg")
        val pack = candidate("pack", 2, 1000, 1200, 90_000, filename = "retail-box.jpg", tags = listOf("packaging"))
        val text = candidate("text", 3, 1080, 2400, 35_000, filename = "size-card.png", reasons = "text-only infographic")
        val otherColor = candidate("other", 4, 900, 1100, 120_000, filename = "red-variant.jpg")
        val detail = candidate("detail", 5, 1000, 1300, 140_000, filename = "handle-close.jpg")
        val extra = candidate("extra", 6, 1050, 1350, 145_000, filename = "lid-top.jpg")
        val consistency = JSONObject()
            .put("conflicting_image_indices", JSONArray().put(4))
            .put("duplicate_groups", JSONArray().put(JSONArray().put(0).put(1)))
        val picks = VeoReferenceSelector.select(
            listOf(hero, dup, pack, text, otherColor, detail, extra),
            firstFrameId = "hero",
            consistency = consistency,
        )
        val ids = picks.map { it.id }
        assertEquals("hero", ids.first())
        assertTrue("dup" !in ids)
        assertTrue("pack" !in ids)
        assertTrue("text" !in ids)
        assertTrue("other" !in ids)
        assertTrue(picks.size in 3..5)
        assertEquals(picks.size, picks.map { it.id }.distinct().size)
        assertEquals(listOf("FIRST FRAME", "SUPPORT 1", "SUPPORT 2").take(picks.size), picks.map { it.label }.take(3))
    }

    @Test
    fun keepsUsefulCloseUpAndCapsAtFive() {
        val hero = candidate("hero", 0, 1400, 1800, 220_000, filename = "full.jpg")
        val close = candidate("close", 1, 400, 420, 95_000, filename = "handle-detail.jpg")
        val a = candidate("a", 2, 1100, 1400, 130_000)
        val b = candidate("b", 3, 1150, 1450, 135_000)
        val c = candidate("c", 4, 1180, 1480, 138_000)
        val d = candidate("d", 5, 1220, 1520, 142_000)
        val picks = VeoReferenceSelector.select(listOf(hero, close, a, b, c, d), firstFrameId = "hero")
        assertEquals("FIRST_FRAME", picks.first().role)
        assertTrue(picks.any { it.id == "close" })
        assertTrue(picks.size in 3..5)
        assertEquals(5, picks.size)
    }

    private fun candidate(
        id: String,
        index: Int,
        width: Int,
        height: Int,
        bytes: Long,
        filename: String = "$id.jpg",
        tags: List<String> = emptyList(),
        reasons: String = "",
    ) = VeoReferenceSelector.Candidate(
        id = id,
        index = index,
        width = width,
        height = height,
        compressedBytes = bytes,
        filename = filename,
        tags = tags,
        reasons = reasons,
    )
}
