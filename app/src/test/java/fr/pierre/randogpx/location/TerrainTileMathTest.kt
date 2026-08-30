package fr.pierre.randogpx.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerrainTileMathTest {

    @Test
    fun `elevation from rgb decodes the standard Terrarium reference values`() {
        // R=128,G=0,B=0 encode exactement 0 m (128*256 - 32768 = 0), le point neutre du format.
        assertEquals(0.0, TerrainTileMath.elevationFromRgb(128, 0, 0), 1e-9)
        // Un mètre de plus s'ajoute au canal bleu, pondéré par 1/256.
        assertEquals(1.0 / 256.0, TerrainTileMath.elevationFromRgb(128, 0, 1), 1e-9)
        // R=0,G=0,B=0 correspond à l'altitude minimale représentable (-32768 m).
        assertEquals(-32768.0, TerrainTileMath.elevationFromRgb(0, 0, 0), 1e-9)
    }

    @Test
    fun `tile coordinates increase with longitude and decrease with latitude going north`() {
        val west = TerrainTileMath.tileFor(lat = 47.5, lon = 5.0, zoom = 10)
        val east = TerrainTileMath.tileFor(lat = 47.5, lon = 6.0, zoom = 10)
        assertTrue(east.x > west.x)

        val north = TerrainTileMath.tileFor(lat = 48.0, lon = 5.0, zoom = 10)
        val south = TerrainTileMath.tileFor(lat = 47.0, lon = 5.0, zoom = 10)
        assertTrue(north.y < south.y) // le schéma slippy-map a l'axe Y inversé (0 = pôle nord)
    }

    @Test
    fun `pixel within tile stays inside the 256px bounds for a point inside that tile`() {
        val lat = 47.508
        val lon = 5.093
        val tile = TerrainTileMath.tileFor(lat, lon, zoom = 12)

        val pixel = TerrainTileMath.pixelWithinTile(lat, lon, tile)

        assertTrue(pixel.px in 0.0..TerrainTileMath.TILE_SIZE.toDouble())
        assertTrue(pixel.py in 0.0..TerrainTileMath.TILE_SIZE.toDouble())
    }

    @Test
    fun `bilinear interpolation returns the exact value on a uniform grid`() {
        val result = TerrainTileMath.bilinearInterpolate(px = 10.7, py = 20.3) { _, _ -> 100.0 }
        assertEquals(100.0, result, 1e-9)
    }

    @Test
    fun `bilinear interpolation is the average at the midpoint of two differing neighbours`() {
        // Grille où l'altitude ne dépend que de x : 0 en x=0, 10 en x=1 (et au-delà).
        val result = TerrainTileMath.bilinearInterpolate(px = 0.5, py = 0.0) { x, _ -> if (x == 0) 0.0 else 10.0 }
        assertEquals(5.0, result, 1e-9)
    }
}
