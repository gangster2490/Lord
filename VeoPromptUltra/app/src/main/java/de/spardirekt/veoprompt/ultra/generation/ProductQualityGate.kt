package de.spardirekt.veoprompt.ultra.generation

import de.spardirekt.veoprompt.ultra.model.AnalysisResult
import de.spardirekt.veoprompt.ultra.model.ProductModel

/**
 * Locks appearance after PHOTO_ANALYSIS / PRODUCT_MODEL.
 * Drops unverified material names and keeps 5–12 identity-critical details.
 */
object ProductQualityGate {

    fun lockAppearance(model: ProductModel, analysis: AnalysisResult?): ProductModel {
        val highFacts = (
            (analysis?.visualFacts?.filter { it.level().name == "HIGH" }?.map { it.fact } ?: emptyList()) +
                model.visualSignature +
                model.confirmedParts +
                model.confirmedColors
            ).distinct()
        val materials = MaterialSafety.filterUnverifiedMaterials(model.confirmedMaterials, highFacts)
        val signature = model.visualSignature
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .let { list ->
                if (list.size <= 12) list else list.subList(0, 12)
            }
        return model.copy(
            confirmedMaterials = materials,
            visualSignature = signature,
            confirmedFunctions = model.confirmedFunctions.filter { fn ->
                analysis?.uncertainFacts?.none { it.contains(fn, ignoreCase = true) } != false ||
                    highFacts.any { it.contains(fn, ignoreCase = true) }
            }
        )
    }
}
