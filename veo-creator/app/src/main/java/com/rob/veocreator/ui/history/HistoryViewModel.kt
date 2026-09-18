package com.rob.veocreator.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rob.veocreator.VeoCreatorApp
import com.rob.veocreator.data.db.HistoryEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val dao get() = getApplication<VeoCreatorApp>().database.historyDao()

    val items: StateFlow<List<HistoryEntity>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun delete(entity: HistoryEntity) {
        viewModelScope.launch { dao.delete(entity) }
    }
}
