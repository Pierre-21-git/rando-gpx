package fr.pierre.randogpx.ui.history

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.pierre.randogpx.data.Hike
import fr.pierre.randogpx.data.HikeRepository
import fr.pierre.randogpx.data.Massif
import fr.pierre.randogpx.data.TrackPoint
import fr.pierre.randogpx.gpx.GpxImporter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(private val repository: HikeRepository) : ViewModel() {

    val hikes: StateFlow<List<Hike>> = repository.getAllHikes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val massifs: StateFlow<List<Massif>> = repository.getAllMassifs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Conservés dans le ViewModel (et non en `remember` côté composable) pour survivre à
    // un aller-retour de navigation vers le détail d'une randonnée : le ViewModel reste
    // en vie tant que l'écran Historique reste dans la pile de navigation.
    private val _selectedMassifId = MutableStateFlow<Long?>(null)
    val selectedMassifId: StateFlow<Long?> = _selectedMassifId.asStateFlow()

    private val _selectedYear = MutableStateFlow<Int?>(null)
    val selectedYear: StateFlow<Int?> = _selectedYear.asStateFlow()

    private val _importResult = MutableSharedFlow<ImportResult>()
    val importResult: SharedFlow<ImportResult> = _importResult

    fun selectMassifFilter(massifId: Long?) {
        _selectedMassifId.value = massifId
    }

    fun selectYearFilter(year: Int?) {
        _selectedYear.value = year
    }

    sealed class ImportResult {
        data class Success(val hikeName: String) : ImportResult()
        data class Error(val message: String) : ImportResult()
        data object EmptyTrack : ImportResult()
    }

    fun deleteHike(hikeId: Long) {
        viewModelScope.launch { repository.deleteHike(hikeId) }
    }

    fun importGpx(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val track = GpxImporter.importFromUri(context, uri)
                if (track.points.isEmpty()) {
                    _importResult.emit(ImportResult.EmptyTrack)
                    return@launch
                }
                val name = track.name ?: "GPX"
                val points = track.points.map {
                    TrackPoint(
                        hikeId = 0,
                        lat = it.lat,
                        lon = it.lon,
                        altitudeGps = it.elevation ?: 0.0,
                        altitudeCorrected = it.elevation,
                        timestamp = it.timestampMillis ?: 0L
                    )
                }
                val startTime = track.points.first().timestampMillis ?: System.currentTimeMillis()
                val endTime = track.points.last().timestampMillis ?: startTime
                repository.importHike(name, startTime, endTime, points)
                _importResult.emit(ImportResult.Success(name))
            } catch (e: Exception) {
                _importResult.emit(ImportResult.Error(e.message ?: e.toString()))
            }
        }
    }
}
