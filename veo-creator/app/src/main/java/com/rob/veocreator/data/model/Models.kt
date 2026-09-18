package com.rob.veocreator.data.model

import android.net.Uri
import java.util.UUID

enum class VideoMode { TEXT_TO_VIDEO, IMAGE_TO_VIDEO }

enum class VeoModel(
    val apiName: String,
    val displayName: String,
    val supportedResolutions: List<Resolution>
) {
    VEO_3_1(
        apiName = "veo-3.1-generate-preview",
        displayName = "Veo 3.1",
        supportedResolutions = listOf(Resolution.R720P, Resolution.R1080P, Resolution.R4K)
    ),
    VEO_3_1_FAST(
        apiName = "veo-3.1-fast-generate-preview",
        displayName = "Veo 3.1 Fast",
        supportedResolutions = listOf(Resolution.R720P, Resolution.R1080P, Resolution.R4K)
    ),
    VEO_3_1_LITE(
        apiName = "veo-3.1-lite-generate-preview",
        displayName = "Veo 3.1 Lite",
        supportedResolutions = listOf(Resolution.R720P, Resolution.R1080P)
    )
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
