package com.tonapp.rando.data

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
    private val trackPointDao: TrackPointDao
) {

    fun getAllHikes(): Flow<List<Hike>> = hikeDao.getAllHikes()

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
                averageSpeedKmh = stats.averageSpeedKmh(startTime = hike.startTime, endTime = endTime)
            )
        )
    }

    suspend fun deleteHike(hikeId: Long) = hikeDao.deleteHike(hikeId)

    data class HikeStats(
        val distanceMeters: Double,
        val elevationGain: Double,
        val elevationLoss: Double
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

            for (i in 1 until points.size) {
                val prev = points[i - 1]
                val curr = points[i]

                distance += haversineDistanceMeters(prev.lat, prev.lon, curr.lat, curr.lon)

                val altPrev = prev.altitudeCorrected ?: prev.altitudeGps
                val altCurr = curr.altitudeCorrected ?: curr.altitudeGps
                val delta = altCurr - altPrev
                if (delta > 0) gain += delta else loss += -delta
            }

            return HikeStats(distance, gain, loss)
        }

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
