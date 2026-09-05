package de.spardirekt.ugcagent.v3.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstFrameHeuristicsTest {
    @Test
    fun flagsTinyFrame() {
        val result = FirstFrameHeuristics.check(120, 90, 2_000)
        assertFalse(result.getBoolean("usable"))
        assertTrue(result.getJSONArray("warnings").length() >= 1)
    }

    @Test
    fun acceptsNormalPhoto() {
        val result = FirstFrameHeuristics.check(1200, 1600, 180_000)
        assertTrue(result.getBoolean("usable"))
        assertTrue(result.getDouble("confidence") >= 0.8)
    }

    @Test
    fun mergeKeepsStricterResult() {
        val local = FirstFrameHeuristics.check(1200, 1600, 180_000)
        val ai = org.json.JSONObject()
            .put("usable", false)
            .put("confidence", 0.4)
            .put("warnings", org.json.JSONArray().put("marketplace UI covers the product"))
        val merged = FirstFrameHeuristics.merge(local, ai)
        assertFalse(merged.getBoolean("usable"))
        assertEquals(0.4, merged.getDouble("confidence"), 0.001)
    }

    @Test
    fun ranksLargerCleanPhotoAboveTinyFrame() {
        val tiny = FirstFrameHeuristics.RankedImage("tiny", 0, 200, 200, 4_000, FirstFrameHeuristics.score(200, 200, 4_000))
        val photo = FirstFrameHeuristics.RankedImage("photo", 1, 1080, 1920, 220_000, FirstFrameHeuristics.score(1080, 1920, 220_000))
        val rec = FirstFrameHeuristics.recommendLocal(listOf(tiny, photo))
        assertEquals("photo", rec?.id)
    }
}
