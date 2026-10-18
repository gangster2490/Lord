package com.rob.aikeyboard.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.rob.aikeyboard.RobApp

/**
 * Opens the system Photo Picker (no gallery permission needed) and copies the chosen
 * screenshots/photos, encrypted, into the keyboard's image history.
 */
class PhotoPickerActivity : ComponentActivity() {
    private val pick = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) { uris ->
        val app = RobApp.instance
        if (uris.isEmpty()) return@registerForActivityResult finish()
        app.io.execute {
            var added = 0
            uris.forEach { uri ->
                runCatching {
                    val mime = contentResolver.getType(uri) ?: "image/jpeg"
                    contentResolver.openInputStream(uri)?.use { input ->
                        val bytes = input.readBytes()
                        if (bytes.size <= 15 * 1024 * 1024) { app.clipboard.addImage(bytes, mime); added++ }
                    }
                }
            }
            runOnUiThread {
                Toast.makeText(this, "Добавлено в буфер клавиатуры: $added", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }
}
