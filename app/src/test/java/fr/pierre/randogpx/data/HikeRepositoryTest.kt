package fr.pierre.randogpx.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HikeRepositoryTest {

    @Test
    fun `haversine distance for one degree of latitude at the equator is about 111 km`() {
        val distance = HikeRepository.haversineDistanceMeters(0.0, 0.0, 1.0, 0.0)
        // Rayon terrestre moyen * 1° en radians ≈ 111 194,9 m
        assertEquals(111_194.9, distance, 1.0)
    }

    @Test
    fun `haversine distance between identical points is zero`() {
        val distance = HikeRepository.haversineDistanceMeters(45.0, 5.0, 45.0, 5.0)
        assertEquals(0.0, distance, 1e-9)
    }

    @Test
    fun `haversine distance is symmetric`() {
        val d1 = HikeRepository.haversineDistanceMeters(48.8566, 2.3522, 45.7640, 4.8357)
        val d2 = HikeRepository.haversineDistanceMeters(45.7640, 4.8357, 48.8566, 2.3522)
        assertEquals(d1, d2, 1e-9)
    }

    @Test
    fun `computeStats returns zero for fewer than two points`() {
        val stats = HikeRepository.computeStats(emptyList())
        assertEquals(0.0, stats.distanceMeters, 0.0)
        assertEquals(0.0, stats.elevationGain, 0.0)
        assertEquals(0.0, stats.elevationLoss, 0.0)
    }

    @Test
    fun `computeStats accumulates elevation gain and loss separately`() {
        val points = listOf(
            point(lat = 45.0, lon = 6.0, altitude = 1000.0, timestamp = 0),
            point(lat = 45.001, lon = 6.0, altitude = 1010.0, timestamp = 1000), // +10
            point(lat = 45.002, lon = 6.0, altitude = 1005.0, timestamp = 2000), // -5
            point(lat = 45.003, lon = 6.0, altitude = 1020.0, timestamp = 3000)  // +15
        )

        val stats = HikeRepository.computeStats(points)

        assertEquals(25.0, stats.elevationGain, 1e-9)
        assertEquals(5.0, stats.elevationLoss, 1e-9)
        assertTrue(stats.distanceMeters > 0.0)
    }

    @Test
    fun `computeStats prefers corrected altitude over raw gps altitude`() {
        val points = listOf(
            point(lat = 45.0, lon = 6.0, altitude = 1000.0, altitudeCorrected = 950.0, timestamp = 0),
            point(lat = 45.001, lon = 6.0, altitude = 1010.0, altitudeCorrected = 970.0, timestamp = 1000)
        )

        val stats = HikeRepository.computeStats(points)

        // Sans correction on aurait +10 (1010-1000) ; avec correction c'est +20 (970-950).
        assertEquals(20.0, stats.elevationGain, 1e-9)
    }

    @Test
    fun `average speed is distance over duration`() {
        val stats = HikeRepository.HikeStats(distanceMeters = 5_000.0, elevationGain = 0.0, elevationLoss = 0.0)
        val speed = stats.averageSpeedKmh(startTime = 0L, endTime = 3_600_000L) // 1 heure
        assertEquals(5.0, speed, 1e-9)
    }

    @Test
    fun `moving speed excludes a stationary segment that average speed would dilute`() {
        val points = listOf(
            // 0 -> 10 min : ~1000 m parcourus (marche normale)
            point(lat = 45.0, lon = 6.0, altitude = 1000.0, timestamp = 0L),
            point(lat = 45.009, lon = 6.0, altitude = 1000.0, timestamp = 600_000L),
            // 10 -> 40 min : pause de 30 min, aucun déplacement
            point(lat = 45.009, lon = 6.0, altitude = 1000.0, timestamp = 2_400_000L),
            // 40 -> 50 min : ~1000 m parcourus à nouveau
            point(lat = 45.018, lon = 6.0, altitude = 1000.0, timestamp = 3_000_000L)
        )

        val stats = HikeRepository.computeStats(points)
        val averageSpeedKmh = stats.averageSpeedKmh(startTime = 0L, endTime = 3_000_000L)

        // Distance totale ~2 km en 20 min de marche effective -> ~6 km/h de déplacement,
        // très différent de la moyenne globale (~2,4 km/h) qui inclut les 30 min de pause.
        assertEquals(6.0, stats.movingSpeedKmh, 0.2)
        assertTrue(stats.movingSpeedKmh > averageSpeedKmh)
    }

    @Test
    fun `moving speed only counts distance from segments actually classified as moving`() {
        val points = listOf(
            // 0 -> 10 s : ~13,9 m -> 5 km/h, un segment "en mouvement".
            point(lat = 45.0, lon = 6.0, altitude = 1000.0, timestamp = 0L),
            point(lat = 45.000125, lon = 6.0, altitude = 1000.0, timestamp = 10_000L),
            // 10 -> 70 s : ~6 m en 60 s -> 0,36 km/h, sous le seuil -> pas "en mouvement".
            // Cette distance ne doit PAS être ajoutée au numérateur de la vitesse de
            // déplacement, sans quoi on diviserait une distance "gonflée" par un temps
            // "réduit" et on surestimerait la vitesse réelle.
            point(lat = 45.0001789, lon = 6.0, altitude = 1000.0, timestamp = 70_000L)
        )

        val stats = HikeRepository.computeStats(points)

        assertEquals(5.0, stats.movingSpeedKmh, 0.2)
    }

    @Test
    fun `max speed ignores a short segment inflated by GPS noise`() {
        val points = listOf(
            // 0 -> 2 s : ~6,7 m parcourus -> ~12 km/h en apparence, mais sur seulement 2 s :
            // c'est de l'ordre de grandeur du bruit GPS (précision civile ~3-5 m), pas une
            // vraie pointe de vitesse -> ce segment doit être ignoré pour la vitesse max.
            point(lat = 45.0, lon = 6.0, altitude = 1000.0, timestamp = 0L),
            point(lat = 45.00006, lon = 6.0, altitude = 1000.0, timestamp = 2_000L),
            // 2 -> 8 s : ~8,3 m parcourus -> ~5 km/h sur 6 s, une vraie vitesse de marche.
            point(lat = 45.0001349, lon = 6.0, altitude = 1000.0, timestamp = 8_000L)
        )

        val stats = HikeRepository.computeStats(points)

        assertEquals(5.0, stats.maxSpeedKmh, 0.3)
    }

    @Test
    fun `average speed is zero for non-positive duration`() {
        val stats = HikeRepository.HikeStats(distanceMeters = 5_000.0, elevationGain = 0.0, elevationLoss = 0.0)
        val speed = stats.averageSpeedKmh(startTime = 1000L, endTime = 1000L)
        assertEquals(0.0, speed, 0.0)
    }

    private fun point(
        lat: Double,
        lon: Double,
        altitude: Double,
        altitudeCorrected: Double? = null,
        timestamp: Long
    ) = TrackPoint(
        hikeId = 1L,
        lat = lat,
        lon = lon,
        altitudeGps = altitude,
        altitudeCorrected = altitudeCorrected,
        timestamp = timestamp
    )
}
