package de.spardirekt.agents.pro.network

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
        val panCase = apiKey.contains("pan", ignoreCase = true) ||
            blob.contains("deep black pan", ignoreCase = true) ||
            blob.contains("wooden crossbar lid", ignoreCase = true)
        return Result.success(
            when (detectStage(blob)) {
                Stage.PHOTO_ANALYSIS -> if (panCase) PAN_PHOTO_ANALYSIS_JSON else PHOTO_ANALYSIS_JSON
                Stage.PRODUCT_MODEL -> if (panCase) PAN_PRODUCT_MODEL_JSON else PRODUCT_MODEL_JSON
                Stage.CREATIVE_DIRECTOR ->
                    if (panCase) PAN_CREATIVE_DIRECTOR_JSON else CREATIVE_DIRECTOR_JSON
                Stage.VOICEOVER -> voiceoverJson(blob, panCase)
                Stage.FINAL_PROMPT, Stage.TARGETED_REPAIR ->
                    if (panCase) PAN_FINAL_PROMPT_JSON else FINAL_PROMPT_JSON
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

    private fun voiceoverJson(blob: String, panCase: Boolean): String {
        val line = when {
            blob.contains("VOICE LANGUAGE: OFF") ||
                blob.contains("Voice is OFF") -> "OFF"
            panCase && (
                blob.contains("VOICE LANGUAGE: RU") ||
                    blob.contains("Russian only")
                ) -> PAN_RU_VOICEOVER
            panCase -> PAN_DE_VOICEOVER
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

    private const val PAN_DE_VOICEOVER =
        "Tiefe Form, Holzgriff und markanter Deckel bleiben im Blick. Entdecke sie im TikTok Shop."

    private const val PAN_RU_VOICEOVER =
        "Глубокая форма, деревянная ручка и необычная крышка всегда на виду. Загляните в TikTok Shop."

    private val PAN_VEO_PROMPT = """
FORMAT
Vertical 9:16. Photorealistic TikTok Shop product ad. Exactly 8.0 seconds with realistic materials, stable product geometry, and natural commercial lighting.

REFERENCES
All uploaded full-product and detail photos collectively confirm one deep black pan with a deep bowl, high curved sides, a long dark wooden handle, hanging ring, gold-tone ferrule, riveted shank, and a fitted wooden crossbar lid.

PRODUCT LOCK
Match uploaded product photos exactly. Do not replace or redesign.
Preserve the deep black bowl, high curved sides, exact rim profile, long dark wooden handle, hanging ring, gold-tone ferrule, visible riveted shank, wooden crossbar lid, component placement, proportions, colors, and photographed material finishes in every shot.

SETTING
Warm uncluttered kitchen counter with directional window light, realistic contact shadows, and enough clear space to keep the complete pan silhouette and handle visible.

SHOT SEQUENCE
0.0–2.0s — HOOK: begin on the hanging ring and travel along the dark wooden handle to the gold-tone ferrule and visible rivets while the pan remains physically rigid.
2.0–4.0s — IDENTITY: pull back to a complete three-quarter view showing the deep black bowl, high curved sides, full long handle, and wooden crossbar lid in exact photographed proportions.
4.0–6.0s — FEATURE / DEMO: one hand lifts and returns the wooden crossbar lid with a simple plausible motion; the ferrule, riveted shank, handle, ring, bowl geometry, and lid construction never change.
6.0–8.0s — HERO / CTA: settle into a stable full-product hold with the bowl, curved sides, wooden handle assembly, hanging ring, ferrule, rivets, and wooden crossbar lid all unobstructed.

ON-SCREEN TEXT
Deep black form
Wooden detail

VOICEOVER
$PAN_DE_VOICEOVER

AUDIO
Quiet kitchen room tone, restrained music, and soft natural wood contact as the lid is returned; no invented latch or mechanical click.

CRITICAL
Keep the same exact pan in all four blocks. Never flatten the deep bowl, remove or shorten the handle, move the rivets or ring, recolor the ferrule, substitute another lid, or simplify any photographed component.

NEGATIVE PROMPT
- no shallow frying pan, saucepan, wok, or generic replacement cookware
- no missing, shortened, recolored, or metal substitute handle
- no missing hanging ring, gold-tone ferrule, or visible riveted shank
- no missing, round generic, glass, metal, or redesigned lid
- no changed deep bowl profile, high curved sides, rim, proportions, or component placement
- no altered black finish, dark wood tone, gold-tone hardware, or photographed material texture
- no duplicated pan, detached hardware, floating parts, or product morphing
- no invented controls, accessories, branding, text, steam, food, flame, or unsupported function
- no marketplace listing, phone frame, price, badge, seller text, button, or interface
- no impossible hand anatomy, impossible mechanics, CGI, illustration, or cartoon rendering

TITLE
Deep Black Pan with Wooden Crossbar Lid

HASHTAGS
#DeepBlackPan #WoodenLid #KitchenDesign #CookwareDetails #TikTokShop
""".trimIndent()

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

    private val PAN_PHOTO_ANALYSIS_JSON = """
{
  "summary": "Deep black pan with a deep high-sided bowl, long dark wooden handle, gold-tone ferrule, riveted shank, hanging ring, and wooden crossbar lid.",
  "classifications": [
    {"imageId":"img_1","category":"PRODUCT_PHOTO","notes":"complete pan and handle assembly"},
    {"imageId":"img_2","category":"PRODUCT_DETAIL_PHOTO","notes":"wooden lid, ferrule, rivets, and hanging ring"}
  ],
  "visualFacts": [
    {"fact":"deep black bowl with high curved sides","confidence":"HIGH","source":"photo"},
    {"fact":"long dark wooden handle with hanging ring","confidence":"HIGH","source":"photo"},
    {"fact":"gold-tone ferrule and visible riveted shank","confidence":"HIGH","source":"photo"},
    {"fact":"wooden crossbar lid","confidence":"HIGH","source":"photo"}
  ],
  "textFacts": [],
  "marketplaceDetected": false
}
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

    private val PAN_PRODUCT_MODEL_JSON = """
{
  "productCategory": "pan",
  "productIdentity": "Deep Black Pan with Wooden Crossbar Lid",
  "visualSignature": ["deep black bowl", "high curved sides", "long dark wooden handle", "hanging ring", "gold-tone ferrule", "riveted shank", "wooden crossbar lid"],
  "confirmedParts": ["deep bowl", "long handle", "hanging ring", "ferrule", "riveted shank", "wooden crossbar lid"],
  "confirmedMaterials": ["black finished pan body", "dark wood handle", "wood lid", "gold-tone metal ferrule"],
  "confirmedColors": ["deep black", "dark wood", "gold-tone hardware"],
  "confirmedStates": ["lid seated", "lid lifted by one hand"],
  "confirmedFunctions": ["removable lid"],
  "confirmedAccessories": ["wooden crossbar lid"],
  "confirmedMarkings": [],
  "visualEvidence": ["complete product view", "handle and hardware detail", "wooden lid detail"],
  "descriptionEvidence": [],
  "listingOnlyFacts": [],
  "possibleUseCases": ["kitchen product showcase"],
  "unsafeAssumptions": ["specific coating", "heat rating", "dishwasher safety"],
  "highRiskHallucinations": ["shallow frying pan", "saucepan", "wok", "metal handle", "glass lid"],
  "imageClassifications": [
    {"imageId":"img_1","category":"PRODUCT_PHOTO","notes":"complete pan"},
    {"imageId":"img_2","category":"PRODUCT_DETAIL_PHOTO","notes":"handle and lid detail"}
  ],
  "hasMarketplaceScreenshots": false
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

    private val PAN_CREATIVE_DIRECTOR_JSON = """
{
  "strategy": "Macro",
  "heroFeature": "distinctive wooden handle assembly and wooden crossbar lid",
  "setting": "kitchen",
  "salesAngle": "recognizable deep form and contrasting wood details",
  "hookIdea": "macro travel from hanging ring through ferrule to the deep bowl",
  "useHands": true,
  "usePeople": false,
  "rationale": "The exact handle hardware, bowl profile, and lid construction are the strongest verified identity details."
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

    private val PAN_FINAL_PROMPT_JSON = buildJsonObject {
        put("veoPrompt", JsonPrimitive(PAN_VEO_PROMPT))
        put("voiceover", JsonPrimitive(PAN_DE_VOICEOVER))
        put("title", JsonPrimitive("Deep Black Pan with Wooden Crossbar Lid"))
        put(
            "hashtags",
            JsonArray(
                listOf(
                    JsonPrimitive("#DeepBlackPan"),
                    JsonPrimitive("#WoodenLid"),
                    JsonPrimitive("#KitchenDesign"),
                    JsonPrimitive("#CookwareDetails"),
                    JsonPrimitive("#TikTokShop")
                )
            )
        )
        put(
            "qualityScores",
            buildJsonObject {
                put("productFidelity", JsonPrimitive(10))
                put("creativity", JsonPrimitive(8))
                put("physicalPlausibility", JsonPrimitive(9))
                put("voiceoverNaturalness", JsonPrimitive(8))
                put("hookStrength", JsonPrimitive(9))
            }
        )
        put("internalSafetyAudit", JsonPrimitive("pan fidelity fixture passed"))
    }.toString()

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
