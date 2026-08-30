package fr.pierre.randogpx.location

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoidGridTest {

    /**
     * Grille synthétique 4x3 (pas de 90° en longitude, 90° en latitude), couvrant les
     * lignes lat = 90N / 0 / 90S. Valeurs brutes choisies pour rendre les interpolations
     * vérifiables à la main :
     *   row 0 (90N) : 0  1  2  3
     *   row 1 (0°)  : 10 11 12 13
     *   row 2 (90S) : 20 21 22 23
     */
    private val grid = GeoidGrid.parsePgm(
        buildPgm(
            width = 4,
            height = 3,
            offset = 0,
            scale = 1.0,
            values = intArrayOf(
                0, 1, 2, 3,
                10, 11, 12, 13,
                20, 21, 22, 23
            )
        )
    )

    @Test
    fun `exact grid points are returned without interpolation`() {
        assertEquals(0.0, grid.undulationAt(90.0, 0.0), 1e-9)
        assertEquals(13.0, grid.undulationAt(0.0, 270.0), 1e-9)
        assertEquals(20.0, grid.undulationAt(-90.0, 0.0), 1e-9)
    }

    @Test
    fun `interpolates along longitude only`() {
        // lat=0 (row 1 exacte), lon=45 = mi-chemin entre col0 (10) et col1 (11)
        assertEquals(10.5, grid.undulationAt(0.0, 45.0), 1e-9)
    }

    @Test
    fun `interpolates along latitude only`() {
        // lon=0 (col 0 exacte), lat=45 = mi-chemin entre row0 (90N, val 0) et row1 (0°, val 10)
        assertEquals(5.0, grid.undulationAt(45.0, 0.0), 1e-9)
    }

    @Test
    fun `interpolates bilinearly and wraps longitude across the antimeridian`() {
        // lat=-45 -> mi-chemin entre row1 (0°) et row2 (90S)
        // lon=-45 -> normalisé à 315°, mi-chemin entre col3 (dernière) et col0 (raccord 360°=0°)
        // top    = (13+10)/2 = 11.5   (row1, col3/col0)
        // bottom = (23+20)/2 = 21.5   (row2, col3/col0)
        // résultat = (11.5+21.5)/2 = 16.5
        assertEquals(16.5, grid.undulationAt(-45.0, -45.0), 1e-9)
    }

    @Test
    fun `longitude 360 wraps to the same value as longitude 0`() {
        assertEquals(grid.undulationAt(0.0, 0.0), grid.undulationAt(0.0, 360.0), 1e-9)
    }

    @Test
    fun `offset and scale from the header are applied to raw pixel values`() {
        // Grille EGM96 réelle : Offset -108, Scale 0.003. N(0,0) attendu ≈ 17.16 m
        // (valeur de référence bien connue pour ce point) avec la valeur brute 41720
        // répétée sur toute la grille, pour neutraliser l'effet de l'interpolation.
        val realistic = GeoidGrid.parsePgm(
            buildPgm(
                width = 2, height = 2, offset = -108, scale = 0.003,
                values = intArrayOf(41720, 41720, 41720, 41720)
            )
        )
        assertEquals(17.16, realistic.undulationAt(0.0, 0.0), 1e-6)
    }

    /** Construit un fichier PGM 16 bits minimal au format attendu par [GeoidGrid.parsePgm]. */
    private fun buildPgm(width: Int, height: Int, offset: Int, scale: Double, values: IntArray): ByteArray {
        val header = buildString {
            append("P5\n")
            append("# Offset $offset\n")
            append("# Scale $scale\n")
            append("$width $height\n")
            append("65535\n")
        }.toByteArray(Charsets.US_ASCII)

        val pixels = ByteArray(values.size * 2)
        for (i in values.indices) {
            val v = values[i]
            pixels[i * 2] = ((v shr 8) and 0xFF).toByte()
            pixels[i * 2 + 1] = (v and 0xFF).toByte()
        }

        return header + pixels
    }
}
