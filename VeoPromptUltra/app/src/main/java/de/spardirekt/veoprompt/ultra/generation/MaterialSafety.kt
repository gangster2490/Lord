package de.spardirekt.veoprompt.ultra.generation

object MaterialSafety {
    private val forbiddenUnlessHigh = listOf(
        "cast iron",
        "чугун",
        "aluminium",
        "aluminum",
        "stainless steel",
        "нержавеющ",
        "non-stick",
        "nonstick",
        "антипригар"
    )

    fun isForcedMaterialTerm(text: String): Boolean {
        val lower = text.lowercase()
        return forbiddenUnlessHigh.any { lower.contains(it) }
    }

    fun filterUnverifiedMaterials(materials: List<String>, highConfidenceFacts: List<String>): List<String> {
        val highBlob = highConfidenceFacts.joinToString(" ").lowercase()
        return materials.filter { material ->
            if (!isForcedMaterialTerm(material)) return@filter true
            highBlob.contains(material.lowercase()) ||
                forbiddenUnlessHigh.any { term ->
                    material.lowercase().contains(term) && highBlob.contains(term)
                }
        }
    }
}
