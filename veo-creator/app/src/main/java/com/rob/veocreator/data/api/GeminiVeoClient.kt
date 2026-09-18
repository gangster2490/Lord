package com.rob.veocreator.data.api

import android.util.Base64
import android.util.Log
import com.rob.veocreator.data.model.ApiException
import com.rob.veocreator.data.model.AspectRatio
import com.rob.veocreator.data.model.Duration
import com.rob.veocreator.data.model.ImageAnalysisEntry
import com.rob.veocreator.data.model.ImageAnalysisResult
import com.rob.veocreator.data.model.ImageRole
import com.rob.veocreator.data.model.RequestMode
import com.rob.veocreator.data.model.Resolution
import com.rob.veocreator.data.model.VeoModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** One image, already loaded into memory as base64, ready to embed in a Gemini/Veo request. */
data class InlineImage(val base64: String, val mimeType: String)

sealed class OperationResult {
    data object Pending : OperationResult()
    data class Done(val videoUri: String) : OperationResult()
    data class Failed(val message: String) : OperationResult()
}

/**
 * Talks directly to Google's official Generative Language (Gemini) REST API.
 * No proxy, no third-party server. Endpoints per https://ai.google.dev/gemini-api/docs/veo
 */
class GeminiVeoClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .build()

    private val downloadClient = client.newBuilder()
        .callTimeout(10, TimeUnit.MINUTES)
        .readTimeout(5, TimeUnit.MINUTES)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun testConnection(apiKey: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$BASE_URL/models?pageSize=1")
                .header("x-goog-api-key", apiKey)
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw mapHttpError(response.code, response.body?.string())
            }
        }
    }

    /**
     * [requestMode] decides which of [primaryImage] / [referenceImages] is actually used - the
     * two are mutually exclusive in the real API (see the class doc on RequestMode), so whichever
     * one doesn't match the mode is ignored even if the caller passed it in by mistake.
     */
    suspend fun submitGeneration(
        apiKey: String,
        model: VeoModel,
        prompt: String,
        requestMode: RequestMode,
        primaryImage: InlineImage?,
        referenceImages: List<InlineImage>,
        aspectRatio: AspectRatio,
        duration: Duration,
        resolution: Resolution
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val usesReferenceImages = requestMode == RequestMode.REFERENCE_IMAGES && referenceImages.isNotEmpty()
            val usesStartingImage = requestMode == RequestMode.IMAGE_TO_VIDEO && primaryImage != null

            // Veo's "image" (Vertex-style predict Image object) is NOT the same shape as Gemini
            // Content.Part.inlineData - sending inlineData here gets a 400 "`inlineData` isn't
            // supported by this model." The Veo Image object is {bytesBase64Encoded, mimeType}.
            val instance = JSONObject().apply {
                put("prompt", prompt)
                if (usesStartingImage) {
                    put("image", veoImageJson(primaryImage!!))
                } else if (usesReferenceImages) {
                    val refsArray = JSONArray()
                    referenceImages.forEach { ref ->
                        refsArray.put(JSONObject().apply {
                            put("image", veoImageJson(ref))
                            put("referenceType", "asset")
                        })
                    }
                    put("referenceImages", refsArray)
                }
            }

            // durationSeconds must be 8 whenever reference images or a resolution above 720p is
            // used - see https://ai.google.dev/gemini-api/docs/veo. The UI already enforces this,
            // this is just a last-line-of-defense guard against sending an invalid combination.
            val effectiveDuration = if (usesReferenceImages || resolution != Resolution.R720P) {
                Duration.D8
            } else duration

            // Per https://ai.google.dev/gemini-api/docs/veo: "allow_all" only for text-to-video /
            // extension, "allow_adult" only for image-to-video / interpolation / reference images.
            val personGeneration = when (requestMode) {
                RequestMode.TEXT_TO_VIDEO -> "allow_all"
                RequestMode.IMAGE_TO_VIDEO, RequestMode.REFERENCE_IMAGES -> "allow_adult"
            }

            val parameters = JSONObject().apply {
                put("aspectRatio", aspectRatio.apiValue)
                put("durationSeconds", effectiveDuration.apiValue)
                put("resolution", resolution.apiValue)
                put("personGeneration", personGeneration)
                put("numberOfVideos", 1)
            }

            val body = JSONObject().apply {
                put("instances", JSONArray().put(instance))
                put("parameters", parameters)
            }

            logSanitizedRequest(model.apiName, requestMode, body)

            val request = Request.Builder()
                .url("$BASE_URL/models/${model.apiName}:predictLongRunning")
                .header("x-goog-api-key", apiKey)
                .post(body.toString().toRequestBody(jsonMedia))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string()
                Log.d(TAG, "submitGeneration response httpCode=${response.code}")
                if (!response.isSuccessful) throw mapHttpError(response.code, bodyStr)
                val json = JSONObject(bodyStr ?: "{}")
                json.optString("name").takeIf { it.isNotBlank() }
                    ?: throw ApiException(null, "Gemini did not return an operation id.")
            }
        }
    }

    /** The Veo REST "Image" object: {bytesBase64Encoded, mimeType} - distinct from Gemini inlineData. */
    private fun veoImageJson(image: InlineImage): JSONObject = JSONObject().apply {
        put("bytesBase64Encoded", image.base64)
        put("mimeType", image.mimeType)
    }

    /** Logs the exact JSON sent to Google with all image bytes redacted, for debugging 400s. */
    private fun logSanitizedRequest(modelName: String, requestMode: RequestMode, body: JSONObject) {
        val sanitized = JSONObject().apply {
            put("model", modelName)
            put("mode", requestMode.name)
            put("endpoint", ":predictLongRunning")
            put("instances", redactImageData(body.optJSONArray("instances") ?: JSONArray()))
            put("parameters", body.optJSONObject("parameters") ?: JSONObject())
        }
        val text = sanitized.toString(2)
        // Logcat truncates long single lines; chunk so the full sanitized body is readable.
        text.chunked(3500).forEachIndexed { index, chunk ->
            Log.d(TAG, "submitGeneration request[$index]: $chunk")
        }
    }

    private fun redactImageData(value: Any): Any = when (value) {
        is JSONObject -> {
            val copy = JSONObject()
            value.keys().forEach { key ->
                val v = value.get(key)
                val isImageBytesField = (key == "data" || key == "bytesBase64Encoded") && v is String
                copy.put(key, if (isImageBytesField) "<IMAGE_DATA_REMOVED>" else redactImageData(v))
            }
            copy
        }
        is JSONArray -> {
            val copy = JSONArray()
            for (i in 0 until value.length()) copy.put(redactImageData(value.get(i)))
            copy
        }
        else -> value
    }

    suspend fun pollOperation(apiKey: String, operationName: String): Result<OperationResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("$BASE_URL/$operationName")
                    .header("x-goog-api-key", apiKey)
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string()
                    Log.d(TAG, "pollOperation httpCode=${response.code}")
                    if (!response.isSuccessful) throw mapHttpError(response.code, bodyStr)
                    val json = JSONObject(bodyStr ?: "{}")
                    val done = json.optBoolean("done", false)
                    if (!done) return@use OperationResult.Pending

                    val error = json.optJSONObject("error")
                    if (error != null) {
                        return@use OperationResult.Failed(describeOperationError(error))
                    }

                    val response0 = json.optJSONObject("response")
                    val videoUri = response0
                        ?.optJSONObject("generateVideoResponse")
                        ?.optJSONArray("generatedSamples")
                        ?.optJSONObject(0)
                        ?.optJSONObject("video")
                        ?.optString("uri")

                    if (videoUri.isNullOrBlank()) {
                        OperationResult.Failed("Generation finished but no video was returned. It may have been blocked by the safety system.")
                    } else {
                        OperationResult.Done(videoUri)
                    }
                }
            }
        }

    suspend fun downloadVideo(apiKey: String, videoUri: String, destination: File): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(videoUri)
                    .header("x-goog-api-key", apiKey)
                    .get()
                    .build()

                downloadClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw mapHttpError(response.code, null)
                    val body = response.body ?: throw ApiException(null, "Empty video response from server.")
                    destination.outputStream().use { out ->
                        body.byteStream().copyTo(out)
                    }
                }
                destination
            }
        }

    /**
     * Uses Gemini's multimodal understanding to classify uploaded photos and pull out any
     * clearly legible spec text, WITHOUT ever instructing Veo to render that text on screen.
     */
    suspend fun analyzeImages(
        apiKey: String,
        images: List<InlineImage>,
        userPrompt: String
    ): Result<ImageAnalysisResult> = withContext(Dispatchers.IO) {
        runCatching {
            val instruction = """
                You are helping build a prompt for AI video generation (Veo) from product photos.
                The user's current draft prompt is: "${userPrompt.ifBlank { "(empty)" }}"

                For each image below (numbered in order starting at 1), classify its role as exactly one of:
                MAIN_PRODUCT_PHOTO, ALTERNATE_ANGLE, DETAIL_PHOTO, OPEN_CLOSED_STATE, ACCESSORIES,
                PACKAGING, SPECIFICATION_IMAGE, DESCRIPTION_IMAGE, DIMENSION_IMAGE, OTHER.

                Only extract product specification text (dimensions, wattage, capacity, materials,
                counts, certifications) if it is clearly and confidently legible in the image. If you
                are not confident, omit it rather than guessing. Never invent specifications that are
                not visually confirmed.

                Then write a short "promptEnhancement" paragraph describing the CONFIRMED physical
                appearance and specs to help animate the product accurately. It must describe the
                physical product only. It must NEVER ask for on-screen text, captions, labels, price
                tags, banners or logos to appear in the video - those are handled separately.

                Respond with ONLY strict JSON, no markdown fences, matching this schema:
                {"images":[{"index":1,"role":"MAIN_PRODUCT_PHOTO","extractedText":["1200W"],"notes":"short description"}],"promptEnhancement":"paragraph or empty string"}
            """.trimIndent()

            val parts = JSONArray().apply {
                put(JSONObject().put("text", instruction))
                images.forEach { image ->
                    put(JSONObject().apply {
                        put("inlineData", JSONObject().apply {
                            put("mimeType", image.mimeType)
                            put("data", image.base64)
                        })
                    })
                }
            }

            val body = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().put("parts", parts)))
                put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
            }

            val request = Request.Builder()
                .url("$BASE_URL/models/$VISION_MODEL:generateContent")
                .header("x-goog-api-key", apiKey)
                .post(body.toString().toRequestBody(jsonMedia))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string()
                if (!response.isSuccessful) throw mapHttpError(response.code, bodyStr)
                val json = JSONObject(bodyStr ?: "{}")
                val text = json.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text")
                    ?: throw ApiException(null, "Image analysis returned no content.")

                parseAnalysisJson(text)
            }
        }
    }

    private fun parseAnalysisJson(text: String): ImageAnalysisResult {
        val cleaned = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val json = JSONObject(cleaned)
        val imagesArray = json.optJSONArray("images") ?: JSONArray()
        val entries = (0 until imagesArray.length()).map { i ->
            val entry = imagesArray.getJSONObject(i)
            val roleStr = entry.optString("role", "OTHER")
            val role = runCatching { ImageRole.valueOf(roleStr) }.getOrDefault(ImageRole.OTHER)
            val textArray = entry.optJSONArray("extractedText")
            val extracted = textArray?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList()
            ImageAnalysisEntry(
                index = entry.optInt("index", i + 1),
                role = role,
                extractedText = extracted,
                notes = entry.optString("notes").takeIf { it.isNotBlank() }
            )
        }
        val enhancement = json.optString("promptEnhancement").takeIf { it.isNotBlank() }
        return ImageAnalysisResult(entries, enhancement)
    }

    private fun describeOperationError(error: JSONObject): String {
        val message = error.optString("message", "Generation failed.")
        val code = error.optInt("code", -1)
        Log.w(TAG, "operation error code=$code message=$message")
        return when {
            message.contains("safety", ignoreCase = true) ||
                message.contains("blocked", ignoreCase = true) ||
                message.contains("PROHIBITED", ignoreCase = true) ->
                "The request was blocked by the safety system. Try a different prompt or image."
            else -> message
        }
    }

    private fun mapHttpError(code: Int, body: String?): ApiException {
        val errorJson = body?.let { runCatching { JSONObject(it).optJSONObject("error") }.getOrNull() }
        val serverMessage = errorJson?.optString("message")?.takeIf { it.isNotBlank() }
        val serverStatus = errorJson?.optString("status")?.takeIf { it.isNotBlank() }

        Log.w(TAG, "API error httpCode=$code status=$serverStatus message=$serverMessage")

        val message = when (code) {
            401 -> "Invalid Gemini API key. Please check it in Settings."
            403 -> "This API key doesn't have permission to use the Veo video generation API."
            404 -> "The selected model is not available for this API key yet."
            429 -> "Rate limit reached. Please wait a moment and try again."
            in 500..599 -> "Google's server had a problem. Please try again shortly."
            else -> serverMessage ?: "Request failed (HTTP $code)."
        }
        val technicalDetails = "HTTP $code" +
            (serverStatus?.let { " · $it" } ?: "") +
            (serverMessage?.let { " · $it" } ?: "")
        return ApiException(code, message, technicalDetails)
    }

    companion object {
        private const val TAG = "VeoCreatorAPI"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        private const val VISION_MODEL = "gemini-2.5-flash"

        fun toBase64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}

fun IOException.toUserMessage(): String = "Network unavailable. Check your internet connection and try again."
