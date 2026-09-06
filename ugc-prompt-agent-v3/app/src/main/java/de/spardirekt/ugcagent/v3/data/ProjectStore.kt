package de.spardirekt.ugcagent.v3.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ProjectStore(context: Context) {
    private val root = File(context.filesDir, "projects").apply { mkdirs() }

    fun dir(id: String): File = File(root, id).apply { mkdirs() }

    fun save(project: ProjectRecord) {
        project.touch()
        File(dir(project.id), "project.json").writeText(project.toJson().toString(), Charsets.UTF_8)
    }

    fun load(id: String): ProjectRecord? {
        val file = File(dir(id), "project.json")
        if (!file.exists()) return null
        return ProjectRecord.fromJson(JSONObject(file.readText(Charsets.UTF_8)))
    }

    fun delete(id: String) {
        File(root, id).deleteRecursively()
    }

    fun duplicate(id: String): ProjectRecord? {
        val src = load(id) ?: return null
        val copy = ProjectRecord.fromJson(src.toJson())
        val newId = java.util.UUID.randomUUID().toString()
        val dest = dir(newId)
        File(root, id).copyRecursively(dest, overwrite = true)
        val rewritten = File(dest, "project.json")
                val json = JSONObject(rewritten.readText(Charsets.UTF_8))
        json.put("id", newId)
        json.put("createdAt", System.currentTimeMillis())
        json.put("updatedAt", System.currentTimeMillis())
        rewritten.writeText(json.toString(), Charsets.UTF_8)
        return load(newId)
    }

    fun list(): JSONArray {
        val arr = JSONArray()
        root.listFiles()?.filter { it.isDirectory }?.sortedByDescending { File(it, "project.json").lastModified() }?.forEach { folder ->
            val file = File(folder, "project.json")
            if (file.exists()) {
                val obj = JSONObject(file.readText(Charsets.UTF_8))
                arr.put(
                    JSONObject()
                        .put("id", obj.optString("id"))
                        .put("createdAt", obj.optLong("createdAt"))
                        .put("updatedAt", obj.optLong("updatedAt"))
                        .put("provider", obj.optString("provider"))
                        .put("model", obj.optString("model"))
                        .put("targetGenerator", obj.optString("targetGenerator"))
                        .put("imageCount", obj.optJSONArray("images")?.length() ?: 0)
                        .put("useCase", obj.optJSONObject("analysis")?.optString("observed_use_case") ?: "")
                        .put("hasPrompt", obj.optString("finalPrompt").isNotBlank())
                        .put("thumbnailId", obj.optString("firstFrameId")),
                )
            }
        }
        return arr
    }
}
