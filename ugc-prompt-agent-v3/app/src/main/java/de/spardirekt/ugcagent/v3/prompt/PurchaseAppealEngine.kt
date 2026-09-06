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
        val settingCoherence: Double = 0.95,
        val speechNaturalness: Double = 0.92,
    ) {
        val total: Double
            get() = 0.20 * purchaseAppeal + 0.16 * naturalness + 0.12 * claimSafety +
                0.12 * visualClarity + 0.11 * motionSafety + 0.11 * identity + 0.08 * relevance +
                0.06 * settingCoherence + 0.04 * speechNaturalness
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
            .put("setting_type", plan.settingType.name)
            .put("setting_confidence", plan.settingConfidence)
            .put("emotional_tone", plan.emotionalTone)
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
        CreativeStrategyEngine.Motivation.PROBLEM_SOLVER, CreativeStrategyEngine.Motivation.CLEANLINESS,
        CreativeStrategyEngine.Motivation.SAFETY_CONVENIENCE -> "This solves an annoying problem."
        CreativeStrategyEngine.Motivation.COMFORT -> "This makes the experience easier / more comfortable."
        CreativeStrategyEngine.Motivation.HOME_COZY -> "This feels pleasant, useful and nice to have at home."
        CreativeStrategyEngine.Motivation.DEMONSTRABLE_FUNCTION -> "I immediately understand what it does."
        CreativeStrategyEngine.Motivation.PORTABLE, CreativeStrategyEngine.Motivation.SPACE_SAVING ->
            "This is easy to carry / store / keep around."
        CreativeStrategyEngine.Motivation.VISUAL, CreativeStrategyEngine.Motivation.PREMIUM_FEEL,
        CreativeStrategyEngine.Motivation.GIFT_APPEAL -> "This looks really good."
        CreativeStrategyEngine.Motivation.OUTDOOR_HOBBY -> "This improves the experience outdoors."
        CreativeStrategyEngine.Motivation.ORGANIZATION -> "This makes everyday things easier to keep in order."
        CreativeStrategyEngine.Motivation.CONVENIENCE, CreativeStrategyEngine.Motivation.TIME_SAVING ->
            "This makes the moment easier."
        CreativeStrategyEngine.Motivation.SIMPLICITY, CreativeStrategyEngine.Motivation.OTHER,
        CreativeStrategyEngine.Motivation.DURABILITY_APPEAL -> "This is simple and useful in real life."
    }

    fun boughtFor(primary: CreativeStrategyEngine.Motivation): String = when (primary) {
        CreativeStrategyEngine.Motivation.PROBLEM_SOLVER, CreativeStrategyEngine.Motivation.CLEANLINESS,
        CreativeStrategyEngine.Motivation.SAFETY_CONVENIENCE -> "relief from an everyday mess or hassle"
        CreativeStrategyEngine.Motivation.COMFORT -> "more comfortable everyday use"
        CreativeStrategyEngine.Motivation.HOME_COZY -> "a pleasant useful home object"
        CreativeStrategyEngine.Motivation.DEMONSTRABLE_FUNCTION -> "one clear useful function"
        CreativeStrategyEngine.Motivation.PORTABLE, CreativeStrategyEngine.Motivation.SPACE_SAVING ->
            "easy carrying or compact keeping"
        CreativeStrategyEngine.Motivation.VISUAL, CreativeStrategyEngine.Motivation.PREMIUM_FEEL,
        CreativeStrategyEngine.Motivation.GIFT_APPEAL -> "how it looks in real life"
        CreativeStrategyEngine.Motivation.OUTDOOR_HOBBY -> "more comfortable, practical outdoor use"
        CreativeStrategyEngine.Motivation.ORGANIZATION -> "keeping things in easy reach"
        CreativeStrategyEngine.Motivation.CONVENIENCE, CreativeStrategyEngine.Motivation.TIME_SAVING ->
            "less fuss in a real moment"
        CreativeStrategyEngine.Motivation.SIMPLICITY, CreativeStrategyEngine.Motivation.OTHER,
        CreativeStrategyEngine.Motivation.DURABILITY_APPEAL -> "simple everyday usefulness"
    }

    private fun conceptsFor(
        plan: CreativeStrategyEngine.Plan,
        analysis: JSONObject?,
        fingerprint: JSONObject?,
    ): List<Concept> {
        val setting = plan.setting
        val human = plan.human
        val action = plan.action
        val glance = "The referenced product stays in place while the person shares one quiet natural glance. Do not fold, open, rotate, pull, detach or reconstruct hidden geometry."
        val safestAction = if (action.contains("already seated") || ProductIdentity.looksLikeMicrowaveCover(fingerprint)) action else glance
        val purchaseIdea = when (plan.primary) {
            CreativeStrategyEngine.Motivation.PROBLEM_SOLVER -> CreativeStrategyEngine.SellingIdea.CLEANLINESS
            CreativeStrategyEngine.Motivation.OUTDOOR_HOBBY -> CreativeStrategyEngine.SellingIdea.CONVENIENCE
            else -> plan.idea
        }
        val purchaseHook = when (plan.primary) {
            CreativeStrategyEngine.Motivation.PROBLEM_SOLVER -> CreativeStrategyEngine.HookType.PROBLEM
            CreativeStrategyEngine.Motivation.OUTDOOR_HOBBY -> CreativeStrategyEngine.HookType.CONVENIENCE
            else -> plan.hookType
        }
        val scores = scoresFor(plan.primary)
        return listOf(
            Concept("safest", plan.idea, plan.hookType, setting, safestAction, human, plan.pitch, scores.safest),
            Concept("purchase", purchaseIdea, purchaseHook, setting, action, human, plan.pitch, scores.purchase),
            Concept("natural", plan.idea, plan.hookType, setting, action, human, plan.pitch, scores.natural),
        ).map { concept ->
            val actionRisk = scoreAction(concept.action)
            val motion = minOf(concept.scores.motionSafety, actionRisk.motionSafety)
            val identity = minOf(concept.scores.identity, actionRisk.identity)
            if (actionRisk.highRisk) {
                concept.copy(
                    action = CreativeStrategyEngine.safeAction(fingerprint, analysis, plan.idea, plan.settingType),
                    scores = concept.scores.copy(motionSafety = motion * 0.4, identity = identity * 0.5, purchaseAppeal = concept.scores.purchaseAppeal * 0.7),
                )
            } else {
                concept.copy(scores = concept.scores.copy(motionSafety = motion, identity = identity))
            }
        }
    }

    private data class Trio(val safest: ConceptScore, val purchase: ConceptScore, val natural: ConceptScore)

    private fun scoresFor(primary: CreativeStrategyEngine.Motivation): Trio = when (primary) {
        CreativeStrategyEngine.Motivation.OUTDOOR_HOBBY -> Trio(
            ConceptScore(0.78, 0.84, 0.96, 0.86, 0.98, 0.98, 0.94),
            ConceptScore(0.95, 0.86, 0.94, 0.9, 0.9, 0.94, 0.92),
            ConceptScore(0.91, 0.97, 0.96, 0.92, 0.94, 0.96, 0.96),
        )
        CreativeStrategyEngine.Motivation.PROBLEM_SOLVER, CreativeStrategyEngine.Motivation.CLEANLINESS -> Trio(
            ConceptScore(0.8, 0.86, 0.95, 0.88, 0.96, 0.98, 0.9),
            ConceptScore(0.96, 0.9, 0.93, 0.9, 0.92, 0.96, 0.96),
            ConceptScore(0.9, 0.95, 0.94, 0.88, 0.94, 0.96, 0.93),
        )
        CreativeStrategyEngine.Motivation.HOME_COZY -> Trio(
            ConceptScore(0.8, 0.88, 0.97, 0.86, 0.98, 0.98, 0.9),
            ConceptScore(0.92, 0.9, 0.96, 0.9, 0.94, 0.96, 0.94),
            ConceptScore(0.88, 0.97, 0.96, 0.88, 0.95, 0.96, 0.93),
        )
        else -> Trio(
            ConceptScore(0.76, 0.84, 0.96, 0.84, 0.98, 0.98, 0.88),
            ConceptScore(0.93, 0.86, 0.94, 0.9, 0.9, 0.94, 0.92),
            ConceptScore(0.88, 0.96, 0.95, 0.88, 0.93, 0.95, 0.9),
        )
    }

    private fun select(concepts: List<Concept>): Concept {
        val safe = concepts.filter { it.scores.safe }
        val pool = if (safe.isNotEmpty()) safe else concepts.sortedByDescending { it.scores.motionSafety * it.scores.identity }
        return pool.maxByOrNull { it.score } ?: concepts.first()
    }

    private fun openingFor(concept: Concept): String = CreativeConsistencyEngine.openingFor(concept.hookType)

    private fun formatTone(plan: CreativeStrategyEngine.Plan, winner: Concept): String {
        val desire = desireFor(winner.idea)
        val base = plan.formatTone.substringBefore(" One desire:")
        return "$base One desire only: $desire. Not a technical demo or feature brochure."
    }
}
