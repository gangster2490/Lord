package de.spardirekt.ugcagent.v3.prompt

import org.json.JSONObject

/**
 * Internal stage that runs before final Veo prompt assembly.
 * Answers why someone would want the product, then picks one SAFE desire.
 * Candidate concepts stay internal.
 */
object PurchaseAppealEngine {
    data class ActionRisk(
        val morph: Double,
        val hiddenGeometry: Double,
        val mechanism: Double,
        val occlusion: Double,
        val identity: Double,
    ) {
        val motionSafety: Double
            get() = (morph + hiddenGeometry + mechanism + occlusion + identity) / 5.0
        val highRisk: Boolean get() = motionSafety < 0.62 || morph < 0.5 || mechanism < 0.5
    }

    data class ConceptScore(
        val purchaseAppeal: Double,
        val naturalness: Double,
        val claimSafety: Double,
        val visualClarity: Double,
        val motionSafety: Double,
        val identity: Double,
        val relevance: Double,
    ) {
        val total: Double
            get() = 0.22 * purchaseAppeal + 0.18 * naturalness + 0.14 * claimSafety +
                0.14 * visualClarity + 0.12 * motionSafety + 0.12 * identity + 0.08 * relevance
        val safe: Boolean get() = motionSafety >= 0.75 && identity >= 0.85 && claimSafety >= 0.85
    }

    data class Concept(
        val kind: String,
        val idea: CreativeStrategyEngine.SellingIdea,
        val hookType: CreativeStrategyEngine.HookType,
        val setting: String,
        val action: String,
        val human: String,
        val pitch: String,
        val scores: ConceptScore,
    ) {
        val score: Double get() = scores.total
    }

    data class Brief(
        val boughtFor: String,
        val desireKind: String,
        val viewerFeel: String,
        val sellingIdea: String,
        val setting: String,
        val action: String,
        val hookType: CreativeStrategyEngine.HookType,
        val plan: CreativeStrategyEngine.Plan,
        val concepts: List<Concept>,
        val winnerKind: String,
    ) {
        fun toPublicJson(): JSONObject = JSONObject()
            .put("bought_for", boughtFor)
            .put("desire", desireKind)
            .put("viewer_should_feel", viewerFeel)
            .put("selling_idea", sellingIdea)
            .put("hook_type", hookType.name)
            .put("concept_kind", winnerKind)
            .put("primary_motivation", plan.primary.name)
            .put("secondary_motivation", plan.secondary?.name ?: "")
    }

    fun evaluate(analysis: JSONObject?, fingerprint: JSONObject? = null): Brief {
        val plan = CreativeStrategyEngine.plan(analysis, fingerprint)
        val concepts = conceptsFor(plan, analysis, fingerprint)
        val winner = select(concepts)
        val refined = plan.copy(
            idea = winner.idea,
            hookType = winner.hookType,
            setting = winner.setting,
            human = winner.human,
            action = winner.action,
            opening = openingFor(winner),
            pitch = winner.pitch,
            conceptKind = winner.kind,
            boughtFor = plan.boughtFor,
            viewerFeel = plan.viewerFeel,
            desire = desireFor(winner.idea),
            formatTone = formatTone(plan, winner),
        )
        return Brief(
            boughtFor = refined.boughtFor,
            desireKind = desireFor(winner.idea),
            viewerFeel = refined.viewerFeel,
            sellingIdea = winner.pitch,
            setting = winner.setting,
            action = winner.action,
            hookType = winner.hookType,
            plan = refined,
            concepts = concepts,
            winnerKind = winner.kind,
        )
    }

    fun apply(plan: CreativeStrategyEngine.Plan, analysis: JSONObject?, fingerprint: JSONObject?): CreativeStrategyEngine.Plan {
        val winner = select(conceptsFor(plan, analysis, fingerprint))
        return plan.copy(
            idea = winner.idea,
            hookType = winner.hookType,
            setting = winner.setting,
            human = winner.human,
            action = winner.action,
            opening = openingFor(winner),
            pitch = winner.pitch,
            conceptKind = winner.kind,
            desire = desireFor(winner.idea),
            formatTone = formatTone(plan, winner),
        )
    }

