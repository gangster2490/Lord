package de.spardirekt.recipeveo.ui.history

import android.net.Uri
import java.io.File

object ProjectThumbnail {
    fun model(thumbnailUri: String): Any? {
        val path = thumbnailUri.trim()
        if (path.isBlank()) return null
        if (path.startsWith("/") && !path.startsWith("//")) return File(path)
        return Uri.parse(path)
    }
}
