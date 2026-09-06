package de.spardirekt.ugcclean.gen

import de.spardirekt.ugcclean.model.CopyPack
import de.spardirekt.ugcclean.model.PipelineStage
import de.spardirekt.ugcclean.model.ProductPlan
import de.spardirekt.ugcclean.model.SpeechLanguage
import de.spardirekt.ugcclean.net.ChatClient
import de.spardirekt.ugcclean.net.DemoClient
import de.spardirekt.ugcclean.net.Keys
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

class PipelineException(message: String) : Exception(message)

data class PipelineResult(
    val plan: ProductPlan,
    val veoPrompt: String,
    val copyPack: CopyPack,
)

fun interface ProgressSink {
    fun onProgress(stage: PipelineStage, percent: Int)
}

class Pipeline(
    private val liveClient: ChatClient,
    private val demoClient: ChatClient = DemoClient(),
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {
    suspend fun run(
        apiKey: String,
        imageDataUrls: List<String>,
        language: SpeechLanguage,
        progress: ProgressSink = ProgressSink { _, _ -> },
    ): PipelineResult {
        if (imageDataUrls.size !in StartGate.MIN_PHOTOS..StartGate.MAX_PHOTOS) {
            throw PipelineException("Bitte ${StartGate.MIN_PHOTOS}–${StartGate.MAX_PHOTOS} Fotos laden.")
        }
        progress.onProgress(PipelineStage.PHOTOS, 10)
        val client = if (Keys.isDemo(apiKey)) demoClient else liveClient
        progress.onProgress(PipelineStage.ANALYZE, 25)
        val plan = analyze(client, apiKey, imageDataUrls)
        progress.onProgress(PipelineStage.PROMPT, 65)
        val veoPrompt = PromptComposer.compose(plan, language)
        if (!PromptComposer.isCanonical(veoPrompt)) {
            throw PipelineException("Prompt-Struktur ungültig.")
        }
        progress.onProgress(PipelineStage.COPY, 80)
        val copy = copyPack(client, apiKey, plan, language)
        progress.onProgress(PipelineStage.DONE, 100)
        return PipelineResult(plan, veoPrompt, copy)
    }

    private suspend fun analyze(client: ChatClient, apiKey: String, images: List<String>): ProductPlan {
        val raw = client.chat(
            apiKey = apiKey,
            systemPrompt = SystemPrompts.ANALYZE,
            userText = "Analyze these ${images.size} photos of one product. First image is First Frame. JSON only.",
            imageDataUrls = images,
            jsonMode = true,
        )
        return parsePlan(raw)
    }

    private suspend fun copyPack(
        client: ChatClient,
        apiKey: String,
        plan: ProductPlan,
        language: SpeechLanguage,
    ): CopyPack {
        val lang = if (language == SpeechLanguage.DE) "German" else "Russian"
        val raw = client.chat(
            apiKey = apiKey,
            systemPrompt = SystemPrompts.COPY,
            userText = "Language: $lang\nWrite caption and hashtags for this plan:\n${plan.toLooseJson()}",
            imageDataUrls = emptyList(),
            jsonMode = true,
        )
        val obj = parseObject(raw)
        val caption = string(obj, "caption")
        val tags = (obj["hashtags"] as? JsonArray)?.mapNotNull { el ->
            when (el) {
                is JsonPrimitive -> el.contentOrNull
                else -> null
            }
        }.orEmpty()
        return Compliance.pack(caption, tags, plan, language)
    }

    fun parsePlan(raw: String): ProductPlan {
        val obj = parseObject(raw)
        val features = stringList(obj, "visibleFeatures").ifEmpty { stringList(obj, "visible_features") }
        val moving = stringList(obj, "movingParts").ifEmpty { stringList(obj, "moving_parts") }
        return ProductPlan(
            productName = string(obj, "productName").ifBlank { string(obj, "product_name") },
            category = string(obj, "category").ifBlank { "unknown" },
            visibleFeatures = features.take(10),
            movingParts = moving,
            useCase = string(obj, "useCase").ifBlank { string(obj, "use_case") },
            desire = string(obj, "desire"),
            setting = string(obj, "setting"),
            camera = string(obj, "camera"),
            action = string(obj, "action"),
            humanBehaviour = string(obj, "humanBehaviour").ifBlank { string(obj, "human_behaviour") },
            lighting = string(obj, "lighting"),
            spokenHookDe = string(obj, "spokenHookDe").ifBlank { string(obj, "spoken_hook_de") },
            spokenHookRu = string(obj, "spokenHookRu").ifBlank { string(obj, "spoken_hook_ru") },
        )
    }

    private fun parseObject(raw: String): JsonObject {
        val repaired = JsonExtractor.repairLiteralControlsInStrings(JsonExtractor.extractObject(raw))
        return try {
            json.parseToJsonElement(repaired) as? JsonObject
                ?: throw PipelineException("Modell lieferte kein JSON-Objekt.")
        } catch (e: PipelineException) {
            throw e
        } catch (e: Exception) {
            throw PipelineException("JSON ungültig: ${e.message}")
        }
    }

    private fun string(obj: JsonObject, key: String): String =
        (obj[key] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()

    private fun stringList(obj: JsonObject, key: String): List<String> {
        val el = obj[key] ?: return emptyList()
        return when (el) {
            is JsonArray -> el.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }.filter { it.isNotBlank() }
            is JsonPrimitive -> el.contentOrNull?.split(',', ';')?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
            else -> emptyList()
        }
    }

    private fun ProductPlan.toLooseJson(): String = buildString {
        append("{")
        append("\"productName\":${quote(productName)},")
        append("\"category\":${quote(category)},")
        append("\"useCase\":${quote(useCase)},")
        append("\"desire\":${quote(desire)},")
        append("\"setting\":${quote(setting)}")
        append("}")
    }

    private fun quote(value: String): String = JsonPrimitive(value).toString()
}
