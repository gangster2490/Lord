package de.spardirekt.recipeveo.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CulinaryPackageParserTest {
    @Test
    fun readsCanonicalJson() {
        val pack = CulinaryPackageParser.parse(
            dishHint = "Плов",
            raw = """
                {
                  "recipe": {
                    "servings": "6",
                    "time": "1 ч",
                    "ingredients": ["рис", "мясо"],
                    "steps": ["обжарить", "томить"]
                  },
                  "veoPrompt": "FORMAT\nРовно 8.0с\nПлов",
                  "negativePrompt": "- нет CGI",
                  "voiceover": "Плов из казана.",
                  "tiktokTitle": "Плов за 8 секунд",
                  "hashtags": ["#Плов", "#Рецепт", "#Еда", "#TikTokFood", "#Veo"]
                }
            """.trimIndent(),
            now = 1L,
            fromOpenAi = true,
        )
        assertThat(pack.dish).isEqualTo("Плов")
        assertThat(pack.recipe.ingredients).contains("рис")
        assertThat(pack.veoPrompt).contains("8.0с")
        assertThat(pack.hashtags).hasSize(5)
        assertThat(pack.fromOpenAi).isTrue()
        assertThat(pack.fullPackage()).contains("НЕГАТИВНЫЙ ПРОМПТ")
    }

    @Test
    fun stripsMarkdownAndPadsHashtags() {
        val pack = CulinaryPackageParser.parse(
            "борщ",
            """
            Вот JSON:
            ```json
            {"recipe":{"servings":"4","time":"1 ч","ingredients":["свёкла"],"steps":["варить"]},
             "veoPrompt":"Борщ, 8.0с","negativePrompt":"нет пластика","voiceover":"Густой борщ.",
             "tiktokTitle":"Борщ","hashtags":"борщ еда"}
            ```
            """.trimIndent(),
            2L,
            true,
        )
        assertThat(pack.hashtags).hasSize(5)
        assertThat(pack.hashtags.all { it.startsWith("#") }).isTrue()
        assertThat(pack.recipe.ingredients).contains("свёкла")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptyPrompt() {
        CulinaryPackageParser.parse(
            "Суп",
            """{"recipe":{"ingredients":["вода"],"steps":["кипятить"]},"veoPrompt":"","negativePrompt":"x","voiceover":"x","tiktokTitle":"x","hashtags":[]}""",
            3L,
            true,
        )
    }
}
