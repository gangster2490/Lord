package com.rob.veocreator.data.model

import android.net.Uri
import java.util.UUID

enum class VideoMode { TEXT_TO_VIDEO, IMAGE_TO_VIDEO }

/**
 * The Veo REST API treats a starting "image" and "referenceImages" as mutually exclusive -
 * https://ai.google.dev/gemini-api/docs/veo shows them in separate, non-overlapping examples.
 * Sending both in the same instance is what previously caused a 400 INVALID_ARGUMENT
 * "Unsupported video generation request" response.
 */
enum class RequestMode { TEXT_TO_VIDEO, IMAGE_TO_VIDEO, REFERENCE_IMAGES }

/** Internal naming for the two image-based strategies, shown in Technical Details. */
val RequestMode.generationStrategyLabel: String
    get() = when (this) {
        RequestMode.TEXT_TO_VIDEO -> "TEXT_TO_VIDEO"
        RequestMode.IMAGE_TO_VIDEO -> "SAFE_PRODUCT"
        RequestMode.REFERENCE_IMAGES -> "CONSISTENCY"
    }

enum class VeoModel(
    val apiName: String,
    val displayName: String,
    val supportedResolutions: List<Resolution>,
    /** Cheapest first - used by ModelChoice.AUTO_CHEAPEST to pick the lowest-cost compatible model. */
    val costRank: Int,
    /** Veo 3.1 Lite supports image input and lastFrame but NOT referenceImages - only Fast and
     *  Standard do. Routing must never send referenceImages to a model where this is false. */
    val supportsReferenceImages: Boolean,
    private val pricePerSecondByResolution: Map<Resolution, Double>
) {
    VEO_3_1_LITE(
        apiName = "veo-3.1-lite-generate-preview",
        displayName = "Veo 3.1 Lite",
        supportedResolutions = listOf(Resolution.R720P, Resolution.R1080P),
        costRank = 0,
        supportsReferenceImages = false,
        pricePerSecondByResolution = mapOf(Resolution.R720P to 0.05, Resolution.R1080P to 0.08)
    ),
    VEO_3_1_FAST(
        apiName = "veo-3.1-fast-generate-preview",
        displayName = "Veo 3.1 Fast",
        supportedResolutions = listOf(Resolution.R720P, Resolution.R1080P, Resolution.R4K),
        costRank = 1,
        supportsReferenceImages = true,
        pricePerSecondByResolution = mapOf(Resolution.R720P to 0.10, Resolution.R1080P to 0.12, Resolution.R4K to 0.30)
    ),
    VEO_3_1(
        apiName = "veo-3.1-generate-preview",
        displayName = "Veo 3.1",
        supportedResolutions = listOf(Resolution.R720P, Resolution.R1080P, Resolution.R4K),
        costRank = 2,
        supportsReferenceImages = true,
        pricePerSecondByResolution = mapOf(Resolution.R720P to 0.40, Resolution.R1080P to 0.40, Resolution.R4K to 0.60)
    );

    /** Per https://ai.google.dev/gemini-api/docs/pricing - null if this model/resolution pair is unsupported. */
    fun pricePerSecond(resolution: Resolution): Double? = pricePerSecondByResolution[resolution]

    fun estimatedCost(resolution: Resolution, duration: Duration): Double? =
        pricePerSecond(resolution)?.times(duration.seconds)
}

/** What the user picks in the Model chip row. AUTO_CHEAPEST is the default and resolves to the
 *  lowest-cost VeoModel that still supports the currently selected resolution. */
enum class ModelChoice(val label: String) {
    AUTO_CHEAPEST("AUTO — Cheapest"),
    LITE("Veo 3.1 Lite"),
    FAST("Veo 3.1 Fast"),
    STANDARD("Veo 3.1");

    /**
     * The concrete model this choice maps to for the given resolution and whether the request
     * needs referenceImages. Veo 3.1 Lite never supports referenceImages, so a fixed LITE choice
     * (or AUTO) transparently upgrades to the cheapest model that does - Fast first, then
     * Standard - whenever [requiresReferenceImages] is true. A fixed choice whose model doesn't
     * support the resolution (shouldn't happen - the UI disables that combination) falls back the
     * same way.
     */
    fun resolve(resolution: Resolution, requiresReferenceImages: Boolean = false): VeoModel {
        val fixed = when (this) {
            AUTO_CHEAPEST -> null
            LITE -> VeoModel.VEO_3_1_LITE
            FAST -> VeoModel.VEO_3_1_FAST
            STANDARD -> VeoModel.VEO_3_1
        }
        val fixedIsCompatible = fixed != null &&
            resolution in fixed.supportedResolutions &&
            (!requiresReferenceImages || fixed.supportsReferenceImages)
        if (fixedIsCompatible) return fixed!!

        return VeoModel.entries
            .filter { resolution in it.supportedResolutions && (!requiresReferenceImages || it.supportsReferenceImages) }
            .minByOrNull { it.costRank }
            ?: VeoModel.VEO_3_1
    }

