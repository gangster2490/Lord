package de.spardirekt.recipeveo.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CulinaryAgentTest {
    @Test
    fun borschtIsAFullRussianSoupPackage() {
        val pack = CulinaryAgent.create("борщ", 1L)
        assertThat(pack.recipe.ingredients.joinToString()).contains("свёкл")
        assertThat(pack.recipe.steps.size).isAtLeast(4)
        assertThat(pack.veoPrompt).contains("Ровно 8.0с")
        assertThat(pack.veoPrompt).contains("Борщ")
        assertThat(pack.negativePrompt).contains("Борщ")
        assertThat(pack.voiceover.lowercase()).contains("борщ")
        assertThat(pack.tiktokTitle.lowercase()).contains("борщ")
        assertThat(pack.hashtags).hasSize(5)
        assertThat(pack.hashtags[0]).isEqualTo("#Борщ")
        assertThat(pack.copyPrompt()).isEqualTo(pack.veoPrompt)
    }

    @Test
    fun plovDiffersFromBorscht() {
        val plov = CulinaryAgent.create("Плов", 2L)
        val borscht = CulinaryAgent.create("Борщ", 3L)
        assertThat(plov.recipe.ingredients.joinToString()).contains("рис")
        assertThat(plov.veoPrompt).contains("казан")
        assertThat(borscht.veoPrompt).contains("тарелка")
        assertThat(plov.veoPrompt).isNotEqualTo(borscht.veoPrompt)
        assertThat(plov.voiceover).isNotEqualTo(borscht.voiceover)
        assertThat(plov.hashtags).hasSize(5)
    }

    @Test
    fun unknownDishStillCompletes() {
        val pack = CulinaryAgent.create("тыквенный крем-суп", 4L)
        assertThat(pack.recipe.asText()).contains("Тыквенный крем-суп")
        assertThat(pack.veoPrompt).contains("8.0с")
        assertThat(pack.negativePrompt).isNotEmpty()
        assertThat(pack.voiceover).isNotEmpty()
        assertThat(pack.hashtags).hasSize(5)
        assertThat(pack.hashtags.count { it.startsWith("#") }).isEqualTo(5)
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyNameFails() {
        CulinaryAgent.create(" ")
    }

    @Test
    fun fourShotsAndNoOnScreenText() {
        val pack = CulinaryAgent.create("блины")
        assertThat(pack.veoPrompt).contains("0.0–2.0с")
        assertThat(pack.veoPrompt).contains("6.0–8.0с")
        assertThat(pack.veoPrompt).contains("ТЕКСТ НА ЭКРАНЕ")
        assertThat(pack.veoPrompt).contains("Нет. Только еда.")
        assertThat(pack.negativePrompt).isNotEqualTo(pack.veoPrompt)
    }
}
