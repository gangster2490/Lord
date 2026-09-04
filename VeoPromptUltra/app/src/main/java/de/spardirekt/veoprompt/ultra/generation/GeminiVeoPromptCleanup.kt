package de.spardirekt.veoprompt.ultra.generation

/**
 * Gemini / VEO copy sanitizer (PromptCleanup from Veo Prompt Pro).
 *
 * Builds the concise 12-section body the owner pastes into Gemini / VEO.
 * Does not replace the stored veoPrompt and does not remove
 * [de.spardirekt.veoprompt.ultra.compliance.GeminiVeoPromptSanitizer].
 */
object GeminiVeoPromptCleanup {

    private val REQUIRED_SECTIONS = listOf(
        "FORMAT",
        "REFERENCES",
        "PRODUCT LOCK",
        "SETTING",
        "SHOT SEQUENCE",
        "ON-SCREEN TEXT",
        "VOICEOVER",
        "AUDIO",
        "CRITICAL",
        "NEGATIVE PROMPT",
        "TITLE",
        "HASHTAGS"
    )

    /**
     * Headers that must never appear in the copied Gemini/VEO prompt.
     * Models sometimes emit these from the internal agent doctrine.
     */
    private val LEGACY_SECTIONS = listOf(
        "VISUAL FIDELITY",
        "VISUAL FIDELITY CORE",
        "VISUAL EVIDENCE",
        "VISUAL EVIDENCE PRIORITY",
        "VISUAL SIGNATURE",
        "PRODUCT FIDELITY",
        "PRODUCT FIDELITY CORE",
        "PRODUCT FIDELITY CORE RULE",
        "CORE PRINCIPLE",
        "TIKTOK SHOP SAFETY AUDIT",
        "SAFETY AUDIT",
        "AIGC AUDIT",
        "GEMINI AUDIT",
        "GEMINI / VEO",
        "INTERNAL PRODUCT MODEL",
        "INTERNAL SAFETY AUDIT",
        "QUALITY GATE",
        "QUALITY SCORES",
        "CREATIVE DIRECTOR",
        "CREATIVE PLAN",
        "PRODUCT MODEL",
        "PHOTO ANALYSIS",
        "ANALYSIS SUMMARY",
        "PHYSICAL PLAUSIBILITY",
        "PRIMARY REFERENCE",
        "MAIN REFERENCE",
        "STORYBOARD",
        "CAMERA NOTES",
        "LIGHTING NOTES",
        "TIMELINE NOTES",
        "REGRESSION LOCKS",
        "FINAL OWNER EXPERIENCE",
        "SALES STYLE",
        "ONE HERO FEATURE",
        "VOICEOVER CLEANUP",
        "COMPLETENESS",
        "PIPELINE STAGES",
        "HOOK",
        "HANDS",
        "PEOPLE",
        "CONFIDENCE",
        "FACT PRIORITY",
        "MARKETPLACE NOISE",
        "ОЗВУЧКА",
        "НАЗВАНИЕ",
        "ХЕШТЕГИ"
    )

    /** Short lock line for the copied Gemini/VEO prompt — not the long internal doctrine essay. */
    private val SHORT_PRODUCT_LOCK =
        "Match uploaded photos exactly. No replacement or redesign."

    private val SHORT_MARKETPLACE =
        "Marketplace shots are references only; no listing UI."

    private val SHORT_NEGATIVE = listOf(
        "no generic replacement product",
        "no redesign / wrong proportions / colors / materials",
        "no missing confirmed parts or invented accessories",
        "no product morphing",
        "no marketplace UI or phone interface",
        "no CGI/cartoon look"
    )

    /** Soft ceiling for the copied Gemini/VEO body (chars). */
    const val MAX_COPIED_PROMPT_CHARS = 1100


    data class CleanupResult(
        val veoPrompt: String,
        val voiceover: String,
        val title: String,
        val hashtags: List<String>,
        val issues: List<String>
    )

    fun finalize(
        rawPrompt: String,
        voiceover: String,
        title: String,
        hashtags: List<String>,
        voiceLanguage: String,
        marketplace: Boolean,
        tiktokShopMode: Boolean = true
    ): CleanupResult {
        val issues = mutableListOf<String>()
        var prompt = rawPrompt.trim()

        prompt = stripAfterHashtags(prompt)
        prompt = prompt.replace(Regex("(?is)\\n*TIKTOK SHOP SAFETY AUDIT[\\s\\S]*$"), "")
            .trim()
        prompt = stripLegacySections(prompt)

        prompt = dedupeParagraphs(prompt)
        prompt = normalizePunctuation(prompt)
        prompt = removeDuplicateVisualFidelity(prompt)
        prompt = removeDuplicateMarketplaceRules(prompt)
        prompt = ensureSectionOrder(prompt, marketplace, issues)

        var vo = cleanupVoiceover(
            voiceover.ifBlank { extractSection(prompt, "VOICEOVER") },
            voiceLanguage,
            tiktokShopMode
        )
        if (voiceLanguage == "OFF") {
            vo = "OFF"
            prompt = replaceSection(prompt, "VOICEOVER", "OFF")
        } else if (vo.isNotBlank()) {
            prompt = replaceSection(prompt, "VOICEOVER", vo)
        }

        var cleanTitle = title.ifBlank { extractSection(prompt, "TITLE") }
            .lineSequence().firstOrNull { it.isNotBlank() }
            ?.removePrefix("TITLE")
            ?.trim()
            ?.trimStart(':')
            ?.trim()
            .orEmpty()
        if (cleanTitle.isBlank()) {
            cleanTitle = "Product Ad"
            issues += "title_missing_defaulted"
        }
        prompt = replaceSection(prompt, "TITLE", cleanTitle)

        var tags = normalizeHashtags(hashtags.ifEmpty { extractHashtags(prompt) })
        tags = ensureFiveHashtags(tags, cleanTitle, tiktokShopMode)
        if (tags.size != 5) issues += "hashtags_normalized_to_5"
        prompt = replaceSection(prompt, "HASHTAGS", tags.joinToString(" "))

        if (!prompt.contains("0.0") || !prompt.contains("8.0")) {
            issues += "timeline_markers_weak"
        }

        REQUIRED_SECTIONS.forEach { section ->
            if (!Regex("""(?im)^$section\b""").containsMatchIn(prompt)) {
                issues += "missing_$section"
            }
        }

        // Copy-ready Gemini/VEO prompt: concise section bodies, same section order.
        prompt = simplifyCopiedPrompt(prompt, marketplace)
        prompt = stripAfterHashtags(prompt).trim() + "\n"
        return CleanupResult(prompt, vo, cleanTitle, tags, issues)
    }

    /**
     * Rebuild the Gemini/VEO copy body from a stored (possibly dirty) prompt.
     * Syncs VOICEOVER / TITLE / HASHTAGS from the Result cards.
     * Local only — does not call the model or change pipeline stages.
     */
    fun composeCopiedPrompt(
        rawPrompt: String,
        voiceover: String,
        title: String,
        hashtags: List<String>,
        marketplace: Boolean,
        tiktokShopMode: Boolean = true
    ): String {
        var prompt = rawPrompt.trim()
        if (prompt.isBlank()) return ""

        if (looksLikeRawJson(prompt)) {
            prompt = StructuredResponseParser.parse(prompt).veoPrompt
            if (prompt.isBlank()) return ""
        }

        prompt = stripAfterHashtags(prompt)
        prompt = prompt.replace(Regex("(?is)\\n*TIKTOK SHOP SAFETY AUDIT[\\s\\S]*$"), "").trim()
        prompt = stripLegacySections(prompt)
        prompt = dedupeParagraphs(prompt)
        prompt = normalizePunctuation(prompt)
        prompt = removeDuplicateVisualFidelity(prompt)
        prompt = removeDuplicateMarketplaceRules(prompt)

        val issues = mutableListOf<String>()
        prompt = ensureSectionOrder(prompt, marketplace, issues)

        val vo = voiceover.trim().ifBlank { extractSection(prompt, "VOICEOVER").ifBlank { "OFF" } }
        prompt = replaceSection(prompt, "VOICEOVER", vo)

        val cleanTitle = title.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
            .ifBlank { extractSection(prompt, "TITLE").lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty() }
            .ifBlank { "Product Ad" }
        prompt = replaceSection(prompt, "TITLE", cleanTitle)

        var tags = normalizeHashtags(hashtags.ifEmpty { extractHashtags(prompt) })
        tags = ensureFiveHashtags(tags, cleanTitle, tiktokShopMode)
        prompt = replaceSection(prompt, "HASHTAGS", tags.joinToString(" "))

        prompt = simplifyCopiedPrompt(prompt, marketplace)
        return stripAfterHashtags(prompt).trim() + "\n"
    }

