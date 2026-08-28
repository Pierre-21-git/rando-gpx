package com.tonapp.rando.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapp.rando.data.Hike
import com.tonapp.rando.data.HikeRepository
import com.tonapp.rando.data.TrackPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DetailViewModel(private val repository: HikeRepository) : ViewModel() {

    private val _hike = MutableStateFlow<Hike?>(null)
    val hike: StateFlow<Hike?> = _hike.asStateFlow()

    private val _points = MutableStateFlow<List<TrackPoint>>(emptyList())
    val points: StateFlow<List<TrackPoint>> = _points.asStateFlow()

    fun load(hikeId: Long) {
        viewModelScope.launch {
            _hike.value = repository.getHikeById(hikeId)
            _points.value = repository.getPointsForHike(hikeId)
        }
    }
}
