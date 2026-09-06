package de.spardirekt.ugcclean.net

import de.spardirekt.ugcclean.model.ProductPlan
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DemoClient(
    private val json: Json = Json { encodeDefaults = true },
) : ChatClient {
    override suspend fun chat(
        apiKey: String,
        systemPrompt: String,
        userText: String,
        imageDataUrls: List<String>,
        jsonMode: Boolean,
    ): String {
        if (systemPrompt.contains("caption", ignoreCase = true) || userText.contains("hashtags", true)) {
            return """
                {"caption":"Alltag mit genau diesem Produkt vom First Frame.","hashtags":["#Alltag","#Fund","#TikTokShop","#Werbung","#Video"]}
            """.trimIndent()
        }
        val plan = demoPlan(imageDataUrls.size)
        return json.encodeToString(plan)
    }

    companion object {
        fun demoPlan(photoCount: Int): ProductPlan = ProductPlan(
            productName = "uploaded product",
            category = "unknown",
            visibleFeatures = listOf(
                "exact silhouette from the first frame",
                "exact colors from the first frame",
                "exact component count from the photos",
                "surface finish as photographed",
                "no extra parts beyond the photos",
                "$photoCount reference photos of the same object",
            ),
            movingParts = listOf("any uncertain hinge, lid, strap or joint"),
            useCase = "everyday use of the photographed object",
            desire = "see this exact product used naturally in its real context",
            setting = "a real everyday space that matches how THIS photographed product is used",
            camera = "slight handheld, natural crop, first-frame product stays recognizable",
            action = "one low-risk handheld interaction with this exact product",
            humanBehaviour = "casual, unscripted glance at the product, no presenter cadence",
            lighting = "real room light from a window or ceiling, not a studio",
            spokenHookDe = "Schau mal, genau das hier benutze ich so.",
            spokenHookRu = "Смотри, вот так я этим пользуюсь.",
        )
    }
}
