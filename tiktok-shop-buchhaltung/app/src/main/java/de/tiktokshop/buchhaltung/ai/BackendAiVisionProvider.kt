package de.tiktokshop.buchhaltung.ai

import android.content.Context
import android.net.Uri
import de.tiktokshop.buchhaltung.ai.dto.AiVisionMapper
import de.tiktokshop.buchhaltung.scan.TransactionCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException

/**
 * AI-Vision-Fallback über den eigenen Backend-Proxy (siehe [BackendAiVisionApi]). Die
 * Backend-URL wird zur Laufzeit konfiguriert ([BackendConfigStore]) - ohne konfigurierte
 * URL schlägt die Analyse kontrolliert fehl (kein Absturz, kein erfundenes Ergebnis) und
 * die App bleibt bei den lokalen OCR-Kandidaten.
 */
class BackendAiVisionProvider(
    private val context: Context,
    private val configStore: BackendConfigStore,
) : AiVisionProvider {

    override suspend fun analyzeReceipt(imageUri: Uri): Result<List<TransactionCandidate>> = withContext(Dispatchers.IO) {
        val baseUrl = configStore.currentBaseUrl()
        if (baseUrl.isNullOrBlank()) {
            return@withContext Result.failure(IllegalStateException("Kein AI-Vision-Backend konfiguriert."))
        }

        runCatching {
            val api = NetworkModule.createApi(baseUrl, configStore.backendToken())
            val tempFile = copyToTempFile(imageUri)
            try {
                val requestBody = tempFile.asRequestBody("image/jpeg".toMediaType())
                val part = MultipartBody.Part.createFormData("image", tempFile.name, requestBody)
                val response = api.analyzeReceipt(part)
                response.transactions.map(AiVisionMapper::toDomain)
            } finally {
                tempFile.delete()
            }
        }
    }

    private fun copyToTempFile(imageUri: Uri): File {
        val tempFile = File.createTempFile("ai_vision_upload", ".jpg", context.cacheDir)
        val input = context.contentResolver.openInputStream(imageUri)
            ?: throw IOException("Konnte Bild-URI nicht öffnen: $imageUri")
        input.use { stream -> tempFile.outputStream().use { stream.copyTo(it) } }
        return tempFile
    }
}
