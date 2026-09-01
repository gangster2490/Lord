package de.spardirekt.agents.pro.generation

object PromptCleanup {

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
        "Match uploaded product photos exactly. Do not replace or redesign."

    private val SHORT_MARKETPLACE =
        "Marketplace shots = reference only; no listing UI."

    private val SHORT_NEGATIVE = listOf(
        "no generic replacement product",
        "no redesign / wrong proportions / colors / materials",
        "no missing confirmed parts or invented accessories",
        "no product morphing",
        "no marketplace UI or phone interface",
        "no CGI/cartoon look"
    )

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
        tiktokShopMode: Boolean = true,
        productEvidence: String = ""
    ): CleanupResult {
        val issues = mutableListOf<String>()
        var prompt = rawPrompt.trim()

        prompt = stripAfterHashtags(prompt)
        prompt = prompt.replace(Regex("(?is)\\n*TIKTOK SHOP SAFETY AUDIT[\\s\\S]*$"), "")
            .trim()
        prompt = stripLegacySections(prompt)

        prompt = normalizePunctuation(prompt)
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
        prompt = cleanGeneratedSections(prompt)
        prompt = applyPanFidelity(prompt, productEvidence)

        if (!prompt.contains("0.0") || !prompt.contains("8.0")) {
            issues += "timeline_markers_weak"
        }

        REQUIRED_SECTIONS.forEach { section ->
            if (!Regex("""(?im)^$section\b""").containsMatchIn(prompt)) {
                issues += "missing_$section"
            }
        }

        prompt = stripAfterHashtags(prompt).trim() + "\n"
        return CleanupResult(prompt, vo, cleanTitle, tags, issues)
    }

    /**
     * Prepares a stored prompt for display without shortening model output.
     *
     * This only removes non-display legacy sections, repairs the required
     * structure, and syncs VOICEOVER / TITLE / HASHTAGS from their cards.
     * Generated section bodies are otherwise preserved verbatim.
     */
    fun prepareStoredPromptForDisplay(
        rawPrompt: String,
        voiceover: String,
        title: String,
        hashtags: List<String>,
        marketplace: Boolean,
        tiktokShopMode: Boolean = true,
        productEvidence: String = ""
    ): String {
        var prompt = rawPrompt.trim()
        if (prompt.isBlank()) return ""

        if (looksLikeRawJson(prompt)) {
            prompt = JsonExtractor.salvageVeoPrompt(prompt).orEmpty()
            if (prompt.isBlank()) return ""
        }

        prompt = stripAfterHashtags(prompt)
        prompt = prompt.replace(Regex("(?is)\\n*TIKTOK SHOP SAFETY AUDIT[\\s\\S]*$"), "").trim()
        prompt = stripLegacySections(prompt)
        prompt = normalizePunctuation(prompt)

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
        prompt = cleanGeneratedSections(prompt)
        prompt = applyPanFidelity(prompt, productEvidence)
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

    private fun cleanGeneratedSections(prompt: String): String {
        var cleaned = prompt

        val productLockLines = extractSection(cleaned, "PRODUCT LOCK")
            .lineSequence()
            .map(::removeLegacyFidelityDoctrine)
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .toList()
        val productLock = buildList {
            if (productLockLines.none { it.contains("uploaded product photos", ignoreCase = true) }) {
                add(SHORT_PRODUCT_LOCK)
            }
            addAll(productLockLines)
        }.joinToString("\n")
        cleaned = replaceSection(cleaned, "PRODUCT LOCK", productLock)

        val overlays = cleanOnScreenText(extractSection(cleaned, "ON-SCREEN TEXT"))
        cleaned = replaceSection(cleaned, "ON-SCREEN TEXT", overlays)

        val shotSequence = extractSection(cleaned, "SHOT SEQUENCE")
            .lineSequence()
            .joinToString("\n") { line ->
                if (line.contains("FEATURE", ignoreCase = true) || line.contains("4.0")) {
                    enforceOneHandInFeatureDemo(line)
                } else {
                    line
                }
            }
            .trim()
        return replaceSection(cleaned, "SHOT SEQUENCE", shotSequence)
    }

    /**
     * Adds the exact known pan signature without replacing or shortening any
     * product-specific content generated from the uploaded images.
     */
    private fun applyPanFidelity(prompt: String, productEvidence: String): String {
        if (!PanFidelity.matches(prompt, productEvidence)) return prompt

        var locked = prompt
        locked = replaceSection(
            locked,
            "PRODUCT LOCK",
            mergeDistinctLines(
                required = PanFidelity.PRODUCT_LOCK,
                generated = extractSection(locked, "PRODUCT LOCK")
            )
        )
        locked = replaceSection(
            locked,
            "CRITICAL",
            mergeDistinctLines(
                required = PanFidelity.CRITICAL,
                generated = extractSection(locked, "CRITICAL")
            )
        )

        val requiredNegatives = PanFidelity.NEGATIVE_BULLETS.joinToString("\n") { "- $it" }
        locked = replaceSection(
            locked,
            "NEGATIVE PROMPT",
            mergeDistinctLines(
                required = requiredNegatives,
                generated = extractSection(locked, "NEGATIVE PROMPT")
            )
        )
        return locked
    }

    private fun mergeDistinctLines(required: String, generated: String): String =
        (required.lineSequence() + generated.lineSequence())
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.removePrefix("-").trim().lowercase() }
            .joinToString("\n")

    /**
     * ON-SCREEN TEXT must only contain actual overlay copy that may appear in the video.
     * Production instructions are removed, but generated overlay copy is never clipped.
     */
    fun cleanOnScreenText(raw: String): String {
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
        if (cleaned.isEmpty()) return "None."
        return cleaned.joinToString(" · ")
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

    private fun removeLegacyFidelityDoctrine(line: String): String {
        val boilerplate = listOf(
            "Use the uploaded product photos as strict visual references for the physical product",
            "The generated product must remain the same physical product shown in the uploaded photos",
            "Preserve the exact overall silhouette, proportions, construction, colors, materials, controls, handles, hinges, accessories, markings and distinctive visual details",
            "Do not reinterpret the product based on category knowledge",
            "Do not replace the photographed product with a generic or similar product",
            "Do not redesign, modernize, simplify or stylize the product",
            "If creative instructions conflict with accurate product identity, preserve the photographed product and simplify the creative action instead",
            "CORE PRINCIPLE: CREATIVE PRESENTATION = FLEXIBLE; PRODUCT DESIGN = LOCKED"
        )
        return boilerplate.fold(line.trim()) { text, sentence ->
            text.replace(Regex("""(?i)${Regex.escape(sentence)}\.?"""), "")
        }.trim().trimStart(':', ';', '-', '—').trim()
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
            !Regex("""(?i)\bone hand\b""").containsMatchIn(out)
        ) {
            out = out.replace(
                Regex("""(?i)(FEATURE\s*/\s*DEMO\s*:?\s*)"""),
                "$1one hand — "
            )
        }
        return out.replace(Regex("""\s{2,}"""), " ").trim()
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

    private fun normalizePunctuation(text: String): String {
        return text
            .replace(Regex(" +([,.;:!?])"), "$1")
            .replace(Regex("\n{3,}"), "\n\n")
            .replace(Regex(" {2,}"), " ")
            .trim()
    }

    fun cleanupVoiceover(raw: String, language: String, tiktokShop: Boolean = true): String {
        return VoiceoverSystem.finalize(raw, language, tiktokShop).text
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
        0.0–2.0s — HOOK: product visible with strongest verified detail
        2.0–4.0s — IDENTITY: clear full product framing
        4.0–6.0s — FEATURE / DEMO: one hero feature; one hand only if hands used
        6.0–8.0s — HERO / CTA: hero hold and soft CTA
    """.trimIndent()
}
