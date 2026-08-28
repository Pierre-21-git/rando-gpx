package com.tonapp.rando.data

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
