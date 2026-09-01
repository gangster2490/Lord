package de.spardirekt.veoprompt.ultra.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.spardirekt.veoprompt.ultra.VeoPromptUltraApp
import de.spardirekt.veoprompt.ultra.data.db.ProjectEntity
import de.spardirekt.veoprompt.ultra.model.ProjectStatus
import de.spardirekt.veoprompt.ultra.storage.ImageStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = VeoPromptUltraApp.instance.projectRepository

    val projects: StateFlow<List<ProjectEntity>> = repo.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(id: String) {
        viewModelScope.launch {
            ImageStore.deleteProject(getApplication(), id)
            repo.delete(id)
        }
    }

    fun duplicate(entity: ProjectEntity, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val copy = repo.duplicate(entity)
            onCreated(copy.id)
        }
    }

    fun newVersion(entity: ProjectEntity, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val copy = repo.newVersion(entity)
            onCreated(copy.id)
        }
    }

    fun openTarget(entity: ProjectEntity): OpenTarget {
        return when (entity.status) {
            ProjectStatus.Ready.name -> OpenTarget.Result(entity.id)
            ProjectStatus.Failed.name, ProjectStatus.Generating.name -> OpenTarget.Generation(entity.id)
            else -> OpenTarget.Create(entity.id)
        }
    }

    sealed class OpenTarget {
        data class Result(val id: String) : OpenTarget()
        data class Generation(val id: String) : OpenTarget()
        data class Create(val id: String) : OpenTarget()
    }
}