    fun scoreAction(action: String): ActionRisk {
        val lower = action.replace(Regex("(?i)do not[^.\\n]*"), " ").lowercase()
        val riskyMotion = listOf("fold", "unfold", "180", "recline", "leg height", "height adjustment", "disassemble", "unscrew")
        val mechanismWords = listOf("mechanism", "rotate the backrest", "animate", "adjust the legs", "changing mechanism")
        val occlusionWords = listOf("both hands cover", "hide the product", "fully occlude")
        val morph = if (riskyMotion.any { lower.contains(it) }) 0.18 else 0.96
        val hidden = if (listOf("hidden", "unseen", "invent", "reconstruct").any { lower.contains(it) }) 0.2 else 0.95
        val mechanism = if (mechanismWords.any { lower.contains(it) }) 0.22 else 0.94
        val occlusion = if (occlusionWords.any { lower.contains(it) }) 0.4 else 0.93
        val identity = if (morph < 0.5 || hidden < 0.5) 0.45 else 0.95
        return ActionRisk(morph, hidden, mechanism, occlusion, identity)
    }

    fun desireFor(idea: CreativeStrategyEngine.SellingIdea): String = when (idea) {
        CreativeStrategyEngine.SellingIdea.COMFORT -> "comfort"
        CreativeStrategyEngine.SellingIdea.CONVENIENCE -> "convenience"
        CreativeStrategyEngine.SellingIdea.CLEANLINESS -> "cleanliness"
        CreativeStrategyEngine.SellingIdea.PORTABILITY -> "portability"
        CreativeStrategyEngine.SellingIdea.SIMPLE_USE -> "ease of use"
        CreativeStrategyEngine.SellingIdea.VISUAL_APPEAL -> "visual appeal"
        CreativeStrategyEngine.SellingIdea.ORGANIZATION -> "organization"
        CreativeStrategyEngine.SellingIdea.OUTDOOR_PRACTICALITY -> "outdoor practicality"
        CreativeStrategyEngine.SellingIdea.HOME_FEELING -> "coziness"
    }

    fun viewerFeel(primary: CreativeStrategyEngine.Motivation): String = when (primary) {
        CreativeStrategyEngine.Motivation.PROBLEM_SOLVER -> "This solves an annoying problem."
        CreativeStrategyEngine.Motivation.COMFORT -> "This makes the experience easier / more comfortable."
        CreativeStrategyEngine.Motivation.HOME_COZY -> "This feels pleasant, useful and nice to have at home."
        CreativeStrategyEngine.Motivation.DEMONSTRABLE_FUNCTION -> "I immediately understand what it does."
        CreativeStrategyEngine.Motivation.PORTABLE -> "This is easy to carry / store / keep around."
        CreativeStrategyEngine.Motivation.VISUAL -> "This looks really good."
        CreativeStrategyEngine.Motivation.OUTDOOR_HOBBY -> "This improves the experience outdoors."
    }

    fun boughtFor(primary: CreativeStrategyEngine.Motivation): String = when (primary) {
        CreativeStrategyEngine.Motivation.PROBLEM_SOLVER -> "relief from an everyday mess or hassle"
        CreativeStrategyEngine.Motivation.COMFORT -> "more comfortable everyday use"
        CreativeStrategyEngine.Motivation.HOME_COZY -> "a pleasant useful home object"
        CreativeStrategyEngine.Motivation.DEMONSTRABLE_FUNCTION -> "one clear useful function"
        CreativeStrategyEngine.Motivation.PORTABLE -> "easy carrying or compact keeping"
        CreativeStrategyEngine.Motivation.VISUAL -> "how it looks in real life"
        CreativeStrategyEngine.Motivation.OUTDOOR_HOBBY -> "more comfortable, practical outdoor use"
    }

