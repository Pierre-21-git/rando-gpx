package fr.pierre.randogpx.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GpsFilterTest {

    private lateinit var filter: GpsFilter

    @Before
    fun setUp() {
        filter = GpsFilter(windowSize = 3, minAltitudeDeltaMeters = 2.5)
    }

    @Test
    fun `returns null when the fix has no altitude`() {
        val result = filter.processAltitude(altitude = 1200.0, hasAltitude = false, verticalAccuracyMeters = null)
        assertNull(result)
    }

    @Test
    fun `first accepted sample is returned as-is`() {
        val result = filter.processAltitude(altitude = 1000.0, hasAltitude = true, verticalAccuracyMeters = null)
        assertEquals(1000.0, result!!, 1e-9)
    }

    @Test
    fun `variation below the noise threshold is ignored and keeps the last accepted value`() {
        filter.processAltitude(altitude = 1000.0, hasAltitude = true, verticalAccuracyMeters = null)

        // Variation de 1m < seuil de 2.5m : doit être considérée comme du bruit.
        val result = filter.processAltitude(altitude = 1001.0, hasAltitude = true, verticalAccuracyMeters = null)

        assertEquals(1000.0, result!!, 1e-9)
    }

    @Test
    fun `variation at or above the noise threshold updates the accepted value`() {
        filter.processAltitude(altitude = 1000.0, hasAltitude = true, verticalAccuracyMeters = null)

        // Fenêtre glissante de taille 3 : après ce point, la moyenne est (1000+1000+1010)/3.
        // On enchaîne des mesures cohérentes pour dépasser clairement le seuil.
        filter.processAltitude(altitude = 1010.0, hasAltitude = true, verticalAccuracyMeters = null)
        val result = filter.processAltitude(altitude = 1010.0, hasAltitude = true, verticalAccuracyMeters = null)

        assertTrue(result!! > 1000.0)
    }

    @Test
    fun `poor vertical accuracy is rejected and keeps the last accepted value`() {
        filter.processAltitude(altitude = 1000.0, hasAltitude = true, verticalAccuracyMeters = null)

        val result = filter.processAltitude(altitude = 1200.0, hasAltitude = true, verticalAccuracyMeters = 30f)

        assertEquals(1000.0, result!!, 1e-9)
    }

    @Test
    fun `good vertical accuracy is accepted`() {
        filter.processAltitude(altitude = 1000.0, hasAltitude = true, verticalAccuracyMeters = null)

        val result = filter.processAltitude(altitude = 1010.0, hasAltitude = true, verticalAccuracyMeters = 5f)

        assertNotNull(result)
    }

    @Test
    fun `reset clears the smoothing window and last accepted altitude`() {
        filter.processAltitude(altitude = 1000.0, hasAltitude = true, verticalAccuracyMeters = null)
        filter.reset()

        // Après reset, la première mesure suivante doit de nouveau être acceptée telle quelle,
        // même si elle est très différente de la dernière valeur pré-reset.
        val result = filter.processAltitude(altitude = 200.0, hasAltitude = true, verticalAccuracyMeters = null)

        assertEquals(200.0, result!!, 1e-9)
    }

    @Test
    fun `complete track smoothing does not lag behind a steady descent`() {
        // Descente régulière de 1500 m à 1100 m, 40 m entre deux points (points espacés).
        val descent = (0..10).map { 1500.0 - 40.0 * it }
        val fiveWide = GpsFilter(windowSize = 5, minAltitudeDeltaMeters = 2.5)

        val smoothed = fiveWide.smoothCompleteTrack(descent)

        // La moyenne centrée suit une pente constante sans retard, extrémités comprises.
        descent.zip(smoothed).forEach { (raw, value) -> assertEquals(raw, value, 1e-9) }

        // Le filtre temps réel, lui, termine 2 points (80 m) trop haut sur la même trace.
        val liveFilter = GpsFilter(windowSize = 5, minAltitudeDeltaMeters = 2.5)
        val live = descent.map {
            liveFilter.processAltitude(altitude = it, hasAltitude = true, verticalAccuracyMeters = null)!!
        }
        assertEquals(1180.0, live.last(), 1e-9)
    }

    @Test
    fun `complete track smoothing averages noise and applies the noise threshold`() {
        val smoothed = filter.smoothCompleteTrack(listOf(1000.0, 1003.0, 1000.0, 1003.0, 1000.0))

        // Fenêtre centrée de 3 : les moyennes intermédiaires (1001 / 1002) restent sous le
        // seuil de 2,5 m par rapport au premier point, l'altitude acceptée ne bouge pas.
        smoothed.forEach { assertEquals(1000.0, it, 1e-9) }
    }

    @Test
    fun `complete track smoothing of an empty track is empty`() {
        assertTrue(filter.smoothCompleteTrack(emptyList()).isEmpty())
    }
}
