package com.rob.neon.data

import android.content.Context
import org.json.JSONArray

data class IconEntry(
    val id: String,
    val ru: String,
    val de: String,
    val packages: List<String>,
    val components: List<String>,
) {
    fun label(lang: String) = if (lang == "de") de else ru
    val drawableName get() = "ic_$id"
}

object Catalog {
    fun load(ctx: Context): List<IconEntry> {
        val text = ctx.assets.open("catalog.json").bufferedReader().use { it.readText() }
        val arr = JSONArray(text)
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            fun list(key: String) = o.getJSONArray(key).let { a -> List(a.length()) { a.getString(it) } }
            IconEntry(o.getString("id"), o.getString("ru"), o.getString("de"), list("packages"), list("components"))
        }
    }
}
