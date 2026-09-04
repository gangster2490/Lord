package de.spardirekt.ugcagent.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class HistoryEntry(
    val id: String,
    val createdAt: Long,
    val label: String,
    val analysisJson: String,
    val sceneJson: String,
    val prompt: String,
    val caption: String,
    val firstFrameThumb: String,
)

class HistoryStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun list(): List<HistoryEntry> {
        val raw = prefs.getString(KEY, "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                add(
                    HistoryEntry(
                        id = obj.optString("id"),
                        createdAt = obj.optLong("createdAt"),
                        label = obj.optString("label"),
                        analysisJson = obj.optString("analysisJson"),
                        sceneJson = obj.optString("sceneJson"),
                        prompt = obj.optString("prompt"),
                        caption = obj.optString("caption"),
                        firstFrameThumb = obj.optString("firstFrameThumb"),
                    )
                )
            }
        }.sortedByDescending { it.createdAt }
    }

    fun save(entry: HistoryEntry) {
        val current = list().filterNot { it.id == entry.id }.toMutableList()
        current.add(0, entry)
        persist(current.take(MAX_ITEMS))
    }

    fun delete(id: String) {
        persist(list().filterNot { it.id == id })
    }

    private fun persist(items: List<HistoryEntry>) {
        val array = JSONArray()
        items.forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("createdAt", entry.createdAt)
                    .put("label", entry.label)
                    .put("analysisJson", entry.analysisJson)
                    .put("sceneJson", entry.sceneJson)
                    .put("prompt", entry.prompt)
                    .put("caption", entry.caption)
                    .put("firstFrameThumb", entry.firstFrameThumb),
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    companion object {
        private const val PREFS = "ugc_agent_history"
        private const val KEY = "entries"
        private const val MAX_ITEMS = 50
    }
}
