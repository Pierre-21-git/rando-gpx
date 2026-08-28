package com.tonapp.rando.location

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
}
