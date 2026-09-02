package de.spardirekt.recipeveo

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

class PhotoStore(private val context: Context) {
    private val dir: File = File(context.filesDir, "photos").also { it.mkdirs() }

    fun import(uri: Uri): String {
        val out = File(dir, "${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        } ?: error("не удалось прочитать фото")
        return out.absolutePath
    }

    fun delete(path: String) {
        runCatching { File(path).takeIf { it.exists() }?.delete() }
    }
}
