package com.rob.aikeyboard.data

import android.content.Context
import com.rob.aikeyboard.clipboard.ClipItem
import com.rob.aikeyboard.clipboard.ClipboardHistory
import com.rob.aikeyboard.security.SecureStore
import com.rob.aikeyboard.text.UserModel
import java.io.File
import java.util.UUID

/** Learned words, encrypted at rest. */
class UserModelStore(context: Context, private val secure: SecureStore) {
    private val file = File(context.filesDir, "user_model.bin")
    val model = UserModel()

    fun load() {
        secure.readFile(file)?.let { runCatching { model.loadJson(String(it)) } }
    }

    fun saveIfDirty() {
        if (model.dirty) runCatching { secure.writeFile(file, model.toJson().toByteArray()) }
    }

    fun clear() {
        model.clear()
        file.delete()
    }
}

/**
 * Clipboard history and pinned images, encrypted at rest. Image bytes are stored encrypted in
 * the app's private directory; deleting them never touches the user's gallery.
 */
class ClipboardStore(context: Context, private val secure: SecureStore) {
    private val file = File(context.filesDir, "clipboard.bin")
    private val imageDir = File(context.filesDir, "clip_images").apply { mkdirs() }
    private val shareDir = File(context.cacheDir, "share").apply { mkdirs() }
    val history = ClipboardHistory(maxUnpinned = 10)
    private val listeners = mutableListOf<() -> Unit>()

    fun load() {
        secure.readFile(file)?.let { runCatching { history.loadJson(String(it)) } }
    }

    fun addListener(l: () -> Unit) { listeners += l }
    fun removeListener(l: () -> Unit) { listeners -= l }

    private fun changed(evicted: List<ClipItem> = emptyList()) {
        evicted.filter { it.kind == ClipItem.Kind.IMAGE }.forEach { File(imageDir, it.value).delete() }
        runCatching { secure.writeFile(file, history.toJson().toByteArray()) }
        listeners.toList().forEach { it() }
    }

    fun addText(text: String) = changed(history.add(ClipItem.Kind.TEXT, text, System.currentTimeMillis()))

    fun addImage(bytes: ByteArray, mimeType: String) {
        val name = UUID.randomUUID().toString() + ".bin"
        secure.writeFile(File(imageDir, name), bytes)
        changed(history.add(ClipItem.Kind.IMAGE, name, System.currentTimeMillis(), mimeType))
    }

    fun imageBytes(item: ClipItem): ByteArray? = secure.readFile(File(imageDir, item.value))

    /** Decrypts an image into the cache so it can be shared through the FileProvider. */
    fun exportForSharing(item: ClipItem): File? {
        val bytes = imageBytes(item) ?: return null
        shareDir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 10 * 60_000 }?.forEach { it.delete() }
        val ext = when (item.mimeType) { "image/png" -> "png"; "image/gif" -> "gif"; "image/webp" -> "webp"; else -> "jpg" }
        return File(shareDir, "rob_${item.id}.$ext").apply { writeBytes(bytes) }
    }

    fun setPinned(id: Long, pinned: Boolean) = changed(history.setPinned(id, pinned))

    fun remove(id: Long) = changed(listOfNotNull(history.remove(id)))

    fun clearUnpinned(kind: ClipItem.Kind) = changed(history.clearUnpinned(kind))

    fun clearAll() {
        changed(history.clearAll())
        imageDir.listFiles()?.forEach { it.delete() }
        shareDir.listFiles()?.forEach { it.delete() }
    }
}
