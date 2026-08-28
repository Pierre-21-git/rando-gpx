package com.tonapp.rando.gpx

import com.tonapp.rando.data.Hike
import com.tonapp.rando.data.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.xml.parsers.DocumentBuilderFactory

class GpxExporterTest {

    private val hike = Hike(
        id = 1L,
        name = "Randonnée du 28/08/2026",
        startTime = 1_724_830_000_000L,
        endTime = 1_724_833_600_000L
    )

    private val points = listOf(
        TrackPoint(hikeId = 1L, lat = 45.8326, lon = 6.8652, altitudeGps = 1035.2, altitudeCorrected = 987.4, timestamp = 1_724_830_000_000L, speed = 1.2f),
        TrackPoint(hikeId = 1L, lat = 45.8330, lon = 6.8660, altitudeGps = 1040.0, altitudeCorrected = 992.1, timestamp = 1_724_830_005_000L, speed = null)
    )

    @Test
    fun `generated xml is well-formed`() {
        val xml = GpxExporter.buildGpxXml(hike, points)

        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(xml.byteInputStream(Charsets.UTF_8))

        assertEquals("gpx", doc.documentElement.tagName)
    }

    @Test
    fun `uses corrected altitude when available`() {
        val xml = GpxExporter.buildGpxXml(hike, points)

        assertTrue(xml.contains("<ele>987.4</ele>"))
        assertTrue(xml.contains("<ele>992.1</ele>"))
        assertFalse(xml.contains("<ele>1035.2</ele>"))
    }

    @Test
    fun `falls back to raw gps altitude when no correction is available`() {
        val uncorrected = listOf(
            TrackPoint(hikeId = 1L, lat = 45.0, lon = 6.0, altitudeGps = 1500.5, altitudeCorrected = null, timestamp = 0L)
        )

        val xml = GpxExporter.buildGpxXml(hike, uncorrected)

        assertTrue(xml.contains("<ele>1500.5</ele>"))
    }

    @Test
    fun `speed extension is only emitted when speed is known`() {
        val xml = GpxExporter.buildGpxXml(hike, points)

        assertTrue(xml.contains("<speed>1.20</speed>"))
        // Un seul point a une vitesse connue : une seule balise <speed> attendue.
        assertEquals(1, Regex("<speed>").findAll(xml).count())
    }

    @Test
    fun `hike name is xml-escaped`() {
        val unsafeHike = hike.copy(name = "Rando <spéciale> \"test\" & fils")

        val xml = GpxExporter.buildGpxXml(unsafeHike, points)

        assertFalse(xml.contains("<spéciale>"))
        assertTrue(xml.contains("&lt;spéciale&gt;"))
        assertTrue(xml.contains("&amp;"))
        assertTrue(xml.contains("&quot;test&quot;"))
    }

    @Test
    fun `emits one trkpt per point in order`() {
        val xml = GpxExporter.buildGpxXml(hike, points)

        val trkptCount = Regex("<trkpt ").findAll(xml).count()
        assertEquals(points.size, trkptCount)

        val firstIndex = xml.indexOf("lat=\"45.8326\"")
        val secondIndex = xml.indexOf("lat=\"45.833\"")
        assertTrue(firstIndex in 0 until secondIndex)
    }
}
