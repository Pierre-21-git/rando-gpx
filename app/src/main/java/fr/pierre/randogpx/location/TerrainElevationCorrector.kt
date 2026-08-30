package fr.pierre.randogpx.location

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import fr.pierre.randogpx.location.TerrainTileMath.TileCoordinates
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Corrige l'altitude de points GPS à partir d'un modèle numérique de terrain public
 * (tuiles "Terrarium" hébergées par AWS Open Data, dérivées de SRTM, domaine public,
 * aucune clé d'API requise) plutôt que du capteur GPS du téléphone — bien plus stable
 * pour calculer un dénivelé fiable, au prix d'un besoin ponctuel de réseau pour
 * récupérer les tuiles concernées (mises en cache disque ensuite, réutilisables
 * hors-ligne pour toute randonnée future dans la même zone).
 *
 * Limite connue (mesurée sur une vraie randonnée le long d'une voie ferrée) : le
 * modèle de terrain reflète le relief naturel du sol, pas le nivellement d'une
 * infrastructure construite (voie ferrée, route en remblai, tunnel...). Sur ce type
 * de tracé, la correction reste nettement meilleure que le GPS brut mais pas parfaite.
 */
class TerrainElevationCorrector(private val context: Context) {

    /**
     * Retourne l'altitude terrain (mètres) pour chaque (lat, lon) fourni, dans le même
     * ordre, ou `null` si une tuile nécessaire n'a pas pu être obtenue (pas de réseau
     * et pas déjà en cache) — correction "tout ou rien" pour ne pas mélanger sur une
     * même randonnée des altitudes venant de sources différentes.
     */
    suspend fun correctElevations(points: List<Pair<Double, Double>>): List<Double>? = withContext(Dispatchers.IO) {
        val tileCache = mutableMapOf<TileCoordinates, Bitmap?>()

        val result = ArrayList<Double>(points.size)
        for ((lat, lon) in points) {
            val tile = TerrainTileMath.tileFor(lat, lon, ZOOM)
            val bitmap = tileCache.getOrPut(tile) { loadOrDownloadTile(tile) } ?: return@withContext null
            val pixel = TerrainTileMath.pixelWithinTile(lat, lon, tile)
            val elevation = TerrainTileMath.bilinearInterpolate(pixel.px, pixel.py) { x, y ->
                val argb = bitmap.getPixel(x, y)
                TerrainTileMath.elevationFromRgb(
                    r = (argb shr 16) and 0xFF,
                    g = (argb shr 8) and 0xFF,
                    b = argb and 0xFF
                )
            }
            result.add(elevation)
        }
        result
    }

    private fun loadOrDownloadTile(tile: TileCoordinates): Bitmap? {
        val cacheDir = File(context.getExternalFilesDir(null), "elevation_tiles").apply { mkdirs() }
        val file = File(cacheDir, "z${tile.zoom}_x${tile.x}_y${tile.y}.png")

        if (!file.exists()) {
            val url = "https://s3.amazonaws.com/elevation-tiles-prod/terrarium/${tile.zoom}/${tile.x}/${tile.y}.png"
            val downloaded = runCatching {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 10_000
                }
                val ok = connection.responseCode == HttpURLConnection.HTTP_OK
                if (ok) {
                    connection.inputStream.use { input -> file.outputStream().use { input.copyTo(it) } }
                }
                connection.disconnect()
                ok
            }.getOrDefault(false)

            if (!downloaded) {
                file.delete()
                return null
            }
        }

        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }

    companion object {
        /** ~13-26 m/pixel selon la latitude — proche de la résolution native des données (~30 m). */
        private const val ZOOM = 12
    }
}
