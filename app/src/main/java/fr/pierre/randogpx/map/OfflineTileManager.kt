package fr.pierre.randogpx.map

import fr.pierre.randogpx.R
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.TileSourcePolicyException
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView
import org.osmdroid.tileprovider.cachemanager.CacheManager

/**
 * Permet de pré-télécharger les tuiles d'une zone rectangulaire (dessinée par
 * l'utilisateur sur la carte) pour un usage hors ligne. S'appuie sur le CacheManager
 * natif d'osmdroid, qui écrit dans le même cache disque que l'affichage normal —
 * donc rien de plus à faire côté lecture, la carte utilisera automatiquement les
 * tuiles déjà en cache quand il n'y a pas de réseau.
 */
class OfflineTileManager(
    private val mapView: MapView,
    @Suppress("UNUSED_PARAMETER") private val tileSource: ITileSource = OsmdroidConfigurator.openTopoMapSource
) {

    interface DownloadCallback {
        fun onProgress(current: Int, total: Int)
        fun onComplete()
        fun onError(message: String)
    }

    // Le CacheManager s'appuie sur la source de tuiles déjà configurée sur le MapView
    // (voir setTileSource dans RecordingScreen/OfflineAreaScreen) plutôt que d'en
    // reconstruire une : c'est le constructeur à un seul argument de CacheManager.
    private fun newCacheManager(): CacheManager? = runCatching { CacheManager(mapView) }.getOrNull()

    /**
     * Lance le téléchargement des tuiles pour [boundingBox] entre les niveaux de zoom
     * [minZoom] et [maxZoom]. Un zoom trop large (ex: 10 à 17) sur une grande zone peut
     * représenter plusieurs centaines de Mo : prévoir une estimation de taille avant
     * confirmation utilisateur (voir CacheManager.possibleTilesInArea).
     */
    fun downloadArea(
        boundingBox: BoundingBox,
        minZoom: Int,
        maxZoom: Int,
        callback: DownloadCallback
    ) {
        val cacheManager = try {
            CacheManager(mapView)
        } catch (e: TileSourcePolicyException) {
            callback.onError(e.message ?: mapView.context.getString(R.string.offline_area_policy_error))
            return
        }

        cacheManager.downloadAreaAsync(
            mapView.context,
            boundingBox,
            minZoom,
            maxZoom,
            object : CacheManager.CacheManagerCallback {
                override fun onTaskComplete() = callback.onComplete()
                override fun onTaskFailed(errors: Int) = callback.onError(mapView.context.getString(R.string.offline_area_tiles_failed, errors))
                override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) {
                    callback.onProgress(progress, 100)
                }
                override fun downloadStarted() {}
                override fun setPossibleTilesInArea(total: Int) {}
            }
        )
    }

    /** Estimation du nombre de tuiles à télécharger, pour informer l'utilisateur avant confirmation. */
    fun estimateTileCount(boundingBox: BoundingBox, minZoom: Int, maxZoom: Int): Int {
        val cacheManager = newCacheManager() ?: return 0
        return cacheManager.possibleTilesInArea(boundingBox, minZoom, maxZoom)
    }
}
