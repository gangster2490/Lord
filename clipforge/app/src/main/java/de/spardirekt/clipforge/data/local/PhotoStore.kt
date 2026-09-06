package de.spardirekt.clipforge.data.local

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

object PhotoStore {
    fun persistPicked(context: Context, source: Uri): String {
        val dest = File(photosDir(context), "${UUID.randomUUID()}.jpg")
        return copyOrKeep(context, source, dest)
    }

    fun persistThumb(context: Context, entryId: String, source: Uri): String {
        val dest = File(thumbsDir(context), "$entryId.jpg")
        return copyOrKeep(context, source, dest)
    }

    fun deleteThumb(context: Context, entryId: String) {
        File(thumbsDir(context), "$entryId.jpg").delete()
    }

    fun deleteLocal(context: Context, uriString: String) {
        val file = uriFile(uriString) ?: return
        val root = context.filesDir.canonicalFile
        if (file.canonicalFile.path.startsWith(root.path)) {
            file.delete()
        }
    }

    fun clearThumbs(context: Context) {
        thumbsDir(context).deleteRecursively()
    }

    private fun copyOrKeep(context: Context, source: Uri, dest: File): String {
        dest.parentFile?.mkdirs()
        val copied = runCatching {
            context.contentResolver.openInputStream(source)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        }.getOrNull() != null && dest.length() > 0
        return if (copied) Uri.fromFile(dest).toString() else source.toString()
    }

    private fun photosDir(context: Context) = File(context.filesDir, "photos")
    private fun thumbsDir(context: Context) = File(context.filesDir, "thumbs")

    private fun uriFile(uriString: String): File? {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
        return when (uri.scheme) {
            "file" -> uri.path?.let(::File)
            null -> File(uriString)
            else -> null
        }
    }
}
