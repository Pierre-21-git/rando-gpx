package fr.pierre.randogpx.location

import kotlin.math.floor

/**
 * Grille d'ondulation du géoïde au format PGM 16 bits de GeographicLib (voir
 * [Egm96Corrector] pour le détail du format et la source des données) et interpolation
 * bilinéaire associée.
 *
 * Séparée d'[Egm96Corrector] (qui gère le chargement depuis les assets Android via
 * [android.content.Context]) pour rester testable en JVM pur (JUnit, sans Robolectric).
 */
class GeoidGrid private constructor(
    private val width: Int,
    private val height: Int,
    private val offset: Double,
    private val scale: Double,
    private val pixels: ByteArray
) {
    private val lonStepDegrees = 360.0 / width
    private val latStepDegrees = 180.0 / (height - 1)

    private fun rawValueAt(row: Int, col: Int): Int {
        val idx = (row * width + col) * 2
        val hi = pixels[idx].toInt() and 0xFF
        val lo = pixels[idx + 1].toInt() and 0xFF
        return (hi shl 8) or lo
    }

    /**
     * Ondulation du géoïde (en mètres) au point donné, par interpolation bilinéaire.
     * La longitude est normalisée sur [0, 360) et la dernière colonne de la grille
     * raccorde à la première (méridien 360° = 0°, pas de coupure à l'antiméridien).
     */
    fun undulationAt(lat: Double, lon: Double): Double {
        val normLon = ((lon % 360.0) + 360.0) % 360.0
        val row = ((90.0 - lat) / latStepDegrees).coerceIn(0.0, (height - 1).toDouble())
        val col = normLon / lonStepDegrees

        val r0 = floor(row).toInt().coerceIn(0, height - 2)
        val c0 = floor(col).toInt().coerceIn(0, width - 1)
        val c1 = (c0 + 1) % width
        val fr = row - r0
        val fc = col - c0

        fun value(r: Int, c: Int): Double = offset + scale * rawValueAt(r, c)

        val top = value(r0, c0) * (1 - fc) + value(r0, c1) * fc
        val bottom = value(r0 + 1, c0) * (1 - fc) + value(r0 + 1, c1) * fc
        return top * (1 - fr) + bottom * fr
    }

    companion object {
        private const val NEWLINE: Byte = '\n'.code.toByte()

        /**
         * Parse un fichier au format PGM 16 bits GeographicLib::Geoid : en-tête ASCII
         * (magic "P5", commentaires `# Offset` / `# Scale`, dimensions, maxval=65535)
         * suivi des valeurs en pixels 16 bits big-endian ; ondulation réelle (m) =
         * Offset + Scale * valeur_pixel.
         */
        fun parsePgm(bytes: ByteArray): GeoidGrid {
            var pos = 0
            fun readLine(): String {
                val start = pos
                while (bytes[pos] != NEWLINE) pos++
                val line = String(bytes, start, pos - start, Charsets.US_ASCII)
                pos++
                return line
            }

            val magic = readLine().trim()
            check(magic == "P5") { "Format PGM inattendu pour la grille EGM96 : $magic" }

            var parsedOffset = 0.0
            var parsedScale = 1.0
            var line = readLine()
            while (line.startsWith("#")) {
                val comment = line.removePrefix("#").trim()
                when {
                    comment.startsWith("Offset") -> parsedOffset = comment.substringAfter("Offset").trim().toDouble()
                    comment.startsWith("Scale") -> parsedScale = comment.substringAfter("Scale").trim().toDouble()
                }
                line = readLine()
            }

            val dims = line.trim().split(Regex("\\s+"))
            val w = dims[0].toInt()
            val h = dims[1].toInt()
            val maxVal = readLine().trim().toInt()
            check(maxVal == 65535) { "Grille EGM96 : profondeur de pixel inattendue ($maxVal)" }

            val pixelBytes = bytes.copyOfRange(pos, pos + w * h * 2)
            return GeoidGrid(w, h, parsedOffset, parsedScale, pixelBytes)
        }
    }
}
