package com.rob.veocreator.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.rob.veocreator.data.api.GeminiVeoClient
import com.rob.veocreator.data.api.InlineImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MediaUtils {

    /** Reads an image Uri into memory without recompressing it, ready to embed in a Veo request. */
    fun loadInlineImage(context: Context, uri: Uri): InlineImage {
        val mimeType = context.contentResolver.getType(uri) ?: guessMimeFromUri(uri) ?: "image/jpeg"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Could not read image")
        return InlineImage(GeminiVeoClient.toBase64(bytes), mimeType)
    }

    private fun guessMimeFromUri(uri: Uri): String? {
        val path = uri.path ?: return null
        return when {
            path.endsWith(".png", true) -> "image/png"
            path.endsWith(".webp", true) -> "image/webp"
            path.endsWith(".jpg", true) || path.endsWith(".jpeg", true) -> "image/jpeg"
            else -> null
        }
    }

    /** Copies a downloaded mp4 into the app's private cache so it can be played/shared. */
    fun cacheDir(context: Context): File =
        File(context.cacheDir, "videos").apply { mkdirs() }

    fun newVideoCacheFile(context: Context): File {
        val name = "veo_${System.currentTimeMillis()}.mp4"
        return File(cacheDir(context), name)
    }

    /** Saves a generated video into Movies/VeoCreator via MediaStore (scoped storage). Returns the public Uri. */
    fun saveVideoToMovies(context: Context, source: File): Uri {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "VeoCreator_$timestamp.mp4"

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, filename)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/VeoCreator")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val itemUri = resolver.insert(collection, values)
            ?: throw IllegalStateException("Could not create MediaStore entry")

        resolver.openOutputStream(itemUri)?.use { out ->
            source.inputStream().use { it.copyTo(out) }
        } ?: throw IllegalStateException("Could not open output stream")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(itemUri, values, null, null)
        }

        return itemUri
    }
}
