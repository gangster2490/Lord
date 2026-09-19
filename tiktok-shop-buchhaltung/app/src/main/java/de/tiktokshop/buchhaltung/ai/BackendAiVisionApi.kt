package de.tiktokshop.buchhaltung.ai

import de.tiktokshop.buchhaltung.ai.dto.AiVisionResponseDto
import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/**
 * Vertrag des eigenen Backend-Proxys (Android App -> Backend Proxy -> AI Vision API).
 * Der Proxy kapselt den eigentlichen AI-Anbieter (OpenAI/Gemini/Claude Vision) und dessen
 * API-Key liegt ausschließlich dort - niemals in der App.
 */
interface BackendAiVisionApi {
    @Multipart
    @POST("v1/vision/analyze")
    suspend fun analyzeReceipt(@Part image: MultipartBody.Part): AiVisionResponseDto
}
