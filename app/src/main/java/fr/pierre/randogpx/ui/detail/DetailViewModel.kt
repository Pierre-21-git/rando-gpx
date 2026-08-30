package fr.pierre.randogpx.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.pierre.randogpx.data.Hike
import fr.pierre.randogpx.data.HikeRepository
import fr.pierre.randogpx.data.Massif
import fr.pierre.randogpx.data.TrackPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DetailViewModel(private val repository: HikeRepository) : ViewModel() {

    private val _hike = MutableStateFlow<Hike?>(null)
    val hike: StateFlow<Hike?> = _hike.asStateFlow()

    private val _points = MutableStateFlow<List<TrackPoint>>(emptyList())
    val points: StateFlow<List<TrackPoint>> = _points.asStateFlow()

    val massifs: StateFlow<List<Massif>> = repository.getAllMassifs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var currentHikeId: Long? = null

    fun load(hikeId: Long) {
        currentHikeId = hikeId
        viewModelScope.launch {
            repository.refreshStatsIfFinished(hikeId)
            _hike.value = repository.getHikeById(hikeId)
            _points.value = repository.getPointsForHike(hikeId)
        }
    }

    fun renameHike(newName: String) {
        val hikeId = currentHikeId ?: return
        viewModelScope.launch {
            repository.renameHike(hikeId, newName)
            _hike.value = repository.getHikeById(hikeId)
        }
    }

    fun setMassif(massifId: Long?) {
        val hikeId = currentHikeId ?: return
        viewModelScope.launch {
            repository.setHikeMassif(hikeId, massifId)
            _hike.value = repository.getHikeById(hikeId)
        }
    }

    fun deleteHike(onDeleted: () -> Unit) {
        val hikeId = currentHikeId ?: return
        viewModelScope.launch {
            repository.deleteHike(hikeId)
            onDeleted()
        }
    }
}
