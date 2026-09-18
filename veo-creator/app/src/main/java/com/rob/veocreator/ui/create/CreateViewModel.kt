package com.rob.veocreator.ui.create

import android.app.Application
import android.graphics.RectF
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rob.veocreator.VeoCreatorApp
import com.rob.veocreator.data.api.InlineImage
import com.rob.veocreator.data.api.OperationResult
import com.rob.veocreator.data.db.HistoryEntity
import com.rob.veocreator.data.model.ApiException
import com.rob.veocreator.data.model.AspectRatio
import com.rob.veocreator.data.model.Duration
import com.rob.veocreator.data.model.GenerationState
import com.rob.veocreator.data.model.ImageRole
import com.rob.veocreator.data.model.ModelCapabilities
import com.rob.veocreator.data.model.ModelChoice
import com.rob.veocreator.data.model.RequestMode
import com.rob.veocreator.data.model.Resolution
import com.rob.veocreator.data.model.SelectedImage
import com.rob.veocreator.data.model.VeoModel
import com.rob.veocreator.data.model.VideoMode
import com.rob.veocreator.data.model.generationStrategyLabel
import com.rob.veocreator.util.ImageLoadResult
import com.rob.veocreator.util.MIN_USEFUL_DIMENSION_PX
import com.rob.veocreator.util.MediaUtils
import com.rob.veocreator.util.ProductCropper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.math.roundToInt

/** Normalized full-frame rect - stored once a crop suggestion finds nothing worth cropping, so
 *  MediaUtils' "meaningful crop" check treats it as a no-op pass-through. */
private val FULL_IMAGE_RECT = RectF(0f, 0f, 1f, 1f)

private const val MAX_ANALYSIS_IMAGES = 8
private const val MAX_REFERENCE_IMAGES = 3
private const val POLL_INTERVAL_MS = 10_000L
private const val MAX_POLL_MINUTES = 10

/** Best-3 selection for referenceImages: excludes text-heavy roles entirely and prioritizes
 *  clean product photos (main/front view first, then alternate angle, then detail/open-state)
 *  over packaging or unclassified shots, per ImageRole.primaryScore. */
private fun selectReferenceImages(images: List<SelectedImage>): List<SelectedImage> =
    images.filterNot { it.role.isTextHeavy }
        .sortedByDescending { it.role.primaryScore }
        .take(MAX_REFERENCE_IMAGES)

