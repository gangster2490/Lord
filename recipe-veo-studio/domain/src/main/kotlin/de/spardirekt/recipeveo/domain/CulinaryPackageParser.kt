package de.spardirekt.recipeveo.domain

/**
 * Turns a model JSON blob into a culinary package.
 * Tolerates markdown fences, extra prose, string-or-array fields, and missing keys.
 */
object CulinaryPackageParser {

    fun parse(dishHint: String, raw: String, now: Long, fromOpenAi: Boolean): CulinaryPackage {
        val json = extractJsonObject(raw)
            ?: throw IllegalArgumentException("Модель вернула не JSON. Нажмите «Создать» ещё раз.")

        val dish = firstNonBlank(
            json.optString("dish"),
            json.optString("title"),
            dishHint,
        ).ifBlank { dishHint }.trim().replaceFirstChar { it.uppercase() }

        val rec = json.optJSONObject("recipe")
        val ingredients = stringList(rec?.opt("ingredients") ?: json.opt("ingredients"))
        val steps = stringList(rec?.opt("steps") ?: json.opt("steps"))
        val recipe = Recipe(
            dish = dish,
            servings = firstNonBlank(rec?.optString("servings"), json.optString("servings")),
            time = firstNonBlank(rec?.optString("time"), json.optString("time")),
            ingredients = ingredients,
            steps = steps,
        )

        val veo = firstNonBlank(
            json.optString("veoPrompt"),
            json.optString("prompt"),
            json.optString("videoPrompt"),
        )
        val negative = firstNonBlank(
            json.optString("negativePrompt"),
            stringList(json.opt("negativePrompt")).joinToString("\n") { if (it.startsWith("-")) it else "- $it" },
        )
        val voiceover = firstNonBlank(json.optString("voiceover"), json.optString("vo"))
        val tiktokTitle = firstNonBlank(
            json.optString("tiktokTitle"),
            json.optString("tikTokTitle"),
            json.optString("name"),
        ).take(80)
        val hashtags = normalizeHashtags(stringList(json.opt("hashtags")), dish)

        if (recipe.ingredients.isEmpty() || recipe.steps.isEmpty()) {
            throw IllegalArgumentException("Рецепт неполный. Нажмите «Создать» ещё раз.")
        }
        if (veo.isBlank()) {
            throw IllegalArgumentException("Промпт Veo пустой. Нажмите «Создать» ещё раз.")
        }

        return CulinaryPackage(
            dish = dish,
            recipe = recipe,
            veoPrompt = veo.trim(),
            negativePrompt = negative.trim(),
            voiceover = voiceover.trim(),
            tiktokTitle = tiktokTitle.trim(),
            hashtags = hashtags,
            createdAt = now,
            fromOpenAi = fromOpenAi,
        )
    }

    fun extractJsonObject(raw: String): org.json.JSONObject? {
        val text = raw.trim()
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            org.json.JSONObject(text.substring(start, end + 1))
        } catch (_: Exception) {
            null
        }
    }

    fun normalizeHashtags(raw: List<String>, dish: String): List<String> {
        val slug = dish.filter { it.isLetterOrDigit() }.replaceFirstChar { it.uppercase() }.ifBlank { "Еда" }
        val cleaned = raw
            .flatMap { it.split(Regex("[\\s,]+")) }
            .map { it.trim().trimStart('#') }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .take(5)
            .map { "#$it" }
        val pad = listOf("#$slug", "#Рецепт", "#Еда", "#TikTokFood", "#Veo")
        return (cleaned + pad.filter { tag -> cleaned.none { it.equals(tag, ignoreCase = true) } }).take(5)
    }

    private fun stringList(value: Any?): List<String> = when (value) {
        null, org.json.JSONObject.NULL -> emptyList()
        is org.json.JSONArray -> (0 until value.length()).mapNotNull { i ->
            value.optString(i)?.trim()?.takeIf { it.isNotBlank() }
        }
        is String -> value.split(Regex("[\n,;]+")).map { it.trim().trimStart('•', '-', ' ') }.filter { it.isNotBlank() }
        else -> emptyList()
    }

    private fun firstNonBlank(vararg values: String?): String =
        values.firstOrNull { !it.isNullOrBlank() && it != "null" }.orEmpty()
}
