package de.spardirekt.veoprompt.ultra.model

import kotlinx.serialization.Serializable

enum class ImageType {
    PRODUCT_PHOTO,
    PRODUCT_DETAIL,
    PRODUCT_DEMO,
    PRODUCT_DESCRIPTION,
    MARKETPLACE_LISTING,
    LIFESTYLE_REFERENCE,
    UNKNOWN;

    fun badgeLabel(): String = when (this) {
        PRODUCT_PHOTO -> "Product"
        PRODUCT_DETAIL -> "Detail"
        PRODUCT_DEMO -> "Demo"
        PRODUCT_DESCRIPTION -> "Description"
        MARKETPLACE_LISTING -> "Listing"
        LIFESTYLE_REFERENCE -> "Lifestyle"
        UNKNOWN -> "Unknown"
    }

    companion object {
        fun fromRaw(raw: String?): ImageType {
            val value = raw?.trim()?.uppercase().orEmpty()
                .replace('-', '_')
                .replace(' ', '_')
            return when (value) {
                "PRODUCT_PHOTO", "PRODUCT" -> PRODUCT_PHOTO
                "PRODUCT_DETAIL", "PRODUCT_DETAIL_PHOTO", "DETAIL" -> PRODUCT_DETAIL
                "PRODUCT_DEMO", "PRODUCT_DEMO_PHOTO", "DEMO" -> PRODUCT_DEMO
                "PRODUCT_DESCRIPTION", "DESCRIPTION" -> PRODUCT_DESCRIPTION
                "MARKETPLACE_LISTING", "LISTING" -> MARKETPLACE_LISTING
                "LIFESTYLE_REFERENCE", "LIFESTYLE" -> LIFESTYLE_REFERENCE
                else -> UNKNOWN
            }
        }
    }
}

enum class VoiceLanguage { DE, RU, OFF }

enum class AppMode { Simple, Advanced }

enum class CreativeMode {
    AUTO,
    SHOWCASE,
    DEMO,
    LIFESTYLE,
    MACRO,
    PROBLEM_SOLUTION,
    SATISFYING,
    UNBOXING;

    fun uiLabel(): String = when (this) {
        AUTO -> "Auto"
        SHOWCASE -> "Showcase"
        DEMO -> "Demo"
        LIFESTYLE -> "Lifestyle"
        MACRO -> "Macro"
        PROBLEM_SOLUTION -> "Problem"
        SATISFYING -> "Satisfying"
        UNBOXING -> "Unboxing"
    }

    companion object {
        fun fromRaw(raw: String?): CreativeMode {
            val value = raw?.trim()?.uppercase().orEmpty()
                .replace('-', '_')
                .replace(' ', '_')
                .replace("/", "_")
            return when (value) {
                "AUTO", "AUTOMATIC" -> AUTO
                "SHOWCASE" -> SHOWCASE
                "DEMO" -> DEMO
                "LIFESTYLE" -> LIFESTYLE
                "MACRO" -> MACRO
                "PROBLEM_SOLUTION", "PROBLEM", "PROBLEMSOLUTION" -> PROBLEM_SOLUTION
                "SATISFYING" -> SATISFYING
                "UNBOXING" -> UNBOXING
                else -> AUTO
            }
        }
    }
}

enum class ProjectStatus {
    Draft,
    Generating,
    Ready,
    Failed
}

enum class GenerationStage {
    IDLE,
    PHOTO_ANALYSIS,
    PRODUCT_MODEL,
    VISUAL_LOCK,
    CREATIVE_DIRECTOR,
    FINAL_PROMPT,
    FINAL_VALIDATION,
    FINALIZATION,
    TARGETED_REPAIR,
    DONE,
    FAILED
}

enum class Confidence { HIGH, MEDIUM, LOW }

@Serializable
data class ProjectImage(
    val id: String,
    val uri: String,
    val localPath: String? = null,
    val category: ImageType = ImageType.UNKNOWN,
    val orderIndex: Int = 0
)