data class CreateUiState(
    val mode: VideoMode = VideoMode.IMAGE_TO_VIDEO,
    val prompt: String = "",
    val modelChoice: ModelChoice = ModelChoice.AUTO_CHEAPEST,
    val aspectRatio: AspectRatio = AspectRatio.PORTRAIT_9_16,
    val resolution: Resolution = Resolution.R720P,
    val duration: Duration = Duration.D8,
    val images: List<SelectedImage> = emptyList(),
    val enableTextOverlays: Boolean = false,
    /** Off by default: use one starting image (IMAGE_TO_VIDEO). On: send up to 3 photos as
     *  Veo referenceImages instead - these two are mutually exclusive at the API level. Ignored
     *  while [productFidelityMode] is on, since that decides automatically. */
    val useMultipleImages: Boolean = false,
    /** On by default. Prioritizes exact product consistency over cost: never resolves to Veo
     *  3.1 Lite, and automatically uses REFERENCE_IMAGES the moment 2+ suitable photos exist
     *  instead of requiring the user to flip [useMultipleImages] manually. */
    val productFidelityMode: Boolean = true,
    /** True once the user has explicitly tapped a thumbnail to make it primary - after that,
     *  automatic re-scoring on analysis results must not override their choice. */
    val primaryManuallySet: Boolean = false,
    /** True once an AI-generated suggestion (which may carry extracted spec text) has been
     *  inserted into the prompt - shown in Technical Details for debugging prompt quality. */
    val textExtractionUsed: Boolean = false,
    val isAnalyzing: Boolean = false,
    val analysisSuggestion: String? = null,
    val generationState: GenerationState = GenerationState.Idle,
    val elapsedSeconds: Int = 0,
    val hasApiKey: Boolean = false
) {
    val canGenerate: Boolean
        get() = hasApiKey && prompt.isNotBlank() &&
            (mode == VideoMode.TEXT_TO_VIDEO || images.isNotEmpty()) &&
            generationState !is GenerationState.Submitting &&
            generationState !is GenerationState.Generating &&
            generationState !is GenerationState.Downloading &&
            generationState !is GenerationState.Uploading

    /** Which shape of request this configuration will actually produce. In Product Fidelity mode
     *  (the default), 2+ suitable photos automatically switch to CONSISTENCY regardless of the
     *  manual toggle; a single clean image always uses SAFE_PRODUCT (IMAGE_TO_VIDEO), since
     *  CONSISTENCY only makes sense once there's more than one usable photo to reference. */
    val requestMode: RequestMode
        get() {
            if (mode == VideoMode.TEXT_TO_VIDEO) return RequestMode.TEXT_TO_VIDEO
            val suitableCount = images.count { !it.role.isTextHeavy }
            val wantsReferenceImages = if (productFidelityMode) suitableCount > 1 else useMultipleImages
            return if (wantsReferenceImages && suitableCount > 1) RequestMode.REFERENCE_IMAGES else RequestMode.IMAGE_TO_VIDEO
        }

    /** Which photos actually get sent as Veo image data for the current configuration. */
    val sentImages: List<SelectedImage>
        get() = when (requestMode) {
            RequestMode.IMAGE_TO_VIDEO -> images.filter { it.isPrimary }
            RequestMode.REFERENCE_IMAGES -> selectReferenceImages(images)
            RequestMode.TEXT_TO_VIDEO -> emptyList()
        }

    /** How many of the uploaded photos would actually be sent as Veo referenceImages. */
    val referenceImageCount: Int
        get() = if (requestMode == RequestMode.REFERENCE_IMAGES) selectReferenceImages(images).size else 0

    val usesReferenceImages: Boolean get() = requestMode == RequestMode.REFERENCE_IMAGES

    private val costBasedModel: VeoModel get() = modelChoice.resolve(resolution, usesReferenceImages)

    /** The concrete Veo model actually used - resolved from [modelChoice] + [resolution], then
     *  transparently upgraded off Lite whenever referenceImages are required (Lite doesn't
     *  support them at all) or whenever Product Fidelity mode is on (Lite is never used when
     *  exact product consistency is required - Fast is the floor). */
    val effectiveModel: VeoModel
        get() {
            val base = costBasedModel
            return if (productFidelityMode && base == VeoModel.VEO_3_1_LITE) VeoModel.VEO_3_1_FAST else base
        }

    val estimatedCostUsd: Double? get() = effectiveModel.estimatedCost(resolution, duration)

    /** Non-null whenever the model actually used differs from what [modelChoice] alone would
     *  have picked, so the UI can explain why instead of leaving the user guessing. */
    val costExplanation: String?
        get() {
            val base = costBasedModel
            if (effectiveModel != base) {
                return "Using ${effectiveModel.displayName} for Product Fidelity mode - Lite is not used when exact product consistency is required."
            }
            val canOverride = modelChoice == ModelChoice.AUTO_CHEAPEST || modelChoice == ModelChoice.LITE
            if (!canOverride || effectiveModel == VeoModel.VEO_3_1_LITE) return null
            return if (usesReferenceImages) {
                "Switched to ${effectiveModel.displayName} because Lite does not support reference images."
            } else {
                "Veo 3.1 Lite doesn't support ${resolution.label} - using ${effectiveModel.displayName} instead."
            }
        }

    /** True when any transmitted image's crop, at its real pixel size, is too small to reliably
     *  preserve product detail - computed without any extra image decode (crop is a fraction of
     *  the already-known original dimensions). */
    val anySentImageLowRes: Boolean
        get() = sentImages.any { img ->
            val crop = img.cropRect ?: return@any false
            if (img.originalWidth <= 0 || img.originalHeight <= 0) return@any false
            val w = (crop.width() * img.originalWidth).roundToInt()
            val h = (crop.height() * img.originalHeight).roundToInt()
            minOf(w, h) < MIN_USEFUL_DIMENSION_PX
        }
}

