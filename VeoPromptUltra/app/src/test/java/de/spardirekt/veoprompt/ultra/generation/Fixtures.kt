package de.spardirekt.veoprompt.ultra.generation

import de.spardirekt.veoprompt.ultra.model.ProductModel

object Fixtures {

    fun validVeoPrompt(model: ProductModel = ProductModel()): String {
        val details = model.visualSignature.ifEmpty {
            listOf("deep rounded bowl", "high sides", "wooden handle", "ferrule", "rivets", "hanging ring", "wooden lid")
        }.joinToString(", ")
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
Preserve $details.
Do not reinterpret the product from category knowledge.
CORE PRINCIPLE: CREATIVE PRESENTATION = FLEXIBLE. PRODUCT DESIGN = LOCKED.

SETTING
Product-appropriate premium kitchen. Simple background. Realistic lighting. Product dominant.

SHOT SEQUENCE
0.0–2.0s — HOOK
Product visible immediately with strongest verified visual detail: $details.
2.0–4.0s — IDENTITY
Clear framing of the same exact physical product.
4.0–6.0s — FEATURE / DEMO
Exactly one verified hero feature or physically plausible action with one hand.
6.0–8.0s — HERO / CTA
Stable desirable hero shot of the same unchanged product. End exactly at 8.0s.

ON-SCREEN TEXT
Holzdeckel
Tiefe Form

VOICEOVER
Tiefer Topf, fester Holzdeckel, einfach kochen.

AUDIO
Subtle background music. Clear voice. Soft wood-on-metal lid contact only.

CRITICAL
The same single physical product must remain visually consistent throughout all four shots.
Uploaded physical product photos override category knowledge.
If visual accuracy conflicts with creativity, preserve product accuracy.

NEGATIVE PROMPT
- no generic replacement pan or wok
- no redesigned silhouette or shallower bowl
- no missing wooden lid, ferrule, rivets or hanging ring
- no changed handle geometry
- no invented non-stick claims
- no product morphing between shots
- no duplicate product
- no marketplace UI
- no malformed hands
""".trimIndent()
    }
}
