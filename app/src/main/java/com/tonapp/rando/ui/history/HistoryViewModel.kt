package com.tonapp.rando.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapp.rando.data.Hike
import com.tonapp.rando.data.HikeRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(private val repository: HikeRepository) : ViewModel() {

    val hikes: StateFlow<List<Hike>> = repository.getAllHikes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteHike(hikeId: Long) {
        viewModelScope.launch { repository.deleteHike(hikeId) }
    }
}
