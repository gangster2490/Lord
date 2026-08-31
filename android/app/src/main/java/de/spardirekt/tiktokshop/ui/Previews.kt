package de.spardirekt.tiktokshop.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import de.spardirekt.tiktokshop.data.model.GenerateResult
import de.spardirekt.tiktokshop.data.model.ImageKind
import de.spardirekt.tiktokshop.data.model.ImageSlot
import de.spardirekt.tiktokshop.data.model.ProductFacts
import de.spardirekt.tiktokshop.ui.theme.TikTokShopTheme

internal val PreviewResult = GenerateResult(
    productFacts = ProductFacts(
        name = "Outdoor Rucksack 40L",
        dimensions = "55 x 32 x 22 cm",
        capacity = "40 L",
        material = "Ripstop Nylon",
        weight = "1,2 kg",
        color = "Olivgrün",
        includedItems = listOf("Regenhülle"),
        keyFeatures = listOf("Brustgurt", "Laptop-Fach"),
        useCases = listOf("Wandern", "Pendeln"),
    ),
    hooks = listOf("Der sitzt den ganzen Tag", "Kein Drücken mehr", "Endlich Ordnung drin", "Outdoor ready", "Leicht wie nie"),
    title = "🎒 Der Rucksack, der den Rücken rettet",
    hashtags = listOf("#TikTokShop", "#Rucksack", "#Outdoor", "#Wandern", "#Alltag", "#Deutschland", "#MustHave"),
    bannerText = listOf("40L Ripstop", "Den ganzen Tag bequem", "Laptop-Fach dabei", "Jetzt unten im Warenkorb"),
    bannerPrompt = "9:16 vertical banner, black background, neon green #39FF14, olive hiking backpack, no price.",
    voiceoverText = "0s – Der sitzt.\n2s – Den ganzen Tag.\n4s – Ordnung drin.\n6s – Jetzt unten im Warenkorb.",
    musicSuggestion = "Deep House, 118 BPM, ruhig und selbstbewusst",
    soundEffects = "0s – Whoosh\n2s – Zipper\n4s – Soft click",
    veoPrompt = "VIDEO LENGTH: Exactly 8 seconds. 9:16 vertical. Person hiking with the locked product backpack.",
    liveScript = "0:00 | Hook-Eröffnung\n0:15 | Produktvorstellung\n1:45 | CTA",
)

@Preview(name = "Home", showBackground = true, backgroundColor = 0xFF000000, widthDp = 390, heightDp = 844)
@Composable
private fun CreatorHomePreview() {
    TikTokShopTheme {
        CreatorScreen(state = CreatorUiState(), onEvent = {})
    }
}

@Preview(name = "Results", showBackground = true, backgroundColor = 0xFF000000, widthDp = 390, heightDp = 1600)
@Composable
private fun CreatorResultsPreview() {
    TikTokShopTheme {
        CreatorScreen(
            state = CreatorUiState(
                apiKey = "sk-ant-••••",
                images = listOf(
                    ImageSlot(ImageKind.Product, uri = "content://preview/product", fileName = "rucksack.jpg"),
                    ImageSlot(ImageKind.Product),
                    ImageSlot(ImageKind.Product),
                    ImageSlot(ImageKind.Description),
                ),
                result = PreviewResult,
            ),
            onEvent = {},
        )
    }
}
