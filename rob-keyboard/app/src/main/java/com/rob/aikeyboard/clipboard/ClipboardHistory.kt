package com.rob.aikeyboard.clipboard

import org.json.JSONArray
import org.json.JSONObject

data class ClipItem(
    val id: Long,
    val kind: Kind,
    /** Text content, or the private file name of an image. */
    val value: String,
    val pinned: Boolean,
    val createdAt: Long,
    val mimeType: String = "text/plain",
) {
    enum class Kind { TEXT, IMAGE }
}

/**
 * Clipboard history rules: at most [maxUnpinned] unpinned items per kind (oldest dropped first),
 * pinned items are kept until removed by the user.
 */
class ClipboardHistory(private val maxUnpinned: Int = 10) {
    private val items = ArrayList<ClipItem>()
    private var nextId = 1L

    @Synchronized
    fun all(kind: ClipItem.Kind): List<ClipItem> =
        items.filter { it.kind == kind }.sortedWith(compareByDescending<ClipItem> { it.pinned }.thenByDescending { it.createdAt })

    /**
     * Adds an item (moving an identical existing text to the top).
     * @return items evicted to respect the limit, so image files can be deleted.
     */
    @Synchronized
    fun add(kind: ClipItem.Kind, value: String, now: Long, mimeType: String = "text/plain"): List<ClipItem> {
        if (kind == ClipItem.Kind.TEXT && value.isBlank()) return emptyList()
        val existing = items.firstOrNull { it.kind == kind && it.value == value }
        if (existing != null) {
            items.remove(existing)
            items += existing.copy(createdAt = now)
            return emptyList()
        }
        items += ClipItem(nextId++, kind, value, pinned = false, createdAt = now, mimeType = mimeType)
        return evict(kind)
    }

    @Synchronized
    fun setPinned(id: Long, pinned: Boolean): List<ClipItem> {
        val i = items.indexOfFirst { it.id == id }
        if (i < 0) return emptyList()
        items[i] = items[i].copy(pinned = pinned)
        return evict(items[i].kind)
    }

    @Synchronized
    fun remove(id: Long): ClipItem? {
        val i = items.indexOfFirst { it.id == id }
        return if (i >= 0) items.removeAt(i) else null
    }

    /** Removes unpinned items; pinned ones stay. */
    @Synchronized
    fun clearUnpinned(kind: ClipItem.Kind): List<ClipItem> {
        val removed = items.filter { it.kind == kind && !it.pinned }
        items.removeAll(removed.toSet())
        return removed
    }

    @Synchronized
    fun clearAll(): List<ClipItem> {
        val removed = items.toList()
        items.clear()
        return removed
    }

    private fun evict(kind: ClipItem.Kind): List<ClipItem> {
        val unpinned = items.filter { it.kind == kind && !it.pinned }.sortedBy { it.createdAt }
        val excess = unpinned.size - maxUnpinned
        if (excess <= 0) return emptyList()
        val drop = unpinned.take(excess)
        items.removeAll(drop.toSet())
        return drop
    }

    @Synchronized
    fun toJson(): String = JSONObject().apply {
        put("nextId", nextId)
        put("items", JSONArray().apply {
            items.forEach {
                put(JSONObject().apply {
                    put("id", it.id)
                    put("kind", it.kind.name)
                    put("value", it.value)
                    put("pinned", it.pinned)
                    put("createdAt", it.createdAt)
                    put("mime", it.mimeType)
                })
            }
        })
    }.toString()

    @Synchronized
    fun loadJson(json: String) {
        items.clear()
        val o = JSONObject(json)
        nextId = o.optLong("nextId", 1)
        val arr = o.optJSONArray("items") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val it = arr.getJSONObject(i)
            items += ClipItem(
                id = it.getLong("id"),
                kind = ClipItem.Kind.valueOf(it.getString("kind")),
                value = it.getString("value"),
                pinned = it.optBoolean("pinned"),
                createdAt = it.optLong("createdAt"),
                mimeType = it.optString("mime", "text/plain"),
            )
        }
        nextId = maxOf(nextId, (items.maxOfOrNull { it.id } ?: 0) + 1)
    }
}
