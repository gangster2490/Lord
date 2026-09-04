package de.spardirekt.ugcagent.bridge

import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.util.Base64
import de.spardirekt.ugcagent.compliance.ComplianceChecker
import de.spardirekt.ugcagent.data.HistoryEntry
import de.spardirekt.ugcagent.data.HistoryStore
import de.spardirekt.ugcagent.data.ImageStore
import de.spardirekt.ugcagent.data.SecureStore
import de.spardirekt.ugcagent.data.SimilarityChecker
import de.spardirekt.ugcagent.gemini.GeminiClient
import de.spardirekt.ugcagent.gemini.GeminiException
import de.spardirekt.ugcagent.prompt.SceneIdea
import de.spardirekt.ugcagent.prompt.ScenePool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class NativeBridge(
    private val webView: WebView,
    private val scope: CoroutineScope,
    private val secureStore: SecureStore,
    private val historyStore: HistoryStore,
    private val imageStore: ImageStore,
    private val gemini: GeminiClient,
    private val onPickImages: () -> Unit,
    private val onCopy: (String) -> Unit,
) {
    @Volatile
    var firstFrameId: String? = null
        private set

    @JavascriptInterface
    fun getBootstrap(): String {
        return JSONObject()
            .put("hasApiKey", secureStore.hasApiKey())
            .put("maskedKey", secureStore.maskedApiKey())
            .put("language", secureStore.getLanguage())
            .put("minImages", ImageStore.MIN_IMAGES)
            .put("maxImages", ImageStore.MAX_IMAGES)
            .toString()
    }

    @JavascriptInterface
    fun saveApiKey(key: String) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) {
            secureStore.clearApiKey()
        } else {
            secureStore.saveApiKey(trimmed)
        }
        emit("apiKey", JSONObject()
            .put("hasApiKey", secureStore.hasApiKey())
            .put("maskedKey", secureStore.maskedApiKey()))
    }

    @JavascriptInterface
    fun setLanguage(lang: String) {
        secureStore.setLanguage(lang)
        emit("language", JSONObject().put("language", secureStore.getLanguage()))
    }

    @JavascriptInterface
    fun pickImages() {
        onPickImages()
    }

    @JavascriptInterface
    fun setFirstFrame(id: String) {
        firstFrameId = id
        emit("firstFrame", JSONObject().put("id", id))
    }

    @JavascriptInterface
    fun checkSimilarity() {
        scope.launch {
            val result = runCatching {
                val first = requireFirstFrame()
                SimilarityChecker.compare(first, imageStore.list()).toJson()
            }
            result.fold(
                onSuccess = { emit("similarity", it) },
                onFailure = { emitError("similarity", it) },
            )
        }
    }

    @JavascriptInterface
    fun analyze() {
        scope.launch {
            try {
                val key = requireApiKey()
                val images = imageStore.list()
                if (images.size < ImageStore.MIN_IMAGES) {
                    throw GeminiException("NEED_PHOTOS", "need_15_20")
                }
                val analysis = withContext(Dispatchers.IO) { gemini.analyze(key, images) }
                emit("analysis", analysis)
            } catch (t: Throwable) {
                emitError("analysis", t)
            }
        }
    }

    @JavascriptInterface
    fun generateScenes() {
        val scenes = ScenePool.pick(count = 4, excludeKey = secureStore.getLastSceneKey())
        emit("scenes", JSONObject().put("scenes", scenesToJson(scenes)))
    }

    @JavascriptInterface
    fun buildPrompt(sceneJson: String, analysisJson: String) {
        scope.launch {
            try {
                val key = requireApiKey()
                val scene = parseScene(sceneJson)
                secureStore.setLastSceneKey(scene.key)
                if (analysisJson.isNotBlank()) {
                    currentAnalysis = JSONObject(analysisJson)
                }
                val analysis = currentAnalysis ?: throw GeminiException("GENERIC", "no_analysis")
                val first = requireFirstFrame()
                val prompt = withContext(Dispatchers.IO) {
                    gemini.buildPrompt(key, analysis, scene, first)
                }
                emit(
                    "prompt",
                    JSONObject()
                        .put("prompt", prompt)
                        .put("improved", false)
                        .put("scene", sceneToJson(scene)),
                )
            } catch (t: Throwable) {
                emitError("prompt", t)
            }
        }
    }

    @JavascriptInterface
    fun improvePrompt(currentPrompt: String, sceneJson: String) {
        scope.launch {
            try {
                val key = requireApiKey()
                val scene = parseScene(sceneJson)
                val improved = withContext(Dispatchers.IO) {
                    gemini.improvePrompt(key, currentPrompt, scene)
                }
                emit(
                    "prompt",
                    JSONObject()
                        .put("prompt", improved)
                        .put("improved", true)
                        .put("scene", sceneToJson(scene)),
                )
            } catch (t: Throwable) {
                emitError("prompt", t)
            }
        }
    }

    @JavascriptInterface
    fun checkCompliance(prompt: String, caption: String) {
        val result = ComplianceChecker.evaluate(prompt, caption)
        val hits = JSONArray()
        result.forbiddenHits.forEach { hits.put(it.pattern) }
        emit(
            "compliance",
            JSONObject()
                .put("forbiddenHits", hits)
                .put("hasAdDisclosure", result.hasAdDisclosure)
                .put("hasForbiddenLanguage", result.hasForbiddenLanguage)
                .put("missingAdDisclosure", result.missingAdDisclosure),
        )
    }

    @JavascriptInterface
    fun saveHistory(payloadJson: String) {
        val obj = JSONObject(payloadJson)
        val entry = HistoryEntry(
            id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
            createdAt = System.currentTimeMillis(),
            label = obj.optString("label"),
            analysisJson = obj.optString("analysisJson"),
            sceneJson = obj.optString("sceneJson"),
            prompt = obj.optString("prompt"),
            caption = obj.optString("caption"),
            firstFrameThumb = obj.optString("firstFrameThumb"),
        )
        historyStore.save(entry)
        emitHistory()
    }

    @JavascriptInterface
    fun loadHistory() {
        emitHistory()
    }

    @JavascriptInterface
    fun deleteHistory(id: String) {
        historyStore.delete(id)
        emitHistory()
    }

    @JavascriptInterface
    fun copyText(text: String) {
        onCopy(text)
        emit("copied", JSONObject().put("ok", true))
    }

    fun onImagesImported() {
        val array = JSONArray()
        imageStore.list().forEach { image ->
            array.put(
                JSONObject()
                    .put("id", image.id)
                    .put("thumb", image.thumbDataUrl),
            )
        }
        firstFrameId = imageStore.list().firstOrNull()?.id
        emit(
            "images",
            JSONObject()
                .put("images", array)
                .put("firstFrameId", firstFrameId ?: JSONObject.NULL),
        )
    }

    fun onPickCancelled() {
        emit("images", JSONObject().put("cancelled", true))
    }

    var currentAnalysis: JSONObject? = null
        private set

    private fun emitHistory() {
        val array = JSONArray()
        historyStore.list().forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("createdAt", entry.createdAt)
                    .put("label", entry.label)
                    .put("analysisJson", entry.analysisJson)
                    .put("sceneJson", entry.sceneJson)
                    .put("prompt", entry.prompt)
                    .put("caption", entry.caption)
                    .put("firstFrameThumb", entry.firstFrameThumb),
            )
        }
        emit("history", JSONObject().put("entries", array))
    }

    private fun requireApiKey(): String =
        secureStore.getApiKey() ?: throw GeminiException("NO_API_KEY", "missing_api_key")

    private fun requireFirstFrame() =
        imageStore.get(firstFrameId ?: "") ?: imageStore.list().firstOrNull()
            ?: throw GeminiException("NEED_PHOTOS", "no_first_frame")

    private fun parseScene(json: String): SceneIdea {
        val obj = JSONObject(json)
        return SceneIdea(
            opener = obj.getString("opener"),
            environment = obj.getString("environment"),
            action = obj.getString("action"),
        )
    }

    private fun sceneToJson(scene: SceneIdea): JSONObject =
        JSONObject()
            .put("opener", scene.opener)
            .put("environment", scene.environment)
            .put("action", scene.action)
            .put("key", scene.key)

    private fun scenesToJson(scenes: List<SceneIdea>): JSONArray {
        val array = JSONArray()
        scenes.forEach { array.put(sceneToJson(it)) }
        return array
    }

    private fun emitError(event: String, error: Throwable) {
        val code = (error as? GeminiException)?.code ?: "GENERIC"
        emit(
            "error",
            JSONObject()
                .put("event", event)
                .put("code", code)
                .put("message", error.message ?: code),
        )
    }

    fun rememberAnalysis(json: JSONObject) {
        currentAnalysis = json
    }

    private fun emit(event: String, payload: JSONObject) {
        if (event == "analysis") currentAnalysis = payload
        val envelope = JSONObject().put("event", event).put("payload", payload)
        val encoded = Base64.encodeToString(
            envelope.toString().toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP,
        )
        webView.post {
            webView.evaluateJavascript("window.__nativeEvent && window.__nativeEvent('$encoded')", null)
        }
    }
}
