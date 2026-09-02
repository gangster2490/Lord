package de.spardirekt.recipeveo.domain

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class VeoPromptTest {
    @Test
    fun twelveSectionsFromPhotos() {
        val prompt = VeoPrompt.compile(2, "", 1L)
        assertThat(StudioRules.looksComplete(prompt.text)).isTrue()
        assertThat(prompt.title).isEqualTo("Product film")
        assertThat(prompt.text).contains("2 frame(s)")
        assertThat(prompt.text).contains("VOICEOVER\nOFF")
        assertThat(prompt.text).doesNotContain("gold cap")
    }

    @Test
    fun wishBecomesTitle() {
        val prompt = VeoPrompt.compile(1, "keep the lid", 2L)
        assertThat(prompt.title).isEqualTo("Keep the lid")
        assertThat(prompt.text).contains("Wish: keep the lid")
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesWithoutPhotos() {
        VeoPrompt.compile(0, "", 1L)
    }
}

class StudioStoreTest {
    @Test
    fun hydrateStartsEmptyDraft() = runTest {
        val store = StudioStore(MemoryPersist())
        store.hydrate()
        assertThat(store.ready.value).isTrue()
        assertThat(store.state.value.projects).hasSize(1)
        assertThat(StudioRules.canGenerate(store.state.value.active()!!)).isFalse()
    }

    @Test
    fun generateWritesPrompt() = runTest {
        val store = StudioStore(MemoryPersist())
        store.hydrate()
        store.update { state ->
            val draft = state.active()!!
            state.upsert(draft.copy(photos = listOf(PhotoRef("1", "content://x"))))
        }
        val result = store.generateActive(9L)
        assertThat(result.isSuccess).isTrue()
        assertThat(StudioRules.looksComplete(result.getOrThrow().prompt!!.text)).isTrue()
        assertThat(store.state.value.ready()).hasSize(1)
    }

    @Test
    fun generateWithoutPhotosFails() = runTest {
        val store = StudioStore(MemoryPersist())
        store.hydrate()
        assertThat(store.generateActive().isFailure).isTrue()
        assertThat(store.state.value.active()!!.prompt).isNull()
    }
}
