package com.tonapp.rando.ui.recording

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapp.rando.data.HikeRepository
import com.tonapp.rando.data.TrackPoint
import com.tonapp.rando.location.LocationTrackingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Ce ViewModel ne parle pas directement au GPS : c'est le rôle du
 * [LocationTrackingService] (Foreground Service), pour que le tracking survive à la
 * destruction de l'écran/ViewModel. Le ViewModel se contente de relayer les actions
 * utilisateur (start/pause/stop) et d'observer l'état exposé par le service, une fois
 * celui-ci lié depuis RecordingScreen (bindService).
 */
class RecordingViewModel(private val repository: HikeRepository) : ViewModel() {

    private val _boundService = MutableStateFlow<LocationTrackingService?>(null)

    /** Le composable RecordingScreen collecte `boundService.value?.state` une fois non-null. */
    val boundService: StateFlow<LocationTrackingService?> = _boundService.asStateFlow()

    /**
     * Tracé cumulatif de la rando en cours, dérivé en direct de la base (via Room Flow)
     * plutôt que reconstruit à la main, pour rester la source de vérité unique et
     * survivre à un re-rattachement de l'écran au service déjà en cours d'enregistrement.
     */
    val trackPoints: StateFlow<List<TrackPoint>> = _boundService
        .flatMapLatest { service -> service?.state?.map { it.hikeId } ?: flowOf(null) }
        .flatMapLatest { hikeId ->
            if (hikeId != null) repository.getPointsForHikeFlow(hikeId) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onServiceConnected(service: LocationTrackingService) {
        _boundService.value = service
    }

    fun onServiceDisconnected() {
        _boundService.value = null
    }

    fun startHike(defaultName: String) {
        _boundService.value?.startRecording(defaultName)
    }

    fun pauseHike() {
        _boundService.value?.pauseRecording()
    }

    fun resumeHike() {
        _boundService.value?.resumeRecording()
    }

    fun stopHike(onFinished: (hikeId: Long) -> Unit) {
        _boundService.value?.stopRecording(onFinished)
    }
}
