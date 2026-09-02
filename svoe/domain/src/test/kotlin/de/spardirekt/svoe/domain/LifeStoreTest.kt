package de.spardirekt.svoe.domain

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.nio.file.Files
import java.time.LocalDate

class LifeStoreTest {
    @Test
    fun hydrateAndPersistRoundTrip() = runTest {
        val dir = Files.createTempDirectory("svoe-store").toFile()
        val file = dir.resolve("life.json")
        val clock = FixedAppClock(LocalDate.of(2026, 9, 2), 42L)
        val store = LifeStore(FileLifePersist(file), clock)
        store.hydrate()
        store.update { it.completeOnboarding("Роберт", "EUR") }
        store.update { it.addTask("Купить хлеб", clock = clock) }

        val reloaded = LifeStore(FileLifePersist(file), clock)
        reloaded.hydrate()
        assertThat(reloaded.state.value.prefs.displayName).isEqualTo("Роберт")
        assertThat(reloaded.state.value.tasks.single().title).isEqualTo("Купить хлеб")
        assertThat(reloaded.ready.value).isTrue()
    }

    @Test
    fun missingFileStartsEmpty() = runTest {
        val store = LifeStore(MemoryPersist(), FixedAppClock(LocalDate.of(2026, 9, 2)))
        store.hydrate()
        assertThat(store.state.value).isEqualTo(LifeState.Empty)
        assertThat(store.ready.value).isTrue()
    }
}

class MoneyFormatTest {
    @Test
    fun euroFormatterUsesCurrencySymbol() {
        val text = MoneyFormat.format(1234, "EUR")
        assertThat(text).contains("12")
        assertThat(text).contains("€")
    }

    @Test
    fun categoryLabelsAreRussian() {
        assertThat(MoneyFormat.categoryLabel(SpendCategory.FOOD)).isEqualTo("Еда")
        assertThat(MoneyFormat.categoryLabel(SpendCategory.SUBSCRIPTIONS)).isEqualTo("Подписки")
    }
}

class HomeMathTest {
    @Test
    fun snapshotCountsOpenWorkAndTodaySpend() {
        val clock = FixedAppClock(LocalDate.of(2026, 9, 2), 10L)
        val state = SeedData.populated(clock, Prefs(displayName = "Роберт", onboardingDone = true))
        val snap = HomeMath.snapshot(state, clock)
        assertThat(snap.greeting).contains("Роберт")
        assertThat(snap.openTasks).isEqualTo(3)
        assertThat(snap.habitsTotal).isEqualTo(3)
        assertThat(snap.habitsDone).isEqualTo(2)
        assertThat(snap.spentTodayMinor).isEqualTo(1240_00)
        assertThat(snap.todayTaskTitles).isNotEmpty()
    }
}
