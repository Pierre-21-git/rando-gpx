package fr.pierre.randogpx.location

import android.content.Context

/**
 * Corrige l'altitude GPS (référence ellipsoïde WGS84) en altitude orthométrique
 * (référence géoïde EGM96, "niveau moyen de la mer"), en s'appuyant sur une grille
 * d'ondulation du géoïde chargée localement (aucune requête réseau).
 *
 * La grille embarquée (`assets/egm96/egm96-15.pgm`) est le fichier EGM96 15' au
 * format PGM 16 bits de GeographicLib (BSD, Charles Karney), lui-même dérivé des
 * données publiques NGA WW15MGH — c'est le même fichier/format que celui utilisé par
 * BasicAirData GPS Logger (GPL-3.0, `github.com/BasicAirData/GPSLogger`), référence
 * d'implémentation pour ce module.
 *
 * Le parsing du format et l'interpolation bilinéaire vivent dans [GeoidGrid] (JVM pur,
 * testable sans Android) ; cette classe se contente du chargement depuis les assets.
 */
class Egm96Corrector(private val context: Context) {

    private var grid: GeoidGrid? = null

    fun preload() {
        if (grid != null) return
        runCatching {
            val bytes = context.assets.open(ASSET_PATH).use { it.readBytes() }
            grid = GeoidGrid.parsePgm(bytes)
        }
        // En cas d'échec (fichier absent/corrompu), on reste en mode dégradé silencieusement :
        // correct() retombera sur l'altitude non corrigée (ondulation supposée nulle).
    }

    /**
     * Convertit une altitude ellipsoïdale (brute GPS) en altitude orthométrique
     * (au-dessus du niveau de la mer). altitude_orthometrique = altitude_ellipsoide - N
     * où N est l'ondulation du géoïde au point considéré.
     */
    fun correct(lat: Double, lon: Double, ellipsoidAltitude: Double): Double {
        val n = grid?.undulationAt(lat, lon) ?: 0.0
        return ellipsoidAltitude - n
    }

    companion object {
        private const val ASSET_PATH = "egm96/egm96-15.pgm"
    }
}