    private fun looksLikeRawJson(text: String): Boolean {
        val t = text.trim()
        return t.startsWith("{") && (
            t.contains("\"veoPrompt\"") ||
                t.contains("\"mainPrompt\"") ||
                t.contains("\"finalPrompt\"")
            )
    }

    /**
     * Deterministic local pass that shortens only the prompt the owner copies into Gemini/VEO.
     * Does not change photo analysis or other pipeline stages.
     */
    fun simplifyCopiedPrompt(prompt: String, marketplace: Boolean): String {
        val map = linkedMapOf<String, String>()
        REQUIRED_SECTIONS.forEach { name ->
            map[name] = extractSection(prompt, name)
        }
        map["FORMAT"] = "Vertical 9:16. Photorealistic TikTok Shop ad. Exactly 8.0s."
        map["REFERENCES"] = simplifyReferences(map["REFERENCES"].orEmpty(), marketplace)
        map["PRODUCT LOCK"] = compressProductLock(map["PRODUCT LOCK"].orEmpty(), maxDetails = 5)
        val identity = map["PRODUCT LOCK"].orEmpty()
        map["SETTING"] = simplifySetting(map["SETTING"].orEmpty(), identity)
        map["SHOT SEQUENCE"] = simplifyShotSequence(map["SHOT SEQUENCE"].orEmpty(), identity)
        map["ON-SCREEN TEXT"] = simplifyOnScreenText(map["ON-SCREEN TEXT"].orEmpty(), identity)
        map["VOICEOVER"] = map["VOICEOVER"].orEmpty().lineSequence()
            .firstOrNull { it.isNotBlank() }?.trim().orEmpty()
            .ifBlank { "OFF" }
        map["AUDIO"] = clipWords(firstSentences(map["AUDIO"].orEmpty(), 1), 8)
            .ifBlank { "Subtle music. Clear voice." }
        map["CRITICAL"] = simplifyCritical(marketplace)
        map["NEGATIVE PROMPT"] = simplifyNegative(
            map["NEGATIVE PROMPT"].orEmpty(),
            identity,
            maxBullets = 4
        )
        map["TITLE"] = map["TITLE"].orEmpty().lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
            .ifBlank { "Product Ad" }
            .let { clipChars(it, 48) }
        map["HASHTAGS"] = map["HASHTAGS"].orEmpty().trim()

        var out = REQUIRED_SECTIONS.joinToString("\n\n") { name ->
            "$name\n${map[name].orEmpty().trim()}"
        }.trim() + "\n"

        return finalCleanupCopiedPrompt(out, marketplace)
    }

    /**
     * Last local pass for the Gemini/VEO copy body.
     * Locks exact 12-section shape, spacing, and residue stripping.
     * Does not call the model.
     */
    fun finalCleanupCopiedPrompt(prompt: String, marketplace: Boolean = false): String {
        if (prompt.isBlank()) return ""
        var text = prompt.trim()
        text = text.replace(Regex("```(?:json)?|```"), "")
        text = stripAfterHashtags(text)
        text = stripLegacySections(text)
        text = text.replace(Regex("(?is)\\n*TIKTOK SHOP SAFETY AUDIT[\\s\\S]*$"), "").trim()
        text = text.replace(Regex("(?im)^(Озвучка|Название|Хештеги)\\b.*$"), "")
        text = text.replace(Regex("""(?im)^Timeline ends at.*$"""), "")
        text = text.replace(Regex("""(?im)^Four blocks only\..*$"""), "")
        text = text.replace(Regex("""(?im)^No continuation after.*$"""), "")

        val map = linkedMapOf<String, String>()
        val identity = extractSection(text, "PRODUCT LOCK")
        REQUIRED_SECTIONS.forEach { name ->
            map[name] = polishSectionBody(name, extractSection(text, name), marketplace, identity)
        }

        // Guarantee required non-blank defaults for every structural section.
        if (map["FORMAT"].isNullOrBlank()) {
            map["FORMAT"] = "Vertical 9:16. Photorealistic TikTok Shop ad. Exactly 8.0s."
        }
        if (map["REFERENCES"].isNullOrBlank()) {
            map["REFERENCES"] = "Uploaded product photos are the visual evidence."
        }
        if (map["SHOT SEQUENCE"].isNullOrBlank() || !hasFourBlocks(map["SHOT SEQUENCE"].orEmpty())) {
            map["SHOT SEQUENCE"] = CANONICAL_SHOT_SEQUENCE
        }
        if (map["PRODUCT LOCK"].isNullOrBlank()) {
            map["PRODUCT LOCK"] = SHORT_PRODUCT_LOCK
        }
        if (map["SETTING"].isNullOrBlank()) {
            map["SETTING"] = "Uncluttered premium studio."
        }
        if (map["ON-SCREEN TEXT"].isNullOrBlank()) map["ON-SCREEN TEXT"] = "None."
        if (map["VOICEOVER"].isNullOrBlank()) map["VOICEOVER"] = "OFF"
        if (map["AUDIO"].isNullOrBlank()) map["AUDIO"] = "Subtle music. Clear voice."
        if (map["CRITICAL"].isNullOrBlank()) {
            map["CRITICAL"] = "Keep product identity. Exactly 8.0s. Four blocks only."
        }
        if (map["NEGATIVE PROMPT"].isNullOrBlank()) {
            map["NEGATIVE PROMPT"] = SHORT_NEGATIVE.joinToString("\n") { "- $it" }
        }
        if (map["TITLE"].isNullOrBlank()) map["TITLE"] = "Product Ad"
        if (map["HASHTAGS"].isNullOrBlank()) {
            map["HASHTAGS"] = "#TikTokShop #ProductAd #MustSee #HomeFinds #ShopNow"
        }
        if (marketplace) {
            val refs = map["REFERENCES"].orEmpty()
            if (!refs.contains("marketplace", ignoreCase = true)) {
                map["REFERENCES"] = listOf(refs, SHORT_MARKETPLACE)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                    .trim()
            }
        }

        // Pad hashtag count to exactly 5 when present but short.
        val tags = Regex("#[\\p{L}\\p{N}_]+")
            .findAll(map["HASHTAGS"].orEmpty())
            .map { it.value }
            .distinct()
            .toList()
        if (tags.size != 5) {
            map["HASHTAGS"] = ensureFiveHashtags(tags, map["TITLE"].orEmpty(), tiktokShopMode = true)
                .joinToString(" ")
        }

        var out = REQUIRED_SECTIONS.joinToString("\n\n") { name ->
            "$name\n${map[name].orEmpty().trim()}"
        }.trim() + "\n"
        var guard = 0
        while (out.length > MAX_COPIED_PROMPT_CHARS && guard < 3) {
            guard++
            val lockMax = if (guard == 1) 5 else 4
            val negMax = if (guard == 1) 4 else 3
            map["PRODUCT LOCK"] = compressProductLock(map["PRODUCT LOCK"].orEmpty(), maxDetails = lockMax)
            map["NEGATIVE PROMPT"] = simplifyNegative(
                map["NEGATIVE PROMPT"].orEmpty(),
                map["PRODUCT LOCK"].orEmpty(),
                maxBullets = negMax
            )
            map["SETTING"] = clipWords(map["SETTING"].orEmpty(), if (guard == 1) 6 else 4)
            if (guard >= 2) map["AUDIO"] = "Subtle music."
            // Never truncate REFERENCES or SHOT SEQUENCE. A cut sentence is not
            // a pasteable Veo prompt, even when it would satisfy the soft budget.
            out = REQUIRED_SECTIONS.joinToString("\n\n") { name ->
                "$name\n${map[name].orEmpty().trim()}"
            }.trim() + "\n"
        }
        return out
    }

