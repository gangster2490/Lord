package de.spardirekt.ugcagent.v3.ai

import de.spardirekt.ugcagent.v3.config.AiModelConfig
import de.spardirekt.ugcagent.v3.prompt.ProductLock
import de.spardirekt.ugcagent.v3.prompt.SystemPrompts
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

class GeminiProvider(
    private val http: OkHttpClient = defaultClient(),
) : AiProvider {
    override val id: String = "GEMINI"

    override fun testConnection(apiKey: String): JSONObject {
        val models = listModels(apiKey)
        return JSONObject()
            .put("status", "Connected")
            .put("provider", id)
            .put("models", JSONArray(models))
            .put("primary", AiModelConfig.geminiCandidates(models.toSet()).first())
    }

    override fun consistencyCheck(apiKey: String, images: List<ApiImage>): JSONObject =
        jsonCall(apiKey, SystemPrompts.CONSISTENCY, "Validate these ${images.size} images. 0-based indices.", images, JsonExtractor.consistencySchemaKeys(), 0.1)

    override fun analyseProduct(apiKey: String, images: List<ApiImage>): JSONObject =
        jsonCall(apiKey, SystemPrompts.PRODUCT_ANALYSIS, "Analyse these ${images.size} source images.", images, JsonExtractor.analysisSchemaKeys(), 0.2)

    override fun productIdentityFingerprint(apiKey: String, images: List<ApiImage>): JSONObject =
        jsonCall(
            apiKey,
            SystemPrompts.PRODUCT_IDENTITY_FINGERPRINT,
            "Extract the product identity fingerprint from all ${images.size} reference images.",
            images,
            JsonExtractor.fingerprintSchemaKeys(),
            0.1,
        )

    override fun actionIdentityRiskCheck(
        apiKey: String,
        fingerprint: JSONObject,
        scene: JSONObject,
        images: List<ApiImage>,
    ): JSONObject = jsonCall(
        apiKey,
        SystemPrompts.ACTION_IDENTITY_RISK_CHECK,
        "Proposed scene:\n$scene\nFingerprint:\n$fingerprint",
        images,
        JsonExtractor.actionRiskSchemaKeys(),
        0.1,
    )

    override fun productIdentityReadiness(apiKey: String, fingerprint: JSONObject, images: List<ApiImage>): JSONObject =
        jsonCall(
            apiKey,
            SystemPrompts.PRODUCT_IDENTITY_READINESS,
            "Fingerprint:\n$fingerprint\nJudge readiness from all ${images.size} images.",
            images,
            JsonExtractor.readinessSchemaKeys(),
            0.1,
        )

    override fun recommendFirstFrame(apiKey: String, images: List<ApiImage>): JSONObject =
        jsonCall(
            apiKey,
            SystemPrompts.FIRST_FRAME_RECOMMENDATION,
            "Recommend the best First Frame. 0-based order, count=${images.size}.",
            images,
            JsonExtractor.firstFrameRecommendSchemaKeys(),
            0.1,
        )

    override fun firstFrameQuality(apiKey: String, image: ApiImage): JSONObject =
        jsonCall(apiKey, SystemPrompts.FIRST_FRAME_QUALITY, "Check this original uploaded image as First Frame.", listOf(image), JsonExtractor.firstFrameSchemaKeys(), 0.1)

    override fun generateScene(apiKey: String, analysis: JSONObject, images: List<ApiImage>, previous: JSONObject?, fingerprint: JSONObject?): JSONObject {
        val user = buildString {
            appendLine("Evidence JSON:")
            appendLine(analysis.toString())
            if (fingerprint != null) {
                appendLine("Product identity fingerprint JSON:")
                appendLine(fingerprint.toString())
            }
            appendLine("Use only a LOW-RISK action. Product identity wins over creativity.")
            if (previous != null) {
                appendLine("Previous scene (different LOW-RISK action/context, same product):")
                appendLine(previous.toString())
            }
        }
        val text = complete(apiKey, SystemPrompts.SCENE, user, images, json = true, temperature = 0.8)
        return JsonExtractor.extractObject(text)
    }

    override fun generateVideoPrompt(apiKey: String, images: List<ApiImage>, ctx: PromptContext): String {
        val raw = complete(apiKey, SystemPrompts.VIDEO_PROMPT, userContext(ctx), images, json = false, temperature = 0.8)
        return finalizePrompt(raw, ctx)
    }

    override fun improvePrompt(apiKey: String, ctx: PromptContext): String {
        val raw = complete(apiKey, SystemPrompts.IMPROVE, "Current prompt:\n${ctx.currentPrompt}\n\n${userContext(ctx)}", emptyList(), json = false, temperature = 0.5)
        return finalizePrompt(raw, ctx)
    }

    override fun regenerateSpeech(apiKey: String, ctx: PromptContext): String {
        val raw = complete(
            apiKey,
            SystemPrompts.NEW_SPEECH,
            "Current prompt:\n${ctx.currentPrompt}\nSpeech=${ctx.speechLanguage}",
            emptyList(),
            json = false,
            temperature = 0.7,
        )
        return finalizePrompt(raw, ctx)
    }

    override fun generateCaption(apiKey: String, ctx: PromptContext): JSONObject {
        val text = complete(
            apiKey,
            SystemPrompts.CAPTION,
            "Caption language=${ctx.captionLanguage}\nEvidence:\n${ctx.analysis}\nScene:\n${ctx.scene}",
            emptyList(),
            json = true,
            temperature = 0.6,
        )
        return JsonExtractor.extractObject(text)
    }

    override fun checkCompliance(apiKey: String, ctx: PromptContext): JSONObject {
        val text = complete(
            apiKey,
            SystemPrompts.SEMANTIC_COMPLIANCE,
            "Prompt:\n${ctx.currentPrompt}\nEvidence:\n${ctx.analysis}",
            emptyList(),
            json = true,
            temperature = 0.1,
        )
        return parseJson(text, JsonExtractor.complianceSchemaKeys())
    }

    fun listModels(apiKey: String): List<String> {
        requireKey(apiKey)
        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
            .get()
            .build()
        val started = System.currentTimeMillis()
        try {
            http.newCall(req).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                AppLog.event("listModels", id, null, response.code, System.currentTimeMillis() - started, 0, 0)
                if (response.code == 400 || response.code == 401 || response.code == 403) {
                    throw ProviderException.invalidKey(raw)
                }
                if (!response.isSuccessful) throw ProviderException.http(response.code, raw)
                val arr = JSONObject(raw).optJSONArray("models") ?: JSONArray()
                return (0 until arr.length()).map { arr.getJSONObject(it).optString("name") }.filter { it.isNotBlank() }
            }
        } catch (e: ProviderException) {
            throw e
        } catch (_: SocketTimeoutException) {
            throw ProviderException.timeout()
        } catch (_: IOException) {
            throw ProviderException.network()
        }
    }

    private fun jsonCall(
        apiKey: String,
        system: String,
        user: String,
        images: List<ApiImage>,
        keys: List<String>,
        temperature: Double,
    ): JSONObject {
        val text = complete(apiKey, system, user, images, json = true, temperature = temperature)
        return parseJson(text, keys)
    }

    private fun parseJson(text: String, keys: List<String>): JSONObject {
        return try {
            JsonExtractor.withDefaults(JsonExtractor.extractObject(text), keys)
        } catch (_: Exception) {
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start >= 0 && end > start) {
                JsonExtractor.withDefaults(JSONObject(text.substring(start, end + 1)), keys)
            } else {
                throw ProviderException.parse(text)
            }
        }
    }

    private fun finalizePrompt(raw: String, ctx: PromptContext): String {
        var prompt = raw.trim().removePrefix("```").removeSuffix("```").trim()
        val fingerprint = try { JSONObject(ctx.fingerprint) } catch (_: Exception) { null }
        prompt = ProductLock.ensure(prompt, ctx.strictProductLock, fingerprint)
        prompt = ProductLock.applyGenerator(prompt, ctx.targetGenerator)
        prompt = ProductLock.ensureSpeechTiming(prompt, ctx.speechLanguage)
        return prompt
    }

    private fun userContext(ctx: PromptContext): String =
        "${ctx.firstFrameNote}\nStrict lock=${ctx.strictProductLock}\nSpeech=${ctx.speechLanguage}\nGenerator=${ctx.targetGenerator}\nFingerprint:\n${ctx.fingerprint}\nAction risk:\n${ctx.actionRisk}\nReadiness:\n${ctx.readiness}\nEvidence:\n${ctx.analysis}\nScene:\n${ctx.scene}\nUse all uploaded reference images as supporting identity evidence. Use only the selected LOW-RISK action. If motion_geometry_risk is HIGH, keep identity-critical moving components static. Generate exactly 8.0 seconds. The spoken line must finish before the 8.0-second endpoint."

    private fun complete(
        apiKey: String,
        system: String,
        userText: String,
        images: List<ApiImage>,
        json: Boolean,
        temperature: Double,
    ): String {
        requireKey(apiKey)
        val available = try {
            listModels(apiKey).toSet()
        } catch (_: Exception) {
            null
        }
        val models = AiModelConfig.geminiCandidates(available)
        var last: ProviderException? = null
        models.forEachIndexed { index, model ->
            try {
                return requestWithRetry(apiKey, model, system, userText, images, json, temperature)
            } catch (e: ProviderException) {
                last = e
                if (e.code == "MODEL_UNAVAILABLE" && index < models.lastIndex) return@forEachIndexed
                throw e
            }
        }
        throw last ?: ProviderException.generic()
    }

    private fun requestWithRetry(
        apiKey: String,
        model: String,
        system: String,
        userText: String,
        images: List<ApiImage>,
        json: Boolean,
        temperature: Double,
    ): String {
        var networkRetried = false
        while (true) {
            try {
                return requestOnce(apiKey, model, system, userText, images, json, temperature)
            } catch (e: ProviderException) {
                if ((e.code == "NETWORK" || e.code == "TIMEOUT") && !networkRetried) {
                    networkRetried = true
                    Thread.sleep(700)
                } else {
                    throw e
                }
            }
        }
    }

    private fun requestOnce(
        apiKey: String,
        model: String,
        system: String,
        userText: String,
        images: List<ApiImage>,
        json: Boolean,
        temperature: Double,
    ): String {
        val parts = JSONArray().put(JSONObject().put("text", userText))
        images.forEach { image ->
            parts.put(
                JSONObject().put(
                    "inline_data",
                    JSONObject().put("mime_type", image.mime).put("data", image.base64),
                ),
            )
        }
        val payload = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", JSONArray().put(JSONObject().put("parts", parts)))
        val gen = JSONObject().put("temperature", temperature)
        if (json) gen.put("responseMimeType", "application/json")
        payload.put("generationConfig", gen)

        val modelPath = if (model.startsWith("models/")) model else "models/$model"
        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/$modelPath:generateContent?key=$apiKey")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON))
            .build()
        val started = System.currentTimeMillis()
        val payloadBytes = images.sumOf { it.compressedBytes }
        try {
            http.newCall(req).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                AppLog.event("generateContent", id, model, response.code, System.currentTimeMillis() - started, images.size, payloadBytes)
                return when (response.code) {
                    200 -> parseText(raw)
                    400 -> {
                        if (raw.contains("not found", true) || raw.contains("not supported", true)) {
                            throw ProviderException.unavailable(raw)
                        }
                        throw ProviderException.generic(raw)
                    }
                    else -> throw ProviderException.http(response.code, raw)
                }
            }
        } catch (e: ProviderException) {
            throw e
        } catch (_: SocketTimeoutException) {
            throw ProviderException.timeout()
        } catch (_: IOException) {
            throw ProviderException.network()
        }
    }

    private fun parseText(raw: String): String {
        val root = JSONObject(raw)
        val parts = root.optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts") ?: JSONArray()
        val text = (0 until parts.length()).joinToString("") { parts.optJSONObject(it)?.optString("text").orEmpty() }
        if (text.isBlank()) throw ProviderException.parse(raw)
        return text
    }

    private fun requireKey(apiKey: String) {
        if (apiKey.isBlank()) throw ProviderException.missingKey()
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .build()
    }
}
