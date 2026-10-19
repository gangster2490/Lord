package com.rob.aikeyboard.clipboard

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.rob.aikeyboard.data.ClipboardStore
import java.util.Locale
import java.util.concurrent.Executor

/**
 * Copies every new screenshot into the keyboard's image clipboard as soon as the system saves
 * it. Opt-in (needs the photos permission); runs only while the keyboard process is alive.
 */
class ScreenshotWatcher(
    private val context: Context,
    private val store: ClipboardStore,
    private val io: Executor,
    private val onNew: (ClipItem) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val seen = HashSet<Long>()
    private var startedAtSec = 0L
    private var observer: ContentObserver? = null

    val isRunning: Boolean get() = observer != null

    fun start() {
        if (observer != null || !hasPermission(context)) return
        startedAtSec = System.currentTimeMillis() / 1000 - 5
        observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                // Samsung writes the file first and finishes it a moment later; look twice.
                io.execute { scan() }
                handler.postDelayed({ io.execute { scan() } }, 1500)
            }
        }.also {
            context.contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, it)
        }
    }

    fun stop() {
        observer?.let { context.contentResolver.unregisterContentObserver(it) }
        observer = null
    }

    private fun scan() {
        val pathColumn = if (Build.VERSION.SDK_INT >= 29) MediaStore.Images.Media.RELATIVE_PATH else MediaStore.Images.Media.DATA
        val projection = arrayOf(
            MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.MIME_TYPE,
            pathColumn,
        )
        // DATE_ADDED is set when the file is created, possibly well before it is finished.
        val since = maxOf(startedAtSec, System.currentTimeMillis() / 1000 - 600)
        val cursor = runCatching {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection,
                "${MediaStore.Images.Media.DATE_ADDED} >= ?", arrayOf(since.toString()),
                "${MediaStore.Images.Media.DATE_ADDED} DESC",
            )
        }.getOrNull() ?: return
        cursor.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val name = c.getString(1).orEmpty()
                val mime = c.getString(2) ?: "image/png"
                val path = c.getString(3).orEmpty()
                // SIZE can still be empty while the file is being written; the bytes below decide.
                if (id in seen) continue
                if (!isScreenshot(name, path)) continue
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                if (bytes == null || bytes.isEmpty() || bytes.size > 15 * 1024 * 1024) continue
                seen += id // scans run one at a time on the single io thread
                val item = store.addImage(bytes, mime)
                handler.post { onNew(item) }
            }
        }
    }

    companion object {
        fun permission(): String =
            if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

        fun hasPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, permission()) == PackageManager.PERMISSION_GRANTED

        /** Samsung: DCIM/Screenshots/Screenshot_20261019_….jpg; Pixel: Pictures/Screenshots/. */
        internal fun isScreenshot(name: String, path: String): Boolean {
            val p = path.lowercase(Locale.ROOT)
            val n = name.lowercase(Locale.ROOT)
            return "screenshot" in p || n.startsWith("screenshot") || "скриншот" in p
        }
    }
}