    private fun polishSectionBody(
        name: String,
        raw: String,
        marketplace: Boolean,
        identity: String
    ): String {
        var body = raw.trim()
            .replace(Regex("\r\n?"), "\n")
            .replace(Regex("[ \t]+"), " ")
            .replace(Regex(" *\n *"), "\n")
            .replace(Regex("\n{2,}"), "\n")
            .trim()
        body = stripLegacyInlineHeaders(body)
        body = body.replace(Regex("(?is)PRODUCT DESIGN\\s*=\\s*LOCKED\\.?"), "")
            .replace(Regex("(?is)CORE PRINCIPLE\\s*:.*"), "")
            .trim()

        return when (name) {
            "FORMAT" -> "Vertical 9:16. Photorealistic TikTok Shop ad. Exactly 8.0s."
            "SHOT SEQUENCE" -> normalizeShotSequence(body, identity)
            "NEGATIVE PROMPT" -> simplifyNegative(body, identity, maxBullets = 4)
            "HASHTAGS" -> Regex("#[\\p{L}\\p{N}_]+")
                .findAll(body)
                .map { it.value }
                .distinct()
                .take(5)
                .joinToString(" ")
            "TITLE", "VOICEOVER", "AUDIO", "CRITICAL" ->
                body.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.joinToString(" ").trim()
            "SETTING" -> simplifySetting(body, identity)
            "ON-SCREEN TEXT" -> simplifyOnScreenText(body, identity)
            "PRODUCT LOCK" -> body.lineSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .filterNot { isGenericFidelityBoilerplate(it) }
                .joinToString("\n")
            "REFERENCES" -> simplifyReferences(body, marketplace)
            else -> body
        }.trim()
    }

    /**
     * ON-SCREEN TEXT must only contain actual overlay copy that may appear in the video.
     * Never keep production instructions, prompt labels, or meta rules here.
     */
    fun simplifyOnScreenText(raw: String, identityBlob: String = ""): String {
        val cleaned = raw.lineSequence()
            .map { it.trim().trimStart('-', '•', '*').trim() }
            .filter { it.isNotBlank() }
            .filterNot { isOnScreenInstruction(it) }
            .map { line ->
                // Drop leading labels like "Text:" / "Overlay:"
                line.replace(Regex("""(?i)^(text|overlay|on[-\s]?screen|caption|label)\s*:\s*"""), "")
                    .trim()
            }
            .filter { it.isNotBlank() }
            .filterNot { isOnScreenInstruction(it) }
            .distinct()
            .toList()
        val kept = dropLeakedPanOverlays(cleaned, identityBlob).take(3)
        if (kept.isEmpty()) return "None."
        val joined = kept.joinToString(" · ")
        return clipChars(joined, 80)
    }

    private fun isOnScreenInstruction(line: String): Boolean {
        val l = line.lowercase()
        if (l == "none" || l == "none." || l == "off" || l == "n/a") return false
        return l.contains("max 2") ||
            l.contains("maximum 2") ||
            l.contains("2–3") ||
            l.contains("2-3") ||
            l.contains("short overlay") ||
            l.contains("product-specific overlay") ||
            l.contains("concise product") ||
            l.contains("no price") ||
            l.contains("fake urgency") ||
            l.contains("fake discount") ||
            l.contains("unsupported spec") ||
            l.contains("do not repeat") ||
            l.contains("do not show") ||
            l.contains("never show") ||
            l.contains("never put") ||
            l.contains("production instruction") ||
            l.contains("prompt label") ||
            l.startsWith("do not ") ||
            l.startsWith("don't ") ||
            l.startsWith("never ") ||
            l.startsWith("maximum ") ||
            l.startsWith("max ") ||
            // Pure meta about the section itself
            l.contains("on-screen text section") ||
            l.contains("overlay rule") ||
            REQUIRED_SECTIONS.any { sec -> l.equals(sec, ignoreCase = true) }
    }

    private fun normalizeShotSequence(raw: String, identity: String = ""): String {
        val merged = mergeTimedShotBlocks(raw, identity)
        val lines = if (hasFourBlocks(merged)) {
            Regex("""(?m)^\s*0\.0[^\n]*|^\s*2\.0[^\n]*|^\s*4\.0[^\n]*|^\s*6\.0[^\n]*""")
                .findAll(merged)
                .map { normalizeShotLine(enforceOneHandInFeatureDemo(it.value.trim())) }
                .distinct()
                .take(4)
                .toList()
        } else {
            emptyList()
        }
        return if (lines.size == 4) lines.joinToString("\n") else CANONICAL_SHOT_SEQUENCE
    }

    private fun normalizeShotLine(line: String): String {
        return clipShotLine(
            line
                .replace(Regex("""\b0\.0\s*[-–—]\s*2\.0"""), "0.0–2.0")
                .replace(Regex("""\b2\.0\s*[-–—]\s*4\.0"""), "2.0–4.0")
                .replace(Regex("""\b4\.0\s*[-–—]\s*6\.0"""), "4.0–6.0")
                .replace(Regex("""\b6\.0\s*[-–—]\s*8\.0"""), "6.0–8.0")
                .replace(Regex("""\s+—\s+"""), " — ")
                .replace(Regex("""\s{2,}"""), " ")
                .trim()
        )
    }

    fun validateCompleteness(prompt: String, hashtags: List<String>): List<String> {
        val issues = mutableListOf<String>()
        REQUIRED_SECTIONS.forEach { section ->
            if (!Regex("""(?im)^$section\b""").containsMatchIn(prompt)) {
                issues += "missing_$section"
            } else if (extractSection(prompt, section).isBlank()) {
                issues += "blank_$section"
            }
        }
        if (hashtags.size != 5) issues += "hashtag_count_${hashtags.size}"
        if (Regex("(?is)TIKTOK SHOP SAFETY AUDIT").containsMatchIn(prompt)) {
            issues += "safety_audit_leaked"
        }
        LEGACY_SECTIONS.forEach { legacy ->
            if (Regex("""(?im)^${Regex.escape(legacy)}\s*:?\s*$""").containsMatchIn(prompt)) {
                issues += "legacy_section_$legacy"
            }
        }
        val after = prompt.substringAfterLast("HASHTAGS", "")
        val leftover = after.lineSequence().drop(1)
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()
        if (leftover.isNotEmpty()) issues += "content_after_hashtags"
        if (!prompt.contains("0.0") || !prompt.contains("2.0") || !prompt.contains("4.0") ||
            !prompt.contains("6.0") || !prompt.contains("8.0")
        ) {
            issues += "incomplete_timeline"
        }
        // Exact section order among required headers present
        val foundOrder = REQUIRED_SECTIONS.mapNotNull { sec ->
            Regex("""(?im)^${Regex.escape(sec)}\s*$""").find(prompt)?.range?.first?.let { sec to it }
        }.sortedBy { it.second }.map { it.first }
        if (foundOrder != REQUIRED_SECTIONS) {
            issues += "section_order_wrong"
        }
        return issues
    }

