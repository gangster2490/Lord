package de.spardirekt.recipeveo.network

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Offline stand-in for OpenAI that returns valid JSON for every pipeline stage.
 * Selected when the stored API key starts with `sk-demo`.
 */
object DemoChatClient : ChatClient {

    override suspend fun chat(
        apiKey: String,
        model: String,
        systemPrompt: String,
        userText: String,
        imageDataUrls: List<String>,
        timeoutSeconds: Long,
        jsonMode: Boolean,
        temperature: Double,
        maxTokens: Int,
        maxAttempts: Int,
        reasoningEffort: String?
    ): Result<String> {
        val blob = "$systemPrompt\n$userText"
        return Result.success(
            when (detectStage(blob)) {
                Stage.PHOTO_ANALYSIS -> PHOTO_ANALYSIS_JSON
                Stage.PRODUCT_MODEL -> PRODUCT_MODEL_JSON
                Stage.CREATIVE_DIRECTOR -> CREATIVE_DIRECTOR_JSON
                Stage.VOICEOVER -> voiceoverJson(blob)
                Stage.FINAL_PROMPT, Stage.TARGETED_REPAIR -> FINAL_PROMPT_JSON
            }
        )
    }

    enum class Stage {
        PHOTO_ANALYSIS,
        PRODUCT_MODEL,
        CREATIVE_DIRECTOR,
        FINAL_PROMPT,
        TARGETED_REPAIR,
        VOICEOVER
    }

    fun detectStage(blob: String): Stage = when {
        blob.contains("CURRENT STAGE: PHOTO_ANALYSIS") -> Stage.PHOTO_ANALYSIS
        blob.contains("CURRENT STAGE: PRODUCT_MODEL") -> Stage.PRODUCT_MODEL
        blob.contains("CURRENT STAGE: CREATIVE_DIRECTOR") -> Stage.CREATIVE_DIRECTOR
        blob.contains("CURRENT STAGE: TARGETED_REPAIR") -> Stage.TARGETED_REPAIR
        blob.contains("CURRENT STAGE: FINAL_PROMPT") -> Stage.FINAL_PROMPT
        blob.contains("YOU WRITE ONLY THE SPOKEN VOICEOVER", ignoreCase = true) -> Stage.VOICEOVER
        blob.contains("Write the spoken voiceover", ignoreCase = true) -> Stage.VOICEOVER
        else -> Stage.FINAL_PROMPT
    }

    private fun voiceoverJson(blob: String): String {
        val line = when {
            blob.contains("VOICE LANGUAGE: OFF") ||
                blob.contains("Voice is OFF") -> "OFF"
            blob.contains("VOICE LANGUAGE: RU") ||
                blob.contains("Russian only") -> RU_VOICEOVER
            else -> DE_VOICEOVER
        }
        return buildJsonObject {
            put("voiceover", JsonPrimitive(line))
        }.toString()
    }

    private const val DE_VOICEOVER =
        "Der goldene Deckel und die cremige Textur bleiben sichtbar. Schau ihn dir im TikTok Shop an."

    private const val RU_VOICEOVER =
        "Золотая крышка и кремовая текстура остаются на виду вечером. Загляни скорее в TikTok Shop."

    private val VEO_PROMPT = """
FORMAT
Vertical 9:16. Photorealistic TikTok Shop ad. Exactly 8.0s.

REFERENCES
Uploaded product photos confirm a gold-lid ivory cream jar.

PRODUCT LOCK
Match uploaded product photos exactly. Do not replace or redesign.
gold cap, ivory jar, cream texture, short jar silhouette, visible brand mark

SETTING
Uncluttered premium studio.

SHOT SEQUENCE
0.0–2.0s — HOOK: gold lid catch light
2.0–4.0s — IDENTITY: full ivory jar
4.0–6.0s — FEATURE / DEMO: one hand lifts lid
6.0–8.0s — HERO / CTA: jar hero hold

ON-SCREEN TEXT
None.

VOICEOVER
$DE_VOICEOVER

AUDIO
Subtle music. Clear voice.

CRITICAL
Keep product identity. Exactly 8.0s. Four blocks only.

NEGATIVE PROMPT
- no generic replacement product
- no redesign / wrong proportions / colors / materials
- no missing confirmed parts or invented accessories
- no product morphing
- no marketplace UI or phone interface
- no CGI/cartoon look

TITLE
Velvet Gold Night Cream

HASHTAGS
#TikTokShop #NightCream #GoldLid #Skincare #ShopNow
""".trimIndent()

