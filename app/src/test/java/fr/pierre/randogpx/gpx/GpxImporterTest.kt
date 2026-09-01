package fr.pierre.randogpx.gpx

import fr.pierre.randogpx.data.Hike
import fr.pierre.randogpx.data.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GpxImporterTest {

    private val sampleXml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <gpx version="1.1" creator="RandoGPX">
          <trk>
            <name>Randonnée du Mont-Blanc</name>
            <trkseg>
              <trkpt lat="45.8326" lon="6.8652">
                <ele>1035.2</ele>
                <time>2026-08-28T10:06:40Z</time>
              </trkpt>
              <trkpt lat="45.8330" lon="6.8660">
                <ele>1040.0</ele>
                <time>2026-08-28T11:06:40Z</time>
              </trkpt>
            </trkseg>
          </trk>
        </gpx>
    """.trimIndent()

    @Test
    fun `parses track name and points`() {
        val track = GpxImporter.parse(sampleXml)

        assertEquals("Randonnée du Mont-Blanc", track.name)
        assertEquals(2, track.points.size)
        assertEquals(45.8326, track.points[0].lat, 1e-9)
        assertEquals(6.8652, track.points[0].lon, 1e-9)
        assertEquals(1035.2, track.points[0].elevation!!, 1e-9)
    }

    @Test
    fun `parses ISO-8601 timestamps to epoch millis`() {
        val track = GpxImporter.parse(sampleXml)

        assertEquals(1_787_911_600_000L, track.points[0].timestampMillis)
        assertEquals(1_787_915_200_000L, track.points[1].timestampMillis)
    }

    @Test
    fun `missing elevation and time are null rather than crashing`() {
        val xml = """
            <gpx><trk><trkseg>
              <trkpt lat="45.0" lon="6.0"></trkpt>
            </trkseg></trk></gpx>
        """.trimIndent()

        val track = GpxImporter.parse(xml)

        assertNull(track.points[0].elevation)
        assertNull(track.points[0].timestampMillis)
        assertNull(track.name)
    }

    @Test
    fun `round-trips through GpxExporter output`() {
        val hike = Hike(id = 1L, name = "Randonnée test", startTime = 1_724_830_000_000L, endTime = 1_724_833_600_000L)
        val points = listOf(
            TrackPoint(hikeId = 1L, lat = 45.8326, lon = 6.8652, altitudeGps = 1035.2, altitudeCorrected = 987.4, timestamp = 1_724_830_000_000L),
            TrackPoint(hikeId = 1L, lat = 45.8330, lon = 6.8660, altitudeGps = 1040.0, altitudeCorrected = 992.1, timestamp = 1_724_830_005_000L)
        )

        val xml = GpxExporter.buildGpxXml(hike, points, massifName = "Jura")
        val reimported = GpxImporter.parse(xml)

        assertEquals("Randonnée test", reimported.name)
        assertEquals("Jura", reimported.massifName)
        assertEquals(points.size, reimported.points.size)
        assertEquals(987.4, reimported.points[0].elevation!!, 1e-9)
        assertEquals(1_724_830_000_000L, reimported.points[0].timestampMillis)
    }

    @Test
    fun `massif name is null when the extension is absent`() {
        val track = GpxImporter.parse(sampleXml)

        assertNull(track.massifName)
    }
}