    /** True when local cleanup must still inject missing/blank tail sections. */
    fun needsCompletenessRepair(prompt: String, hashtags: List<String>): Boolean {
        return validateCompleteness(prompt, hashtags).any {
            it.startsWith("missing_") ||
                it.startsWith("blank_") ||
                it == "incomplete_timeline" ||
                it == "section_order_wrong" ||
                it.startsWith("hashtag_count")
        }
    }

    private fun simplifyFormat(raw: String): String {
        return "Vertical 9:16. Photorealistic TikTok Shop ad. Exactly 8.0s."
    }

    private fun simplifySetting(raw: String, identity: String = ""): String {
        var first = firstSentences(stripLongDoctrine(raw), 1)
        if (isWrongKitchenSetting(first, identity)) {
            first = "Uncluttered premium studio"
        }
        val body = clipWords(first, 8).ifBlank { "Uncluttered premium studio" }
        return body.trimEnd('.', ',') + "."
    }

    private fun simplifyReferences(@Suppress("UNUSED_PARAMETER") raw: String, marketplace: Boolean): String {
        return if (marketplace) {
            "Product photos define appearance; ignore marketplace UI."
        } else {
            "Product photos define appearance."
        }
    }

    private fun simplifyProductLock(raw: String): String = compressProductLock(raw, maxDetails = 8)

    private fun compressProductLock(raw: String, maxDetails: Int): String {
        val withoutEssay = stripLongDoctrine(raw)
        val tokens = mutableListOf<String>()
        withoutEssay
            .lineSequence()
            .map { it.trim().trimStart('-', '•', '*').trim() }
            .filter { it.isNotBlank() }
            .filterNot { line -> isGenericFidelityBoilerplate(line) }
            .filterNot { line ->
                line.startsWith("Match uploaded", ignoreCase = true) ||
                    line.startsWith("Match the uploaded", ignoreCase = true)
            }
            .forEach { line ->
                line.split(Regex("[,;]|(?:\\s+and\\s+)"))
                    .map { it.trim().trimEnd('.') }
                    .map {
                        it.removePrefix("Preserve ").removePrefix("preserve ")
                            .removePrefix("Keep ").removePrefix("keep ")
                            .trim()
                    }
                    .filter { it.length in 3..70 }
                    .filterNot { isGenericFidelityBoilerplate(it) }
                    .forEach { tokens += it }
            }
        val distinctDetails = tokens.distinctBy { it.lowercase() }
        if (looksLikeCookwarePan(distinctDetails.joinToString(", "))) {
            return compactPanProductLock(distinctDetails)
        }
        val details = pickLockDetails(distinctDetails, maxDetails)
        return buildString {
            append(SHORT_PRODUCT_LOCK)
            if (details.isNotEmpty()) {
                append("\n")
                append(details.joinToString(", "))
            }
        }.trim()
    }

    private fun compactPanProductLock(details: List<String>): String {
        val blob = details.joinToString(" ").lowercase()
        val shape = when {
            blob.contains("deep rounded") &&
                (blob.contains("high side") || blob.contains("high-side")) ->
                "deep, rounded, high-sided bowl"
            blob.contains("deep rounded") -> "deep rounded bowl"
            blob.contains("high side") -> "high-sided bowl"
            else -> "bowl"
        }
        val parts = mutableListOf(shape)
        if (blob.contains("wooden lid")) parts += "wooden lid"
        val handle = buildString {
            if (blob.contains("wooden handle")) append("wooden handle")
            val fittings = mutableListOf<String>()
            if (blob.contains("ferrule")) fittings += "ferrule"
            if (blob.contains("rivet")) fittings += "rivets"
            if (blob.contains("hanging ring")) fittings += "hanging ring"
            if (fittings.isNotEmpty()) {
                if (isNotEmpty()) append(" with ") else append("hardware: ")
                append(naturalList(fittings))
            }
        }
        if (handle.isNotBlank()) parts += handle
        return "Same photographed pan: ${parts.joinToString("; ")}."
    }

    private fun pickLockDetails(details: List<String>, maxDetails: Int): List<String> {
        if (details.size <= maxDetails) return details
        val first = details.first()
        val ranked = details.drop(1).sortedByDescending { distinctiveness(it) }
        return (listOf(first) + ranked).distinctBy { it.lowercase() }.take(maxDetails)
    }

    private val DISTINCTIVE_KEYS = listOf(
        "lid", "ferrule", "rivet", "hanging", "handle", "bowl",
        "tray", "frame", "collar", "bit", "drain", "plate", "latch", "wok"
    )

    private fun distinctiveness(token: String): Int {
        val t = token.lowercase()
        var score = DISTINCTIVE_KEYS.count { key -> t.contains(key) } * 12 + minOf(t.length, 24)
        if (t.contains("ferrule")) score += 24
        if (t.contains("lid")) score += 20
        if (t.contains("hanging")) score += 16
        if (t.contains("handle")) score += 10
        if (t.contains("rivet")) score += 8
        return score
    }

    private fun distinctiveKeysIn(text: String): Set<String> {
        val l = text.lowercase()
        return DISTINCTIVE_KEYS.filter { l.contains(it) }.toSet()
    }

    private fun prioritizeDetailCsv(csv: String): String {
        val parts = csv.split(',').map { it.trim().trimEnd('.') }.filter { it.isNotBlank() }
        if (parts.size < 2) return csv.trim()
        val first = parts.first()
        val rest = parts.drop(1).sortedByDescending { distinctiveness(it) }
        return (listOf(first) + rest).joinToString(", ")
    }

    private val PAN_OVERLAY_LEAKS = setOf("holzdeckel", "tiefe form")
    private val PAN_NEGATIVE_LEAKS = listOf(
        "wok",
        "skillet",
        "shallower bowl",
        "non-stick",
        "wooden lid",
        "ferrule",
        "hanging ring",
        "generic replacement pan"
    )

    private fun looksLikeCookwarePan(identity: String): Boolean {
        val i = identity.lowercase()
        return i.contains("deep rounded") ||
            i.contains("wooden lid") ||
            i.contains("holzdeckel") ||
            i.contains("ferrule") ||
            i.contains("shallower bowl") ||
            (i.contains("wooden handle") && i.contains("bowl"))
    }

    private fun isWrongKitchenSetting(setting: String, identity: String): Boolean {
        if (!setting.contains("kitchen", ignoreCase = true)) return false
        if (looksLikeCookwarePan(identity)) return false
        val i = identity.lowercase()
        if (i.contains("rice") || i.contains("grill") || (i.contains("bowl") && i.contains("drain"))) {
            return false
        }
        return i.contains("chair") ||
            (i.contains("frame") && i.contains("tray")) ||
            i.contains("bit") ||
            i.contains("collar") ||
            i.contains("closed case")
    }

    private fun dropLeakedPanOverlays(lines: List<String>, identity: String): List<String> {
        if (looksLikeCookwarePan(identity)) return lines
        return lines.filterNot { it.trim().lowercase() in PAN_OVERLAY_LEAKS }
    }

    private fun isGenericFidelityBoilerplate(line: String): Boolean {
        val l = line.lowercase()
        return l.contains("core principle") ||
            l.contains("creative presentation") ||
            l.contains("product design = locked") ||
            l.contains("strict visual references") ||
            l.contains("do not reinterpret") ||
            l.contains("do not replace the photographed") ||
            l.contains("do not redesign, modernize") ||
            l.contains("if creative instructions conflict") ||
            l.contains("generated product must remain") ||
            l.contains("preserve the exact overall silhouette") ||
            l.contains("proportions, construction, colors, materials, controls, handles, hinges") ||
            l.contains("same silhouette, proportions, colors, materials")
    }

