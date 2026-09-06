package de.spardirekt.ugcclean.gen

import de.spardirekt.ugcclean.model.CopyPack
import de.spardirekt.ugcclean.model.ProductPlan
import de.spardirekt.ugcclean.model.SpeechLanguage

object Compliance {
    private val banned = listOf(
        "beste", "einzigartig", "garantiert", "100%", "heilt", "kuriert",
        "lindert schmerzen", "schnell geliefert", "versandkostenfrei garantiert",
        "nur heute", "limitiert",
    )

    fun sanitizeCaption(raw: String, language: SpeechLanguage, plan: ProductPlan): String {
        var text = raw.trim().trim('"')
        banned.forEach { word ->
            text = Regex("(?i)\\b${Regex.escape(word)}\\b").replace(text, "")
        }
        text = text.replace(Regex("\\s{2,}"), " ").trim()
        if (text.isBlank()) {
            text = fallbackCaption(language, plan)
        }
        val disclosure = if (language == SpeechLanguage.DE) "Werbung" else "Реклама"
        if (!text.contains("Werbung", true) && !text.contains("Anzeige", true) && !text.contains("Реклама")) {
            text = "$text $disclosure".trim()
        }
        return text
    }

    fun sanitizeHashtags(tags: List<String>, language: SpeechLanguage): List<String> {
        val cleaned = tags.map { tag ->
            val t = tag.trim().removePrefix("#").filter { it.isLetterOrDigit() }
            if (t.isBlank()) "" else "#$t"
        }.filter { it.isNotBlank() }.distinct()
        val filled = cleaned.toMutableList()
        val extras = if (language == SpeechLanguage.DE) {
            listOf("#TikTokShop", "#Werbung", "#Alltag", "#Fund", "#MustHave")
        } else {
            listOf("#TikTokShop", "#Реклама", "#обзор", "#находка", "#длядома")
        }
        extras.forEach { extra ->
            if (filled.size >= 5) return@forEach
            if (filled.none { it.equals(extra, true) }) filled += extra
        }
        return filled.take(5)
    }

    fun details(plan: ProductPlan, language: SpeechLanguage): String {
        val labels = if (language == SpeechLanguage.DE) {
            listOf("Produkt", "Kategorie", "Videoidée", "Kaufgrund", "Setting", "Aktion")
        } else {
            listOf("Товар", "Категория", "Идея видео", "Зачем покупают", "Место", "Действие")
        }
        val values = listOf(
            plan.productName,
            plan.category,
            plan.desire,
            plan.useCase,
            plan.setting,
            plan.action,
        )
        return labels.zip(values).joinToString("\n") { (k, v) -> "$k: ${v.trim()}" }
    }

    fun warnings(caption: String): List<String> {
        return banned.filter { caption.contains(it, ignoreCase = true) }
    }

    private fun fallbackCaption(language: SpeechLanguage, plan: ProductPlan): String {
        val name = plan.productName.ifBlank { if (language == SpeechLanguage.DE) "dieses Produkt" else "этот товар" }
        return if (language == SpeechLanguage.DE) {
            "Alltag mit $name — genau so, wie auf dem Foto."
        } else {
            "Обычный день с $name — как на фото."
        }
    }

    fun pack(caption: String, hashtags: List<String>, plan: ProductPlan, language: SpeechLanguage): CopyPack {
        return CopyPack(
            caption = sanitizeCaption(caption, language, plan),
            hashtags = sanitizeHashtags(hashtags, language),
            details = details(plan, language),
        )
    }
}