@Serializable
data class ConfidenceFact(
    val fact: String = "",
    val confidence: String = "LOW",
    val source: String = ""
) {
    fun level(): Confidence = when (confidence.trim().uppercase()) {
        "HIGH" -> Confidence.HIGH
        "MEDIUM" -> Confidence.MEDIUM
        else -> Confidence.LOW
    }
}

@Serializable
data class ImageClassification(
    val imageId: String = "",
    val category: String = ImageType.UNKNOWN.name,
    val notes: String = ""
)

@Serializable
data class ProductModel(
    val productCategory: String = "",
    val productIdentity: String = "",
    val visualSignature: List<String> = emptyList(),
    val confirmedParts: List<String> = emptyList(),
    val confirmedColors: List<String> = emptyList(),
    val confirmedMaterials: List<String> = emptyList(),
    val confirmedStates: List<String> = emptyList(),
    val confirmedFunctions: List<String> = emptyList(),
    val confirmedAccessories: List<String> = emptyList(),
    val confirmedMarkings: List<String> = emptyList(),
    val descriptionEvidence: List<String> = emptyList(),
    val uncertainFacts: List<String> = emptyList(),
    val unsafeAssumptions: List<String> = emptyList(),
    val highRiskHallucinations: List<String> = emptyList(),
    val possibleUseCases: List<String> = emptyList(),
    val imageClassifications: List<ImageClassification> = emptyList(),
    val hasMarketplaceScreenshots: Boolean = false
)

@Serializable
data class AnalysisResult(
    val productCategory: String = "",
    val productIdentity: String = "",
    val visualSignature: List<String> = emptyList(),
    val verifiedFeatures: List<String> = emptyList(),
    val uncertainFacts: List<String> = emptyList(),
    val imageTypes: List<ImageClassification> = emptyList(),
    val visualFacts: List<ConfidenceFact> = emptyList(),
    val textFacts: List<ConfidenceFact> = emptyList(),
    val marketplaceDetected: Boolean = false,
    val summary: String = ""
)

@Serializable
data class CreativeDirection(
    val selectedMode: String = CreativeMode.SHOWCASE.name,
    val heroFeature: String = "",
    val reason: String = "",
    val setting: String = "premium studio",
    val hookIdea: String = "",
    val useHands: Boolean = false,
    val usePeople: Boolean = false
)

@Serializable
data class AigcReport(
    val policyVersion: String = "",
    val verdict: String = "",
    val disclosureRequired: Boolean = true,
    val shopPublishSafe: Boolean = true,
    val findings: List<String> = emptyList(),
    val checklist: List<String> = emptyList(),
    val publishSteps: List<String> = emptyList()
)

@Serializable
data class SafetyAudit(
    val riskLevel: String = "LOW",
    val items: List<String> = emptyList(),
    val policyVersion: String = "",
    val aigcPolicyVersion: String = "",
    val aigc: AigcReport = AigcReport()
)

@Serializable
data class ImageAnalysisBlock(
    val productCategory: String = "",
    val productIdentity: String = "",
    val visualSignature: List<String> = emptyList(),
    val verifiedFeatures: List<String> = emptyList(),
    val uncertainFacts: List<String> = emptyList(),
    val imageTypes: List<ImageClassification> = emptyList()
)

@Serializable
data class StructuredResponse(
    val imageAnalysis: ImageAnalysisBlock = ImageAnalysisBlock(),
    val creativeDirection: CreativeDirection = CreativeDirection(),
    val veoPrompt: String = "",
    val voiceover: String = "",
    val title: String = "",
    val hashtags: List<String> = emptyList(),
    val safetyAudit: SafetyAudit = SafetyAudit()
)

@Serializable
data class GenerationBundle(
    val veoPrompt: String = "",
    val voiceover: String = "",
    val title: String = "",
    val hashtags: List<String> = emptyList(),
    val productModelJson: String = "",
    val creativePlanJson: String = "",
    val analysisJson: String = "",
    val safetyAudit: SafetyAudit = SafetyAudit()
)