    private fun simplifyShotSequence(raw: String, identity: String = ""): String {
        val merged = mergeTimedShotBlocks(raw, identity)
        if (hasFourBlocks(merged)) {
            val blocks = Regex("""(?m)^\s*0\.0[^\n]*|^\s*2\.0[^\n]*|^\s*4\.0[^\n]*|^\s*6\.0[^\n]*""")
                .findAll(merged)
                .map { clipShotLine(enforceOneHandInFeatureDemo(it.value.trim())) }
                .distinct()
                .take(4)
                .toList()
            if (blocks.size == 4) {
                return blocks.joinToString("\n")
            }
        }
        return CANONICAL_SHOT_SEQUENCE
    }

    /**
     * Gemini copy keeps only timed header lines. Fold the following identity
     * sentence onto that line and put photographed detail first.
     */
    private fun mergeTimedShotBlocks(raw: String, identity: String): String {
        val headerRe = Regex(
            """(?m)^\s*((?:0\.0|2\.0|4\.0|6\.0)\s*[–\-]\s*(?:2\.0|4\.0|6\.0|8\.0)s[^\n]*)"""
        )
        val matches = headerRe.findAll(raw).toList()
        if (matches.size < 4) return raw.trim()
        val snippets = identitySnippets(identity, take = 5)
        return matches.mapIndexed { index, match ->
            val header = match.groupValues[1].trim()
            val start = match.range.last + 1
            val end = matches.getOrNull(index + 1)?.range?.first ?: raw.length
            val body = raw.substring(start, end)
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString(" ")
            enrichBareShotLine(mergeShotHeaderAndBody(header, body), snippets, index)
        }.joinToString("\n")
    }

    private val GENERIC_SHOT_PREFIX = Regex(
        """(?i)^(?:Product visible immediately with strongest verified visual detail:\s*|""" +
            """Clear framing of the same exact physical product\.?\s*|""" +
            """Exactly one verified hero feature or physically plausible action with one hand\.?\s*|""" +
            """Stable desirable hero shot of the same unchanged product\.?\s*(?:End exactly at 8\.0s\.?)?\s*)"""
    )

    private fun mergeShotHeaderAndBody(header: String, body: String): String {
        val colon = header.indexOf(':')
        val label = if (colon >= 0) header.substring(0, colon).trim() else header
        val existing = if (colon >= 0) header.substring(colon + 1).trim() else ""
        val combined = listOf(existing, body).filter { it.isNotBlank() }.joinToString(" ")
        if (combined.isBlank()) return header
        val detail = if (isFilmableShotDirection(combined)) {
            combined.trimEnd('.')
        } else {
            val prefix = GENERIC_SHOT_PREFIX.find(combined)
            if (prefix != null) {
                val tail = combined.substring(prefix.range.last + 1).trim().trimEnd('.')
                if (tail.length >= 8) prioritizeDetailCsv(tail) else combined
            } else {
                prioritizeDetailCsv(combined)
            }
        }
        return "$label: $detail"
    }

    private fun isFilmableShotDirection(text: String): Boolean {
        val t = text.lowercase()
        return listOf(
            "close-up", "close up", "hard cut", "camera", "push-in", "push in",
            "lifts", "opens", "rotates", "places", "revealing", "fills frame",
            "fill frame", "full profile", "hold to"
        ).any { t.contains(it) }
    }

    private fun enrichBareShotLine(line: String, snippets: String, index: Int): String {
        val colon = line.indexOf(':')
        val label = if (colon >= 0) line.substring(0, colon).trim() else line
        val after = if (colon >= 0) line.substring(colon + 1).trim() else ""
        val snippetList = snippets.split(',').map { it.trim() }.filter { it.isNotBlank() }
        val hookDetails = pickLockDetails(snippetList, 3)
        val identityDetails = snippetList.filterNot { snip ->
            hookDetails.any { it.equals(snip, ignoreCase = true) }
        }.ifEmpty { snippetList.take(2) }.take(2)
        return "$label: " + when (index) {
            0 -> composeHook(after, hookDetails)
            1 -> composeIdentity(after, identityDetails.ifEmpty { hookDetails.take(2) })
            2 -> composeFeature(after, hookDetails)
            else -> composeHero(after)
        }
    }

    private fun composeHook(after: String, details: List<String>): String {
        if (isActionableHook(after)) return after.trimEnd('.')
        val selected = selectShotDetails(mergeShotDetails(after, details), 44)
        val subject = naturalList(selected).ifBlank { "product" }
        return "A close-up frames the $subject"
    }

    private fun composeIdentity(after: String, details: List<String>): String {
        if (isActionableIdentity(after)) return after.trimEnd('.')
        val selected = selectShotDetails(mergeShotDetails(after, details), 42)
        val subject = naturalList(selected)
        return if (subject.isBlank()) {
            "Hard cut to full product view"
        } else {
            "Hard cut to full profile shows the $subject"
        }
    }

    private fun composeFeature(after: String, details: List<String>): String {
        if (!isFeaturePlaceholder(after) && after.length >= 24) {
            return after.trimEnd('.')
        }
        val keep = clipHookDetails(
            details.sortedByDescending { distinctiveness(it) }.take(2).joinToString(", "),
            36
        )
        return if (keep.isBlank()) {
            "Slow camera push-in; product remains still"
        } else {
            "Slow push-in across $keep; product remains still"
        }
    }

    private fun composeHero(after: String): String {
        if (!isHeroPlaceholder(after) && after.contains("8.0") && after.length >= 20) {
            return after.trimEnd('.')
        }
        return "Hard cut; stable 3/4 hero. Hold to 8.0s"
    }

    private fun isActionableHook(after: String): Boolean {
        val a = after.lowercase()
        val hasShot = a.contains("close-up") || a.contains("close up") ||
            a.contains("macro") || a.contains("camera") || a.contains("fills frame") ||
            a.contains("fill frame")
        return hasShot && distinctiveKeysIn(after).isNotEmpty()
    }

    private fun isActionableIdentity(after: String): Boolean {
        val a = after.lowercase()
        val hasCutOrFraming = a.contains("hard cut") || a.contains("full profile") ||
            a.contains("full product") || a.contains("full framing")
        return hasCutOrFraming && distinctiveKeysIn(after).isNotEmpty()
    }

    private fun isFeaturePlaceholder(after: String): Boolean {
        val a = after.lowercase()
        return a.isBlank() ||
            a == "feature / demo" ||
            a == "one hand, one verified action" ||
            a.contains("verified action") ||
            a.contains("; keep ") ||
            a.contains("one verified hero feature") ||
            a.contains("one hero feature") ||
            GENERIC_SHOT_PREFIX.containsMatchIn(after)
    }

    private fun isHeroPlaceholder(after: String): Boolean {
        val a = after.lowercase()
        return a.isBlank() ||
            a == "hero / cta" ||
            a.contains("same product hero") ||
            a.contains("hero hold") ||
            a == "stable hero of the same product. end 8.0s" ||
            a.startsWith("stable desirable") ||
            GENERIC_SHOT_PREFIX.containsMatchIn(after)
    }