class CreateViewModel(application: Application) : AndroidViewModel(application) {

    private val app get() = getApplication<VeoCreatorApp>()
    private val client get() = app.veoClient
    private val apiKeyStore get() = app.apiKeyStore

    private val _uiState = MutableStateFlow(CreateUiState())
    val uiState: StateFlow<CreateUiState> = _uiState.asStateFlow()

    private var generationJob: Job? = null

    fun refreshApiKeyState() {
        _uiState.update { it.copy(hasApiKey = apiKeyStore.hasApiKey()) }
    }

    fun setMode(mode: VideoMode) = _uiState.update { clampDurationForState(it.copy(mode = mode)) }

    fun setPrompt(text: String) = _uiState.update { it.copy(prompt = text) }

    fun clearPrompt() = _uiState.update { it.copy(prompt = "") }

    fun setModelChoice(choice: ModelChoice) = _uiState.update { state ->
        val allowedRes = choice.allowedResolutions()
        val resolution = if (state.resolution in allowedRes) state.resolution else allowedRes.first()
        val allowedDur = ModelCapabilities.allowedDurations(resolution, state.usesReferenceImages)
        val duration = if (state.duration in allowedDur) state.duration else allowedDur.last()
        state.copy(modelChoice = choice, resolution = resolution, duration = duration)
    }

    fun setAspectRatio(ratio: AspectRatio) = _uiState.update { it.copy(aspectRatio = ratio) }

    fun setResolution(resolution: Resolution) = _uiState.update { state ->
        val allowedDur = ModelCapabilities.allowedDurations(resolution, state.usesReferenceImages)
        val duration = if (state.duration in allowedDur) state.duration else allowedDur.last()
        state.copy(resolution = resolution, duration = duration)
    }

    fun setDuration(duration: Duration) = _uiState.update { it.copy(duration = duration) }

    /** Re-clamps duration to what's still allowed after the image set (and therefore
     *  referenceImageCount) changes - e.g. adding a 2nd/3rd image forces 8s. */
    private fun clampDurationForState(state: CreateUiState): CreateUiState {
        val allowedDur = ModelCapabilities.allowedDurations(state.resolution, state.usesReferenceImages)
        return if (state.duration in allowedDur) state else state.copy(duration = allowedDur.last())
    }

    fun setTextOverlaysEnabled(enabled: Boolean) = _uiState.update { it.copy(enableTextOverlays = enabled) }

    fun setUseMultipleImages(enabled: Boolean) =
        _uiState.update { clampDurationForState(it.copy(useMultipleImages = enabled)) }

    fun setProductFidelityMode(enabled: Boolean) =
        _uiState.update { clampDurationForState(it.copy(productFidelityMode = enabled)) }

    /** Picks the best starting/hero image by role score; ties keep the earliest-uploaded one. */
    private fun pickBestPrimaryId(images: List<SelectedImage>): String? =
        images.withIndex().maxByOrNull { (index, img) -> img.role.primaryScore * 1000 - index }?.value?.id

    private fun applyAutoPrimary(state: CreateUiState): CreateUiState {
        if (state.primaryManuallySet || state.images.isEmpty()) return state
        val bestId = pickBestPrimaryId(state.images) ?: return state
        return state.copy(images = state.images.map { it.copy(isPrimary = it.id == bestId) })
    }

    fun addImages(uris: List<Uri>) {
        var newOnes: List<SelectedImage> = emptyList()
        _uiState.update { state ->
            val existingIds = state.images.map { it.uri }.toSet()
            newOnes = uris.filterNot { it in existingIds }.map { uri ->
                val mime = app.contentResolver.getType(uri) ?: "image/jpeg"
                SelectedImage(uri = uri, mimeType = mime)
            }
            val combined = state.images + newOnes
            applyAutoPrimary(clampDurationForState(state.copy(images = combined, analysisSuggestion = null)))
        }
        // Role classification (Analyze & Enhance Prompt) stays manual-only - only compute the
        // auto-crop suggestion automatically, since that's needed just to know what gets sent.
        newOnes.forEach { computeCropSuggestion(it) }
    }