    private fun conceptsFor(
        plan: CreativeStrategyEngine.Plan,
        analysis: JSONObject?,
        fingerprint: JSONObject?,
    ): List<Concept> {
        val fishing = CreativeStrategyEngine.looksLikeFishingChair(fingerprint, analysis)
        val microwave = ProductIdentity.looksLikeMicrowaveCover(fingerprint)
        val pan = ProductIdentity.looksLikeCookwarePan(fingerprint, analysis)
        val setting = plan.setting
        val human = plan.human
        return when {
            fishing -> fishingConcepts(setting, human)
            microwave -> microwaveConcepts(setting, human)
            pan -> panConcepts(setting, human)
            else -> genericConcepts(plan, setting, human)
        }.map { concept ->
            val actionRisk = scoreAction(concept.action)
            val motion = minOf(concept.scores.motionSafety, actionRisk.motionSafety)
            val identity = minOf(concept.scores.identity, actionRisk.identity)
            if (actionRisk.highRisk) {
                concept.copy(
                    action = CreativeStrategyEngine.safeAction(fingerprint, analysis),
                    scores = concept.scores.copy(motionSafety = motion * 0.4, identity = identity * 0.5, purchaseAppeal = concept.scores.purchaseAppeal * 0.7),
                )
            } else {
                concept.copy(scores = concept.scores.copy(motionSafety = motion, identity = identity))
            }
        }
    }

    private fun fishingConcepts(setting: String, human: String): List<Concept> {
        val seated = "The person is already seated in the referenced fishing chair. Relaxed posture. One LOW-RISK moment: a hand rests on the arm. Do not fold, unfold, rotate a backrest, change leg height or reconstruct hidden geometry. The chair stays exact and stable."
        val tray = "The person is already seated in the referenced fishing chair. Relaxed posture. One LOW-RISK moment: a hand uses a clearly visible bait tray or rests on the arm. Do not fold, unfold, rotate a backrest, change leg height or reconstruct hidden geometry. The chair stays exact and stable."
        val natural = "The person is already seated in the referenced fishing chair at a real fishing spot. Relaxed posture. A brief natural hand rest on the arm or tray. Do not fold, unfold, rotate a backrest, change leg height or reconstruct hidden geometry. The chair stays exact and stable."
        return listOf(
            Concept(
                "safest",
                CreativeStrategyEngine.SellingIdea.COMFORT,
                CreativeStrategyEngine.HookType.OUTDOOR,
                setting,
                seated,
                human,
                "comfortable real fishing sit, simplest motion",
                ConceptScore(0.78, 0.84, 0.96, 0.86, 0.98, 0.98, 0.94),
            ),
            Concept(
                "purchase",
                CreativeStrategyEngine.SellingIdea.CONVENIENCE,
                CreativeStrategyEngine.HookType.CONVENIENCE,
                setting,
                tray,
                human,
                "needed bits stay in reach while fishing",
                ConceptScore(0.95, 0.86, 0.94, 0.9, 0.9, 0.94, 0.92),
            ),
            Concept(
                "natural",
                CreativeStrategyEngine.SellingIdea.COMFORT,
                CreativeStrategyEngine.HookType.OUTDOOR,
                setting,
                natural,
                human,
                "comfortable real fishing use, not a technical demo",
                ConceptScore(0.91, 0.97, 0.96, 0.92, 0.94, 0.96, 0.96),
            ),
        )
    }

    private fun microwaveConcepts(setting: String, human: String): List<Concept> {
        val action = ActionIdentity.MICROWAVE_SAFE_ACTION
        return listOf(
            Concept("safest", CreativeStrategyEngine.SellingIdea.SIMPLE_USE, CreativeStrategyEngine.HookType.CONVENIENCE, setting, action, human, "cover already in place, light handle touch", ConceptScore(0.8, 0.86, 0.95, 0.88, 0.96, 0.98, 0.9)),
            Concept("purchase", CreativeStrategyEngine.SellingIdea.CLEANLINESS, CreativeStrategyEngine.HookType.PROBLEM, setting, action, human, "pain of a messy microwave, simple relief", ConceptScore(0.96, 0.9, 0.93, 0.9, 0.92, 0.96, 0.96)),
            Concept("natural", CreativeStrategyEngine.SellingIdea.CLEANLINESS, CreativeStrategyEngine.HookType.PROBLEM, setting, action, human, "everyday kitchen cover moment", ConceptScore(0.9, 0.95, 0.94, 0.88, 0.94, 0.96, 0.93)),
        )
    }