    private fun mergeShotDetails(after: String, details: List<String>): String {
        val stripped = after
            .replace(Regex("(?i)\\bvisible now\\b"), "")
            .replace(Regex("(?i)same product,\\s*full framing\\s*—\\s*"), "")
            .replace(Regex("(?i)same product,\\s*full framing"), "")
            .replace(Regex("(?i)^same\\s+"), "")
            .replace(Regex("(?i),\\s*full framing"), "")
            .replace(Regex("(?i)product visible now\\s*—\\s*"), "")
            .replace(Regex("(?i)one hand,\\s*one verified action;\\s*keep\\s*"), "")
        val fromAfter = stripped.split(Regex("[,;]|\\s+and\\s+|\\s+—\\s+"))
            .map { it.trim().trimEnd('.') }
            .filter { it.length in 3..40 }
            .filterNot { part ->
                val p = part.lowercase()
                p == "product" || p == "now" || p == "same" || p == "unchanged" ||
                    p.contains("verified") || p.contains("hero") || p.contains("framing") ||
                    p.contains("visible") || p == "one hand" || p.startsWith("keep ")
            }
        return (fromAfter + details).distinctBy { it.lowercase() }.joinToString(", ")
    }

    /**
     * Keep the first photographed token, then the most distinctive ones that fit.
     * Never tail-ellipsis a HOOK — that is how ferrule / wooden lid disappeared.
     */
    private fun clipHookDetails(csv: String, max: Int = 72): String {
        return selectShotDetails(csv, max).joinToString(", ")
    }

    private fun selectShotDetails(csv: String, max: Int): List<String> {
        val parts = csv.split(',')
            .map { it.trim().trimEnd('.') }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
        if (parts.isEmpty()) return emptyList()
        val first = parts.first()
        val rest = parts.drop(1).sortedByDescending { distinctiveness(it) }
        val kept = mutableListOf(first)
        for (part in rest) {
            val candidate = (kept + part).joinToString(", ")
            if (candidate.length <= max) kept += part
        }
        return kept
    }

    private fun naturalList(parts: List<String>): String = when (parts.size) {
        0 -> ""
        1 -> parts.first()
        2 -> "${parts[0]} and ${parts[1]}"
        else -> parts.dropLast(1).joinToString(", ") + ", and " + parts.last()
    }

    private fun identitySnippets(lock: String, take: Int): String {
        return lock.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { isGenericFidelityBoilerplate(it) }
            .filterNot { it.startsWith("Match uploaded", ignoreCase = true) }
            .flatMap { it.split(Regex("[,;]|(?:\\s+and\\s+)")) }
            .map {
                it.trim().trimEnd('.')
                    .removePrefix("Preserve ").removePrefix("preserve ")
                    .trim()
            }
            .filter { it.length in 3..70 }
            .distinctBy { it.lowercase() }
            .toList()
            .let { pickLockDetails(it, take) }
            .joinToString(", ")
    }

    /** FEATURE / DEMO may use at most one hand — never two hands / both hands. */
    private fun enforceOneHandInFeatureDemo(line: String): String {
        val isFeature = Regex("""(?i)4\.0|FEATURE\s*/\s*DEMO""").containsMatchIn(line)
        if (!isFeature) return line
        var out = line
            .replace(Regex("""(?i)\bboth hands\b"""), "one hand")
            .replace(Regex("""(?i)\btwo hands\b"""), "one hand")
            .replace(Regex("""(?i)\bhands\b"""), "one hand")
            .replace(Regex("""(?i)\bone one hand\b"""), "one hand")
            .replace(Regex("""(?i)\bone hand one hand\b"""), "one hand")
        if (Regex("""(?i)\bhand\b""").containsMatchIn(out) &&
            !Regex("""(?i)\bone(?:\s+adult)?\s+hand\b""").containsMatchIn(out)
        ) {
            out = out.replace(
                Regex("""(?i)(FEATURE\s*/\s*DEMO\s*:?\s*)"""),
                "$1one hand — "
            )
        }
        return out.replace(Regex("""\s{2,}"""), " ").trim()
    }

    private fun clipShotLine(line: String, max: Int = 100): String {
        val cleaned = line
            .replace(Regex("""(?i)\bimmediately\b"""), "")
            .replace(Regex("""(?i),\s*physically plausible"""), "")
            .replace(Regex("""(?i)\bphysically plausible\b"""), "")
            .replace(Regex("""(?i)\bdesirable\b"""), "")
            .replace(Regex("""(?i)\bclear full/product-true framing\b"""), "full product framing")
            .replace(Regex("""(?i)\bwith strongest verified detail\b"""), "strongest detail")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
        if (cleaned.length <= max) return cleaned
        if (Regex("(?i)FEATURE|HERO").containsMatchIn(cleaned)) return cleaned
        val colon = cleaned.indexOf(':')
        if (colon >= 0 && cleaned.contains("HOOK", ignoreCase = true)) {
            val prefix = cleaned.substring(0, colon).trim()
            var body = cleaned.substring(colon + 1).trim()
            val suffix = if (body.endsWith("visible now", ignoreCase = true)) " visible now" else ""
            if (suffix.isNotEmpty()) {
                body = body.removeSuffix(suffix).trim().trimEnd(',', ';')
            }
            val budget = (max - prefix.length - 2 - suffix.length).coerceAtLeast(24)
            return "$prefix: ${clipHookDetails(body, budget)}$suffix"
        }
        if (colon >= 0) {
            val prefix = cleaned.substring(0, colon).trim()
            val details = cleaned.substring(colon + 1).trim()
            val budget = (max - prefix.length - 2).coerceAtLeast(24)
            val clipped = clipHookDetails(details, budget)
            if (clipped.isNotBlank()) return "$prefix: $clipped"
        }
        return clipChars(cleaned, max)
    }

    private fun simplifyCritical(marketplace: Boolean): String {
        return if (marketplace) {
            "Same product. Exactly 8.0s, four blocks. No listing UI."
        } else {
            "Same product. Exactly 8.0s, four blocks only."
        }
    }

    private fun simplifyNegative(raw: String, identity: String = "", maxBullets: Int = 6): String {
        val pan = looksLikeCookwarePan(identity)
        if (pan) {
            return listOf(
                "no missing wooden lid, ferrule, rivets, or hanging ring",
                "no generic pan or wok",
                "no shallower bowl or changed silhouette"
            ).take(maxBullets).joinToString("\n") { "- $it" }
        }
        val fromPrompt = raw.lineSequence()
            .map { it.trim().removePrefix("-").trim() }
            .filter { it.isNotBlank() }
            .filterNot {
                it.contains("malformed hands", ignoreCase = true) ||
                    it.contains("impossible mechanics", ignoreCase = true)
            }
            .filterNot { bullet ->
                !pan && PAN_NEGATIVE_LEAKS.any { leak -> bullet.lowercase().contains(leak) }
            }
            .toList()
        val fromIdentity = identitySnippets(identity, take = 6)
            .split(",")
            .map { it.trim() }
            .filter { it.length in 3..70 }
            .map { "no missing or redesigned $it" }
        val bullets = dropRedundantNegatives(
            (fromPrompt + fromIdentity)
                .distinctBy { it.lowercase() }
                .sortedByDescending { bullet -> negativeScore(bullet, identity, pan) }
                .map { clipChars(it, 72) }
        )
        val chosen = when {
            bullets.size >= 4 -> bullets
            bullets.isNotEmpty() -> (bullets + SHORT_NEGATIVE).distinctBy { it.lowercase() }
            else -> SHORT_NEGATIVE
        }
        return chosen.take(maxBullets).joinToString("\n") { "- $it" }
    }

    private fun negativeScore(bullet: String, identity: String, panIdentity: Boolean): Int {
        val b = bullet.lowercase()
        if (!panIdentity && PAN_NEGATIVE_LEAKS.any { leak -> b.contains(leak) }) return -20
        val tokens = b.split(Regex("[^a-zA-Zа-яА-Я0-9]+")).filter { it.length >= 4 }
        val blob = identity.lowercase()
        val overlap = tokens.count { blob.contains(it) }
        var score = when {
            overlap >= 2 -> overlap + 8
            overlap == 1 -> 4
            b.contains("morphing") || b.contains("marketplace") || b.contains("listing ui") -> 1
            else -> 0
        }
        if (panIdentity && b.contains("wok")) score += 28
        return score
    }

