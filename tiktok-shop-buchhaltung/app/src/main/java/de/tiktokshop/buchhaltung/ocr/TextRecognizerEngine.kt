package de.tiktokshop.buchhaltung.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/** Dünner Wrapper um Google ML Kit Text Recognition (on-device, keine Cloud-Pflicht). */
class TextRecognizerEngine(private val context: Context) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(imageUri: Uri): String {
        val image = InputImage.fromFilePath(context, imageUri)
        val result = recognizer.process(image).await()
        return result.text
    }
}
