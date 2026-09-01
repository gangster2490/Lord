package de.spardirekt.veoprompt.ultra.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.spardirekt.veoprompt.ultra.model.AppMode
import de.spardirekt.veoprompt.ultra.model.CreativeMode
import de.spardirekt.veoprompt.ultra.model.GenerationStage
import de.spardirekt.veoprompt.ultra.model.ProjectStatus
import de.spardirekt.veoprompt.ultra.model.VoiceLanguage

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    val imageUrisJson: String = "[]",
    val optionalWish: String = "",
    val voiceLanguage: String = VoiceLanguage.DE.name,
    val mode: String = AppMode.Simple.name,
    val creativeMode: String = CreativeMode.AUTO.name,
    val tiktokShopMode: Boolean = true,
    val analysisResultJson: String = "",
    val productModelJson: String = "",
    val creativePlanJson: String = "",
    val generationStage: String = GenerationStage.IDLE.name,
    val completedStagesJson: String = "[]",
    val veoPrompt: String = "",
    val voiceover: String = "",
    val title: String = "",
    val hashtagsJson: String = "[]",
    val safetyAuditJson: String = "",
    val errorState: String = "",
    val errorDetail: String = "",
    val status: String = ProjectStatus.Draft.name,
    val thumbnailUri: String = "",
    val retryCountJson: String = "{}"
)
