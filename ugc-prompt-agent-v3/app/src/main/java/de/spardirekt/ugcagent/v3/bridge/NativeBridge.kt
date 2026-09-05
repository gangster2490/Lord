package de.spardirekt.ugcagent.v3.bridge

import android.net.Uri
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import de.spardirekt.ugcagent.v3.ai.ApiImage
import de.spardirekt.ugcagent.v3.ai.GeminiProvider
import de.spardirekt.ugcagent.v3.ai.OpenAiProvider
import de.spardirekt.ugcagent.v3.ai.PromptContext
import de.spardirekt.ugcagent.v3.ai.ProviderException
import de.spardirekt.ugcagent.v3.compliance.ComplianceEngine
import de.spardirekt.ugcagent.v3.compliance.TikTokShopPolicyConfig
import de.spardirekt.ugcagent.v3.data.ImageRules
import de.spardirekt.ugcagent.v3.data.ProjectRecord
import de.spardirekt.ugcagent.v3.data.ProjectStore
import de.spardirekt.ugcagent.v3.data.SettingsStore
import de.spardirekt.ugcagent.v3.data.StoredImage
import de.spardirekt.ugcagent.v3.image.ImageProcessor
import de.spardirekt.ugcagent.v3.security.SecureApiKeyStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

class NativeBridge(
    private val activity: AppCompatActivity,
    private val webView: WebView,
    private val pickImages: () -> Unit,
    private val shareFile: (File, String) -> Unit,
) {
    private val executor = Executors.newSingleThreadExecutor()
    private val keys = SecureApiKeyStore(activity)
    private val settings = SettingsStore(activity)
    private val projects = ProjectStore(activity)
    private val openAi = OpenAiProvider()
    private val gemini = GeminiProvider()
    private var project = ProjectRecord(
        speechLanguage = settings.speechLanguage,
        captionLanguage = settings.captionLanguage,
        targetGenerator = settings.targetGenerator,
        strictProductLock = settings.strictProductLock,
        provider = settings.provider,
    )

    fun persist() {
        projects.save(project)
    }

    fun onImagesPicked(uris: List<Uri>) {
        executor.execute {
            try {
                val remaining = ImageRules.MAX - project.images.size
                uris.take(remaining.coerceAtLeast(0)).forEach { uri ->
                    activity.contentResolver.openInputStream(uri)?.use { input ->
                        val originals = File(projects.dir(project.id), "originals").apply { mkdirs() }
                        val compressedDir = File(projects.dir(project.id), "compressed").apply { mkdirs() }
                        val original = File(originals, UUID.randomUUID().toString() + ".img")
                        original.outputStream().use { input.copyTo(it) }
                        val prepared = ImageProcessor.prepare(original, compressedDir, original.length())
                        val stored = StoredImage(
                            id = UUID.randomUUID().toString(),
                            originalPath = original.absolutePath,
                            compressedPath = prepared.file.absolutePath,
                            width = prepared.width,
                            height = prepared.height,
                            originalBytes = prepared.originalBytes,
                            compressedBytes = prepared.compressedBytes,
                        )
                        project.images.add(stored)
                    }
                }
                if (project.firstFrameId == null) {
                    project.firstFrameId = project.images.firstOrNull()?.id
                }
                persist()
                emit("images", snapshot())
            } catch (e: Exception) {
                emitError("image_decode_error", e.message ?: "image")
            }
        }
    }

    @JavascriptInterface
    fun ready() {
        emit("ready", snapshot())
    }

    @JavascriptInterface
    fun getState(): String = snapshot().toString()

    @JavascriptInterface
    fun saveSettings(json: String) {
        val obj = JSONObject(json)
        if (obj.has("appLanguage")) settings.appLanguage = obj.getString("appLanguage")
        if (obj.has("speechLanguage")) {
            settings.speechLanguage = obj.getString("speechLanguage")
            project.speechLanguage = settings.speechLanguage
        }
        if (obj.has("captionLanguage")) {
            settings.captionLanguage = obj.getString("captionLanguage")
            project.captionLanguage = settings.captionLanguage
        }
        if (obj.has("targetGenerator")) {
            settings.targetGenerator = obj.getString("targetGenerator")
            project.targetGenerator = settings.targetGenerator
        }
        if (obj.has("strictProductLock")) {
            settings.strictProductLock = obj.getBoolean("strictProductLock")
            project.strictProductLock = settings.strictProductLock
        }
        if (obj.has("provider")) {
            settings.provider = obj.getString("provider")
            project.provider = settings.provider
        }
        if (obj.has("privacyAccepted")) settings.privacyAccepted = obj.getBoolean("privacyAccepted")
        persist()
        emit("settings", snapshot())
    }

    @JavascriptInterface
    fun saveProviderKey(provider: String, key: String) {
        if (key.isBlank()) {
            emitError("NO_API_KEY", "empty")
            return
        }
        keys.save(provider, key.trim())
        emit("providerStatus", providerStatus())
    }

    @JavascriptInterface
    fun deleteProviderKey(provider: String) {
        keys.delete(provider)
        emit("providerStatus", providerStatus())
    }

    @JavascriptInterface
    fun testConnection(provider: String) {
        executor.execute {
            try {
                val apiKey = requireKey(provider)
                val result = providerOf(provider).testConnection(apiKey)
                emit("providerStatus", providerStatus().put("test", result))
            } catch (e: Exception) {
                emitError(e)
            }
        }
    }

    @JavascriptInterface
    fun startPickImages() {
        activity.runOnUiThread { pickImages() }
    }

    @JavascriptInterface
    fun removeImage(id: String) {
        val removingFirst = project.firstFrameId == id
        project.images.removeAll { it.id == id }
        if (removingFirst) {
            project.firstFrameId = project.images.firstOrNull()?.id
            project.firstFrameQuality = null
        }
        persist()
        emit("images", snapshot())
    }

    @JavascriptInterface
    fun clearImages() {
        val keepId = project.firstFrameId
        val keep = project.images.firstOrNull { it.id == keepId }
        project.images.clear()
        if (keep != null) project.images.add(keep)
        persist()
        emit("images", snapshot())
    }

    @JavascriptInterface
    fun newProject() {
        persist()
        project = ProjectRecord(
            speechLanguage = settings.speechLanguage,
            captionLanguage = settings.captionLanguage,
            targetGenerator = settings.targetGenerator,
            strictProductLock = settings.strictProductLock,
            provider = settings.provider,
        )
        persist()
        emit("project", snapshot())
    }

    @JavascriptInterface
    fun setFirstFrame(id: String) {
        project.firstFrameId = id
        project.firstFrameQuality = null
        persist()
        emit("firstFrame", snapshot())
    }

    @JavascriptInterface
    fun continueAnyway() {
        project.consistencyOverride = true
        persist()
        emit("consistency", snapshot())
    }

    @JavascriptInterface
    fun runConsistency() = runOp("consistency") {
        requireImages()
        val result = provider().consistencyCheck(apiKey(), apiImages())
        project.consistency = result
        project.consistencyOverride = false
        persist()
        snapshot()
    }

    @JavascriptInterface
    fun runAnalysis() = runOp("analysis") {
        requireImages()
        val result = provider().analyseProduct(apiKey(), apiImages())
        project.analysis = result
        persist()
        snapshot()
    }

    @JavascriptInterface
    fun runFirstFrameQuality() = runOp("firstFrameQuality") {
        val image = firstFrameImage() ?: throw ProviderException.generic("First Frame missing")
        val api = apiImages().first { it.id == image.id }
        project.firstFrameQuality = provider().firstFrameQuality(apiKey(), api)
        persist()
        snapshot()
    }

    @JavascriptInterface
    fun generateScene() = runOp("scene") {
        val analysis = project.analysis ?: throw ProviderException.generic("analysis_missing")
        project.scene = provider().generateScene(apiKey(), analysis, apiImages(), null)
        persist()
        snapshot()
    }

    @JavascriptInterface
    fun newScene() = runOp("scene") {
        val analysis = project.analysis ?: throw ProviderException.generic("analysis_missing")
        project.scene = provider().generateScene(apiKey(), analysis, apiImages(), project.scene)
        persist()
        snapshot()
    }

    @JavascriptInterface
    fun generatePrompt() = runOp("prompt") {
        val prompt = provider().generateVideoPrompt(apiKey(), listOfNotNull(firstApiImage()), ctx())
        project.finalPrompt = prompt
        persist()
        snapshot()
    }

    @JavascriptInterface
    fun improvePrompt() = runOp("prompt") {
        val current = project.finalPrompt ?: throw ProviderException.generic("prompt_missing")
        project.improvedPrompt = provider().improvePrompt(apiKey(), ctx().copy(currentPrompt = current))
        persist()
        snapshot()
    }

    @JavascriptInterface
    fun newSpeech() = runOp("prompt") {
        val current = project.improvedPrompt ?: project.finalPrompt ?: throw ProviderException.generic("prompt_missing")
        project.finalPrompt = provider().regenerateSpeech(apiKey(), ctx().copy(currentPrompt = current))
        persist()
        snapshot()
    }

    @JavascriptInterface
    fun generateCaption() = runOp("caption") {
        val result = provider().generateCaption(apiKey(), ctx())
        project.caption = result.optString("caption")
        val tags = result.optJSONArray("hashtags") ?: JSONArray()
        project.hashtags = MutableList(tags.length()) { tags.optString(it) }
        persist()
        snapshot()
    }

    @JavascriptInterface
    fun regenerateCaption() = generateCaption()

    @JavascriptInterface
    fun runCompliance() = runOp("compliance") {
        val prompt = activePrompt()
        val semantic = try {
            provider().checkCompliance(apiKey(), ctx().copy(currentPrompt = prompt))
        } catch (_: Exception) {
            null
        }
        project.compliance = ComplianceEngine.review(
            prompt = prompt,
            speech = prompt,
            caption = project.caption.orEmpty(),
            hashtags = project.hashtags,
            analysis = project.analysis,
            semantic = semantic,
        )
        persist()
        snapshot()
    }

    @JavascriptInterface
    fun addWerbung() {
        project.caption = ComplianceEngine.addWerbung(project.caption.orEmpty())
        persist()
        runCompliance()
    }

    @JavascriptInterface
    fun ignoreDisclosure() {
        emit("compliance", snapshot())
    }

    @JavascriptInterface
    fun copyText(value: String) {
        activity.runOnUiThread {
            val clipboard = activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("ugc", value))
            emit("copied", JSONObject().put("ok", true))
        }
    }

    @JavascriptInterface
    fun shareFirstFrame() {
        val image = firstFrameImage() ?: return
        shareFile(File(image.originalPath), "image/jpeg")
    }

    @JavascriptInterface
    fun shareProject() {
        val file = File(projects.dir(project.id), "project.json")
        shareFile(file, "application/json")
    }

    @JavascriptInterface
    fun saveProjectNow() {
        persist()
        emit("saved", snapshot())
    }

    @JavascriptInterface
    fun listHistory() {
        emit("history", JSONObject().put("items", projects.list()))
    }

    @JavascriptInterface
    fun openProject(id: String) {
        project = projects.load(id) ?: project
        emit("project", snapshot())
    }

    @JavascriptInterface
    fun duplicateProject(id: String) {
        val copy = projects.duplicate(id)
        if (copy != null) {
            emit("history", JSONObject().put("items", projects.list()).put("duplicated", copy.id))
        }
    }

    @JavascriptInterface
    fun deleteProject(id: String) {
        if (project.id == id) newProject()
        projects.delete(id)
        emit("history", JSONObject().put("items", projects.list()))
    }

    private fun runOp(event: String, block: () -> JSONObject) {
        executor.execute {
            try {
                emit("busy", JSONObject().put("op", event).put("busy", true))
                val payload = block()
                emit(event, payload)
            } catch (e: Exception) {
                emitError(e)
            } finally {
                emit("busy", JSONObject().put("op", event).put("busy", false))
            }
        }
    }

    private fun requireImages() {
        if (project.images.size < ImageRules.MIN) {
            throw ProviderException("NEED_IMAGES", "Provider Error", ImageRules.needMoreMessage())
        }
    }

    private fun apiKey(): String = requireKey(project.provider)

    private fun requireKey(provider: String): String {
        return keys.get(provider) ?: throw ProviderException.missingKey()
    }

    private fun provider() = providerOf(project.provider)

    private fun providerOf(provider: String) = if (provider.equals("GEMINI", true)) gemini else openAi

    private fun apiImages(): List<ApiImage> {
        var total = project.images.sumOf { it.compressedBytes }
        val prepared = project.images.mapIndexed { index, image ->
            var file = File(image.compressedPath)
            var w = image.width
            var h = image.height
            var compressed = image.compressedBytes
            if (total > 18_000_000) {
                val more = ImageProcessor.maybeCompressMore(
                    ImageProcessor.Prepared(file, w, h, image.originalBytes, compressed),
                    File(projects.dir(project.id), "compressed"),
                )
                file = more.file
                w = more.width
                h = more.height
                compressed = more.compressedBytes
            }
            val b64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
            ApiImage(image.id, index, "image/jpeg", b64, image.originalBytes, compressed, w, h)
        }
        return prepared
    }

    private fun firstFrameImage(): StoredImage? =
        project.images.firstOrNull { it.id == project.firstFrameId } ?: project.images.firstOrNull()

    private fun firstApiImage(): ApiImage? {
        val image = firstFrameImage() ?: return null
        return apiImages().firstOrNull { it.id == image.id }
    }

    private fun activePrompt(): String = project.improvedPrompt ?: project.finalPrompt.orEmpty()

    private fun ctx(): PromptContext = PromptContext(
        analysis = project.analysis?.toString() ?: "{}",
        scene = project.scene?.toString() ?: "{}",
        speechLanguage = project.speechLanguage,
        captionLanguage = project.captionLanguage,
        targetGenerator = project.targetGenerator,
        strictProductLock = project.strictProductLock,
        currentPrompt = activePrompt(),
    )

    private fun snapshot(): JSONObject {
        val images = JSONArray()
        project.images.forEach { image ->
            val file = File(image.compressedPath)
            val thumb = if (file.exists()) {
                val bytes = ImageProcessor.thumbnailJpeg(file)
                if (bytes.isEmpty()) "" else "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
            } else ""
            images.put(
                JSONObject()
                    .put("id", image.id)
                    .put("width", image.width)
                    .put("height", image.height)
                    .put("originalBytes", image.originalBytes)
                    .put("compressedBytes", image.compressedBytes)
                    .put("thumb", thumb)
                    .put("isFirstFrame", image.id == project.firstFrameId),
            )
        }
        return JSONObject()
            .put("project", project.toJson())
            .put("images", images)
            .put("settings", settings.toJson())
            .put("providerStatus", providerStatus())
            .put("policyVersion", TikTokShopPolicyConfig.VERSION)
            .put("policyUpdated", TikTokShopPolicyConfig.LAST_UPDATED)
            .put("analyseEnabled", de.spardirekt.ugcagent.v3.data.ImageRules.canAnalyse(project.images.size))
            .put("payload", payloadInfo())
            .put("activePrompt", activePrompt())
            .put("minImagesMessage", de.spardirekt.ugcagent.v3.data.ImageRules.needMoreMessage())
    }

    private fun payloadInfo(): JSONObject {
        val original = project.images.sumOf { it.originalBytes }
        val compressed = project.images.sumOf { it.compressedBytes }
        return JSONObject()
            .put("imageCount", project.images.size)
            .put("originalBytes", original)
            .put("compressedBytes", compressed)
            .put("warning", compressed > 18_000_000)
    }

    private fun providerStatus(): JSONObject = JSONObject()
        .put("OPENAI", statusFor("OPENAI"))
        .put("GEMINI", statusFor("GEMINI"))
        .put("active", project.provider)

    private fun statusFor(provider: String): JSONObject = JSONObject()
        .put("configured", keys.has(provider))
        .put("status", if (keys.has(provider)) "Configured" else "Not Configured")

    private fun emitError(error: Exception) {
        if (error is ProviderException) {
            emitError(error.code, error.message ?: error.statusLabel, error.statusLabel)
        } else {
            emitError("GENERIC", error.message ?: "error")
        }
    }

    private fun emitError(code: String, message: String, status: String = "Provider Error") {
        emit("error", JSONObject().put("code", code).put("message", message).put("status", status))
    }

    private fun emit(event: String, payload: JSONObject) {
        val encoded = Base64.encodeToString(payload.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val js = "window.UgcV3App && window.UgcV3App.onNativeEvent('$event', JSON.parse(atob('$encoded')))"
        webView.post { webView.evaluateJavascript(js, null) }
    }
}
