package com.rob.aikeyboard

import com.rob.aikeyboard.clipboard.ClipItem.Kind
import com.rob.aikeyboard.clipboard.ClipboardHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardHistoryTest {
    @Test fun keepsTenUnpinnedAndDropsOldest() {
        val h = ClipboardHistory()
        for (i in 1..10) h.add(Kind.TEXT, "t$i", i.toLong())
        val evicted = h.add(Kind.TEXT, "t11", 11)
        assertEquals(listOf("t1"), evicted.map { it.value })
        assertEquals(10, h.all(Kind.TEXT).size)
        assertEquals("t11", h.all(Kind.TEXT).first().value)
    }

    @Test fun pinnedItemsSurviveAndDoNotCount() {
        val h = ClipboardHistory()
        h.add(Kind.TEXT, "keep", 0)
        h.setPinned(h.all(Kind.TEXT).first().id, true)
        for (i in 1..15) h.add(Kind.TEXT, "t$i", i.toLong())
        val all = h.all(Kind.TEXT)
        assertEquals(11, all.size)
        assertTrue(all.first().pinned)
        h.clearUnpinned(Kind.TEXT)
        assertEquals(listOf("keep"), h.all(Kind.TEXT).map { it.value })
    }

    @Test fun unpinningReappliesLimit() {
        val h = ClipboardHistory()
        h.add(Kind.TEXT, "old", 0)
        val id = h.all(Kind.TEXT).first().id
        h.setPinned(id, true)
        for (i in 1..10) h.add(Kind.TEXT, "t$i", i.toLong())
        assertEquals(listOf("old"), h.setPinned(id, false).map { it.value })
    }

    @Test fun duplicatesMoveToTopAndJsonRoundTrips() {
        val h = ClipboardHistory()
        h.add(Kind.TEXT, "a", 1); h.add(Kind.TEXT, "b", 2); h.add(Kind.TEXT, "a", 3)
        assertEquals(listOf("a", "b"), h.all(Kind.TEXT).map { it.value })
        h.add(Kind.IMAGE, "img1.bin", 4, "image/png")
        val r = ClipboardHistory().apply { loadJson(h.toJson()) }
        assertEquals(listOf("a", "b"), r.all(Kind.TEXT).map { it.value })
        assertEquals("image/png", r.all(Kind.IMAGE).single().mimeType)
    }
}
