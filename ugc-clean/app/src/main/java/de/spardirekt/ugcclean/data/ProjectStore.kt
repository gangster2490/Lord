package de.spardirekt.ugcclean.data

import android.content.Context
import de.spardirekt.ugcclean.model.ProjectRecord
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class ProjectStore(
    context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) {
    private val dir = File(context.filesDir, "projects").apply { mkdirs() }

    fun list(): List<ProjectRecord> {
        return dir.listFiles { file -> file.extension == "json" }
            .orEmpty()
            .mapNotNull { file -> runCatching { json.decodeFromString<ProjectRecord>(file.readText()) }.getOrNull() }
            .sortedByDescending { it.updatedAt }
    }

    fun get(id: String): ProjectRecord? {
        val file = File(dir, "$id.json")
        if (!file.exists()) return null
        return runCatching { json.decodeFromString<ProjectRecord>(file.readText()) }.getOrNull()
    }

    fun save(record: ProjectRecord) {
        File(dir, "${record.id}.json").writeText(json.encodeToString(record))
    }

    fun delete(id: String) {
        File(dir, "$id.json").delete()
    }
}