    private val CLIP_TRAILING_STOP = setOf(
        "no", "or", "a", "an", "the", "of", "to", "in", "on", "at", "and", "be"
    )

    private fun dropRedundantNegatives(bullets: List<String>): List<String> {
        val kept = mutableListOf<String>()
        for (bullet in bullets) {
            val keys = distinctiveKeysIn(bullet)
            val covered = keys.isNotEmpty() && kept.any { existing ->
                val existingKeys = distinctiveKeysIn(existing)
                keys.all { it in existingKeys }
            }
            if (!covered) kept += bullet
        }
        return kept
    }

    private fun clipChars(text: String, max: Int): String {
        val t = text.trim()
        if (t.length <= max) return t
        val window = t.substring(0, max)
        val punct = maxOf(window.lastIndexOf('.'), window.lastIndexOf(';'), window.lastIndexOf(','))
        val space = window.lastIndexOf(' ')
        val cutAt = when {
            punct > max / 3 -> punct + 1
            space > max / 3 -> space
            else -> max
        }
        var result = t.substring(0, cutAt.coerceIn(1, t.length)).trimEnd(' ', ',', ';', '.')
        val words = result.split(Regex("\\s+")).filter { it.isNotBlank() }.toMutableList()
        while (words.isNotEmpty()) {
            val last = words.last().lowercase().trimEnd(',', ';', '.', '…')
            if (last.length <= 2 || last in CLIP_TRAILING_STOP) {
                words.removeAt(words.lastIndex)
            } else {
                break
            }
        }
        result = words.joinToString(" ").trimEnd(',', ';', '.')
        if (result.isBlank()) return t.take(max).trimEnd()
        val cutAtPunctuation = punct > max / 3 && cutAt == punct + 1
        return if (cutAtPunctuation) result else "$result…"
    }

