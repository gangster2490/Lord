package de.spardirekt.recipeveo.domain

import kotlinx.serialization.Serializable

@Serializable
data class StudioState(
    val version: Int = 1,
    val prefs: Prefs = Prefs(),
    val projects: List<Project> = emptyList(),
    val activeId: String? = null,
) {
    fun prefs(theme: ThemeMode = prefs.theme, defaultVoice: VoiceLang = prefs.defaultVoice, tiktokShop: Boolean = prefs.tiktokShop) =
        copy(prefs = prefs.copy(theme = theme, defaultVoice = defaultVoice, tiktokShop = tiktokShop))

    fun project(id: String?): Project? = id?.let { id0 -> projects.firstOrNull { it.id == id0 } }

    fun active(): Project? = project(activeId) ?: projects.firstOrNull()

    fun upsert(project: Project): StudioState {
        val without = projects.filterNot { it.id == project.id }
        return copy(projects = listOf(project) + without)
    }

    fun delete(id: String): StudioState {
        val next = projects.filterNot { it.id == id }
        return copy(
            projects = next,
            activeId = if (activeId == id) next.firstOrNull()?.id else activeId,
        )
    }

    fun open(id: String): StudioState = copy(activeId = id)

    fun history(): List<Project> = projects.filter { it.status != ProjectStatus.Draft || it.photos.isNotEmpty() || it.result != null }

    companion object {
        val Empty = StudioState()
    }
}

fun StudioState.ensureDraft(clock: AppClock): StudioState {
    val reusable = projects.firstOrNull {
        it.status == ProjectStatus.Draft && it.photos.isEmpty() && it.result == null
    }
    if (reusable != null) return copy(activeId = reusable.id)
    val now = clock.nowMillis()
    val draft = Project(
        id = newId(),
        voice = prefs.defaultVoice,
        tiktokShop = prefs.tiktokShop,
        createdAt = now,
        updatedAt = now,
    )
    return upsert(draft).copy(activeId = draft.id)
}
