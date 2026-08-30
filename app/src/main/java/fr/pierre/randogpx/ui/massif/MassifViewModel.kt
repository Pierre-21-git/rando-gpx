package fr.pierre.randogpx.ui.massif

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.pierre.randogpx.data.HikeRepository
import fr.pierre.randogpx.data.Massif
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MassifViewModel(private val repository: HikeRepository) : ViewModel() {

    val massifs: StateFlow<List<Massif>> = repository.getAllMassifs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addMassif(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repository.createMassif(trimmed) }
    }

    fun deleteMassif(massifId: Long) {
        viewModelScope.launch { repository.deleteMassif(massifId) }
    }
}