    private fun clipWords(text: String, maxWords: Int): String {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size <= maxWords) return text.trim()
        val taken = words.take(maxWords).toMutableList()
        val dangling = setOf("warm", "soft", "cold", "slight", "natural", "premium")
        while (taken.size > 1 && taken.last().trimEnd(',', ';', '.').lowercase() in dangling) {
            taken.removeAt(taken.lastIndex)
        }
        return taken.joinToString(" ").trimEnd(',', ';', '.') + "."
    }

    private fun stripLongDoctrine(text: String): String {
        var out = text
        out = out.replace(
            Regex("(?is)Use the uploaded product photos as strict visual references[\\s\\S]{0,800}?PRODUCT DESIGN = LOCKED\\.?"),
            ""
        )
        out = out.replace(
            Regex("(?is)The generated product must remain the same physical product shown in the uploaded photos\\.?"),
            ""
        )
        out = out.replace(
            Regex("(?is)Preserve the exact overall silhouette, proportions, construction, colors, materials, controls, handles, hinges, accessories, markings and distinctive visual details\\.?"),
            ""
        )
        out = out.replace(
            Regex("(?is)The uploaded marketplace screenshots are reference material only\\.[\\s\\S]{0,500}?Recreate only the physical product\\.?"),
            ""
        )
        return out.replace(Regex("\n{3,}"), "\n\n").trim()
    }

    private fun firstSentences(text: String, max: Int): String {
        val cleaned = text.replace(Regex("\\s+"), " ").trim()
        if (cleaned.isBlank()) return ""
        val parts = cleaned.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
        return parts.take(max).joinToString(" ").trim()
    }

    private fun stripAfterHashtags(prompt: String): String {
        val idx = Regex("(?im)^HASHTAGS\\b").find(prompt)?.range?.first ?: return prompt
        val head = prompt.substring(0, idx)
        val tail = prompt.substring(idx)
        val lines = tail.lines().toMutableList()
        val keep = mutableListOf<String>()
        keep += lines.first()
        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) {
                if (keep.size > 1) break
                continue
            }
            if (line.startsWith("#") || line.contains("#")) {
                keep += lines[i]
            } else {
                break
            }
        }
        return (head + keep.joinToString("\n")).trimEnd()
    }

    private fun dedupeParagraphs(text: String): String {
        val parts = text.split(Regex("\n{2,}"))
        val seen = linkedSetOf<String>()
        val out = mutableListOf<String>()
        parts.forEach { p ->
            val norm = p.trim().replace(Regex("\\s+"), " ").lowercase()
            if (norm.isBlank()) return@forEach
            if (seen.add(norm)) out += p.trim()
        }
        return out.joinToString("\n\n")
    }

    private fun removeDuplicateVisualFidelity(text: String): String {
        val pattern = Regex(
            "(?is)(Use the uploaded product photos as strict visual references[\\s\\S]{0,500}?PRODUCT DESIGN = LOCKED\\.?)"
        )
        val matches = pattern.findAll(text).toList()
        if (matches.size <= 1) return text
        var result = text
        matches.drop(1).forEach { m ->
            result = result.replace(m.value, "")
        }
        return result.replace(Regex("\n{3,}"), "\n\n")
    }

    private fun removeDuplicateMarketplaceRules(text: String): String {
        val pattern = Regex(
            "(?is)(The uploaded marketplace screenshots are reference material only\\.[\\s\\S]{0,400}?Recreate only the physical product\\.?)"
        )
        val matches = pattern.findAll(text).toList()
        if (matches.size <= 1) return text
        var result = text
        matches.drop(1).forEach { m -> result = result.replace(m.value, "") }
        return result.replace(Regex("\n{3,}"), "\n\n")
    }

    private fun normalizePunctuation(text: String): String {
        return text
            .replace(Regex(" +([,.;:!?])"), "$1")
            .replace(Regex("\n{3,}"), "\n\n")
            .replace(Regex(" {2,}"), " ")
            .trim()
    }

    fun cleanupVoiceover(raw: String, language: String, @Suppress("UNUSED_PARAMETER") tiktokShop: Boolean = true): String {
        val t = raw.trim()
        if (language.equals("OFF", true) || t.equals("OFF", true)) return "OFF"
        return t.replace(Regex("\\s+"), " ").trim()
    }

    private fun normalizeHashtags(tags: List<String>): List<String> {
        return tags.map { t ->
            val cleaned = t.trim().removePrefix("#").replace(Regex("[^\\p{L}\\p{N}_]"), "")
            if (cleaned.isBlank()) null else "#$cleaned"
        }.filterNotNull().distinct().take(5)
    }

    private fun extractHashtags(prompt: String): List<String> {
        val section = extractSection(prompt, "HASHTAGS")
        return Regex("#[\\p{L}\\p{N}_]+").findAll(section).map { it.value }.toList()
    }

    fun extractSection(prompt: String, section: String): String {
        val regex = Regex("(?im)^$section\\b\\s*:?\\s*", RegexOption.MULTILINE)
        val match = regex.find(prompt) ?: return ""
        val start = match.range.last + 1
        val rest = prompt.substring(start)
        val boundaryNames = (REQUIRED_SECTIONS + LEGACY_SECTIONS)
            .filter { !it.equals(section, true) }
        val next = boundaryNames
            .mapNotNull { name ->
                val pattern = if (LEGACY_SECTIONS.any { it.equals(name, true) }) {
                    // Standalone legacy header only
                    Regex("""(?im)^${Regex.escape(name)}\s*:?\s*$""")
                } else {
                    Regex("""(?im)^${Regex.escape(name)}\b""")
                }
                pattern.find(rest)?.range?.first
            }
            .minOrNull()
        val body = if (next != null) rest.substring(0, next) else rest
        return stripLegacyInlineHeaders(body.trim())
    }

    /**
     * Drop entire legacy doctrine blocks (header + body until next known header).
     * Required 12-section structure is rebuilt afterward.
     */
    fun stripLegacySections(prompt: String): String {
        if (prompt.isBlank()) return prompt
        // Required headers may be followed by body on later lines.
        // Legacy headers must be alone on the line (optional trailing ':') so we
        // do not treat inline doctrine like "CORE PRINCIPLE: …" as a section cut.
        val requiredAlt = REQUIRED_SECTIONS.joinToString("|") { Regex.escape(it) }
        val legacyAlt = LEGACY_SECTIONS.joinToString("|") { Regex.escape(it) }
        val headerRegex = Regex(
            "(?im)^(?:(?:$requiredAlt)\\b\\s*:?\\s*|(?:$legacyAlt)\\s*:?\\s*$)"
        )
        val matches = headerRegex.findAll(prompt).toList()
        if (matches.isEmpty()) return prompt

        val allHeaders = (REQUIRED_SECTIONS + LEGACY_SECTIONS)
            .distinctBy { it.uppercase() }
            .sortedByDescending { it.length }

        fun headerNameAt(index: Int): String {
            val slice = prompt.substring(index, minOf(prompt.length, index + 80))
            return allHeaders.first { h -> slice.startsWith(h, ignoreCase = true) }
        }

        val keep = StringBuilder()
        for (i in matches.indices) {
            val m = matches[i]
            val name = headerNameAt(m.range.first)
            val requiredName = REQUIRED_SECTIONS.firstOrNull { it.equals(name, true) }
            if (requiredName == null) continue
            val bodyStart = m.range.last + 1
            val bodyEnd = matches.getOrNull(i + 1)?.range?.first ?: prompt.length
            val body = stripLegacyInlineHeaders(prompt.substring(bodyStart, bodyEnd).trim())
            if (keep.isNotEmpty()) keep.append("\n\n")
            keep.append(requiredName)
            if (body.isNotBlank()) {
                keep.append('\n').append(body)
            }
        }
        return if (keep.isEmpty()) prompt else keep.toString().trim() + "\n"
    }

    private fun stripLegacyInlineHeaders(body: String): String {
        if (body.isBlank()) return body
        // Only cut at true standalone legacy section headers (line = header only).
        val legacyAlt = LEGACY_SECTIONS.joinToString("|") { Regex.escape(it) }
        val cut = Regex("(?im)^(?:$legacyAlt)\\s*:?\\s*$").find(body)?.range?.first
        return if (cut != null) body.substring(0, cut).trimEnd() else body.trim()
    }

    private fun replaceSection(prompt: String, section: String, body: String): String {
        val map = linkedMapOf<String, String>()
        REQUIRED_SECTIONS.forEach { name ->
            map[name] = extractSection(prompt, name)
        }
        map[section] = body.trim()
        return REQUIRED_SECTIONS.joinToString("\n\n") { name ->
            "$name\n${map[name].orEmpty().trim()}"
        }.trim() + "\n"
    }

    private fun ensureSectionOrder(prompt: String, marketplace: Boolean, issues: MutableList<String>): String {
        val map = linkedMapOf<String, String>()
        REQUIRED_SECTIONS.forEach { name ->
            map[name] = extractSection(prompt, name)
        }
        if (map["FORMAT"].isNullOrBlank()) {
            map["FORMAT"] = "Vertical 9:16. Photorealistic TikTok Shop ad. Exactly 8.0s."
            issues += "format_injected"
        }
        if (map["SHOT SEQUENCE"].isNullOrBlank() || !hasFourBlocks(map["SHOT SEQUENCE"].orEmpty())) {
            map["SHOT SEQUENCE"] = CANONICAL_SHOT_SEQUENCE
            issues += "shot_sequence_injected"
        }
        if (map["PRODUCT LOCK"].isNullOrBlank()) {
            map["PRODUCT LOCK"] = SHORT_PRODUCT_LOCK
            issues += "product_lock_injected"
        }
        if (marketplace) {
            val refs = map["REFERENCES"].orEmpty()
            if (!refs.contains("marketplace", ignoreCase = true) &&
                !refs.contains("reference only", ignoreCase = true)
            ) {
                map["REFERENCES"] = (refs + "\n" + SHORT_MARKETPLACE).trim()
            }
        }
        if (map["NEGATIVE PROMPT"].isNullOrBlank()) {
            map["NEGATIVE PROMPT"] = SHORT_NEGATIVE.joinToString("\n") { "- $it" }
            issues += "negative_prompt_injected"
        }
        if (map["CRITICAL"].isNullOrBlank()) {
            map["CRITICAL"] = "Keep product identity. Exactly 8.0s. Four blocks only."
        }
        if (map["AUDIO"].isNullOrBlank()) {
            map["AUDIO"] = "Subtle music. Clear voice."
        }
        if (map["ON-SCREEN TEXT"].isNullOrBlank()) {
            map["ON-SCREEN TEXT"] = "None."
        }
        if (map["SETTING"].isNullOrBlank()) {
            map["SETTING"] = "Uncluttered premium studio."
        }
        if (map["REFERENCES"].isNullOrBlank()) {
            map["REFERENCES"] = "Uploaded product photos are the visual evidence."
        }
        if (map["VOICEOVER"].isNullOrBlank()) {
            map["VOICEOVER"] = "OFF"
        }
        if (map["TITLE"].isNullOrBlank()) {
            map["TITLE"] = "Product Ad"
        }
        if (map["HASHTAGS"].isNullOrBlank()) {
            map["HASHTAGS"] = "#TikTokShop #ProductAd #MustSee #HomeFinds #ShopNow"
        }
        return REQUIRED_SECTIONS.joinToString("\n\n") { name ->
            "$name\n${map[name].orEmpty().trim()}"
        }.trim() + "\n"
    }

    private fun hasFourBlocks(sequence: String): Boolean {
        return listOf("0.0", "2.0", "4.0", "6.0", "8.0").all { sequence.contains(it) } &&
            !Regex("(?i)(9 scenes|25[-–]35|long-form)").containsMatchIn(sequence)
    }

    private fun padHashtags(tags: List<String>, title: String): List<String> {
        val base = tags.toMutableList()
        val fallbacks = listOf(
            "#TikTokShop",
            "#ProductAd",
            "#MustSee",
            "#HomeFinds",
            "#ShopNow",
            "#ViralProduct",
            "#SmartBuy"
        )
        val fromTitle = title.split(Regex("\\s+"))
            .map { it.replace(Regex("[^\\p{L}\\p{N}]"), "") }
            .filter { it.length >= 3 }
            .map { "#$it" }
        (fromTitle + fallbacks).forEach { tag ->
            if (base.size >= 5) return@forEach
            if (base.none { it.equals(tag, true) }) base += tag
        }
        while (base.size < 5) base += "#TikTokShop${base.size}"
        return base.take(5)
    }

    private fun ensureFiveHashtags(
        tags: List<String>,
        title: String,
        tiktokShopMode: Boolean
    ): List<String> {
        val padded = if (tags.size == 5) tags else padHashtags(tags, title)
        val result = padded.take(5).toMutableList()
        if (tiktokShopMode && result.none { it.equals("#TikTokShop", ignoreCase = true) }) {
            if (result.size >= 5) result[result.lastIndex] = "#TikTokShop"
            else result += "#TikTokShop"
        }
        return result.take(5)
    }

    private val CANONICAL_SHOT_SEQUENCE = """
        0.0–2.0s — HOOK: product visible now with strongest verified detail
        2.0–4.0s — IDENTITY: same product, full framing of verified parts
        4.0–6.0s — FEATURE / DEMO: slow camera push-in; product remains still
        6.0–8.0s — HERO / CTA: hard cut; stable 3/4 hero. Hold to 8.0s
    """.trimIndent()
}
