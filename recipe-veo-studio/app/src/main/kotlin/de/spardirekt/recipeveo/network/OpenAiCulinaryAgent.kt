package de.spardirekt.recipeveo.network

import de.spardirekt.recipeveo.domain.CulinaryPackage
import de.spardirekt.recipeveo.domain.Recipe

/**
 * Calls GPT-4o-mini to generate a full Russian culinary package for the given dish.
 * Returns a CulinaryPackage. Throws on network or parse error.
 */
object OpenAiCulinaryAgent {

    private val SYSTEM = """
Ты — кулинарный AI-агент. На входе — название блюда на русском языке.
Твоя задача: вернуть СТРОГО JSON следующей структуры (только JSON, никакого Markdown, никаких пояснений):
{
  "recipe": {
    "servings": "N порций",
    "time": "X мин / X ч",
    "ingredients": ["..."],
    "steps": ["..."]
  },
  "veoPrompt": "...",
  "negativePrompt": "...",
  "voiceover": "...",
  "tiktokTitle": "...",
  "hashtags": ["#...", "#...", "#...", "#...", "#..."]
}
Правила:
- Всё на русском.
- recipe.ingredients — массив строк с количествами.
- recipe.steps — массив строк, по одному шагу.
- veoPrompt: подробный промпт кулинарного видео Veo 3.1 на РОВНО 8 секунд, вертикаль 9:16, фотореализм. Включает секции: FORMAT, БЛЮДО, РЕЦЕПТ В КАДРЕ, СРЕДА, ПЛАНЫ (0.0–2.0с HOOK, 2.0–4.0с IDENTITY, 4.0–6.0с CRAFT, 6.0–8.0с HERO), ТЕКСТ НА ЭКРАНЕ (Нет), ОЗВУЧКА, ЗВУК, КРИТИЧНО.
- negativePrompt: список запретов, что не должно появиться в кадре.
- voiceover: короткая 1–2 фразы живая озвучка на русском для ролика.
- tiktokTitle: до 70 символов, цепляющий заголовок.
- hashtags: РОВНО 5 элементов, каждый начинается с #.
""".trimIndent()

    suspend fun create(dish: String, apiKey: String, now: Long = System.currentTimeMillis()): CulinaryPackage {
        val client = OpenAiClient(apiKey)
        val raw = client.chat(SYSTEM, dish.trim())
        return parse(dish.trim().replaceFirstChar { it.uppercase() }, raw, now)
    }

    private fun parse(dish: String, json: String, now: Long): CulinaryPackage {
        val cleaned = json.trim()
            .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val root = try {
            org.json.JSONObject(cleaned)
        } catch (e: Exception) {
            throw RuntimeException("Не удалось разобрать ответ AI. Попробуйте ещё раз.")
        }

        val rec = root.getJSONObject("recipe")
        val ingredients = rec.getJSONArray("ingredients").let { arr ->
            (0 until arr.length()).map { arr.getString(it) }
        }
        val steps = rec.getJSONArray("steps").let { arr ->
            (0 until arr.length()).map { arr.getString(it) }
        }
        val recipe = Recipe(
            dish = dish,
            servings = rec.getString("servings"),
            time = rec.getString("time"),
            ingredients = ingredients,
            steps = steps,
        )

        val tags = root.getJSONArray("hashtags").let { arr ->
            (0 until arr.length()).map { arr.getString(it) }
        }.take(5).let { list ->
            // Ensure exactly 5 — pad if model returned fewer
            if (list.size < 5) list + List(5 - list.size) { "#Рецепт" } else list
        }

        return CulinaryPackage(
            dish = dish,
            recipe = recipe,
            veoPrompt = root.getString("veoPrompt"),
            negativePrompt = root.getString("negativePrompt"),
            voiceover = root.getString("voiceover"),
            tiktokTitle = root.getString("tiktokTitle").take(70),
            hashtags = tags,
            createdAt = now,
        )
    }
}
