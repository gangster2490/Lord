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
import de.spardirekt.ugcagent.v3.image.FirstFrameHeuristics
import de.spardirekt.ugcagent.v3.image.ImageProcessor
import de.spardirekt.ugcagent.v3.prompt.ActionIdentity
import de.spardirekt.ugcagent.v3.prompt.ProductIdentity
import de.spardirekt.ugcagent.v3.prompt.ProductLock
import de.spardirekt.ugcagent.v3.pipeline.PauseReasons
import de.spardirekt.ugcagent.v3.pipeline.PipelineAi
import de.spardirekt.ugcagent.v3.pipeline.PipelineEngine
import de.spardirekt.ugcagent.v3.pipeline.PipelineImage
import de.spardirekt.ugcagent.v3.pipeline.PipelinePaused
import de.spardirekt.ugcagent.v3.pipeline.PipelineSession
import de.spardirekt.ugcagent.v3.pipeline.PipelineStage
import de.spardirekt.ugcagent.v3.security.SecureApiKeyStore
import de.spardirekt.ugcagent.v3.text.Utf8Guard
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
                if (project.images.isNotEmpty() && project.firstFrameRecommendation?.optString("source") != "local+ai") {
                    val ranked = project.images.mapIndexed { index, image ->
                        FirstFrameHeuristics.RankedImage(
                            id = image.id,
                            index = index,
                            width = image.width,
                            height = image.height,
                            compressedBytes = image.compressedBytes,
                            score = FirstFrameHeuristics.score(image.width, image.height, image.compressedBytes),
                        )
                    }
                    project.recommendedFirstFrameId = FirstFrameHeuristics.recommendLocal(ranked)?.id
                }
                if (project.images.size in ImageRules.MIN..ImageRules.MAX && project.pipelineStage == "IDLE") {
                    project.pipelineStage = PipelineStage.IMAGES_READY.name
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
        project.firstFrameUserChosen = true
        persist()
        emit("firstFrame", snapshot())
    }

    @JavascriptInterface
    fun continueAnyway() {
        project.consistencyOverride = true
        persist()
        emit("consistency", snapshot())
        if (project.pipelineStage == PipelineStage.PAUSED.name &&
            (project.pausedReason == PauseReasons.DIFFERENT_PRODUCTS || project.pausedReason == PauseReasons.LOW_CONSISTENCY)
        ) {
            resumePipeline()
        }
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
        project.identityFingerprint = provider().productIdentityFingerprint(apiKey(), apiImages())
        refreshFirstFrameRecommendation()
        persist()
        snapshot()
    }

    @JavascriptInterface
    fun runFirstFrameQuality() = runOp("firstFrameQuality") {
        val image = firstFrameImage() ?: throw ProviderException.generic("First Frame missing")
        val local = FirstFrameHeuristics.check(image.width, image.height, image.compressedBytes)
        val api = apiImages().first { it.id == image.id }
        val ai = provider().firstFrameQuality(apiKey(), api)
        project.firstFrameQuality = FirstFrameHeuristics.merge(local, ai)
        persist()
        snapshot()
    }

    @JavascriptInterface
    fun generateScene() = runOp("scene") {
        val analysis = project.analysis ?: throw ProviderException.generic("analysis_missing")
        ensureFingerprint()
        val scene = provider().generateScene(apiKey(), analysis, apiImages(), null, project.identityFingerprint)
        project.scene = applyActionRisk(scene)
        persist()
        snapshot()
    }

    @JavascriptInterface
    fun newScene() = runOp("scene") {
        val analysis = project.analysis ?: throw ProviderException.generic("analysis_missing")
        ensureFingerprint()
        val scene = provider().generateScene(apiKey(), analysis, apiImages(), project.scene, project.identityFingerprint)
        project.scene = applyActionRisk(scene)
        persist()
        snapshot()
    }

    @JavascriptInterface
    fun generatePrompt() = runOp("prompt") {
        ensureFingerprint()
        val fingerprint = project.identityFingerprint ?: JSONObject()
        val localReady = ProductIdentity.localReadiness(fingerprint)
        val aiReady = try {
            provider().productIdentityReadiness(apiKey(), fingerprint, apiImages())
        } catch (_: Exception) {
            localReady
        }
        project.identityReadiness = ProductIdentity.mergeReadiness(localReady, aiReady)
        persist()
        val prompt = provider().generateVideoPrompt(apiKey(), imagesForPrompt(), ctx())
        project.finalPrompt = ProductLock.repairOnce(
            prompt,
            fingerprint,
            project.targetGenerator,
            project.speechLanguage,
            project.strictProductLock,
        )
        project.repairApplied = true
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
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("ugc", Utf8Guard.repair(value)))
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

    @JavascriptInterface
    fun startPipeline() = runOp("pipeline") {
        runAutomaticPipeline(resume = false)
    }

    @JavascriptInterface
    fun resumePipeline() = runOp("pipeline") {
        runAutomaticPipeline(resume = true)
    }

    private fun runAutomaticPipeline(resume: Boolean): JSONObject {
        val cachedImages = apiImages()
        val engine = PipelineEngine(LivePipelineAi(cachedImages))
        val session = sessionFromProject()
        val result = try {
            if (resume) engine.resume(session) else engine.start(session)
        } catch (paused: PipelinePaused) {
            session.stage = PipelineStage.PAUSED
            session.resumeStage = paused.stage
            session.pausedReason = paused.reason
            session
        } catch (e: Exception) {
            applySession(session)
            persist()
            throw e
        }
        applySession(result)
        persist()
        return snapshot()
    }

    private fun sessionFromProject(): PipelineSession {
        val session = PipelineSession()
        session.stage = PipelineStage.fromName(project.pipelineStage)
        session.resumeStage = project.resumeStage?.let { PipelineStage.fromName(it) }
        session.pausedReason = project.pausedReason
        session.errorMessage = project.pipelineError
        session.warnings = project.warnings.toMutableList()
        session.completed = PipelineSession.parseCompleted(JSONArray(project.completedStages))
        session.repairApplied = project.repairApplied
        session.firstFrameAutoApplied = project.firstFrameAutoApplied
        session.firstFrameUserChosen = project.firstFrameUserChosen
        session.firstFrameId = project.firstFrameId
        session.recommendedFirstFrameId = project.recommendedFirstFrameId
        session.consistencyOverride = project.consistencyOverride
        session.speechLanguage = project.speechLanguage
        session.captionLanguage = project.captionLanguage
        session.targetGenerator = project.targetGenerator
        session.strictProductLock = project.strictProductLock
        session.hasApiKey = keys.has(project.provider)
        session.images = project.images.mapIndexed { index, image ->
            PipelineImage(image.id, index, image.width, image.height, image.compressedBytes)
        }
        session.consistency = project.consistency
        session.analysis = project.analysis
        session.identityFingerprint = project.identityFingerprint
        session.identityReadiness = project.identityReadiness
        session.firstFrameQuality = project.firstFrameQuality
        session.firstFrameRecommendation = project.firstFrameRecommendation
        session.actionRisk = project.actionRisk
        session.scene = project.scene
        session.finalIdentityLock = project.finalIdentityLock
        session.finalPrompt = project.finalPrompt
        session.caption = project.caption
        session.hashtags = project.hashtags.toMutableList()
        session.compliance = project.compliance
        return session
    }

    private fun applySession(session: PipelineSession) {
        project.pipelineStage = session.stage.name
        project.resumeStage = session.resumeStage?.name
        project.pausedReason = session.pausedReason
        project.pipelineError = session.errorMessage
        project.warnings = session.warnings.toMutableList()
        project.completedStages = session.completed.map { it.name }.toMutableList()
        project.repairApplied = session.repairApplied
        project.firstFrameAutoApplied = session.firstFrameAutoApplied
        project.firstFrameId = session.firstFrameId ?: project.firstFrameId
        project.recommendedFirstFrameId = session.recommendedFirstFrameId
        project.consistency = session.consistency
        project.analysis = session.analysis
        project.identityFingerprint = session.identityFingerprint
        project.identityReadiness = session.identityReadiness
        project.firstFrameQuality = session.firstFrameQuality
        project.firstFrameRecommendation = session.firstFrameRecommendation
        project.actionRisk = session.actionRisk
        project.scene = session.scene
        project.finalIdentityLock = session.finalIdentityLock
        project.finalPrompt = session.finalPrompt?.let { Utf8Guard.repair(it) }
        project.caption = session.caption?.let { Utf8Guard.repair(it) }
        project.hashtags = session.hashtags.map { Utf8Guard.repair(it) }.toMutableList()
        project.compliance = session.compliance
        project.improvedPrompt = null
    }

    private inner class LivePipelineAi(private val images: List<ApiImage>) : PipelineAi {
        override fun consistencyCheck(): JSONObject = provider().consistencyCheck(apiKey(), images)
        override fun analyseProduct(): JSONObject = provider().analyseProduct(apiKey(), images)
        override fun fingerprint(): JSONObject = provider().productIdentityFingerprint(apiKey(), images)
        override fun readiness(fingerprint: JSONObject): JSONObject =
            provider().productIdentityReadiness(apiKey(), fingerprint, images)
        override fun recommendFirstFrame(): JSONObject = provider().recommendFirstFrame(apiKey(), images)
        override fun firstFrameQuality(imageIndex: Int): JSONObject {
            val image = images.getOrNull(imageIndex) ?: images.first()
            return provider().firstFrameQuality(apiKey(), image)
        }
        override fun generateScene(analysis: JSONObject, fingerprint: JSONObject, previous: JSONObject?): JSONObject =
            provider().generateScene(apiKey(), analysis, images, previous, fingerprint)
        override fun actionRisk(fingerprint: JSONObject, scene: JSONObject): JSONObject =
            provider().actionIdentityRiskCheck(apiKey(), fingerprint, scene, images)
        override fun generatePrompt(ctx: PromptContext): String = provider().generateVideoPrompt(apiKey(), imagesForPrompt(), ctx)
        override fun checkCompliance(prompt: String, analysis: JSONObject?, caption: String, hashtags: List<String>): JSONObject {
            val semantic = try {
                provider().checkCompliance(apiKey(), ctx().copy(currentPrompt = prompt, analysis = analysis?.toString() ?: "{}"))
            } catch (_: Exception) {
                null
            }
            return ComplianceEngine.review(prompt, prompt, caption, hashtags, analysis, semantic)
        }
        override fun generateCaption(ctx: PromptContext): JSONObject = provider().generateCaption(apiKey(), ctx)
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

    private fun activePrompt(): String = project.improvedPrompt ?: project.finalPrompt.orEmpty()

    private fun ctx(): PromptContext = PromptContext(
        analysis = project.analysis?.toString() ?: "{}",
        scene = project.scene?.toString() ?: "{}",
        speechLanguage = project.speechLanguage,
        captionLanguage = project.captionLanguage,
        targetGenerator = project.targetGenerator,
        strictProductLock = project.strictProductLock,
        currentPrompt = activePrompt(),
        fingerprint = project.identityFingerprint?.toString() ?: "{}",
        actionRisk = project.actionRisk?.toString() ?: "{}",
        readiness = project.identityReadiness?.toString() ?: "{}",
        finalIdentityLock = project.finalIdentityLock ?: ProductIdentity.finalIdentityLockBlock(project.identityFingerprint),
    )

    private fun ensureFingerprint() {
        if (project.identityFingerprint != null) return
        requireImages()
        project.identityFingerprint = provider().productIdentityFingerprint(apiKey(), apiImages())
        persist()
    }

    private fun applyActionRisk(scene: JSONObject): JSONObject {
        val fingerprint = project.identityFingerprint ?: JSONObject()
        val local = ActionIdentity.localCheck(scene.optString("main_action"), fingerprint)
        val ai = try {
            provider().actionIdentityRiskCheck(apiKey(), fingerprint, scene, apiImages())
        } catch (_: Exception) {
            local
        }
        val merged = ActionIdentity.merge(local, ai)
        project.actionRisk = merged
        return ActionIdentity.applyIfHighRisk(scene, merged)
    }

    private fun refreshFirstFrameRecommendation() {
        val ranked = project.images.mapIndexed { index, image ->
            FirstFrameHeuristics.RankedImage(
                id = image.id,
                index = index,
                width = image.width,
                height = image.height,
                compressedBytes = image.compressedBytes,
                score = FirstFrameHeuristics.score(image.width, image.height, image.compressedBytes),
            )
        }
        val local = FirstFrameHeuristics.recommendLocal(ranked)
        val ai = try {
            provider().recommendFirstFrame(apiKey(), apiImages())
        } catch (_: Exception) {
            null
        }
        val chosen = FirstFrameHeuristics.mergeRecommendation(
            local?.id,
            ai?.optInt("recommended_image_index", -1) ?: -1,
            ranked,
        )
        project.recommendedFirstFrameId = chosen?.id
        val rec = JSONObject()
            .put("recommended_image_index", chosen?.index ?: 0)
            .put("recommended_image_id", chosen?.id ?: "")
            .put("source", if (ai != null) "local+ai" else "local")
            .put("reasons", ai?.optJSONArray("reasons") ?: JSONArray().put("Largest clean visible product area among uploaded originals."))
        project.firstFrameRecommendation = rec
    }

    private fun imagesForPrompt(): List<ApiImage> {
        val all = apiImages()
        val firstId = firstFrameImage()?.id
        val first = all.firstOrNull { it.id == firstId }
        val rest = all.filter { it.id != firstId }
        return listOfNotNull(first) + rest
    }

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
            .put("recommendedFirstFrameId", project.recommendedFirstFrameId ?: "")
            .put("readinessWarningRu", ProductIdentity.warningFor(project.identityReadiness, "ru"))
            .put("readinessWarningDe", ProductIdentity.warningFor(project.identityReadiness, "de"))
            .put("pipelineStage", project.pipelineStage)
            .put("pausedReason", project.pausedReason ?: "")
            .put("pipelineError", project.pipelineError ?: "")
            .put("warnings", JSONArray(project.warnings))
            .put("finalIdentityLock", project.finalIdentityLock ?: "")
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
            val retryable = error.code == "TIMEOUT" || error.code == "NETWORK" || error.code == "RATE_LIMIT"
            emitError(error.code, error.message ?: error.statusLabel, error.statusLabel, retryable)
        } else {
            emitError("GENERIC", error.message ?: "error")
        }
    }

    private fun emitError(code: String, message: String, status: String = "Provider Error", retryable: Boolean = false) {
        emit(
            "error",
            JSONObject()
                .put("code", code)
                .put("message", message)
                .put("status", status)
                .put("retryable", retryable),
        )
    }

    private fun emit(event: String, payload: JSONObject) {
        val json = Utf8Guard.repair(Utf8Guard.repairJson(payload).toString())
        val encoded = Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val js = "(function(){try{var b=atob('$encoded');var u=new Uint8Array(b.length);for(var i=0;i<b.length;i++)u[i]=b.charCodeAt(i)&255;var t=(typeof TextDecoder!=='undefined')?new TextDecoder('utf-8').decode(u):decodeURIComponent(escape(b));window.UgcV3App&&window.UgcV3App.onNativeEvent('$event',JSON.parse(t));}catch(e){}}())"
        webView.post { webView.evaluateJavascript(js, null) }
    }
}
