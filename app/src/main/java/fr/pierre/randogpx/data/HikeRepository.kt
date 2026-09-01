package fr.pierre.randogpx.data

import kotlinx.coroutines.flow.Flow
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Point d'entrée unique pour toutes les opérations sur les randonnées et leurs points.
 * Les ViewModels ne doivent jamais accéder aux DAO directement.
 */
class HikeRepository(
    private val hikeDao: HikeDao,
    private val trackPointDao: TrackPointDao,
    private val massifDao: MassifDao
) {

    fun getAllHikes(): Flow<List<Hike>> = hikeDao.getAllHikes()

    fun getAllMassifs(): Flow<List<Massif>> = massifDao.getAllMassifs()

    suspend fun createMassif(name: String): Long = massifDao.insertMassif(Massif(name = name))

    /** Retrouve un massif existant par son nom exact, ou le crée — utilisé par l'import GPX (extension `randogpx:massif`). */
    suspend fun getOrCreateMassifByName(name: String): Long =
        massifDao.findByName(name)?.id ?: massifDao.insertMassif(Massif(name = name))

    suspend fun renameMassif(massifId: Long, name: String) = massifDao.renameMassif(massifId, name)

    /**
     * Refuse de supprimer un massif encore utilisé par au moins une randonnée (renvoie
     * `false`) plutôt que de détacher silencieusement ces randonnées — l'utilisateur doit
     * d'abord les reclasser ou choisir "Aucun massif" lui-même, à la vue de laquelle
     * randonnée est concernée.
     */
    suspend fun deleteMassif(massifId: Long): Boolean {
        if (hikeDao.countHikesUsingMassif(massifId) > 0) return false
        massifDao.deleteMassif(massifId)
        return true
    }

    suspend fun renameHike(hikeId: Long, name: String) {
        val hike = hikeDao.getHikeById(hikeId) ?: return
        hikeDao.updateHike(hike.copy(name = name))
    }

    suspend fun setHikeMassif(hikeId: Long, massifId: Long?) {
        val hike = hikeDao.getHikeById(hikeId) ?: return
        hikeDao.updateHike(hike.copy(massifId = massifId))
    }

    suspend fun getHikeById(hikeId: Long): Hike? = hikeDao.getHikeById(hikeId)

    suspend fun getPointsForHike(hikeId: Long): List<TrackPoint> =
        trackPointDao.getPointsForHike(hikeId)

    /** Flux réactif des points d'une rando, utilisé pour le tracé cumulatif sur la carte live. */
    fun getPointsForHikeFlow(hikeId: Long): Flow<List<TrackPoint>> =
        trackPointDao.getPointsForHikeFlow(hikeId)

    suspend fun createHike(name: String, startTime: Long): Long =
        hikeDao.insertHike(Hike(name = name, startTime = startTime))

    suspend fun addPoint(point: TrackPoint) {
        trackPointDao.insertPoint(point)
    }

    /**
     * Crée une randonnée déjà terminée à partir de points importés (GPX externe) :
     * insère les points puis calcule/persiste directement les statistiques finales,
     * contrairement à [createHike] + [finalizeHike] utilisés pour un enregistrement live.
     */
    suspend fun importHike(name: String, startTime: Long, endTime: Long, points: List<TrackPoint>): Long {
        val hikeId = hikeDao.insertHike(Hike(name = name, startTime = startTime))
        trackPointDao.insertPoints(points.map { it.copy(hikeId = hikeId) })
        finalizeHike(hikeId, endTime)
        return hikeId
    }

    suspend fun getLastPoint(hikeId: Long): TrackPoint? =
        trackPointDao.getLastPointForHike(hikeId)

    /**
     * À appeler au moment du "stop" de l'enregistrement : recalcule les stats finales
     * à partir de l'ensemble des points et les persiste sur l'objet Hike.
     */
    suspend fun finalizeHike(hikeId: Long, endTime: Long) {
        val hike = hikeDao.getHikeById(hikeId) ?: return
        val points = trackPointDao.getPointsForHike(hikeId)
        val stats = computeStats(points)
        hikeDao.updateHike(
            hike.copy(
                endTime = endTime,
                distanceMeters = stats.distanceMeters,
                elevationGainMeters = stats.elevationGain,
                elevationLossMeters = stats.elevationLoss,
                averageSpeedKmh = stats.averageSpeedKmh(startTime = hike.startTime, endTime = endTime),
                maxSpeedKmh = stats.maxSpeedKmh,
                minAltitudeMeters = stats.minAltitudeMeters,
                maxAltitudeMeters = stats.maxAltitudeMeters,
                movingSpeedKmh = stats.movingSpeedKmh
            )
        )
    }

    suspend fun deleteHike(hikeId: Long) = hikeDao.deleteHike(hikeId)

    /**
     * Remplace l'altitude de chaque point par une valeur issue d'un modèle de terrain
     * (voir [fr.pierre.randogpx.location.TerrainElevationCorrector]), plus stable que le
     * capteur GPS pour calculer un dénivelé fiable, puis recalcule les stats de la
     * randonnée à partir de ces nouvelles valeurs. Ne fait rien et renvoie `false` si la
     * correction échoue (typiquement : pas de réseau et tuile pas déjà en cache) — la
     * randonnée garde alors ses valeurs GPS d'origine, jamais d'état à moitié corrigé.
     */
    suspend fun applyTerrainElevationCorrection(
        hikeId: Long,
        correctElevations: suspend (List<Pair<Double, Double>>) -> List<Double>?
    ): Boolean {
        val hike = hikeDao.getHikeById(hikeId) ?: return false
        val points = trackPointDao.getPointsForHike(hikeId)
        if (points.isEmpty()) return false

        val corrected = correctElevations(points.map { it.lat to it.lon }) ?: return false

        points.zip(corrected).forEach { (point, altitude) ->
            trackPointDao.updateAltitudeCorrected(point.id, altitude)
        }

        hike.endTime?.let { finalizeHike(hikeId, it) }
        return true
    }

    /**
     * Recalcule les stats d'une randonnée déjà terminée à partir de ses points actuels.
     * Utile pour rafraîchir les randonnées enregistrées/importées avant l'ajout d'une
     * nouvelle statistique (ex. vitesse de déplacement) : sans ça, elles resteraient
     * bloquées sur la valeur par défaut de la migration au lieu d'être vraiment calculées.
     * Ne touche pas aux randonnées en cours (endTime encore nul).
     */
    suspend fun refreshStatsIfFinished(hikeId: Long) {
        val endTime = hikeDao.getHikeById(hikeId)?.endTime ?: return
        finalizeHike(hikeId, endTime)
    }

    data class HikeStats(
        val distanceMeters: Double,
        val elevationGain: Double,
        val elevationLoss: Double,
        val maxSpeedKmh: Double = 0.0,
        val minAltitudeMeters: Double = 0.0,
        val maxAltitudeMeters: Double = 0.0,
        /** Distance / temps réellement en mouvement, arrêts exclus — voir [computeStats]. */
        val movingSpeedKmh: Double = 0.0
    ) {
        fun averageSpeedKmh(startTime: Long, endTime: Long): Double {
            val durationHours = (endTime - startTime) / 3_600_000.0
            if (durationHours <= 0) return 0.0
            return (distanceMeters / 1000.0) / durationHours
        }
    }

    companion object {

        /**
         * Calcule distance totale et dénivelé cumulé à partir d'une liste de points
         * déjà filtrés/corrigés (voir location/GpsFilter.kt et location/Egm96Corrector.kt).
         */
        fun computeStats(points: List<TrackPoint>): HikeStats {
            if (points.size < 2) {
                return HikeStats(0.0, 0.0, 0.0)
            }

            var distance = 0.0
            var gain = 0.0
            var loss = 0.0
            var maxSpeedMs = 0.0
            var movingTimeMillis = 0L
            var movingDistance = 0.0
            var minAltitude = points.first().altitudeCorrected ?: points.first().altitudeGps
            var maxAltitude = minAltitude

            for (point in points) {
                point.speed?.let { if (it > maxSpeedMs) maxSpeedMs = it.toDouble() }
                val altitude = point.altitudeCorrected ?: point.altitudeGps
                if (altitude < minAltitude) minAltitude = altitude
                if (altitude > maxAltitude) maxAltitude = altitude
            }

            for (i in 1 until points.size) {
                val prev = points[i - 1]
                val curr = points[i]

                val segmentDistance = haversineDistanceMeters(prev.lat, prev.lon, curr.lat, curr.lon)
                distance += segmentDistance

                val altPrev = prev.altitudeCorrected ?: prev.altitudeGps
                val altCurr = curr.altitudeCorrected ?: curr.altitudeGps
                val delta = altCurr - altPrev
                if (delta > 0) gain += delta else loss += -delta

                // Vitesse de déplacement : on exclut les arrêts (pause explicite ou halte sans
                // mise en pause) en ne comptant, dans le temps "en mouvement", que les segments
                // où la vitesse dépasse un seuil de marche minimal — sous ce seuil, l'écart
                // constaté est soit un arrêt réel, soit du bruit GPS, jamais de la progression.
                // Important : la distance associée à ces segments lents doit elle aussi être
                // exclue du calcul, sans quoi on diviserait la distance TOTALE (marche lente
                // incluse) par le seul temps "rapide", ce qui gonfle artificiellement le résultat.
                val segmentTimeMillis = curr.timestamp - prev.timestamp
                if (segmentTimeMillis > 0) {
                    val segmentSpeedMs = segmentDistance / (segmentTimeMillis / 1000.0)
                    if (segmentSpeedMs >= MOVING_SPEED_THRESHOLD_MS) {
                        movingTimeMillis += segmentTimeMillis
                        movingDistance += segmentDistance
                    }
                    // Filet de sécurité pour la vitesse max : un GPX importé n'a en général pas
                    // de vitesse instantanée par point (extension non standard), seule la vitesse
                    // dérivée distance/temps consécutifs est alors disponible. On l'ignore sous
                    // MIN_SEGMENT_MS : sur un intervalle aussi court, quelques mètres de bruit GPS
                    // (précision civile ~3-5 m) suffisent à simuler une pointe de vitesse
                    // irréaliste (ex. 6-7 m parcourus en 2 s -> plus de 12 km/h en "marche").
                    if (segmentTimeMillis >= MIN_SEGMENT_MS_FOR_MAX_SPEED && segmentSpeedMs > maxSpeedMs) {
                        maxSpeedMs = segmentSpeedMs
                    }
                }
            }

            val movingSpeedKmh = if (movingTimeMillis > 0) {
                (movingDistance / 1000.0) / (movingTimeMillis / 3_600_000.0)
            } else {
                0.0
            }

            return HikeStats(
                distanceMeters = distance,
                elevationGain = gain,
                elevationLoss = loss,
                maxSpeedKmh = maxSpeedMs * 3.6,
                minAltitudeMeters = minAltitude,
                maxAltitudeMeters = maxAltitude,
                movingSpeedKmh = movingSpeedKmh
            )
        }

        /** En dessous de 0,5 m/s (~1,8 km/h), on considère qu'il n'y a pas de progression réelle. */
        private const val MOVING_SPEED_THRESHOLD_MS = 0.5

        /** Durée minimale d'un segment pour être candidat à la vitesse max dérivée (voir plus haut). */
        private const val MIN_SEGMENT_MS_FOR_MAX_SPEED = 5_000L

        /** Distance orthodromique entre deux points GPS (formule de Haversine), en mètres. */
        fun haversineDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val earthRadius = 6_371_000.0 // mètres
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return earthRadius * c
        }
    }
}
