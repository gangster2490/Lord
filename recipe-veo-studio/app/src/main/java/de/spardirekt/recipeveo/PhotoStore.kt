package de.spardirekt.recipeveo

import android.content.Context
import android.net.Uri
import de.spardirekt.recipeveo.domain.PhotoRef
import de.spardirekt.recipeveo.domain.SeedProjects
import de.spardirekt.recipeveo.domain.newId
import java.io.File

class PhotoStore(private val context: Context) {
    fun persist(projectId: String, uris: List<String>): List<PhotoRef> = uris.map { uriString ->
        if (uriString == SeedProjects.DEMO_PHOTO || uriString.startsWith("demo://")) {
            PhotoRef(newId(), SeedProjects.DEMO_PHOTO)
        } else {
            val id = newId()
            val dest = File(File(context.filesDir, "photos/$projectId"), "$id.jpg")
            dest.parentFile?.mkdirs()
            val copied = runCatching {
                context.contentResolver.openInputStream(Uri.parse(uriString))?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
            }.getOrNull()
            if (copied != null && dest.exists() && dest.length() > 0L) {
                PhotoRef(id, dest.toURI().toString())
            } else {
                PhotoRef(id, uriString)
            }
        }
    }

    fun deletePhoto(projectId: String, photoId: String) {
        File(context.filesDir, "photos/$projectId/$photoId.jpg").delete()
    }

    fun deleteProject(projectId: String) {
        File(context.filesDir, "photos/$projectId").deleteRecursively()
    }

    fun deleteAll() {
        File(context.filesDir, "photos").deleteRecursively()
    }
}
