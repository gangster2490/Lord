package de.spardirekt.recipeveo.domain

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class PhotoRef(val id: String, val uri: String)

@Serializable
data class Prompt(
    val text: String,
    val title: String,
    val compiledAt: Long,
)

@Serializable
data class Project(
    val id: String,
    val photos: List<PhotoRef> = emptyList(),
    val wish: String = "",
    val prompt: Prompt? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class StudioState(
    val version: Int = 1,
    val projects: List<Project> = emptyList(),
    val activeId: String? = null,
) {
    fun active(): Project? = activeId?.let { id -> projects.firstOrNull { it.id == id } }
        ?: projects.firstOrNull()

    fun upsert(project: Project) = copy(projects = listOf(project) + projects.filterNot { it.id == project.id })

    fun delete(id: String): StudioState {
        val next = projects.filterNot { it.id == id }
        return copy(projects = next, activeId = if (activeId == id) next.firstOrNull()?.id else activeId)
    }

    fun ready(): List<Project> = projects.filter { it.prompt != null }.sortedByDescending { it.updatedAt }

    companion object { val Empty = StudioState() }
}

fun newId(): String = UUID.randomUUID().toString()

object StudioRules {
    const val MAX_PHOTOS = 15
    val sections = listOf(
        "FORMAT", "REFERENCES", "PRODUCT LOCK", "SETTING", "SHOT SEQUENCE",
        "ON-SCREEN TEXT", "VOICEOVER", "AUDIO", "CRITICAL", "NEGATIVE PROMPT",
        "TITLE", "HASHTAGS",
    )
    fun canGenerate(project: Project) = project.photos.isNotEmpty()
    fun looksComplete(text: String) = sections.all { heading ->
        Regex("^${Regex.escape(heading)}$", RegexOption.MULTILINE).containsMatchIn(text)
    } && text.contains("Exactly 8.0s")
}