    private fun panConcepts(setting: String, human: String): List<Concept> {
        val touch = "One hand casually touches the wooden handle of the referenced pan. The lid stays static. Do not fold, open, rotate, pull, detach or reconstruct hidden geometry."
        val glance = "The referenced pan stays in place. One quiet natural glance. The lid stays static. Do not fold, open, rotate, pull, detach or reconstruct hidden geometry."
        return listOf(
            Concept("safest", CreativeStrategyEngine.SellingIdea.HOME_FEELING, CreativeStrategyEngine.HookType.HOME, setting, glance, human, "quiet home presence, lid static", ConceptScore(0.8, 0.88, 0.97, 0.86, 0.98, 0.98, 0.9)),
            Concept("purchase", CreativeStrategyEngine.SellingIdea.HOME_FEELING, CreativeStrategyEngine.HookType.HOME, setting, touch, human, "pleasant useful home cookware", ConceptScore(0.92, 0.9, 0.96, 0.9, 0.94, 0.96, 0.94)),
            Concept("natural", CreativeStrategyEngine.SellingIdea.HOME_FEELING, CreativeStrategyEngine.HookType.HOME, setting, touch, human, "a calm real kitchen moment", ConceptScore(0.88, 0.97, 0.96, 0.88, 0.95, 0.96, 0.93)),
        )
    }

    private fun genericConcepts(plan: CreativeStrategyEngine.Plan, setting: String, human: String): List<Concept> {
        val simple = ActionIdentity.DEFAULT_SAFE_ACTION
        val glance = "The referenced product stays in place while the person shares one quiet natural glance. Do not fold, open, rotate, pull, detach or reconstruct hidden geometry."
        val rest = "One hand rests on the referenced product as it already stands ready. Do not fold, unfold or reconstruct hidden geometry."
        val salesIdea = plan.idea
        val salesHook = plan.hookType
        return listOf(
            Concept("safest", salesIdea, salesHook, setting, glance, human, plan.pitch, ConceptScore(0.76, 0.84, 0.96, 0.84, 0.98, 0.98, 0.88)),
            Concept("purchase", salesIdea, salesHook, setting, rest, human, plan.pitch, ConceptScore(0.93, 0.86, 0.94, 0.9, 0.9, 0.94, 0.92)),
            Concept("natural", salesIdea, salesHook, setting, simple, human, plan.pitch, ConceptScore(0.88, 0.96, 0.95, 0.88, 0.93, 0.95, 0.9)),
        )
    }

    private fun select(concepts: List<Concept>): Concept {
        val safe = concepts.filter { it.scores.safe }
        val pool = if (safe.isNotEmpty()) safe else concepts.sortedByDescending { it.scores.motionSafety * it.scores.identity }
        return pool.maxByOrNull { it.score } ?: concepts.first()
    }

    private fun openingFor(concept: Concept): String = when (concept.hookType) {
        CreativeStrategyEngine.HookType.PROBLEM -> "hook and immediate problem context so the relief idea is already clear"
        CreativeStrategyEngine.HookType.COMFORT -> "hook and immediate comfort context so the viewer already feels why it helps"
        CreativeStrategyEngine.HookType.CONVENIENCE -> "hook and immediate convenience context so usefulness is already clear"
        CreativeStrategyEngine.HookType.VISUAL -> "hook and immediate visual presence so desirability is already clear"
        CreativeStrategyEngine.HookType.CURIOSITY -> "hook and immediate useful detail so the idea is already clear"
        CreativeStrategyEngine.HookType.HOME -> "hook and immediate home context so the viewer already grasps the everyday appeal"
        CreativeStrategyEngine.HookType.OUTDOOR -> "hook and immediate outdoor context so the viewer already feels why this helps outside"
    }

    private fun formatTone(plan: CreativeStrategyEngine.Plan, winner: Concept): String {
        val desire = desireFor(winner.idea)
        val base = plan.formatTone.substringBefore(" One desire:")
        return "$base One desire only: $desire. Not a technical demo or feature brochure."
    }
}
