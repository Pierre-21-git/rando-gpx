package fr.pierre.randogpx.gpx

import android.content.Context
import androidx.core.content.FileProvider
import fr.pierre.randogpx.data.Hike
import fr.pierre.randogpx.data.TrackPoint
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Génère un fichier GPX 1.1 standard à partir d'une randonnée et de ses points.
 * Utilise l'altitude corrigée (GPS filtré + EGM96) quand disponible, sinon l'altitude
 * GPS brute en fallback.
 */
object GpxExporter {

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /** Nom de fichier standard `<nom-normalisé>_<startTime>.gpx`, réutilisé pour un export unique ou groupé. */
    fun fileName(hike: Hike): String {
        val safeName = hike.name.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return "${safeName}_${hike.startTime}.gpx"
    }

    fun export(context: Context, hike: Hike, points: List<TrackPoint>, massifName: String? = null): File {
        val xml = buildGpxXml(hike, points, massifName)

        val exportDir = File(context.getExternalFilesDir(null), "gpx").apply { mkdirs() }
        val file = File(exportDir, fileName(hike))
        file.writeText(xml)
        return file
    }

    /** Retourne une Uri partageable (via FileProvider) prête pour un Intent.ACTION_SEND. */
    fun getShareableUri(context: Context, file: File) =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /**
     * Visibilité `internal` (plutôt que `private`) pour rester testable en JUnit sans Context Android.
     * [massifName] est porté par une extension GPX non-standard (`randogpx:massif`, dans un
     * namespace dédié) : le fichier reste un GPX 1.1 valide pour tout autre logiciel, qui
     * ignorera simplement ce champ qu'il ne connaît pas.
     */
    internal fun buildGpxXml(hike: Hike, points: List<TrackPoint>, massifName: String? = null): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""").append('\n')
        sb.append(
            """<gpx version="1.1" creator="RandoGPX" xmlns="http://www.topografix.com/GPX/1/1" """ +
                """xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" """ +
                """xmlns:randogpx="https://github.com/Pierre-21-git/rando-gpx" """ +
                """xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd">"""
        ).append('\n')

        sb.append("  <metadata>\n")
        sb.append("    <name>${escapeXml(hike.name)}</name>\n")
        sb.append("    <time>${isoFormat.format(java.util.Date(hike.startTime))}</time>\n")
        sb.append("  </metadata>\n")

        sb.append("  <trk>\n")
        sb.append("    <name>${escapeXml(hike.name)}</name>\n")
        if (massifName != null) {
            sb.append("    <extensions>\n")
            sb.append("      <randogpx:massif>${escapeXml(massifName)}</randogpx:massif>\n")
            sb.append("    </extensions>\n")
        }
        sb.append("    <trkseg>\n")

        for (point in points) {
            val altitude = point.altitudeCorrected ?: point.altitudeGps
            sb.append("      <trkpt lat=\"${point.lat}\" lon=\"${point.lon}\">\n")
            sb.append("        <ele>${"%.1f".format(Locale.US, altitude)}</ele>\n")
            sb.append("        <time>${isoFormat.format(java.util.Date(point.timestamp))}</time>\n")
            point.speed?.let {
                sb.append("        <extensions>\n")
                sb.append("          <randogpx:speed>${"%.2f".format(Locale.US, it)}</randogpx:speed>\n")
                sb.append("        </extensions>\n")
            }
            sb.append("      </trkpt>\n")
        }

        sb.append("    </trkseg>\n")
        sb.append("  </trk>\n")
        sb.append("</gpx>\n")

        return sb.toString()
    }

    private fun escapeXml(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