    private val PHOTO_ANALYSIS_JSON = """
{
  "summary": "Luxury night cream in an ivory jar with a gold lid, photographed as a packshot.",
  "classifications": [
    {"imageId":"img_1","category":"PRODUCT_PHOTO","notes":"centered packshot of the cream jar"}
  ],
  "visualFacts": [
    {"fact":"ivory jar body","confidence":"HIGH","source":"photo"},
    {"fact":"gold screw lid","confidence":"HIGH","source":"photo"},
    {"fact":"cream visible at the rim","confidence":"MEDIUM","source":"photo"}
  ],
  "textFacts": [],
  "marketplaceDetected": false
}
""".trimIndent()

    private val PRODUCT_MODEL_JSON = """
{
  "productCategory": "skincare cream",
  "productIdentity": "Velvet Gold Night Cream",
  "visualSignature": ["gold lid", "ivory jar", "short silhouette", "cream texture", "visible brand mark"],
  "confirmedParts": ["jar", "lid"],
  "confirmedMaterials": ["glass-like jar", "metallic lid"],
  "confirmedColors": ["ivory", "gold"],
  "confirmedStates": ["closed jar"],
  "confirmedFunctions": ["night cream"],
  "confirmedAccessories": [],
  "confirmedMarkings": ["front label"],
  "visualEvidence": ["packshot of closed jar"],
  "descriptionEvidence": [],
  "listingOnlyFacts": [],
  "possibleUseCases": ["evening skincare ritual"],
  "unsafeAssumptions": [],
  "highRiskHallucinations": ["pump dispenser", "dropper"],
  "imageClassifications": [
    {"imageId":"img_1","category":"PRODUCT_PHOTO","notes":"packshot"}
  ],
  "hasMarketplaceScreenshots": false
}
""".trimIndent()

    private val CREATIVE_DIRECTOR_JSON = """
{
  "strategy": "HighPerformingProductAd",
  "heroFeature": "gold lid catch-light and cream texture",
  "setting": "premium studio",
  "salesAngle": "cinematic night ritual for the cream",
  "hookIdea": "macro catch-light on the gold lid",
  "useHands": true,
  "usePeople": false,
  "rationale": "Clean packshot supports a high-performing 8s product ad without lifestyle extras."
}
""".trimIndent()

    private val FINAL_PROMPT_JSON = buildJsonObject {
        put("veoPrompt", JsonPrimitive(VEO_PROMPT))
        put("voiceover", JsonPrimitive(DE_VOICEOVER))
        put("title", JsonPrimitive("Velvet Gold Night Cream"))
        put(
            "hashtags",
            JsonArray(
                listOf(
                    JsonPrimitive("#TikTokShop"),
                    JsonPrimitive("#NightCream"),
                    JsonPrimitive("#GoldLid"),
                    JsonPrimitive("#Skincare"),
                    JsonPrimitive("#ShopNow")
                )
            )
        )
        put(
            "qualityScores",
            buildJsonObject {
                put("productFidelity", JsonPrimitive(9))
                put("creativity", JsonPrimitive(8))
                put("physicalPlausibility", JsonPrimitive(8))
                put("voiceoverNaturalness", JsonPrimitive(8))
                put("hookStrength", JsonPrimitive(8))
            }
        )
        put("internalSafetyAudit", JsonPrimitive("internal only"))
    }.toString()
}
