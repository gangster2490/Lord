package de.spardirekt.recipeveo.domain

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object Copy {
    private val ru = Locale.forLanguageTag("ru-RU")
    private val dateFmt = DateTimeFormatter.ofPattern("EEEE, d MMMM", ru)

    fun greeting(name: String, time: LocalTime): String {
        val hello = when (time.hour) {
            in 5..11 -> "Студия утром"
            in 12..16 -> "Студия днём"
            in 17..22 -> "Студия вечером"
            else -> "Ночная студия"
        }
        val who = name.trim()
        return if (who.isEmpty()) hello else "$hello, $who"
    }

    fun prettyDate(clock: AppClock): String =
        clock.today().format(dateFmt).replaceFirstChar { it.titlecase(ru) }

    fun styleBlurb(style: ShotStyle): String = when (style) {
        ShotStyle.Auto -> "Лучший 8-секундный product ad: крюк → силуэт → один proof → hero."
        ShotStyle.Showcase -> "Товар как герой. Четыре кадра держат форму, цвет и маркировку."
        ShotStyle.Demo -> "Одно подтверждённое действие руками, продукт остаётся главным."
        ShotStyle.Lifestyle -> "Живой интерьер, естественный свет, товар не теряется."
        ShotStyle.Macro -> "Крупный план фактуры и деталей, которые видны на фото."
        ShotStyle.Satisfying -> "Медленное тактильное движение без лишних пропсов."
        ShotStyle.Unboxing -> "Коробка и сам товар. Никакого marketplace UI."
    }
}

data class HomeSnapshot(
    val greeting: String,
    val dateLabel: String,
    val readyCount: Int,
    val draftCount: Int,
    val featured: Project?,
    val recent: List<Project>,
    val draft: Project?,
)

object HomeMath {
    fun snapshot(state: StudioState, clock: AppClock): HomeSnapshot {
        val ready = state.projects.filter { it.status == ProjectStatus.Ready && it.result != null }
        val drafts = state.projects.filter { it.status == ProjectStatus.Draft }
        val featured = ready.maxByOrNull { it.updatedAt }
        return HomeSnapshot(
            greeting = Copy.greeting(state.prefs.displayName, clock.nowTime()),
            dateLabel = Copy.prettyDate(clock),
            readyCount = ready.size,
            draftCount = drafts.size,
            featured = featured,
            recent = ready.sortedByDescending { it.updatedAt }.take(4),
            draft = state.active()?.takeIf { it.status == ProjectStatus.Draft } ?: drafts.firstOrNull(),
        )
    }
}
