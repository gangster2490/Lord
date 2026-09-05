package de.spardirekt.ugcagent.prompt

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Random

class ScenePoolTest {

    @Test
    fun pickReturnsThreeToFiveUniqueScenes() {
        val scenes = ScenePool.pick(count = 4, random = Random(7))
        assertThat(scenes).hasSize(4)
        assertThat(scenes.map { it.key }.toSet()).hasSize(4)
    }

    @Test
    fun neverRepeatsExcludedCombination() {
        val excluded = ScenePool.allCombinations().first().key
        repeat(12) { seed ->
            val scenes = ScenePool.pick(count = 5, excludeKey = excluded, random = Random(seed.toLong()))
            assertThat(scenes.map { it.key }).doesNotContain(excluded)
        }
    }

    @Test
    fun systemPromptsForbidVisualProductDescription() {
        assertThat(SystemPrompts.VISION_ANALYSIS).contains("KEINE HALLUZINATION")
        assertThat(SystemPrompts.VISION_ANALYSIS).contains("Form, Farbe, Material")
        assertThat(SystemPrompts.VIDEO_PROMPT).contains("NIEMALS Form, Farbe, Material")
        assertThat(SystemPrompts.VIDEO_PROMPT).contains("MAXIMAL 8 Sekunden")
        assertThat(SystemPrompts.VIDEO_PROMPT).contains("9:16")
        assertThat(SystemPrompts.IMPROVE_PASS).contains("Two-Pass")
    }
}
