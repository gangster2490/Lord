package de.spardirekt.agents.pro.generation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptCleanupTest {

    @Test
    fun finalize_enforcesExactFiveHashtags_andSectionOrder() {
        val raw = """
FORMAT
Vertical 9:16. Exactly 8.0 seconds.

REFERENCES
Product photos confirm black frame.

PRODUCT LOCK
Preserve black tubular frame and red tray.

SETTING
Premium studio.

SHOT SEQUENCE
0.0–2.0s — HOOK
2.0–4.0s — IDENTITY
4.0–6.0s — FEATURE / DEMO
6.0–8.0s — HERO / CTA

ON-SCREEN TEXT
Compact fold

VOICEOVER
Закажите. Закажите в TikTok Shop.

AUDIO
Subtle click and soft music.

CRITICAL
Keep identity locked.

NEGATIVE PROMPT
- no generic chair
- no redesign

TITLE
Fishing Chair Compact Fold

HASHTAGS
#a #b

TIKTOK SHOP SAFETY AUDIT
Something secret
""".trimIndent()

        val result = PromptCleanup.finalize(
            rawPrompt = raw,
            voiceover = "Закажите. Закажите в TikTok Shop.",
            title = "Fishing Chair Compact Fold",
            hashtags = listOf("#a", "#b"),
            voiceLanguage = "RU",
            marketplace = true,
            tiktokShopMode = true
        )

        assertEquals(5, result.hashtags.size)
        assertTrue(result.hashtags.any { it.equals("#TikTokShop", ignoreCase = true) })
        assertFalse(result.veoPrompt.contains("TIKTOK SHOP SAFETY AUDIT"))
        assertTrue(result.veoPrompt.contains("FORMAT"))
        assertTrue(result.veoPrompt.contains("PRODUCT LOCK"))
        assertTrue(result.veoPrompt.contains("NEGATIVE PROMPT"))
        assertTrue(result.veoPrompt.trim().substringAfterLast("HASHTAGS").contains("#"))
        val zakCount = Regex("(?iu)закажите").findAll(result.voiceover).count()
        assertTrue("voiceover should not repeat CTA: ${result.voiceover}", zakCount <= 1)
        val issues = PromptCleanup.validateCompleteness(result.veoPrompt, result.hashtags)
        assertTrue(issues.none { it == "safety_audit_leaked" })
        assertTrue(issues.none { it.startsWith("hashtag_count") })
        assertTrue(result.veoPrompt.contains("0.0"))
        assertTrue(result.veoPrompt.contains("8.0"))
        assertTrue(result.veoPrompt.contains("marketplace", ignoreCase = true))
        assertFalse(result.veoPrompt.contains("PRODUCT DESIGN = LOCKED"))
        assertFalse(result.veoPrompt.contains("CORE PRINCIPLE"))
    }

    @Test
    fun cleanupVoiceover_removesDuplicateWords() {
        val cleaned = PromptCleanup.cleanupVoiceover(
            "Kompakt  kompakt falten. Jetzt bestellen. Jetzt bestellen.",
            "DE"
        )
        assertFalse(cleaned.lowercase().split("jetzt bestellen").size > 2)
    }

    @Test
    fun injectsFourShotBlocksWhenMissing() {
        val raw = """
FORMAT
9:16

REFERENCES
Photos

PRODUCT LOCK
Keep identity

SETTING
Studio

SHOT SEQUENCE
A pretty video of the product.

ON-SCREEN TEXT
Hello

VOICEOVER
OFF

AUDIO
Music

CRITICAL
Lock

NEGATIVE PROMPT
- no redesign

TITLE
Pan

HASHTAGS
#a
""".trimIndent()
        val result = PromptCleanup.finalize(
            rawPrompt = raw,
            voiceover = "OFF",
            title = "Deep Black Pan",
            hashtags = emptyList(),
            voiceLanguage = "OFF",
            marketplace = false,
            tiktokShopMode = true
        )
        assertTrue(result.veoPrompt.contains("0.0–2.0s"))
        assertTrue(result.veoPrompt.contains("2.0–4.0s"))
        assertTrue(result.veoPrompt.contains("4.0–6.0s"))
        assertTrue(result.veoPrompt.contains("6.0–8.0s"))
        assertEquals(5, result.hashtags.size)
        assertEquals("OFF", result.voiceover)
        assertFalse(result.veoPrompt.contains("Use the uploaded product photos as strict visual references"))
    }

    @Test
    fun finalize_stripsLegacyDoctrine_butPreservesCompleteGeneratedDetails() {
        val verbose = """
FORMAT
Vertical 9:16.
Photorealistic commercial TikTok Shop product ad style.
Generate exactly 8.0 seconds total.
Timeline ends at 8.0s.

REFERENCES
${PromptTemplates.MARKETPLACE_RULE}

Photos confirm black tubular frame and red tray.

PRODUCT LOCK
${PromptTemplates.PRODUCT_FIDELITY_CORE}

Preserve black tubular X-braced frame, perforated upper backrest, red circular right-front tray, silver clamps, disc feet.

SETTING
Uncluttered premium studio environment with soft light and shallow depth of field and no clutter anywhere.

SHOT SEQUENCE
0.0–2.0s — HOOK: product visible immediately with strongest verified detail.
2.0–4.0s — IDENTITY: clear full/product-true framing.
4.0–6.0s — FEATURE / DEMO: one hero feature only, physically plausible.
6.0–8.0s — HERO / CTA: desirable hero hold and soft CTA.
Timeline ends at 8.0s. Four blocks only. No extra scenes.

ON-SCREEN TEXT
Compact fold

VOICEOVER
OFF

AUDIO
Subtle background music. Clear dominant voice. Realistic product-action sounds only when mechanism is visible.

CRITICAL
${PromptTemplates.MARKETPLACE_RULE}
Preserve photographed product identity. Exactly 8.0 seconds. Four blocks only. No continuation after 8.0s.

NEGATIVE PROMPT
- no generic replacement product
- no redesign or modernized look
- no changed proportions or silhouette
- no changed colors or materials
- no duplicated product
- no missing confirmed parts
- no invented accessories or controls
- no product morphing
- no wrong left/right placement
- no fake branding or random text
- no marketplace UI or phone interface
- no impossible mechanics
- no malformed hands
- no CGI/cartoon look

TITLE
Fishing Chair

HASHTAGS
#a #b #c #d #TikTokShop
""".trimIndent()

        val result = PromptCleanup.finalize(
            rawPrompt = verbose,
            voiceover = "OFF",
            title = "Fishing Chair",
            hashtags = listOf("#a", "#b", "#c", "#d", "#TikTokShop"),
            voiceLanguage = "OFF",
            marketplace = true,
            tiktokShopMode = true
        )

        assertFalse(result.veoPrompt.contains("PRODUCT DESIGN = LOCKED"))
        assertFalse(result.veoPrompt.contains("Do not reinterpret the product based on category knowledge"))
        assertFalse(result.veoPrompt.contains("Preserve the exact overall silhouette"))
        assertTrue(result.veoPrompt.contains("black tubular X-braced frame"))
        assertTrue(result.veoPrompt.contains("perforated upper backrest"))
        assertTrue(result.veoPrompt.contains("red circular right-front tray"))
        assertTrue(result.veoPrompt.contains("silver clamps"))
        assertTrue(result.veoPrompt.contains("disc feet"))
        assertTrue(result.veoPrompt.contains("0.0–2.0s"))
        assertTrue(result.veoPrompt.contains("Timeline ends at 8.0s. Four blocks only. No extra scenes."))
        val negLines = PromptCleanup.extractSection(result.veoPrompt, "NEGATIVE PROMPT")
            .lineSequence().count { it.trim().startsWith("-") }
        assertEquals(14, negLines)
        assertTrue(result.veoPrompt.contains("- no CGI/cartoon look"))
        assertFalse(result.veoPrompt.contains("…"))
    }

    @Test
    fun prepareStoredPrompt_syncsCardFields_andStripsEssayWithoutCompression() {
        val raw = """
FORMAT
Vertical 9:16. Exactly 8.0 seconds.

REFERENCES
Photos confirm black frame.

PRODUCT LOCK
${PromptTemplates.PRODUCT_FIDELITY_CORE}
Preserve black tubular frame and red tray.

SETTING
Studio

SHOT SEQUENCE
0.0–2.0s — HOOK: tray
2.0–4.0s — IDENTITY: full
4.0–6.0s — FEATURE / DEMO: fold
6.0–8.0s — HERO / CTA: hold

ON-SCREEN TEXT
Fold

VOICEOVER
Закажите.

AUDIO
Music

CRITICAL
Lock

NEGATIVE PROMPT
- no redesign

TITLE
Old

HASHTAGS
#x
""".trimIndent()
        val composed = PromptCleanup.prepareStoredPromptForDisplay(
            rawPrompt = raw,
            voiceover = "Загляни в TikTok Shop.",
            title = "Fishing Chair",
            hashtags = listOf("#a", "#b", "#c", "#d", "#TikTokShop"),
            marketplace = true,
            tiktokShopMode = true
        )
        assertEquals(
            "Загляни в TikTok Shop.",
            PromptCleanup.extractSection(composed, "VOICEOVER").trim()
        )
        assertEquals("Fishing Chair", PromptCleanup.extractSection(composed, "TITLE").trim())
        assertTrue(PromptCleanup.extractSection(composed, "HASHTAGS").contains("#TikTokShop"))
        assertFalse(composed.contains("PRODUCT DESIGN = LOCKED"))
        assertFalse(composed.contains("Preserve the exact overall silhouette"))
        assertTrue(composed.contains("black tubular frame and red tray"))
    }

    @Test
    fun stripLegacySections_removesDoctrineBlocks_keepsOnlyRequiredTwelve() {
        val dirty = """
FORMAT
Vertical 9:16. Exactly 8.0s.

VISUAL FIDELITY
Use the uploaded product photos as strict visual references.
PRODUCT DESIGN = LOCKED.

REFERENCES
Photos confirm black tubular frame.

PRODUCT FIDELITY CORE RULE
Long doctrine essay that must not ship.

PRODUCT LOCK
Preserve black tubular frame and red tray.

SETTING
Studio

HOOK
Standalone hook essay that is not a final section.

SHOT SEQUENCE
0.0–2.0s — HOOK: tray
2.0–4.0s — IDENTITY: full
4.0–6.0s — FEATURE / DEMO: fold
6.0–8.0s — HERO / CTA: hold

ON-SCREEN TEXT
Fold

VOICEOVER
OFF

AUDIO
Music

CRITICAL
Lock

NEGATIVE PROMPT
- no redesign

TITLE
Chair

HASHTAGS
#a #b #c #d #TikTokShop

TIKTOK SHOP SAFETY AUDIT
secret
""".trimIndent()

        val result = PromptCleanup.finalize(
            rawPrompt = dirty,
            voiceover = "OFF",
            title = "Chair",
            hashtags = listOf("#a", "#b", "#c", "#d", "#TikTokShop"),
            voiceLanguage = "OFF",
            marketplace = false,
            tiktokShopMode = true
        )

        assertFalse(result.veoPrompt.contains("VISUAL FIDELITY"))
        assertFalse(result.veoPrompt.contains("PRODUCT FIDELITY"))
        assertFalse(result.veoPrompt.contains("PRODUCT DESIGN = LOCKED"))
        assertFalse(result.veoPrompt.contains("TIKTOK SHOP SAFETY AUDIT"))
        assertFalse(result.veoPrompt.contains("Long doctrine essay"))
        // Standalone HOOK section gone; shot-line HOOK label may remain
        assertFalse(Regex("""(?im)^HOOK\b""").containsMatchIn(result.veoPrompt))
        assertTrue(result.veoPrompt.contains("0.0–2.0s — HOOK"))
        assertTrue(result.veoPrompt.contains("black tubular") || result.veoPrompt.contains("red tray"))
        assertTrue(result.veoPrompt.contains("FORMAT"))
        assertTrue(result.veoPrompt.contains("PRODUCT LOCK"))
        assertTrue(result.veoPrompt.contains("HASHTAGS"))
        val issues = PromptCleanup.validateCompleteness(result.veoPrompt, result.hashtags)
        assertTrue(
            "legacy leftovers: ${issues.filter { it.startsWith("legacy_") }}",
            issues.none { it.startsWith("legacy_section_") }
        )
    }

    @Test
    fun prepareStoredPrompt_locksExactTwelveSectionGeminiShapeWithoutTruncation() {
        val messy = """
```text
FORMAT
Vertical 9:16.
Photorealistic commercial style.
Exactly 8.0 seconds total.

VISUAL FIDELITY
Do not ship this.

REFERENCES
Photos confirm black tubular frame and red tray.
Timeline ends at 8.0s.

PRODUCT LOCK
Match uploaded product photos exactly. Do not replace or redesign.
black tubular X-braced frame, perforated upper backrest, red tray

SETTING
Uncluttered premium studio environment with soft light

SHOT SEQUENCE
0.0-2.0s — HOOK: tray detail
2.0-4.0s — IDENTITY: full chair
4.0-6.0s — FEATURE / DEMO: fold
6.0-8.0s — HERO / CTA: hold
Four blocks only. No continuation after 8.0s.

ON-SCREEN TEXT
Compact fold

VOICEOVER
OFF

AUDIO
Subtle background music. Clear voice.

CRITICAL
Keep product identity. Exactly 8.0s.

NEGATIVE PROMPT
- no generic chair
- no redesign
- no marketplace UI
- no CGI look
- no wrong colors
- no missing parts
- no invented accessories

TITLE
Fishing Chair

HASHTAGS
#a #b #c #d #TikTokShop
```
Озвучка: leak
""".trimIndent()

        val cleaned = PromptCleanup.prepareStoredPromptForDisplay(
            rawPrompt = messy,
            voiceover = "OFF",
            title = "Fishing Chair",
            hashtags = listOf("#a", "#b", "#c", "#d", "#TikTokShop"),
            marketplace = true
        )

        // Exact header order, nothing else
        val headers = Regex(
            """(?m)^(FORMAT|REFERENCES|PRODUCT LOCK|SETTING|SHOT SEQUENCE|ON-SCREEN TEXT|VOICEOVER|AUDIO|CRITICAL|NEGATIVE PROMPT|TITLE|HASHTAGS)\s*$"""
        ).findAll(cleaned).map { it.groupValues[1] }.toList()
        assertEquals(
            listOf(
                "FORMAT", "REFERENCES", "PRODUCT LOCK", "SETTING", "SHOT SEQUENCE",
                "ON-SCREEN TEXT", "VOICEOVER", "AUDIO", "CRITICAL", "NEGATIVE PROMPT",
                "TITLE", "HASHTAGS"
            ),
            headers
        )
        // No leftover standalone ALL-CAPS doctrine headers
        assertFalse(Regex("""(?im)^VISUAL FIDELITY\s*$""").containsMatchIn(cleaned))
        assertFalse(Regex("""(?im)^HOOK\s*$""").containsMatchIn(cleaned))
        assertEquals(12, headers.size)
        assertFalse(cleaned.contains("VISUAL FIDELITY"))
        assertFalse(cleaned.contains("```"))
        assertFalse(cleaned.contains("Озвучка"))
        assertTrue(cleaned.contains("Timeline ends at 8.0s."))
        assertTrue(cleaned.contains("Four blocks only. No continuation after 8.0s."))
        assertTrue(cleaned.contains("0.0–2.0s"))
        assertTrue(cleaned.trimEnd().endsWith("#TikTokShop") || cleaned.contains("#TikTokShop"))
        assertTrue(cleaned.endsWith("\n"))
        assertFalse(cleaned.endsWith("\n\n"))
        val issues = PromptCleanup.validateCompleteness(
            cleaned,
            listOf("#a", "#b", "#c", "#d", "#TikTokShop")
        )
        assertTrue("issues=$issues", issues.none { it.startsWith("legacy_") })
        assertTrue(issues.none { it == "content_after_hashtags" })
        assertTrue(issues.none { it == "section_order_wrong" })
        println("FINAL_CLEANUP_LENGTH=${cleaned.length}")
        println(cleaned)
    }

    @Test
    fun cleanOnScreenText_stripsProductionInstructions_keepsAllRealCopy() {
        assertEquals("None.", PromptCleanup.cleanOnScreenText("Max 2–3 short overlays. No price or fake urgency."))
        assertEquals("None.", PromptCleanup.cleanOnScreenText("Do not repeat the whole voiceover."))
        assertEquals("None.", PromptCleanup.cleanOnScreenText("FORMAT"))
        assertEquals(
            "Compact fold",
            PromptCleanup.cleanOnScreenText(
                """
                Max 2–3 concise product-specific overlays.
                Compact fold
                No price or fake urgency.
                """.trimIndent()
            )
        )
        assertEquals(
            "Folds flat · Soft hold",
            PromptCleanup.cleanOnScreenText("Text: Folds flat\nOverlay: Soft hold")
        )
        assertEquals(
            "One · Two · Three · Four",
            PromptCleanup.cleanOnScreenText("One\nTwo\nThree\nFour")
        )
    }

    @Test
    fun finalCleanup_replacesInstructionalOnScreenTextWithNone() {
        val raw = """
FORMAT
Vertical 9:16. Exactly 8.0s.

REFERENCES
Photos confirm black frame.

PRODUCT LOCK
Preserve black frame.

SETTING
Studio.

SHOT SEQUENCE
0.0–2.0s — HOOK: detail
2.0–4.0s — IDENTITY: full
4.0–6.0s — FEATURE / DEMO: both hands open tray
6.0–8.0s — HERO / CTA: hold

ON-SCREEN TEXT
Max 2–3 short overlays. No price or fake urgency.

VOICEOVER
OFF

AUDIO
Soft music.

CRITICAL
Keep identity.

NEGATIVE PROMPT
- no redesign

TITLE
Tray Chair

HASHTAGS
#a #b #c #d #TikTokShop
""".trimIndent()
        val cleaned = PromptCleanup.prepareStoredPromptForDisplay(
            rawPrompt = raw,
            voiceover = "OFF",
            title = "Tray Chair",
            hashtags = listOf("#a", "#b", "#c", "#d", "#TikTokShop"),
            marketplace = false
        )
        val onScreen = cleaned.substringAfter("ON-SCREEN TEXT").substringBefore("VOICEOVER").trim()
        assertEquals("None.", onScreen)
        val feature = cleaned.lineSequence().first { it.contains("FEATURE", ignoreCase = true) }
        assertTrue("expected one hand, got: $feature", feature.contains("one hand", ignoreCase = true))
        assertFalse(feature.contains("both hands", ignoreCase = true))
        assertFalse(cleaned.contains("Max 2"))
        assertFalse(cleaned.contains("No price"))
    }

    @Test
    fun finalize_preservesPanPromptPastLegacy1100CharacterBoundary() {
        val productDetails = (1..12).joinToString(", ") {
            "confirmed pan identity detail $it with exact photographed geometry"
        }
        val negativeRules = (1..12).joinToString("\n") {
            "- preserve generated product-specific restriction $it through the final line"
        }
        val finalShotMarker =
            "the wooden crossbar lid, hanging ring, ferrule, rivets, and photographed bowl profile remain fully visible"
        val raw = """
FORMAT
Vertical 9:16. Photorealistic commercial TikTok Shop product ad. Exactly 8.0 seconds total.

REFERENCES
All uploaded product, detail, alternate-angle, handle, ferrule, rivet, lid, and bowl photos jointly confirm the same physical pan and every photographed component.

PRODUCT LOCK
$productDetails

SETTING
Warm uncluttered kitchen counter with directional window light, realistic contact shadows, and enough negative space to keep the complete pan silhouette readable.

SHOT SEQUENCE
0.0–2.0s — HOOK: macro glide from the hanging ring along the dark wooden handle to the gold-tone ferrule without hiding any photographed hardware.
2.0–4.0s — IDENTITY: pull back to the complete deep black bowl, high curved sides, riveted shank, long handle, and wooden crossbar lid in their exact proportions.
4.0–6.0s — FEATURE / DEMO: one hand lifts and returns the wooden crossbar lid while the pan remains rigid and every component stays on the photographed side.
6.0–8.0s — HERO / CTA: settle into a complete three-quarter product hold where $finalShotMarker.

ON-SCREEN TEXT
Deep form
Wooden detail
Built to stand out

VOICEOVER
OFF

AUDIO
Quiet room tone, soft wood contact from the lid, and restrained music without an invented mechanical click.

CRITICAL
Maintain exact product continuity in every frame; do not simplify the lid, handle assembly, ferrule, rivets, ring, or bowl profile.

NEGATIVE PROMPT
$negativeRules

TITLE
Deep Black Pan

HASHTAGS
#pan #kitchen #cookware #home #TikTokShop
""".trimIndent()

        val result = PromptCleanup.finalize(
            rawPrompt = raw,
            voiceover = "OFF",
            title = "Deep Black Pan",
            hashtags = listOf("#pan", "#kitchen", "#cookware", "#home", "#TikTokShop"),
            voiceLanguage = "OFF",
            marketplace = false
        )

        assertTrue("expected a full prompt beyond the removed legacy budget", result.veoPrompt.length > 1100)
        assertTrue(result.veoPrompt.contains("confirmed pan identity detail 12"))
        assertTrue(result.veoPrompt.contains(finalShotMarker))
        assertTrue(result.veoPrompt.contains("product-specific restriction 12 through the final line"))
        assertTrue(result.veoPrompt.contains("Built to stand out"))
        assertFalse(result.veoPrompt.contains("…"))
    }

    @Test
    fun finalize_completesTruncatedPromptMissingTail() {
        val truncated = """
FORMAT
Vertical 9:16. Exactly 8.0s.

REFERENCES
Photos confirm black frame.

PRODUCT LOCK
Preserve black frame.

SETTING
Studio.

SHOT SEQUENCE
0.0–2.0s — HOOK: detail
2.0–4.0s — IDENTITY: full
4.0–6.0s — FEATURE / DEMO: fold
6.0–8.0s — HERO / CTA: hold

ON-SCREEN TEXT
None.

VOICEOVER
OFF

AUDIO
Soft music.

CRITICAL
Keep identity.
""".trimIndent()
        // Missing NEGATIVE PROMPT, TITLE, HASHTAGS entirely
        val result = PromptCleanup.finalize(
            rawPrompt = truncated,
            voiceover = "OFF",
            title = "Black Frame Chair",
            hashtags = emptyList(),
            voiceLanguage = "OFF",
            marketplace = false,
            tiktokShopMode = true
        )
        val issues = PromptCleanup.validateCompleteness(result.veoPrompt, result.hashtags)
        assertTrue("issues=$issues", issues.none { it.startsWith("missing_") })
        assertTrue(issues.none { it == "section_order_wrong" })
        assertTrue(issues.none { it == "incomplete_timeline" })
        assertEquals(5, result.hashtags.size)
        assertTrue(result.veoPrompt.contains("NEGATIVE PROMPT"))
        assertTrue(result.veoPrompt.contains("TITLE"))
        assertTrue(result.veoPrompt.contains("HASHTAGS"))
        assertTrue(result.veoPrompt.trimEnd().contains("#"))
    }

    @Test
    fun finalize_completesNearlyEmptyPrompt() {
        val result = PromptCleanup.finalize(
            rawPrompt = "FORMAT\n9:16",
            voiceover = "Kompakt falten und mitnehmen.",
            title = "Fold Chair",
            hashtags = listOf("#a"),
            voiceLanguage = "DE",
            marketplace = true,
            tiktokShopMode = true
        )
        val issues = PromptCleanup.validateCompleteness(result.veoPrompt, result.hashtags)
        assertTrue(
            "issues=$issues prompt=\n${result.veoPrompt}",
            issues.none {
                it.startsWith("missing_") ||
                    it == "section_order_wrong" ||
                    it == "incomplete_timeline" ||
                    it.startsWith("hashtag_count")
            }
        )
        assertEquals(5, result.hashtags.size)
    }

    @Test
    fun salvage_partialPromptWithoutHashtags_thenFinalizeCompletes() {
        val raw = """{"veoPrompt": "FORMAT\nVertical 9:16.\n\nREFERENCES\nphotos\n\nPRODUCT LOCK\nlock\n\nSETTING\nstudio\n\nSHOT SEQUENCE\n0.0–2.0s — HOOK\n2.0–4.0s — IDENTITY\n4.0–6.0s — FEATURE\n6.0–8.0s — HERO\n\nON-SCREEN TEXT\nNone\n\nVOICEOVER\nOFF\n\nAUDIO\nmusic\n\nCRITICAL\nlock\n\nNEGATIVE PROMPT\n- no redesign"}"""
        val salvaged = JsonExtractor.salvageVeoPrompt(raw)
        assertTrue("salvage should recover truncated FORMAT…NEGATIVE body", !salvaged.isNullOrBlank())
        val bundle = FinalPromptJson.decode(raw)
        val prompt = bundle.veoPrompt.ifBlank { salvaged.orEmpty() }
        assertTrue(prompt.isNotBlank())
        val result = PromptCleanup.finalize(
            rawPrompt = prompt,
            voiceover = "OFF",
            title = "Product",
            hashtags = emptyList(),
            voiceLanguage = "OFF",
            marketplace = false,
            tiktokShopMode = true
        )
        val issues = PromptCleanup.validateCompleteness(result.veoPrompt, result.hashtags)
        assertTrue("issues=$issues", issues.none { it.startsWith("missing_") })
        assertEquals(5, result.hashtags.size)
    }
}
