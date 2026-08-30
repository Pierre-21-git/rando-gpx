package fr.pierre.randogpx.location

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * Calculs purs (testables en JUnit, sans dépendance Android) pour localiser un point
 * GPS dans une tuile d'élévation "Terrarium" : schéma de tuiles slippy-map standard
 * z/x/y en 256x256px, élévation encodée dans les canaux RVB. Voir
 * [TerrainElevationCorrector] pour le téléchargement/cache réels des tuiles.
 */
object TerrainTileMath {

    const val TILE_SIZE = 256

    data class TileCoordinates(val zoom: Int, val x: Int, val y: Int)
    data class TilePixel(val px: Double, val py: Double)

    fun tileFor(lat: Double, lon: Double, zoom: Int): TileCoordinates {
        val n = 1 shl zoom
        val latRad = Math.toRadians(lat)
        val x = ((lon + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
        val y = ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n).toInt().coerceIn(0, n - 1)
        return TileCoordinates(zoom, x, y)
    }

    fun pixelWithinTile(lat: Double, lon: Double, tile: TileCoordinates): TilePixel {
        val n = 1 shl tile.zoom
        val latRad = Math.toRadians(lat)
        val fx = (lon + 180.0) / 360.0 * n
        val fy = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n
        return TilePixel((fx - tile.x) * TILE_SIZE, (fy - tile.y) * TILE_SIZE)
    }

    /** Décodage Terrarium standard : élévation en mètres encodée sur les 3 canaux RVB. */
    fun elevationFromRgb(r: Int, g: Int, b: Int): Double =
        (r * 256 + g + b / 256.0) - 32768.0

    /** Interpolation bilinéaire entre les 4 pixels voisins d'une position sous-pixel. */
    fun bilinearInterpolate(px: Double, py: Double, elevationAt: (x: Int, y: Int) -> Double): Double {
        val x0 = floor(px).toInt().coerceIn(0, TILE_SIZE - 1)
        val y0 = floor(py).toInt().coerceIn(0, TILE_SIZE - 1)
        val x1 = (x0 + 1).coerceAtMost(TILE_SIZE - 1)
        val y1 = (y0 + 1).coerceAtMost(TILE_SIZE - 1)
        val fx = px - x0
        val fy = py - y0

        val e00 = elevationAt(x0, y0)
        val e10 = elevationAt(x1, y0)
        val e01 = elevationAt(x0, y1)
        val e11 = elevationAt(x1, y1)

        return e00 * (1 - fx) * (1 - fy) + e10 * fx * (1 - fy) + e01 * (1 - fx) * fy + e11 * fx * fy
    }
}
