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

class OpenAiProvider(
    private val http: OkHttpClient = defaultClient(),
) : AiProvider {
    override val id: String = "OPENAI"

    override fun testConnection(apiKey: String): JSONObject {
        val models = listModels(apiKey)
        return JSONObject()
            .put("status", "Connected")
            .put("provider", id)
            .put("models", JSONArray(models))
            .put("primary", AiModelConfig.openaiCandidates(models.toSet()).first())
    }

    override fun consistencyCheck(apiKey: String, images: List<ApiImage>): JSONObject {
        val text = complete(
            apiKey, SystemPrompts.CONSISTENCY,
            "Validate these ${images.size} images. Image indices are 0-based in order.",
            images, json = true, maxTokens = 800, temperature = 0.1,
        )
        return parseJson(text, JsonExtractor.consistencySchemaKeys())
    }

    override fun analyseProduct(apiKey: String, images: List<ApiImage>): JSONObject {
        val text = complete(
            apiKey, SystemPrompts.PRODUCT_ANALYSIS,
            "Analyse these ${images.size} source images. Return the required JSON only.",
            images, json = true, maxTokens = 1600, temperature = 0.2,
        )
        return parseJson(text, JsonExtractor.analysisSchemaKeys())
    }

    override fun productIdentityFingerprint(apiKey: String, images: List<ApiImage>): JSONObject {
        val text = complete(
            apiKey, SystemPrompts.PRODUCT_IDENTITY_FINGERPRINT,
            "Extract the product identity fingerprint from all ${images.size} reference images. Return the required JSON only.",
            images, json = true, maxTokens = 1200, temperature = 0.1,
        )
        return parseJson(text, JsonExtractor.fingerprintSchemaKeys())
    }

    override fun actionIdentityRiskCheck(
        apiKey: String,
        fingerprint: JSONObject,
        scene: JSONObject,
        images: List<ApiImage>,
    ): JSONObject {
        val user = buildString {
            appendLine("Proposed action / scene JSON:")
            appendLine(scene.toString())
            appendLine("Product identity fingerprint JSON:")
            appendLine(fingerprint.toString())
        }
        val text = complete(
            apiKey, SystemPrompts.ACTION_IDENTITY_RISK_CHECK, user,
            images, json = true, maxTokens = 700, temperature = 0.1,
        )
        return parseJson(text, JsonExtractor.actionRiskSchemaKeys())
    }

    override fun productIdentityReadiness(apiKey: String, fingerprint: JSONObject, images: List<ApiImage>): JSONObject {
        val user = buildString {
            appendLine("Fingerprint JSON:")
            appendLine(fingerprint.toString())
            appendLine("Judge readiness using all ${images.size} reference images.")
        }
        val text = complete(
            apiKey, SystemPrompts.PRODUCT_IDENTITY_READINESS, user,
            images, json = true, maxTokens = 700, temperature = 0.1,
        )
        return parseJson(text, JsonExtractor.readinessSchemaKeys())
    }

    override fun recommendFirstFrame(apiKey: String, images: List<ApiImage>): JSONObject {
        val text = complete(
            apiKey, SystemPrompts.FIRST_FRAME_RECOMMENDATION,
            "Recommend the best First Frame. Images are in 0-based order, count=${images.size}.",
            images, json = true, maxTokens = 600, temperature = 0.1,
        )
        return parseJson(text, JsonExtractor.firstFrameRecommendSchemaKeys())
    }

    override fun firstFrameQuality(apiKey: String, image: ApiImage): JSONObject {
        val text = complete(
            apiKey, SystemPrompts.FIRST_FRAME_QUALITY,
            "Check this original uploaded image as First Frame.",
            listOf(image), json = true, maxTokens = 600, temperature = 0.1,
        )
        return parseJson(text, JsonExtractor.firstFrameSchemaKeys())
    }

    override fun generateScene(
        apiKey: String,
        analysis: JSONObject,
        images: List<ApiImage>,
        previous: JSONObject?,
        fingerprint: JSONObject?,
    ): JSONObject {
        val user = buildString {
            appendLine("Evidence JSON:")
            appendLine(analysis.toString())
            if (fingerprint != null) {
                appendLine("Product identity fingerprint JSON:")
                appendLine(fingerprint.toString())
            }
            appendLine("Use only a LOW-RISK action. Product identity wins over creativity.")
            if (previous != null) {
                appendLine("Previous scene (create a different LOW-RISK action/context, same product):")
                appendLine(previous.toString())
            }
        }
        val text = complete(apiKey, SystemPrompts.SCENE, user, images, json = true, maxTokens = 700, temperature = 0.8)
        return JsonExtractor.extractObject(text)
    }

    override fun generateVideoPrompt(apiKey: String, images: List<ApiImage>, ctx: PromptContext): String {
        val user = userContext(ctx)
        val raw = complete(apiKey, SystemPrompts.VIDEO_PROMPT, user, images, json = false, maxTokens = 1400, temperature = 0.8)
        return finalizePrompt(raw, ctx)
    }

    override fun improvePrompt(apiKey: String, ctx: PromptContext): String {
        val user = "Current prompt:\n${ctx.currentPrompt}\n\n${userContext(ctx)}"
        val raw = complete(apiKey, SystemPrompts.IMPROVE, user, emptyList(), json = false, maxTokens = 1400, temperature = 0.5)
        return finalizePrompt(raw, ctx)
    }

    override fun regenerateSpeech(apiKey: String, ctx: PromptContext): String {
        val user = "Current prompt:\n${ctx.currentPrompt}\n\nSpeech language=${ctx.speechLanguage}\nKeep everything except spoken dialogue."
        val raw = complete(apiKey, SystemPrompts.NEW_SPEECH, user, emptyList(), json = false, maxTokens = 900, temperature = 0.7)
        return finalizePrompt(raw, ctx)
    }

    override fun generateCaption(apiKey: String, ctx: PromptContext): JSONObject {
        val user = "Caption language=${ctx.captionLanguage}\nEvidence:\n${ctx.analysis}\nScene:\n${ctx.scene}"
        val text = complete(apiKey, SystemPrompts.CAPTION, user, emptyList(), json = true, maxTokens = 700, temperature = 0.6)
        return JsonExtractor.extractObject(text)
    }

    override fun checkCompliance(apiKey: String, ctx: PromptContext): JSONObject {
        val user = "Prompt:\n${ctx.currentPrompt}\nEvidence:\n${ctx.analysis}\nScene:\n${ctx.scene}"
        val text = complete(apiKey, SystemPrompts.SEMANTIC_COMPLIANCE, user, emptyList(), json = true, maxTokens = 800, temperature = 0.1)
        return parseJson(text, JsonExtractor.complianceSchemaKeys())
    }

    private fun finalizePrompt(raw: String, ctx: PromptContext): String {
        var prompt = raw.trim().removePrefix("```").removeSuffix("```").trim()
        val fingerprint = try { JSONObject(ctx.fingerprint) } catch (_: Exception) { null }
        prompt = ProductLock.ensure(prompt, ctx.strictProductLock, fingerprint)
        prompt = ProductLock.applyGenerator(prompt, ctx.targetGenerator)
        prompt = ProductLock.ensureSpeechTiming(prompt, ctx.speechLanguage)
        return prompt
    }

    private fun userContext(ctx: PromptContext): String = buildString {
        appendLine(ctx.firstFrameNote)
        appendLine("Strict product lock: ${ctx.strictProductLock}")
        appendLine("Speech: ${ctx.speechLanguage}")
        appendLine("Target generator: ${ctx.targetGenerator}")
        appendLine("Product identity fingerprint JSON:")
        appendLine(ctx.fingerprint)
        appendLine("Action identity risk JSON:")
        appendLine(ctx.actionRisk)
        appendLine("Identity readiness JSON:")
        appendLine(ctx.readiness)
        appendLine("Evidence JSON:")
        appendLine(ctx.analysis)
        appendLine("Selected scene JSON:")
        appendLine(ctx.scene)
        appendLine("Use ALL uploaded reference images as supporting identity evidence.")
        appendLine("Use only the selected LOW-RISK action. If the action is HIGH risk, replace it with the recommended safer action.")
        appendLine("If motion_geometry_risk is HIGH, keep identity-critical moving components static.")
        appendLine("Generate exactly 8.0 seconds. The spoken line must finish before the 8.0-second endpoint.")
    }

    private fun parseJson(text: String, keys: List<String>): JSONObject {
        return try {
            JsonExtractor.withDefaults(JsonExtractor.extractObject(text), keys)
        } catch (_: Exception) {
            val repaired = completeRepair(text)
            JsonExtractor.withDefaults(JsonExtractor.extractObject(repaired), keys)
        }
    }

    private fun completeRepair(broken: String): String {
        val start = broken.indexOf('{')
        val end = broken.lastIndexOf('}')
        if (start >= 0 && end > start) return broken.substring(start, end + 1)
        throw ProviderException.parse(broken)
    }

    fun listModels(apiKey: String): List<String> {
        requireKey(apiKey)
        val req = Request.Builder()
            .url("https://api.openai.com/v1/models")
            .addHeader("Authorization", "Bearer $apiKey")
            .get()
            .build()
        val started = System.currentTimeMillis()
        try {
            http.newCall(req).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                AppLog.event("listModels", id, null, response.code, System.currentTimeMillis() - started, 0, 0)
                if (response.code == 401 || response.code == 403) throw ProviderException.invalidKey(raw)
                if (!response.isSuccessful) throw ProviderException.http(response.code, raw)
                val arr = JSONObject(raw).optJSONArray("data") ?: JSONArray()
                return (0 until arr.length()).map { arr.getJSONObject(it).optString("id") }.filter { it.isNotBlank() }
            }
        } catch (e: ProviderException) {
            throw e
        } catch (_: SocketTimeoutException) {
            throw ProviderException.timeout()
        } catch (_: IOException) {
            throw ProviderException.network()
        }
    }

    private fun complete(
        apiKey: String,
        system: String,
        userText: String,
        images: List<ApiImage>,
        json: Boolean,
        maxTokens: Int,
        temperature: Double,
    ): String {
        requireKey(apiKey)
        val available = try {
            listModels(apiKey).toSet()
        } catch (_: Exception) {
            null
        }
        val models = AiModelConfig.openaiCandidates(available)
        var last: ProviderException? = null
        models.forEachIndexed { index, model ->
            try {
                return requestWithRetry(apiKey, model, system, userText, images, json, maxTokens, temperature)
            } catch (e: ProviderException) {
                last = e
                if (e.code == "MODEL_UNAVAILABLE" && index < models.lastIndex) return@forEachIndexed
                if (e.code == "MODEL_UNAVAILABLE") throw e
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
        maxTokens: Int,
        temperature: Double,
    ): String {
        var jsonNow = json
        var reasoning = true
        var rateRetried = false
        var networkRetried = false
        while (true) {
            try {
                return requestOnce(apiKey, model, system, userText, images, jsonNow, reasoning, maxTokens, temperature)
            } catch (e: ProviderException) {
                when {
                    e.code == "RATE_LIMIT" && !rateRetried -> {
                        rateRetried = true
                        Thread.sleep(900)
                    }
                    (e.code == "NETWORK" || e.code == "TIMEOUT") && !networkRetried -> {
                        networkRetried = true
                        Thread.sleep(700)
                    }
                    unsupported(e.message.orEmpty()) && (jsonNow || reasoning) -> {
                        jsonNow = false
                        reasoning = false
                    }
                    e.code == "MODEL_UNAVAILABLE" -> throw e
                    else -> throw e
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
        reasoning: Boolean,
        maxTokens: Int,
        temperature: Double,
    ): String {
        val userContent = JSONArray().put(JSONObject().put("type", "text").put("text", userText))
        images.forEach { image ->
            userContent.put(
                JSONObject()
                    .put("type", "image_url")
                    .put(
                        "image_url",
                        JSONObject()
                            .put("url", "data:${image.mime};base64,${image.base64}")
                            .put("detail", "high"),
                    ),
            )
        }
        val payload = JSONObject()
            .put("model", model)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", userContent)),
            )
        if (json) payload.put("response_format", JSONObject().put("type", "json_object"))
        val gpt5 = model.startsWith("gpt-5")
        if (gpt5) {
            payload.put("max_completion_tokens", (maxTokens + 4000).coerceAtMost(16384))
            if (reasoning) payload.put("reasoning_effort", "medium")
        } else {
            payload.put("temperature", temperature)
            payload.put("max_tokens", maxTokens)
        }

        val req = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON))
            .build()
        val started = System.currentTimeMillis()
        val payloadBytes = images.sumOf { it.compressedBytes }
        try {
            http.newCall(req).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                AppLog.event("chat", id, model, response.code, System.currentTimeMillis() - started, images.size, payloadBytes)
                return when (response.code) {
                    200 -> parseText(raw)
                    400 -> {
                        if (unsupported(raw) || missingModel(raw)) throw ProviderException.unavailable(raw)
                        if (raw.contains("too large", true) || raw.contains("image", true) && raw.contains("limit", true)) {
                            throw ProviderException.payload()
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
        val content = root.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.opt("content")
        val text = flattenContent(content)
        if (text.isBlank()) throw ProviderException.parse(raw)
        return text
    }

    private fun flattenContent(content: Any?): String {
        return when (content) {
            is String -> content
            is JSONArray -> (0 until content.length()).joinToString("") { i ->
                val item = content.opt(i)
                when (item) {
                    is JSONObject -> item.optString("text")
                    else -> item?.toString().orEmpty()
                }
            }
            is JSONObject -> content.optString("text")
            else -> content?.toString().orEmpty()
        }
    }

    private fun requireKey(apiKey: String) {
        if (apiKey.isBlank()) throw ProviderException.missingKey()
    }

    private fun unsupported(raw: String): Boolean {
        val lower = raw.lowercase()
        return lower.contains("unsupported_parameter") ||
            lower.contains("unknown parameter") ||
            lower.contains("reasoning_effort") && lower.contains("unsupported") ||
            lower.contains("response_format") && lower.contains("unsupported")
    }

    private fun missingModel(raw: String): Boolean {
        val lower = raw.lowercase()
        return lower.contains("model") && (lower.contains("does not exist") || lower.contains("not found") || lower.contains("invalid"))
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
