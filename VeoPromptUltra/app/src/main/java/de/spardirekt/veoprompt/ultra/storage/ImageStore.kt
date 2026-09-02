package de.spardirekt.veoprompt.ultra.storage

import android.content.Context
import android.net.Uri
import de.spardirekt.veoprompt.ultra.model.ProjectImage
import java.io.File

object ImageStore {

    fun persist(context: Context, projectId: String, imageId: String, source: Uri): ProjectImage {
        val dir = projectDir(context, projectId)
        dir.mkdirs()
        val dest = File(dir, "$imageId.jpg")
        context.contentResolver.openInputStream(source)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("Cannot read image")
        return ProjectImage(
            id = imageId,
            uri = Uri.fromFile(dest).toString(),
            localPath = dest.absolutePath
        )
    }

    fun deleteImage(context: Context, projectId: String, imageId: String) {
        File(projectDir(context, projectId), "$imageId.jpg").delete()
    }

    fun deleteProject(context: Context, projectId: String) {
        projectDir(context, projectId).deleteRecursively()
    }

    fun deleteAll(context: Context) {
        File(context.filesDir, "projects").deleteRecursively()
    }

    fun copyProjectImages(
        context: Context,
        source: List<ProjectImage>,
        targetProjectId: String
    ): List<ProjectImage> {
        return source.mapIndexed { index, image ->
            val newId = java.util.UUID.randomUUID().toString()
            val from = image.localPath?.takeIf { it.isNotBlank() }?.let { File(it) }
            if (from != null && from.exists()) {
                persist(context, targetProjectId, newId, Uri.fromFile(from)).copy(
                    category = image.category,
                    orderIndex = index
                )
            } else {
                image.copy(id = newId, orderIndex = index)
            }
        }
    }

    private fun projectDir(context: Context, projectId: String): File {
        return File(context.filesDir, "projects/$projectId")
    }
}