    /** Every resolution supported by at least one model - AUTO can always find a compatible model,
     *  so all resolutions stay selectable; a fixed choice is limited to what that model supports. */
    fun allowedResolutions(): List<Resolution> = when (this) {
        AUTO_CHEAPEST -> Resolution.entries.filter { res -> VeoModel.entries.any { res in it.supportedResolutions } }
        LITE -> VeoModel.VEO_3_1_LITE.supportedResolutions
        FAST -> VeoModel.VEO_3_1_FAST.supportedResolutions
        STANDARD -> VeoModel.VEO_3_1.supportedResolutions
    }
}

enum class AspectRatio(val apiValue: String, val label: String) {
    PORTRAIT_9_16("9:16", "9:16"),
    LANDSCAPE_16_9("16:9", "16:9")
}

enum class Duration(val seconds: Int) {
    D4(4), D6(6), D8(8);
    val apiValue: String get() = seconds.toString()
    val label: String get() = "${seconds}s"
}

enum class Resolution(val apiValue: String, val label: String) {
    R720P("720p", "720p"),
    R1080P("1080p", "1080p"),
    R4K("4k", "4K")
}

/**
 * Only 720p supports the shorter 4s/6s durations; 1080p and 4k require the full 8s.
 * Requests that include referenceImages are also restricted to 8s regardless of resolution,
 * per https://ai.google.dev/gemini-api/docs/veo ("8 seconds only if ... using reference images").
 */
object ModelCapabilities {
    fun allowedResolutions(model: VeoModel): List<Resolution> = model.supportedResolutions

    fun allowedDurations(resolution: Resolution, usesReferenceImages: Boolean = false): List<Duration> =
        if (resolution == Resolution.R720P && !usesReferenceImages) listOf(Duration.D4, Duration.D6, Duration.D8)
        else listOf(Duration.D8)

    fun isCombinationValid(
        model: VeoModel,
        resolution: Resolution,
        duration: Duration,
        usesReferenceImages: Boolean = false
    ): Boolean {
        if (resolution !in allowedResolutions(model)) return false
        return duration in allowedDurations(resolution, usesReferenceImages)
    }
}

enum class ImageRole(val label: String) {
    UNANALYZED("Unclassified"),
    MAIN_PRODUCT_PHOTO("Main product photo"),
    ALTERNATE_ANGLE("Alternate angle"),
    DETAIL_PHOTO("Detail photo"),
    OPEN_CLOSED_STATE("Open/closed state"),
    ACCESSORIES("Accessories"),
    PACKAGING("Packaging"),
    SPECIFICATION_IMAGE("Specification image"),
    DESCRIPTION_IMAGE("Description image"),
    DIMENSION_IMAGE("Dimension image"),
    OTHER("Other");

    val isTextHeavy: Boolean
        get() = this == SPECIFICATION_IMAGE || this == DESCRIPTION_IMAGE || this == DIMENSION_IMAGE

    /**
     * How suitable this role is as the starting/hero image for Veo. Text-heavy roles score
     * negative so they are never picked as primary even if every other image is missing.
     */
    val primaryScore: Int
        get() = when (this) {
            MAIN_PRODUCT_PHOTO -> 100
            ALTERNATE_ANGLE -> 70
            OPEN_CLOSED_STATE -> 65
            DETAIL_PHOTO -> 50
            ACCESSORIES -> 30
            PACKAGING -> 20
            OTHER -> 10
            UNANALYZED -> 5
            SPECIFICATION_IMAGE, DESCRIPTION_IMAGE, DIMENSION_IMAGE -> -100
        }
}

data class SelectedImage(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val mimeType: String,
    val role: ImageRole = ImageRole.UNANALYZED,
    val extractedText: List<String> = emptyList(),
    val notes: String? = null,
    val isPrimary: Boolean = false
)

data class ImageAnalysisEntry(
    val index: Int,
    val role: ImageRole,
    val extractedText: List<String>,
    val notes: String?
)

data class ImageAnalysisResult(
    val entries: List<ImageAnalysisEntry>,
    val promptEnhancement: String?
)

sealed class GenerationState {
    data object Idle : GenerationState()
    data object Uploading : GenerationState()
    data object Submitting : GenerationState()
    data class Generating(val operationName: String) : GenerationState()
    data object Downloading : GenerationState()
    data class Completed(val videoFilePath: String) : GenerationState()
    data class Error(val message: String, val technicalDetails: String? = null) : GenerationState()
    data object Cancelled : GenerationState()
}

class ApiException(
    val httpCode: Int?,
    message: String,
    val technicalDetails: String? = null
) : Exception(message)
