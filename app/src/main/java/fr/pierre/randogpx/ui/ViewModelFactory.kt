package fr.pierre.randogpx.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import fr.pierre.randogpx.RandoApplication
import fr.pierre.randogpx.data.HikeRepository
import fr.pierre.randogpx.ui.detail.DetailViewModel
import fr.pierre.randogpx.ui.history.HistoryViewModel
import fr.pierre.randogpx.ui.massif.MassifViewModel
import fr.pierre.randogpx.ui.recording.RecordingViewModel

/**
 * Factory unique pour tous les ViewModels de l'app. Évite Hilt/Dagger pour limiter
 * les risques de configuration d'annotation-processing dans ce squelette généré
 * sans build de vérification — voir remarque dans RandoApplication.kt.
 */
class ViewModelFactory(private val repository: HikeRepository) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        @Suppress("UNCHECKED_CAST")
        return when {
            modelClass.isAssignableFrom(HistoryViewModel::class.java) ->
                HistoryViewModel(repository) as T
            modelClass.isAssignableFrom(RecordingViewModel::class.java) ->
                RecordingViewModel(repository) as T
            modelClass.isAssignableFrom(DetailViewModel::class.java) ->
                DetailViewModel(repository) as T
            modelClass.isAssignableFrom(MassifViewModel::class.java) ->
                MassifViewModel(repository) as T
            else -> throw IllegalArgumentException("ViewModel inconnu : ${modelClass.name}")
        }
    }

    companion object {
        fun from(application: RandoApplication) = ViewModelFactory(application.repository)
    }
}