    /** Runs the offline heuristic crop suggestion for one image and records its original pixel
     *  size, unless the user has since set a manual crop for it. */
    private fun computeCropSuggestion(image: SelectedImage) {
        viewModelScope.launch(Dispatchers.Default) {
            val (width, height) = MediaUtils.decodeBounds(app, image.uri)
            val suggestion = ProductCropper.suggestCrop(app, image.uri) ?: FULL_IMAGE_RECT
            _uiState.update { state ->
                state.copy(images = state.images.map { img ->
                    if (img.id != image.id) return@map img
                    val newCrop = if (img.cropManuallySet) img.cropRect else suggestion
                    img.copy(cropRect = newCrop, originalWidth = width, originalHeight = height)
                })
            }
        }
    }

    fun setManualCrop(imageId: String, rect: RectF) = _uiState.update { state ->
        state.copy(images = state.images.map {
            if (it.id == imageId) it.copy(cropRect = rect, cropManuallySet = true) else it
        })
    }

    fun resetCropToAuto(imageId: String) {
        _uiState.update { state ->
            state.copy(images = state.images.map { if (it.id == imageId) it.copy(cropManuallySet = false) else it })
        }
        _uiState.value.images.find { it.id == imageId }?.let { computeCropSuggestion(it) }
    }

    fun removeImage(id: String) = _uiState.update { state ->
        val remaining = state.images.filterNot { it.id == id }
        val stillHasManualPrimary = remaining.any { it.isPrimary }
        applyAutoPrimary(
            clampDurationForState(
                state.copy(
                    images = remaining,
                    primaryManuallySet = state.primaryManuallySet && stillHasManualPrimary
                )
            )
        )
    }

    fun setPrimaryImage(id: String) = _uiState.update { state ->
        clampDurationForState(
            state.copy(images = state.images.map { it.copy(isPrimary = it.id == id) }, primaryManuallySet = true)
        )
    }

    fun dismissSuggestion() = _uiState.update { it.copy(analysisSuggestion = null) }

    fun insertSuggestion() = _uiState.update { state ->
        val suggestion = state.analysisSuggestion ?: return@update state
        val merged = if (state.prompt.isBlank()) suggestion else state.prompt.trimEnd() + "\n\n" + suggestion
        state.copy(prompt = merged, analysisSuggestion = null, textExtractionUsed = true)
    }

