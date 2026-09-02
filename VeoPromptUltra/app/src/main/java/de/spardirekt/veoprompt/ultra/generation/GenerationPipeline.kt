package de.spardirekt.veoprompt.ultra.generation

import android.content.Context
import de.spardirekt.veoprompt.ultra.compliance.TikTokShopComplianceAuditor
import de.spardirekt.veoprompt.ultra.config.ModelConfig
import de.spardirekt.veoprompt.ultra.diagnostics.AppError
import de.spardirekt.veoprompt.ultra.diagnostics.DebugLog
import de.spardirekt.veoprompt.ultra.model.AnalysisResult
import de.spardirekt.veoprompt.ultra.model.AppMode
import de.spardirekt.veoprompt.ultra.model.CreativeDirection
import de.spardirekt.veoprompt.ultra.model.CreativeMode
import de.spardirekt.veoprompt.ultra.model.GenerationBundle
import de.spardirekt.veoprompt.ultra.model.GenerationStage
import de.spardirekt.veoprompt.ultra.model.ImageClassification
import de.spardirekt.veoprompt.ultra.model.ImageType
import de.spardirekt.veoprompt.ultra.model.ProductModel
import de.spardirekt.veoprompt.ultra.model.ProjectImage
import de.spardirekt.veoprompt.ultra.model.SafetyAudit
import de.spardirekt.veoprompt.ultra.model.StructuredResponse
import de.spardirekt.veoprompt.ultra.model.VoiceLanguage
import de.spardirekt.veoprompt.ultra.network.ChatClient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class GenerationPipeline(
    private val openAi: ChatClient,
    private val imageEncoder: ImageDataUrlEncoder,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }
) {
    constructor(context: Context, openAi: ChatClient) : this(
        openAi = openAi,
        imageEncoder = ImageEncoder.androidEncoder(context)
    )

    data class PipelineInput(
        val projectId: String,
        val images: List<ProjectImage>,
        val optionalWish: String,
        val voiceLanguage: VoiceLanguage,
        val mode: AppMode,
        val creativeMode: CreativeMode,
        val tiktokShopMode: Boolean,
        val apiKey: String,
        val model: String,
        val resumeFrom: GenerationStage = GenerationStage.PHOTO_ANALYSIS,
        val existingAnalysisJson: String = "",
        val existingProductModelJson: String = "",
        val existingCreativePlanJson: String = "",
        val existingVeoPrompt: String = ""
    )

    data class StageUpdate(
        val stage: GenerationStage,
        val analysis: AnalysisResult? = null,
        val productModel: ProductModel? = null,
        val creativePlan: CreativeDirection? = null,
        val bundle: GenerationBundle? = null,
        val classifiedImages: List<ProjectImage>? = null
    )

    suspend fun run(
        input: PipelineInput,
        onStage: suspend (StageUpdate) -> Unit
    ): Result<GenerationBundle> {
        return try {
            val dataUrls = imageEncoder.encodeAll(input.images)
            if (dataUrls.isEmpty()) {
                return Result.failure(AppError.Unknown("Нет доступных изображений для анализа."))
            }

            var analysisJson = input.existingAnalysisJson
            var productModelJson = input.existingProductModelJson
            var creativePlanJson = input.existingCreativePlanJson
            var analysis: AnalysisResult? = null
            var productModel: ProductModel? = null
            var creativePlan: CreativeDirection? = null

            val startOrder = stageOrder(input.resumeFrom)

            if (GenerationStage.PHOTO_ANALYSIS in startOrder) {
                onStage(StageUpdate(GenerationStage.PHOTO_ANALYSIS))
                analysisJson = openAi.chat(
                    apiKey = input.apiKey,
                    model = input.model,
                    systemPrompt = PromptTemplates.photoAnalysisSystem(),
                    userText = buildAnalysisUserPrompt(input),
                    imageDataUrls = dataUrls,
                    timeoutSeconds = ModelConfig.photoAnalysisTimeoutSeconds(),
                    jsonMode = true,
                    maxTokens = 3500
                ).getOrElse { return fail(it) }

                analysis = decodeAnalysis(analysisJson)
                val classified = applyClassifications(input.images, analysis)
                onStage(
                    StageUpdate(
                        stage = GenerationStage.PHOTO_ANALYSIS,
                        analysis = analysis,
                        classifiedImages = classified
                    )
                )
            } else if (analysisJson.isNotBlank()) {
                analysis = decodeAnalysis(analysisJson)
            }

            if (GenerationStage.PRODUCT_MODEL in startOrder) {
                onStage(StageUpdate(GenerationStage.PRODUCT_MODEL, analysis = analysis))
                productModelJson = openAi.chat(
                    apiKey = input.apiKey,
                    model = input.model,
                    systemPrompt = PromptTemplates.productModelSystem(),
                    userText = "Analysis JSON:\n$analysisJson\n\nWish: ${input.optionalWish}\nCreative preference: ${input.creativeMode}",
                    imageDataUrls = emptyList(),
                    timeoutSeconds = ModelConfig.productModelTimeoutSeconds(),
                    jsonMode = true,
                    maxTokens = 3000
                ).getOrElse { return fail(it) }
                productModel = decodeProductModel(productModelJson)
                onStage(StageUpdate(GenerationStage.PRODUCT_MODEL, analysis, productModel))
            } else if (productModelJson.isNotBlank()) {
                productModel = decodeProductModel(productModelJson)
            }

            if (GenerationStage.VISUAL_LOCK in startOrder) {
                onStage(StageUpdate(GenerationStage.VISUAL_LOCK, analysis, productModel))
            }

            if (GenerationStage.CREATIVE_DIRECTOR in startOrder) {
                onStage(StageUpdate(GenerationStage.CREATIVE_DIRECTOR, analysis, productModel))
                val forced = if (input.creativeMode != CreativeMode.AUTO) {
                    "User selected creative mode: ${input.creativeMode.name}. Prefer this unless evidence makes it unsafe."
                } else {
                    "User selected AUTO. Prefer simple high-fidelity concepts. Do not default to Lifestyle. Pattern: ${CreativeDirectorRules.preferredPattern(productModel ?: ProductModel())}"
                }
                creativePlanJson = openAi.chat(
                    apiKey = input.apiKey,
                    model = input.model,
                    systemPrompt = PromptTemplates.creativeDirectorSystem(input.creativeMode),
                    userText = "Product model:\n$productModelJson\n\n$forced\nWish: ${input.optionalWish}\nMode: ${input.mode}\nTikTok Shop Mode: ${input.tiktokShopMode}",
                    timeoutSeconds = ModelConfig.creativeDirectorTimeoutSeconds(),
                    jsonMode = true,
                    maxTokens = 2000
                ).getOrElse { return fail(it) }
                creativePlan = CreativeDirectorRules.sanitizePlan(
                    input.creativeMode,
                    decodeCreativePlan(creativePlanJson)
                )
                creativePlanJson = json.encodeToString(creativePlan)
                onStage(StageUpdate(GenerationStage.CREATIVE_DIRECTOR, analysis, productModel, creativePlan))
            } else if (creativePlanJson.isNotBlank()) {
                creativePlan = decodeCreativePlan(creativePlanJson)
            }

            onStage(StageUpdate(GenerationStage.FINAL_PROMPT, analysis, productModel, creativePlan))
            val finalJson = openAi.chat(
                apiKey = input.apiKey,
                model = input.model,
                systemPrompt = PromptTemplates.finalPromptSystem(input.voiceLanguage, input.tiktokShopMode),
                userText = buildFinalUserPrompt(
                    productModelJson = productModelJson,
                    creativePlanJson = creativePlanJson,
                    analysisJson = analysisJson,
                    wish = input.optionalWish,
                    voice = input.voiceLanguage,
                    tiktok = input.tiktokShopMode
                ),
                imageDataUrls = emptyList(),
                timeoutSeconds = ModelConfig.finalPromptTimeoutSeconds(),
                jsonMode = true,
                temperature = 0.5,
                maxTokens = 4500
            ).getOrElse { return fail(it) }

            var structured = StructuredResponseParser.parse(finalJson)
            val model = productModel ?: ProductModel()
            structured = FinalPromptValidator.localRepair(
                structured, model, input.voiceLanguage, input.tiktokShopMode
            )

            onStage(StageUpdate(GenerationStage.FINAL_VALIDATION, analysis, productModel, creativePlan))
            var report = FinalPromptValidator.validate(
                structured, model, input.voiceLanguage, input.tiktokShopMode
            )
            if (!report.ok) {
                val failed = FinalPromptValidator.failedFields(report)
                DebugLog.d("Targeted repair for: $failed")
                onStage(StageUpdate(GenerationStage.TARGETED_REPAIR, analysis, productModel, creativePlan))
                val repairedRaw = openAi.chat(
                    apiKey = input.apiKey,
                    model = input.model,
                    systemPrompt = PromptTemplates.targetedRepairSystem(failed),
                    userText = "Current JSON:\n${json.encodeToString(report.response)}\nProduct model:\n$productModelJson\nFailed fields: $failed",
                    timeoutSeconds = ModelConfig.targetedRepairTimeoutSeconds(),
                    jsonMode = true,
                    maxTokens = 4000,
                    maxAttempts = 1
                ).getOrElse { return fail(it) }
                val repairedParsed = StructuredResponseParser.parse(repairedRaw)
                structured = mergeRepairedFields(report.response, repairedParsed, failed)
                structured = FinalPromptValidator.localRepair(
                    structured, model, input.voiceLanguage, input.tiktokShopMode
                )
                report = FinalPromptValidator.validate(
                    structured, model, input.voiceLanguage, input.tiktokShopMode
                )
            }

            if (!report.ok) {
                DebugLog.e("Validation failed after repair: ${report.issues}")
                return Result.failure(
                    AppError.Unknown("Проверка точности не пройдена: ${report.issues.joinToString { it.reason }}")
                )
            }

            val compliant = TikTokShopComplianceAuditor.audit(
                response = report.response,
                productModel = model,
                voice = input.voiceLanguage,
                tiktokShopMode = input.tiktokShopMode
            )
            val finalResponse = compliant.response
            if (finalResponse.veoPrompt.isBlank()) {
                return Result.failure(AppError.Unknown("Пустой veoPrompt."))
            }

            onStage(StageUpdate(GenerationStage.FINALIZATION, analysis, productModel, creativePlan))
            val bundle = GenerationBundle(
                veoPrompt = finalResponse.veoPrompt,
                voiceover = finalResponse.voiceover,
                title = finalResponse.title,
                hashtags = finalResponse.hashtags,
                productModelJson = productModelJson,
                creativePlanJson = creativePlanJson,
                analysisJson = analysisJson,
                safetyAudit = compliant.audit.ifEmptyDefault()
            )
            onStage(StageUpdate(GenerationStage.DONE, analysis, productModel, creativePlan, bundle))
            Result.success(bundle)
        } catch (t: Throwable) {
            fail(t)
        }
    }

    private fun fail(t: Throwable): Result<GenerationBundle> =
        Result.failure(t as? AppError ?: AppError.Unknown(t.message.orEmpty()))

    private fun stageOrder(from: GenerationStage): Set<GenerationStage> {
        val all = listOf(
            GenerationStage.PHOTO_ANALYSIS,
            GenerationStage.PRODUCT_MODEL,
            GenerationStage.VISUAL_LOCK,
            GenerationStage.CREATIVE_DIRECTOR,
            GenerationStage.FINAL_PROMPT
        )
        val mapped = when (from) {
            GenerationStage.IDLE, GenerationStage.FAILED -> GenerationStage.PHOTO_ANALYSIS
            GenerationStage.TARGETED_REPAIR,
            GenerationStage.FINAL_VALIDATION,
            GenerationStage.FINALIZATION,
            GenerationStage.DONE -> GenerationStage.FINAL_PROMPT
            else -> from
        }
        val idx = all.indexOf(mapped).coerceAtLeast(0)
        return all.drop(idx).toSet()
    }

    private fun buildAnalysisUserPrompt(input: PipelineInput): String {
        val list = input.images.mapIndexed { i, _ ->
            "img_${i + 1}: uploaded media (treat unknown category initially)"
        }.joinToString("\n")
        return """
Analyze all images together. No Primary/Main reference concept.
Images:
$list
Optional wish: ${input.optionalWish.ifBlank { "(none)" }}
TikTok Shop Mode: ${input.tiktokShopMode}
""".trimIndent()
    }

    private fun buildFinalUserPrompt(
        productModelJson: String,
        creativePlanJson: String,
        analysisJson: String,
        wish: String,
        voice: VoiceLanguage,
        tiktok: Boolean
    ): String {
        return """
Create the final VEO 3.1 package.

PRODUCT MODEL:
$productModelJson

CREATIVE PLAN:
$creativePlanJson

ANALYSIS SUMMARY:
$analysisJson

OPTIONAL WISH: ${wish.ifBlank { "(none)" }}
VOICE: ${voice.name}
TIKTOK SHOP MODE: $tiktok

TITLE and HASHTAGS are separate JSON fields. Do not put them inside veoPrompt.
PRODUCT LOCK must include product-specific visualSignature details.
""".trimIndent()
    }

    private fun mergeRepairedFields(
        original: StructuredResponse,
        repaired: StructuredResponse,
        failed: List<String>
    ): StructuredResponse {
        var out = original
        failed.forEach { field ->
            out = when (field) {
                "veoPrompt" -> out.copy(veoPrompt = repaired.veoPrompt.ifBlank { out.veoPrompt })
                "voiceover" -> out.copy(voiceover = repaired.voiceover.ifBlank { out.voiceover })
                "title" -> out.copy(title = repaired.title.ifBlank { out.title })
                "hashtags" -> out.copy(hashtags = repaired.hashtags.ifEmpty { out.hashtags })
                "safetyAudit" -> out.copy(safetyAudit = repaired.safetyAudit)
                else -> out.copy(veoPrompt = repaired.veoPrompt.ifBlank { out.veoPrompt })
            }
        }
        return out
    }

    private fun decodeAnalysis(raw: String): AnalysisResult {
        val payload = JsonExtractor.extract(raw)
        return runCatching {
            json.decodeFromString(AnalysisResult.serializer(), payload)
        }.getOrElse {
            val structured = StructuredResponseParser.parse(raw)
            AnalysisResult(
                productCategory = structured.imageAnalysis.productCategory,
                productIdentity = structured.imageAnalysis.productIdentity,
                visualSignature = structured.imageAnalysis.visualSignature,
                verifiedFeatures = structured.imageAnalysis.verifiedFeatures,
                uncertainFacts = structured.imageAnalysis.uncertainFacts,
                imageTypes = structured.imageAnalysis.imageTypes,
                summary = raw.replace(Regex("sk-[A-Za-z0-9_\\-]{8,}"), "sk-••••")
            )
        }
    }

    private fun decodeProductModel(raw: String): ProductModel {
        val payload = JsonExtractor.extract(raw)
        return runCatching {
            json.decodeFromString(ProductModel.serializer(), payload)
        }.getOrElse { ProductModel(productIdentity = "unknown") }
    }

    private fun decodeCreativePlan(raw: String): CreativeDirection {
        val payload = JsonExtractor.extract(raw)
        return runCatching {
            json.decodeFromString(CreativeDirection.serializer(), payload)
        }.getOrElse { CreativeDirection() }
    }

    private fun applyClassifications(
        images: List<ProjectImage>,
        analysis: AnalysisResult
    ): List<ProjectImage> {
        val types = analysis.imageTypes.ifEmpty {
            analysis.visualFacts.mapIndexed { i, _ ->
                ImageClassification(imageId = "img_${i + 1}", category = ImageType.UNKNOWN.name)
            }
        }
        return images.mapIndexed { index, img ->
            val id = "img_${index + 1}"
            val catName = types.firstOrNull {
                it.imageId.equals(id, true) || it.imageId == img.id
            }?.category
            img.copy(category = ImageType.fromRaw(catName), orderIndex = index)
        }
    }

    private fun SafetyAudit.ifEmptyDefault(): SafetyAudit =
        if (riskLevel.isBlank()) copy(riskLevel = "LOW") else this
}
