package de.spardirekt.veoprompt.ultra.generation

import de.spardirekt.veoprompt.ultra.model.ProductModel

object Fixtures {

    fun panModel() = ProductModel(
        productCategory = "cookware",
        productIdentity = "Deep black pan with wooden lid",
        visualSignature = listOf(
            "deep rounded bowl",
            "high sides",
            "wooden handle",
            "ferrule",
            "rivets",
            "hanging ring",
            "wooden lid"
        ),
        confirmedStates = listOf("wooden lid seated on the bowl"),
        confirmedFunctions = listOf("wooden lid lifts off")
    )

    fun fishingChairModel() = ProductModel(
        productCategory = "outdoor seating",
        productIdentity = "folding fishing chair",
        visualSignature = listOf("metal frame", "padded backrest", "side tray", "rubber feet")
    )

    fun phBitsModel() = ProductModel(
        productCategory = "tools",
        productIdentity = "PH screwdriver bits",
        visualSignature = listOf("PH tip", "hex collar", "length markings")
    )

    fun riceWasherModel() = ProductModel(
        productCategory = "kitchen",
        productIdentity = "rice washing container",
        visualSignature = listOf("clear bowl", "fitted lid", "side drain")
    )

    fun stoveCaseModel() = ProductModel(
        productCategory = "camping stove",
        productIdentity = "closed portable stove case",
        visualSignature = listOf("closed case", "latches", "carry handle")
    )

    fun contactGrillModel() = ProductModel(
        productCategory = "cookware",
        productIdentity = "contact grill",
        visualSignature = listOf("ridged plates", "hinge", "lid handle")
    )

    fun validVeoPrompt(model: ProductModel = ProductModel()): String {
        val spec = RegressionLocks.matchingSpec(model)
        val details = model.visualSignature.ifEmpty { RegressionLocks.PAN.requiredDetails }
        val detailCsv = details.joinToString(", ")
        val setting = spec?.setting
            ?: if (model.visualSignature.isEmpty()) {
                RegressionLocks.PAN.setting
            } else {
                "Uncluttered premium studio. Product dominant. Realistic lighting."
            }
        val overlays = spec?.overlayLines
            ?: if (model.visualSignature.isEmpty()) {
                RegressionLocks.PAN.overlayLines
            } else {
                details.take(2).map { token ->
                    token.split(" ").take(2).joinToString(" ").replaceFirstChar { it.uppercase() }
                }
            }
        val voiceover = spec?.voiceover
            ?: if (model.productIdentity.isBlank() && model.visualSignature.isEmpty()) {
                RegressionLocks.PAN.voiceover
            } else {
                model.productIdentity.ifBlank { RegressionLocks.PAN.voiceover }
            }
        val negatives = (spec?.negativePrefix ?: details.take(6).map { "- no missing or redesigned $it" }) +
            listOf(
                "- no duplicate product",
                "- no marketplace UI",
                "- no malformed hands"
            )
        val hookParts = distinctiveDetails(details, 3)
        val identityParts = distinctiveDetails(details, 5)
            .filterNot { part -> hookParts.any { it.equals(part, ignoreCase = true) } }
            .take(3)
            .ifEmpty { hookParts.take(2) }
        val keepParts = hookParts.sortedByDescending { distinctiveScore(it) }.take(2)
        val hook = "Close-up shows the ${naturalList(hookParts)}"
        val identity = "Full-profile hard cut shows the ${naturalList(identityParts)}"
        val hasVerifiedLidLift = model.confirmedFunctions.any { function ->
            function.contains("lid", ignoreCase = true) && function.contains("lift", ignoreCase = true)
        }
        val feature = if (hasVerifiedLidLift) {
            "One adult hand lifts the lid slightly, revealing empty bowl"
        } else {
            "Slow push-in across ${keepParts.joinToString(", ")}; product remains still"
        }
        val hero = if (hasVerifiedLidLift) {
            "Hard cut: lid seated; stable hero. Hold to 8.0s"
        } else {
            "Hard cut: stable hero. Hold to 8.0s"
        }
        return """
FORMAT
Vertical 9:16.
Photorealistic commercial TikTok Shop product ad style.
Generate exactly 8.0 seconds total.
Timeline ends at exactly 8.0s.
Use exactly four 2.0-second blocks.

REFERENCES
The uploaded marketplace screenshots are reference material only.
Do not reproduce, animate, display or use marketplace screenshots as video frames.
Do not show marketplace UI, prices, seller text, buttons, banners or phone interface.
Recreate only the physical product.

PRODUCT LOCK
Use the uploaded physical product photos as strict visual references.
The same single physical product shown in the uploaded photos must remain unchanged across all four shots.
Do not regenerate a slightly different version of the product for each shot.
Preserve $detailCsv.
Do not reinterpret the product from category knowledge.
CORE PRINCIPLE: CREATIVE PRESENTATION = FLEXIBLE. PRODUCT DESIGN = LOCKED.

SETTING
$setting

SHOT SEQUENCE
0.0–2.0s — HOOK: $hook
2.0–4.0s — IDENTITY: $identity
4.0–6.0s — FEATURE / DEMO: $feature
6.0–8.0s — HERO / CTA: $hero

ON-SCREEN TEXT
${overlays.joinToString("\n")}

VOICEOVER
$voiceover

AUDIO
Subtle background music. Clear voice.

CRITICAL
The same single physical product must remain visually consistent throughout all four shots.
Uploaded physical product photos override category knowledge.
If visual accuracy conflicts with creativity, preserve product accuracy.

NEGATIVE PROMPT
${negatives.joinToString("\n")}
""".trimIndent()
    }

    private fun naturalList(parts: List<String>): String = when (parts.size) {
        0 -> ""
        1 -> parts.first()
        2 -> "${parts[0]} and ${parts[1]}"
        else -> parts.dropLast(1).joinToString(", ") + ", and " + parts.last()
    }

    /** First photographed token, then rare identity parts (lid / ferrule before handle). */
    private fun distinctiveDetails(details: List<String>, take: Int): List<String> {
        val cleaned = details.map { it.trim() }.filter { it.isNotBlank() }
        if (cleaned.size <= take) return cleaned
        val first = cleaned.first()
        val ranked = cleaned.drop(1).sortedByDescending { distinctiveScore(it) }
        return (listOf(first) + ranked).distinctBy { it.lowercase() }.take(take)
    }

    private fun distinctiveScore(token: String): Int {
        val t = token.lowercase()
        var score = listOf(
            "lid", "ferrule", "rivet", "hanging", "handle", "bowl",
            "tray", "frame", "collar", "bit", "drain", "plate", "latch"
        ).count { key -> t.contains(key) } * 12 + minOf(t.length, 24)
        if (t.contains("ferrule")) score += 24
        if (t.contains("lid")) score += 20
        if (t.contains("hanging")) score += 16
        if (t.contains("handle")) score += 10
        if (t.contains("rivet")) score += 8
        return score
    }
}
