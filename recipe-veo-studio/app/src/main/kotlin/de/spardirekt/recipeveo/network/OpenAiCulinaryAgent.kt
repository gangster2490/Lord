package de.spardirekt.recipeveo.network

import de.spardirekt.recipeveo.domain.CulinaryPackage
import de.spardirekt.recipeveo.domain.CulinaryPackageParser
import de.spardirekt.recipeveo.domain.StudioRules

object OpenAiCulinaryAgent {

    private val SYSTEM = """
Ты кулинарный AI-агент. Отвечай ТОЛЬКО JSON-объектом, без Markdown и без текста вокруг.
Язык всех строк — русский.

Схема:
{
  "dish": "название блюда",
  "recipe": {
    "servings": "например 4 порции",
    "time": "например 1 ч 20 мин",
    "ingredients": ["ингредиент с количеством", "..."],
    "steps": ["шаг приготовления", "..."]
  },
  "veoPrompt": "подробный промпт кулинарного видео",
  "negativePrompt": "запреты для кадра, каждый с новой строки начиная с -",
  "voiceover": "1–2 живые фразы озвучки",
  "tiktokTitle": "заголовок до 70 символов",
  "hashtags": ["#Один", "#Два", "#Три", "#Четыре", "#Пять"]
}

Правила veoPrompt:
- вертикаль 9:16, фотореализм, РОВНО 8.0 секунд, 4 плана по 2.0с;
- секции с новой строки заголовком: FORMAT, БЛЮДО, РЕЦЕПТ В КАДРЕ, СРЕДА, ПЛАНЫ, ТЕКСТ НА ЭКРАНЕ, ОЗВУЧКА, ЗВУК, КРИТИЧНО;
- ПЛАНЫ: 0.0–2.0с HOOK, 2.0–4.0с IDENTITY, 4.0–6.0с CRAFT, 6.0–8.0с HERO;
- ТЕКСТ НА ЭКРАНЕ: Нет. Только еда;
- еда узнаваемая, без морфинга, без замены блюда.

hashtags — ровно 5 элементов, каждый начинается с #.
Рецепт полный и съедобный, не общий шаблон.
""".trimIndent()

    suspend fun create(dish: String, apiKey: String, now: Long = System.currentTimeMillis()): CulinaryPackage {
        val name = dish.trim().replace(Regex("\\s+"), " ")
        require(StudioRules.canCreate(name)) { "Введите название блюда." }
        require(apiKey.trim().startsWith("sk-")) { "Вставьте ключ OpenAI в настройках." }
        val raw = OpenAiClient(apiKey).chat(
            SYSTEM,
            "Блюдо: «$name». Сгенерируй полный JSON-пакет для этого блюда.",
        )
        return CulinaryPackageParser.parse(name, raw, now, fromOpenAi = true)
    }
}
