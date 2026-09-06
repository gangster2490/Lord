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
        val overlays = ad.onScreenTexts
            .map { stripSpam(it).take(brief.platform.overlayMax) }
            .filter { it.isNotEmpty() }
        return ad.copy(
            hooks = hooks,
            caption = caption,
            hashtags = hashtags,
            onScreenTexts = overlays,
            cta = cta,
            storyboard = coverStoryboard(ad.storyboard, brief.length),
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
        if (!out.contains("VIDEO LENGTH:", ignoreCase = true) ||
            !out.contains("locked product", ignoreCase = true)
        ) {
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
}

fun AdPackage.guarded(brief: GenerateBrief): AdPackage = PackageGuard.harden(this, brief)
