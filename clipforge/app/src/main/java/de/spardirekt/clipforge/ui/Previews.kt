package de.spardirekt.clipforge.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import de.spardirekt.clipforge.data.model.AdFormula
import de.spardirekt.clipforge.data.model.AdLanguage
import de.spardirekt.clipforge.data.model.AdLength
import de.spardirekt.clipforge.data.model.AdPackage
import de.spardirekt.clipforge.data.model.HistoryEntry
import de.spardirekt.clipforge.data.model.Platform
import de.spardirekt.clipforge.data.model.ProductInsight
import de.spardirekt.clipforge.data.model.ProductPhoto
import de.spardirekt.clipforge.data.model.StoryboardShot
import de.spardirekt.clipforge.ui.theme.ClipForgeTheme

internal val PreviewAd = AdPackage(
    product = ProductInsight(
        name = "Крем с золотой крышкой",
        category = "Уход",
        visualLock = "Золотая крышка, матовое стекло",
        sellingAngle = "Вечерний ритуал без глянца",
        audience = "25–40",
        keyFeatures = listOf("Текстура", "Крышка"),
    ),
    hooks = listOf("Стой, это не фильтр", "Крышка золотая", "Вечером кожа другая", "Одно касание", "Тот самый ритуал"),
    caption = "Золотая крышка и та самая кремовая текстура. Смотри, как ложится — и бери в корзине.",
    hashtags = listOf("#уход", "#TikTokShop", "#ритуал"),
    onScreenTexts = listOf("Не скролл", "Живая текстура", "Сейчас в корзине"),
    cta = "Сейчас в корзине",
    storyboard = listOf(
        StoryboardShot(0.0, 2.0, "medium", "Руки уже наносят крем", "Не скролл"),
        StoryboardShot(2.0, 5.0, "tracking", "Живое использование", "Живая текстура"),
        StoryboardShot(5.0, 7.0, "close-up", "Крышка крупно", "Как на фото"),
        StoryboardShot(7.0, 8.0, "medium", "Взгляд в камеру", "Сейчас в корзине"),
    ),
    voiceover = "0с – Стой.\n7с – Сейчас в корзине.",
    music = "Cinematic pop, 108 BPM",
    soundEffects = "0с – whoosh",
    veoPrompt = "VIDEO LENGTH: Exactly 8 seconds. 9:16 vertical. TikTok Shop.",
    thumbnailPrompt = "9:16 still, gold lid jar in hand.",
    whyItConverts = "Первый кадр уже с руками и товаром.",
)

@Preview(name = "Studio", showBackground = true, backgroundColor = 0xFF0B0614, widthDp = 390, heightDp = 844)
@Composable
private fun StudioPreview() {
    ClipForgeTheme {
        ClipForgeApp(
            state = StudioUiState(
                photos = listOf(ProductPhoto("https://example.com/p.jpg", "p.jpg")),
                apiKey = "sk-demo",
            ),
            onEvent = {},
        )
    }
}

@Preview(name = "Result", showBackground = true, backgroundColor = 0xFF0B0614, widthDp = 390, heightDp = 1600)
@Composable
private fun ResultPreview() {
    ClipForgeTheme {
        ClipForgeApp(
            state = StudioUiState(
                showResult = true,
                result = PreviewAd,
                platform = Platform.TIKTOK_SHOP,
                length = AdLength.EIGHT,
                formula = AdFormula.HOOK_DEMO_CTA,
                language = AdLanguage.RU,
            ),
            onEvent = {},
        )
    }
}

@Preview(name = "Archive", showBackground = true, backgroundColor = 0xFF0B0614, widthDp = 390, heightDp = 844)
@Composable
private fun ArchivePreview() {
    ClipForgeTheme {
        ClipForgeApp(
            state = StudioUiState(
                tab = Tab.ARCHIVE,
                history = listOf(
                    HistoryEntry(
                        id = "1",
                        createdAt = 1_720_000_000_000L,
                        platformId = Platform.REELS.id,
                        lengthSeconds = 15,
                        formulaId = AdFormula.UGC.id,
                        languageId = AdLanguage.RU.id,
                        productName = "Крем с золотой крышкой",
                        ad = PreviewAd,
                    ),
                ),
            ),
            onEvent = {},
        )
    }
}
