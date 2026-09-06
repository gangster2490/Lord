package de.spardirekt.clipforge.data.model

import de.spardirekt.clipforge.data.prompt.AdSystemPrompt

/**
 * Post-model hardening so a live or demo package is always platform-safe,
 * duration-complete, and copy-ready — even if the model drifts.
 */
object PackageGuard {
    const val MAX_HOOK_WORDS = 6

    private val FORBIDDEN_CTA = listOf(
        "link in bio",
        "link in the bio",
        "ссылка в био",
        "ссылка в instagram",
    )

    private val SPAM = Regex(
        """(?i)(?:€|\$|usd|eur|₽)\s?\d[\d\s.,]*|\d[\d\s.,]*\s?(?:€|\$|eur|₽|usd)|(?:скидк\p{L}*|rabatt|discount)\s?\d+\s?%|\d+\s?%\s?(?:off|скидк\p{L}*)""",
    )

    fun harden(ad: AdPackage, brief: GenerateBrief): AdPackage {
        val cta = sanitizeCta(ad.cta, brief.platform, brief.language)
        val hooks = ad.hooks.map { clipHook(it) }.filter { it.isNotEmpty() }.take(5)
        val hashtags = ad.hashtags.map { normalizeHashtag(it) }.filter { it.isNotEmpty() }
            .distinct()
            .take(brief.platform.hashtagCount)
        val caption = stripSpam(ad.caption).take(brief.platform.captionMax)
        val overlays = ensureCtaOverlay(
            ad.onScreenTexts.map { stripSpam(it).take(brief.platform.overlayMax) }.filter { it.isNotEmpty() },
            cta,
            brief.platform,
        )
        val storyboard = coverStoryboard(ad.storyboard, brief.length).let { shots ->
            if (shots.isEmpty()) shots else shots.dropLast(1) + shots.last().copy(overlay = cta.take(brief.platform.overlayMax))
        }
        return ad.copy(
            hooks = padHooks(hooks, brief.language),
            caption = caption,
            hashtags = padHashtags(hashtags, brief.platform),
            onScreenTexts = overlays,
            cta = cta,
            storyboard = storyboard,
            veoPrompt = completeVeo(ad.veoPrompt, brief.length, brief.platform),
        )
    }

    fun clipHook(text: String, maxWords: Int = MAX_HOOK_WORDS): String =
        text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(maxWords).joinToString(" ")

    fun sanitizeCta(raw: String, platform: Platform, language: AdLanguage): String {
        val cta = raw.trim().ifBlank { return platform.canonicalCta(language) }
        return if (isForbiddenCta(cta)) platform.canonicalCta(language) else cta
    }

    fun isForbiddenCta(text: String): Boolean {
        val lower = text.lowercase()
        return FORBIDDEN_CTA.any { lower.contains(it) }
    }

    fun stripSpam(text: String): String =
        text.replace(SPAM, " ").replace(Regex("\\s{2,}"), " ").trim()

    fun completeVeo(prompt: String, length: AdLength, platform: Platform): String {
        val header = AdSystemPrompt.durationHeader(length, platform)
        var out = prompt.trim()
        val hasCorrectLength = out.contains("Exactly ${length.seconds} seconds", ignoreCase = true)
        val hasPlatform = out.contains(platform.labelEn, ignoreCase = true)
        val hasLock = out.contains("locked product", ignoreCase = true)
        if (!out.contains("VIDEO LENGTH:", ignoreCase = true) || !hasCorrectLength || !hasPlatform || !hasLock) {
            out = out.replace(Regex("(?im)^VIDEO LENGTH:.*(?:\\r?\\n)?"), "").trim()
            out = if (out.isBlank()) header else "$header\n\n$out"
        }
        if (!out.contains("HUMAN INTERACTION RULES")) {
            out = out.trimEnd() + "\n\n" + AdSystemPrompt.HUMAN_INTERACTION_BLOCK
        }
        if (!out.contains("Negative prompt", ignoreCase = true)) {
            out = out.trimEnd() + "\n\n" + AdSystemPrompt.NEGATIVE_PROMPT
        }
        return out
    }

    fun coverStoryboard(shots: List<StoryboardShot>, length: AdLength): List<StoryboardShot> {
        val duration = length.seconds.toDouble()
        val beats = defaultBeats(length)
        if (shots.isEmpty()) {
            return beats.map { (start, end) ->
                StoryboardShot(startSec = start, endSec = end, shot = "medium", action = "", overlay = "")
            }
        }
        val sorted = shots.sortedBy { it.startSec }.mapIndexed { index, shot ->
            val (start, end) = beats.getOrElse(index) { shot.startSec to shot.endSec }
            shot.copy(
                startSec = if (index == 0) 0.0 else shot.startSec.coerceAtLeast(start),
                endSec = if (index == shots.lastIndex) duration else shot.endSec.coerceAtMost(end),
            )
        }
        val last = sorted.last()
        return if (last.endSec + 0.05 < duration) {
            sorted.dropLast(1) + last.copy(endSec = duration)
        } else {
            sorted
        }
    }

    fun defaultBeats(length: AdLength): List<Pair<Double, Double>> = when (length) {
        AdLength.EIGHT -> listOf(0.0 to 2.0, 2.0 to 5.0, 5.0 to 7.0, 7.0 to 8.0)
        AdLength.FIFTEEN -> listOf(0.0 to 3.0, 3.0 to 9.0, 9.0 to 13.0, 13.0 to 15.0)
    }

    fun isRetryableHttp(code: Int): Boolean = code == 429 || code in 500..599

    fun padHooks(hooks: List<String>, language: AdLanguage): List<String> {
        val fallbacks = when (language) {
            AdLanguage.RU -> listOf("Стой, не скролль", "Смотри, как работает", "Вот это в деле", "Одно касание", "Сейчас увидишь")
            AdLanguage.DE -> listOf("Stopp, nicht scrollen", "Sieh es in Aktion", "Echt im Einsatz", "Eine Berührung", "Jetzt ansehen")
            AdLanguage.EN -> listOf("Stop, don't scroll", "Watch it work", "Real use, right now", "One touch", "See it now")
        }
        val out = hooks.toMutableList()
        for (hook in fallbacks) {
            if (out.size >= 5) break
            val clipped = clipHook(hook)
            if (clipped.isNotEmpty() && clipped !in out) out += clipped
        }
        return out.take(5)
    }

    fun padHashtags(tags: List<String>, platform: Platform): List<String> {
        val extras = when (platform) {
            Platform.TIKTOK_SHOP -> listOf("#TikTokShop", "#fyp", "#viral", "#review", "#musthave", "#shop", "#foryou", "#product")
            Platform.REELS -> listOf("#Reels", "#instagram", "#viral", "#review", "#musthave", "#shop", "#foryou", "#product")
            Platform.SHORTS -> listOf("#Shorts", "#youtube", "#viral", "#review", "#musthave")
        }
        val out = tags.toMutableList()
        for (tag in extras) {
            if (out.size >= platform.hashtagCount) break
            if (tag !in out) out += tag
        }
        return out.take(platform.hashtagCount)
    }

    fun ensureCtaOverlay(overlays: List<String>, cta: String, platform: Platform): List<String> {
        val clipped = cta.take(platform.overlayMax)
        val without = overlays.filterNot { it.equals(clipped, ignoreCase = true) }.take(4)
        return (without + clipped).distinct()
    }
}

fun AdPackage.guarded(brief: GenerateBrief): AdPackage = PackageGuard.harden(this, brief)
