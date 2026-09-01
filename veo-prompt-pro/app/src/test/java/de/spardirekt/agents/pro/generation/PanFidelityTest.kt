package de.spardirekt.agents.pro.generation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PanFidelityTest {

    @Test
    fun canonicalRegressionTitleMatches() {
        assertTrue(PanFidelity.matches("TITLE\nDeep Black Pan"))
    }

    @Test
    fun exactSignatureInProductEvidenceMatchesWithNeutralTitle() {
        val evidence = """
            {
              "productCategory":"pan",
              "productIdentity":"deep black cooking pan",
              "visualSignature":[
                "high curved sides",
                "long dark wooden handle",
                "gold-tone ferrule",
                "wooden crossbar lid"
              ]
            }
        """.trimIndent()

        assertTrue(PanFidelity.matches("TITLE\nKitchen Essential", evidence))
    }

    @Test
    fun genericPanDoesNotInheritTheRegressionProductsWoodParts() {
        assertFalse(
            PanFidelity.matches(
                "TITLE\nCast Iron Frying Pan\nPRODUCT LOCK\nblack cast iron, short metal handle"
            )
        )
        assertFalse(PanFidelity.matches("TITLE\nNon-stick Pan"))
    }

    @Test
    fun unrelatedProductsNeverMatch() {
        assertFalse(PanFidelity.matches("Deep black chair with wooden armrests"))
        assertFalse(PanFidelity.matches("Black cream jar with a wooden lid"))
    }

    @Test
    fun finalizeRestoresEveryPanIdentityDetailWhenModelReturnsWeakLock() {
        val result = PromptCleanup.finalize(
            rawPrompt = completePrompt(
                title = "Deep Black Pan",
                productLock = "Keep the pan black.\nPreserve the visible hand-carved lid grain and etched underside mark."
            ),
            voiceover = "OFF",
            title = "Deep Black Pan",
            hashtags = listOf("#pan", "#kitchen", "#cookware", "#home", "#TikTokShop"),
            voiceLanguage = "OFF",
            marketplace = false
        )

        assertPanSignature(result.veoPrompt)
        assertTrue(result.veoPrompt.contains("hand-carved lid grain"))
        assertTrue(result.veoPrompt.contains("etched underside mark"))
        assertTrue(result.veoPrompt.contains("no shallow pan, saucepan"))
        assertTrue(result.veoPrompt.contains("no missing wooden crossbar lid"))
        assertFalse(result.veoPrompt.contains("…"))
    }

    @Test
    fun productModelEvidenceCanRestorePanLockWhenFinalTitleIsGeneric() {
        val productModel = """
            {"productCategory":"pan","productIdentity":"deep black pan",
             "visualSignature":["high curved sides","dark wooden handle","wooden crossbar lid"]}
        """.trimIndent()

        val result = PromptCleanup.finalize(
            rawPrompt = completePrompt(
                title = "Kitchen Essential",
                productLock = "Keep product identity."
            ),
            voiceover = "OFF",
            title = "Kitchen Essential",
            hashtags = emptyList(),
            voiceLanguage = "OFF",
            marketplace = false,
            productEvidence = productModel
        )

        assertPanSignature(result.veoPrompt)
    }

    @Test
    fun genericPanPromptIsLeftAlone() {
        val result = PromptCleanup.finalize(
            rawPrompt = completePrompt(
                title = "Cast Iron Frying Pan",
                productLock = "matte cast iron, short metal handle, shallow sides"
            ),
            voiceover = "OFF",
            title = "Cast Iron Frying Pan",
            hashtags = emptyList(),
            voiceLanguage = "OFF",
            marketplace = false
        )

        assertTrue(result.veoPrompt.contains("short metal handle"))
        assertFalse(result.veoPrompt.contains("wooden crossbar lid"))
        assertFalse(result.veoPrompt.contains("gold-tone ferrule"))
    }

    private fun assertPanSignature(prompt: String) {
        listOf(
            "deep black bowl",
            "high curved sides",
            "long dark wooden handle",
            "hanging ring",
            "gold-tone ferrule",
            "riveted shank",
            "wooden crossbar lid"
        ).forEach { detail ->
            assertTrue("missing pan detail: $detail\n$prompt", prompt.contains(detail))
        }
    }

    private fun completePrompt(title: String, productLock: String) = """
        FORMAT
        Vertical 9:16. Exactly 8.0 seconds.

        REFERENCES
        Uploaded product photos are the visual evidence.

        PRODUCT LOCK
        $productLock

        SETTING
        Premium studio.

        SHOT SEQUENCE
        0.0–2.0s — HOOK: product reveal
        2.0–4.0s — IDENTITY: full product
        4.0–6.0s — FEATURE / DEMO: one hand demonstrates
        6.0–8.0s — HERO / CTA: product hero hold

        ON-SCREEN TEXT
        None.

        VOICEOVER
        OFF

        AUDIO
        Subtle music.

        CRITICAL
        Keep product identity.

        NEGATIVE PROMPT
        - no generic replacement product
        - no product morphing

        TITLE
        $title

        HASHTAGS
        #one #two #three #four #TikTokShop
    """.trimIndent()
}
