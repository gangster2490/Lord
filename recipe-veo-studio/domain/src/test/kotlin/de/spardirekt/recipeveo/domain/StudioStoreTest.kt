package de.spardirekt.recipeveo.domain

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.nio.file.Files

class StudioStoreTest {
    @Test
    fun hydrateSeedsReadyDemoAndDraft() = runTest {
        val store = StudioStore(MemoryPersist(), FixedAppClock(5L))
        store.hydrate()
        assertThat(store.ready.value).isTrue()
        assertThat(store.state.value.projects).hasSize(2)
        val ready = store.state.value.projects.first { it.status == ProjectStatus.Ready }
        assertThat(StudioRules.looksComplete(ready.result!!.veoPrompt, ready.result!!.hashtags)).isTrue()
    }

    @Test
    fun generateWritesReadyPackage() = runTest {
        val store = StudioStore(MemoryPersist(), FixedAppClock(9L))
        store.hydrate()
        val draftId = store.state.value.activeId!!
        store.update { state ->
            val draft = state.project(draftId)!!
            state.upsert(draft.copy(photos = listOf(PhotoRef("p1", "content://photo"))))
        }
        val result = store.generateActive()
        assertThat(result.isSuccess).isTrue()
        val project = result.getOrThrow()
        assertThat(project.status).isEqualTo(ProjectStatus.Ready)
        assertThat(project.stage).isEqualTo(GenerationStage.DONE)
        assertThat(StudioRules.looksComplete(project.result!!.veoPrompt, project.result!!.hashtags)).isTrue()
    }

    @Test
    fun generateWithoutPhotosFails() = runTest {
        val store = StudioStore(MemoryPersist(), FixedAppClock())
        store.hydrate()
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
    fun fileRoundTripKeepsReadyPrompt() = runTest {
        val file = Files.createTempDirectory("rvs").toFile().resolve("studio.json")
        val clock = FixedAppClock(3L)
        val store = StudioStore(FileStudioPersist(file), clock)
        store.hydrate()
        val reloaded = StudioStore(FileStudioPersist(file), clock)
        reloaded.hydrate()
        assertThat(reloaded.state.value.projects.any { it.id == "seed-velvet-gold" }).isTrue()
    }
}
