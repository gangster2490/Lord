package de.spardirekt.recipeveo.domain

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.nio.file.Files

class StudioStoreTest {
    @Test
    fun missingFileSeedsAReadyLibrary() = runTest {
        val store = StudioStore(MemoryPersist(), FixedAppClock(5L))
        store.hydrate()
        assertThat(store.ready.value).isTrue()
        assertThat(store.state.value.recipes).hasSize(4)
        assertThat(store.state.value.recipes.all { it.compiled != null }).isTrue()
    }

    @Test
    fun hydrateRoundTripsEdits() = runTest {
        val file = Files.createTempDirectory("rvs").toFile().resolve("studio.json")
        val clock = FixedAppClock(7L)
        val store = StudioStore(FileStudioPersist(file), clock)
        store.hydrate()
        store.update { it.updatePrefs(theme = ThemeMode.LIGHT) }

        val reloaded = StudioStore(FileStudioPersist(file), clock)
        reloaded.hydrate()
        assertThat(reloaded.state.value.prefs.theme).isEqualTo(ThemeMode.LIGHT)
        assertThat(reloaded.state.value.recipes).hasSize(4)
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
        assertThat(reloaded.state.value.recipes).isEmpty()
    }
}
