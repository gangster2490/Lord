package de.spardirekt.recipeveo.domain

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.nio.file.Files

class StudioStoreTest {
    @Test
    fun hydrateSeedsThreeReadyRecipesAndADraft() = runTest {
        val store = StudioStore(MemoryPersist(), FixedAppClock())
        store.hydrate()
        assertThat(store.ready.value).isTrue()
        assertThat(store.state.value.projects.filter { it.status == ProjectStatus.Ready }).hasSize(3)
        store.state.value.projects.filter { it.status == ProjectStatus.Ready }.forEach { project ->
            assertThat(StudioRules.looksComplete(project.result!!.veoPrompt, project.result!!.hashtags)).isTrue()
        }
        assertThat(StudioRules.canGenerate(store.state.value.active()!!)).isTrue()
    }

    @Test
    fun generateWritesReadyPackage() = runTest {
        val store = StudioStore(MemoryPersist(), FixedAppClock())
        store.hydrate()
        val result = store.generateActive()
        assertThat(result.isSuccess).isTrue()
        val project = result.getOrThrow()
        assertThat(project.status).isEqualTo(ProjectStatus.Ready)
        assertThat(project.title).isEqualTo("Velvet Gold Night Cream")
        assertThat(StudioRules.looksComplete(project.result!!.veoPrompt, project.result!!.hashtags)).isTrue()
    }

    @Test
    fun generateWithoutPhotosFails() = runTest {
        val store = StudioStore(MemoryPersist(), FixedAppClock())
        store.hydrate()
        store.update { state ->
            val draft = state.active()!!
            state.upsert(draft.copy(photos = emptyList(), productId = null))
        }
        val result = store.generateActive()
        assertThat(result.isFailure).isTrue()
        assertThat(store.state.value.active()!!.status).isEqualTo(ProjectStatus.Draft)
    }

    @Test
    fun emptySavedStateIsNotReseeded() = runTest {
        val persist = MemoryPersist()
        val clock = FixedAppClock()
        val store = StudioStore(persist, clock)
        store.hydrate()
        store.update { StudioState(prefs = it.prefs) }
        val reloaded = StudioStore(persist, clock)
        reloaded.hydrate()
        assertThat(reloaded.state.value.projects).isEmpty()
    }

    @Test
    fun fileRoundTripKeepsCatalogRecipes() = runTest {
        val file = Files.createTempDirectory("rvs").toFile().resolve("studio.json")
        val clock = FixedAppClock()
        val store = StudioStore(FileStudioPersist(file), clock)
        store.hydrate()
        val reloaded = StudioStore(FileStudioPersist(file), clock)
        reloaded.hydrate()
        assertThat(reloaded.state.value.projects.map { it.id }).contains("seed-arc-pulse")
    }
}
