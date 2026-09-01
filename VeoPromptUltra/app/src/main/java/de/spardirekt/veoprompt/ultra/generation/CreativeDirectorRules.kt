package de.spardirekt.veoprompt.ultra.generation

import de.spardirekt.veoprompt.ultra.model.CreativeDirection
import de.spardirekt.veoprompt.ultra.model.CreativeMode
import de.spardirekt.veoprompt.ultra.model.ProductModel

object CreativeDirectorRules {

    fun preferredPattern(product: ProductModel): String {
        val hasUsefulResult = product.confirmedFunctions.any { it.isNotBlank() } ||
            product.possibleUseCases.any { it.isNotBlank() }
        return if (hasUsefulResult) {
            "RESULT / HOOK → PRODUCT IDENTITY → ONE SIMPLE ACTION → HERO"
        } else {
            "DETAIL HOOK → PRODUCT IDENTITY → SIMPLE DEMO → HERO"
        }
    }

    fun resolveMode(requested: CreativeMode, selectedRaw: String): CreativeMode {
        if (requested != CreativeMode.AUTO) return requested
        val parsed = CreativeMode.fromRaw(selectedRaw)
        return if (parsed == CreativeMode.LIFESTYLE) CreativeMode.SHOWCASE else parsed
    }

    fun sanitizePlan(requested: CreativeMode, plan: CreativeDirection): CreativeDirection {
        val mode = resolveMode(requested, plan.selectedMode)
        val usePeople = mode == CreativeMode.LIFESTYLE && plan.usePeople
        return plan.copy(
            selectedMode = mode.name,
            usePeople = usePeople,
            useHands = plan.useHands && mode != CreativeMode.SHOWCASE
        )
    }
}