    /** Calls Gemini vision to classify each photo's role and extract confirmed spec text only. */
    fun analyzeImages() {
        val state = _uiState.value
        val apiKey = apiKeyStore.getApiKey() ?: return
        if (state.images.isEmpty() || state.isAnalyzing) return

        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzing = true) }
            val imagesToAnalyze = state.images.take(MAX_ANALYSIS_IMAGES)
            val inline = imagesToAnalyze.map { MediaUtils.loadInlineImage(app, it.uri) }

            val result = client.analyzeImages(apiKey, inline, state.prompt)
            result.onSuccess { analysis ->
                _uiState.update { current ->
                    val updatedImages = current.images.mapIndexed { idx, img ->
                        if (idx >= imagesToAnalyze.size) return@mapIndexed img
                        val entry = analysis.entries.find { it.index == idx + 1 } ?: return@mapIndexed img
                        img.copy(role = entry.role, extractedText = entry.extractedText, notes = entry.notes)
                    }
                    applyAutoPrimary(
                        current.copy(
                            images = updatedImages,
                            isAnalyzing = false,
                            analysisSuggestion = analysis.promptEnhancement
                        )
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(isAnalyzing = false) }
            }
        }
    }

    fun cancelGeneration() {
        generationJob?.cancel()
        generationJob = null
        _uiState.update { it.copy(generationState = GenerationState.Cancelled) }
    }

    fun resetToIdle() = _uiState.update { it.copy(generationState = GenerationState.Idle, elapsedSeconds = 0) }

    fun generate() {
        val state = _uiState.value
        if (!state.canGenerate) return
        val apiKey = apiKeyStore.getApiKey() ?: return

        generationJob = viewModelScope.launch {
            val elapsedJob = launch {
                var seconds = 0
                while (isActive) {
                    delay(1000)
                    seconds += 1
                    _uiState.update { it.copy(elapsedSeconds = seconds) }
                }
            }

            val requestMode = state.requestMode
            var technicalContext = buildTechnicalContext(state, requestMode, emptyList(), null)

            try {
                _uiState.update { it.copy(generationState = GenerationState.Uploading, elapsedSeconds = 0) }

                var primaryInline: InlineImage? = null
                var referenceInline: List<InlineImage> = emptyList()
                val diagnostics = mutableListOf<Pair<SelectedImage, ImageLoadResult>>()

                when (requestMode) {
                    RequestMode.IMAGE_TO_VIDEO -> {
                        val primary = state.images.firstOrNull { it.isPrimary } ?: state.images.firstOrNull()
                            ?: throw ApiException(null, "Please select a starting image.")
                        val loaded = MediaUtils.loadInlineImageWithDiagnostics(app, primary.uri, primary.cropRect)
                        primaryInline = loaded.inline
                        diagnostics += primary to loaded
                    }
                    RequestMode.REFERENCE_IMAGES -> {
                        val chosen = selectReferenceImages(state.images)
                        if (chosen.isEmpty()) {
                            throw ApiException(null, "Please select at least one product image.")
                        }
                        val loadedList = chosen.map { it to MediaUtils.loadInlineImageWithDiagnostics(app, it.uri, it.cropRect) }
                        referenceInline = loadedList.map { it.second.inline }
                        diagnostics += loadedList
                    }
                    RequestMode.TEXT_TO_VIDEO -> Unit
                }

                val finalPrompt = buildFinalPrompt(state, requestMode)
                technicalContext = buildTechnicalContext(state, requestMode, diagnostics, finalPrompt)

                _uiState.update { it.copy(generationState = GenerationState.Submitting) }

                val operationName = client.submitGeneration(
                    apiKey = apiKey,
                    model = state.effectiveModel,
                    prompt = finalPrompt,
                    requestMode = requestMode,
                    primaryImage = primaryInline,
                    referenceImages = referenceInline,
                    aspectRatio = state.aspectRatio,
                    duration = state.duration,
                    resolution = state.resolution
                ).getOrThrow()

                _uiState.update { it.copy(generationState = GenerationState.Generating(operationName)) }

                val deadline = System.currentTimeMillis() + MAX_POLL_MINUTES * 60_000L
                var videoUri: String? = null
                while (isActive && videoUri == null) {
                    if (System.currentTimeMillis() > deadline) {
                        throw ApiException(null, "Generation timed out after $MAX_POLL_MINUTES minutes.")
                    }
                    delay(POLL_INTERVAL_MS)
                    when (val result = client.pollOperation(apiKey, operationName).getOrThrow()) {
                        is OperationResult.Pending -> Unit
                        is OperationResult.Done -> videoUri = result.videoUri
                        is OperationResult.Failed -> throw ApiException(null, result.message)
                    }
                }

                val finalUri = videoUri ?: return@launch
                _uiState.update { it.copy(generationState = GenerationState.Downloading) }

                val destination = MediaUtils.newVideoCacheFile(app)
                client.downloadVideo(apiKey, finalUri, destination).getOrThrow()

                app.database.historyDao().insert(
                    HistoryEntity(
                        prompt = state.prompt,
                        createdAtMillis = System.currentTimeMillis(),
                        model = state.effectiveModel.displayName,
                        aspectRatio = state.aspectRatio.label,
                        durationSeconds = state.duration.seconds,
                        resolution = state.resolution.label,
                        mode = state.mode.name,
                        filePath = destination.absolutePath,
                        thumbnailPath = null
                    )
                )
                val count = app.database.historyDao().count()
                if (count > 100) app.database.historyDao().trimOldest(count - 100)

                _uiState.update { it.copy(generationState = GenerationState.Completed(destination.absolutePath)) }
            } catch (io: IOException) {
                _uiState.update {
                    it.copy(generationState = GenerationState.Error(
                        "Network unavailable. Check your connection and try again.",
                        technicalContext
                    ))
                }
            } catch (api: ApiException) {
                _uiState.update {
                    it.copy(generationState = GenerationState.Error(
                        api.message ?: "Generation failed.",
                        technicalContext + (api.technicalDetails?.let { d -> "\n\n$d" } ?: "")
                    ))
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.update {
                    it.copy(generationState = GenerationState.Error(e.message ?: "Unexpected error.", technicalContext))
                }
            } finally {
                elapsedJob.cancel()
            }
        }
    }

    /** Human-readable request context shown in the error card's "Technical details" section. */
    private fun buildTechnicalContext(
        state: CreateUiState,
        requestMode: RequestMode,
        diagnostics: List<Pair<SelectedImage, ImageLoadResult>>,
        finalPrompt: String?
    ): String {
        val personGeneration = if (requestMode == RequestMode.TEXT_TO_VIDEO) "allow_all" else "allow_adult"
        val primaryIndex = state.images.indexOfFirst { it.isPrimary }.let { if (it >= 0) it + 1 else null }
        val selectedReferenceIndexes = if (requestMode == RequestMode.REFERENCE_IMAGES) {
            selectReferenceImages(state.images).map { state.images.indexOf(it) + 1 }
        } else emptyList()

        return buildString {
            appendLine("Product Fidelity mode: ${if (state.productFidelityMode) "ON" else "OFF"}")
            appendLine("Model: ${state.effectiveModel.apiName}")
            appendLine("Model selection: ${state.modelChoice.label}")
            val cost = state.effectiveModel.estimatedCost(state.resolution, state.duration)
            appendLine("Estimated API cost: ${cost?.let { "$" + "%.2f".format(it) } ?: "n/a"}")
            state.costExplanation?.let { appendLine("Cost note: $it") }
            appendLine("Mode: ${requestMode.name}")
            appendLine("Generation mode: ${requestMode.generationStrategyLabel}")
            appendLine("Uploaded images: ${state.images.size}")
            appendLine("Reference images sent: ${state.referenceImageCount}")
            appendLine("Reference image support on selected model: ${state.effectiveModel.supportsReferenceImages}")
            appendLine("Starting image sent: ${requestMode == RequestMode.IMAGE_TO_VIDEO}")
            if (requestMode != RequestMode.TEXT_TO_VIDEO) {
                appendLine("Selected primary image index: ${primaryIndex ?: "n/a"}")
                appendLine("Selected reference image indexes: ${if (selectedReferenceIndexes.isEmpty()) "none" else selectedReferenceIndexes.joinToString()}")
                appendLine("Image roles: ${state.images.mapIndexed { i, img -> "${i + 1}=${img.role.name}" }.joinToString()}")
            }
            appendLine("Aspect ratio: ${state.aspectRatio.apiValue}")
            appendLine("Resolution: ${state.resolution.apiValue}")
            appendLine("Duration: ${state.duration.seconds}")
            appendLine("Duration JSON type: NUMBER")
            appendLine("Person generation: $personGeneration")
            appendLine("Transport: REST (Gemini API v1beta, x-goog-api-key)")
            appendLine("Image encoding: Veo Image object (bytesBase64Encoded + mimeType)")
            if (requestMode != RequestMode.TEXT_TO_VIDEO) {
                appendLine("MIME type: ${state.images.firstOrNull()?.mimeType ?: "n/a"}")
            }
            appendLine("Using Gemini Content inlineData: false")
            appendLine("numberOfVideos present: false")
            appendLine("Product lock: ON")
            appendLine("Text extraction influenced prompt: ${if (state.textExtractionUsed) "yes" else "no"}")
            if (diagnostics.isNotEmpty()) {
                appendLine("Image diagnostics:")
                diagnostics.forEach { (img, d) ->
                    val idx = state.images.indexOf(img) + 1
                    appendLine(" - img$idx (${img.role.name}):")
                    appendLine("   Original image: ${d.originalWidth}x${d.originalHeight} (${d.originalBytes / 1024} KB)")
                    appendLine("   Product crop: ${d.cropWidth}x${d.cropHeight}")
                    appendLine("   Product coverage: ${d.productCoveragePercent}%")
                    appendLine("   Image sent to Veo: ${d.transmittedWidth}x${d.transmittedHeight} (${d.transmittedBytes / 1024} KB)")
                    appendLine("   Resize: ${if (d.resizeApplied) "yes" else "no"}")
                    appendLine("   Compression: ${if (d.compressionApplied) "yes" else "no"}")
                    if (d.lowResolutionWarning) {
                        appendLine("   WARNING: Product image resolution is too low for reliable product consistency.")
                    }
                }
            }
            if (finalPrompt != null) {
                appendLine("Final generated prompt:")
                append(finalPrompt)
            } else {
                append("Final generated prompt: (not yet built)")
            }
        }
    }

    /**
     * Builds the prompt as four blocks: a hard product-fidelity constraint (always on for
     * image-based modes - this is "STRICT PRODUCT LOCK"), a neutral scene framing, the user's
     * own description, action guidance that only mentions an open/interior reveal when an
     * open-state reference photo actually exists, and finally the text-overlay guard.
     */
    private fun buildFinalPrompt(state: CreateUiState, requestMode: RequestMode): String {
        val sections = mutableListOf<String>()
        val hasOpenState = state.images.any { it.role == ImageRole.OPEN_CLOSED_STATE }

        // Product Fidelity mode replaces prompt engineering with a short, fixed instruction and
        // instead relies on the generation STRATEGY (Fast-or-better model, real referenceImages
        // when available) to hold product identity - a longer, more elaborate prompt didn't fix
        // drift once generation actually started, so this stays deliberately minimal.
        if (state.productFidelityMode && requestMode != RequestMode.TEXT_TO_VIDEO) {
            sections += productFidelityPromptBlock(state.aspectRatio.label, state.duration.seconds)
            if (state.prompt.isNotBlank()) sections += state.prompt.trim()
            if (!state.enableTextOverlays) {
                sections += "Do not render any on-screen text, captions, labels, price tags, banners, " +
                    "or logos in the video unless they are physically part of the product packaging " +
                    "already visible in the reference images."
            }
            return sections.joinToString("\n\n")
        }

        // CAMERA_ONLY_PRODUCT_AD: the strictest default, used whenever there's exactly one image
        // to go on and no visual evidence of an open/interior state - the single most common case
        // and the one most prone to hallucinated redesign, so only camera/lighting/background may move.
        val useCameraOnlyTemplate = requestMode == RequestMode.IMAGE_TO_VIDEO &&
            state.images.size <= 1 && !hasOpenState

        if (useCameraOnlyTemplate) {
            sections += CAMERA_ONLY_PRODUCT_LOCK_BLOCK
            sections += cameraOnlySceneBlock(state.aspectRatio.label, state.duration.seconds)
            if (state.prompt.isNotBlank()) sections += state.prompt.trim()
            sections += CAMERA_ONLY_RESTRICTIONS_BLOCK
        } else {
            if (requestMode != RequestMode.TEXT_TO_VIDEO) {
                sections += STRICT_PRODUCT_LOCK_BLOCK
            }
            sections += SCENE_BLOCK

            if (state.prompt.isNotBlank()) sections += state.prompt.trim()

            if (requestMode != RequestMode.TEXT_TO_VIDEO) {
                sections += if (hasOpenState) {
                    "If the product is shown closed in the reference image, you may show it opening " +
                        "naturally and reveal the interior exactly as visible in the reference images."
                } else {
                    "Do not show or invent an internal or open state that is not visible in the " +
                        "reference images. Keep the product in the state shown and focus on cinematic " +
                        "exterior product shots."
                }
            }
        }

        if (!state.enableTextOverlays) {
            sections += "Do not render any on-screen text, captions, labels, price tags, banners, " +
                "or logos in the video unless they are physically part of the product packaging " +
                "already visible in the reference images."
        }

        return sections.joinToString("\n\n")
    }

    private fun productFidelityPromptBlock(aspectRatioLabel: String, durationSeconds: Int): String =
        "Keep the exact same product from the uploaded reference images. " +
            "Preserve exact shape, proportions, colors, materials, handle, lid, body geometry and " +
            "all visible details. Do not redesign, replace or reinterpret the product. Do not add " +
            "new buttons, lights, handles or accessories. Create a realistic $durationSeconds-second " +
            "vertical $aspectRatioLabel product advertisement with subtle camera movement only."

    private fun cameraOnlySceneBlock(aspectRatioLabel: String, durationSeconds: Int): String {
        return "Create a $durationSeconds-second vertical $aspectRatioLabel commercial product shot. " +
            "Keep the exact same camera angle as the uploaded image for the entire video - do not " +
            "rotate, orbit, or reveal any side of the product not visible in the reference image. " +
            "Only perform a very slow digital zoom-in, subtle lighting enhancement, soft realistic " +
            "shadow changes, and slight background depth-of-field. The product itself must remain " +
            "unchanged from the first frame to the last frame of the video - the last frame must show " +
            "the exact same product as the first frame."
    }

    private companion object {
        // Deliberately generic ("the product", not any specific item's features) - this block runs
        // for every upload, so it must never assert appliance-specific details like colors or trim
        // that would be wrong for a different product. Product-specific details belong in the
        // user's own prompt text or in AI-extracted analysis, not in this constant.
        const val CAMERA_ONLY_PRODUCT_LOCK_BLOCK =
            "ABSOLUTE PRODUCT IDENTITY LOCK. The uploaded image is the exact visual master. Do not " +
                "recreate, reinterpret, or generate a new version of the product. The exact pixels " +
                "and visible geometry of the product must remain consistent throughout the entire " +
                "video. Preserve exactly the original silhouette, proportions, body shape, lid or top " +
                "shape, handle shape, size and position, colors, materials, hinge placement, visible " +
                "controls, feet, contours and all visible surface details. Do not redesign, " +
                "reinterpret, modernize or improve the product. Do not add parts. Do not remove parts. " +
                "Do not change geometry. Do not morph the product. Do not invent any feature that is " +
                "not clearly visible in the reference image."

        const val CAMERA_ONLY_RESTRICTIONS_BLOCK =
            "Photorealistic. Natural lighting. Clean e-commerce advertising style. FORBIDDEN: no " +
                "larger or differently shaped handle, no indicator lights, no buttons or controls not " +
                "in the reference image, no thicker or reshaped housing, no additional trim, seams, " +
                "or vents, no geometry reconstruction, no alternate product design, no rotation, no " +
                "orbit camera movement, no generating an unseen side of the product, no opening " +
                "animation, no invented interior, no additional accessories, no morphing between " +
                "frames, no food. ACCURACY OVER CREATIVITY."

        const val STRICT_PRODUCT_LOCK_BLOCK =
            "Strict product fidelity required. The generated video must depict the exact same " +
                "product as shown in the reference image(s), used as the ground-truth visual " +
                "reference. Preserve exact shape, proportions, color, materials, handles, hinges, " +
                "buttons, openings, cavities, compartments, controls, accessories and surface finish. " +
                "Do not redesign, reinterpret, or restyle the product. Do not add or remove parts. " +
                "Do not change its geometry or morph it between frames. Do not invent features that " +
                "are not visible in the reference images."

        const val SCENE_BLOCK =
            "Create a realistic product advertisement video. Show the product clearly and " +
                "naturally with clean lighting and realistic motion, modern e-commerce style."
    }
}
