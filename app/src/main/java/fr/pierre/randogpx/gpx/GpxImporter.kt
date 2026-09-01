package fr.pierre.randogpx.gpx

import android.content.Context
import android.net.Uri
import java.time.Instant
import java.time.OffsetDateTime
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * Lit un fichier GPX externe (export d'une autre app) pour l'importer dans l'historique.
 * Utilise `javax.xml.parsers` (standard JVM, disponible aussi bien sur Android qu'en test
 * JUnit pur) plutôt que `android.util.Xml`, pour rester testable sans Robolectric —
 * même logique que [GpxExporter].
 */
object GpxImporter {

    data class ParsedPoint(
        val lat: Double,
        val lon: Double,
        val elevation: Double?,
        val timestampMillis: Long?
    )

    data class ParsedTrack(
        val name: String?,
        /** Nom du massif s'il était porté par l'extension GPX `randogpx:massif` (voir [GpxExporter]), sinon `null`. */
        val massifName: String? = null,
        val points: List<ParsedPoint>
    )

    fun importFromUri(context: Context, uri: Uri): ParsedTrack {
        val xml = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
            ?: throw IllegalArgumentException("Impossible de lire le fichier")
        return parse(xml)
    }

    /** Visibilité `internal` pour rester testable en JUnit sans Context Android. */
    internal fun parse(xml: String): ParsedTrack {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(xml.byteInputStream())

        val trkElement = document.getElementsByTagName("trk").item(0)?.let { it as Element }

        val trackName = trkElement
            ?.getElementsByTagName("name")?.item(0)?.textContent
            ?: document.getElementsByTagName("metadata").item(0)
                ?.let { it as Element }
                ?.getElementsByTagName("name")?.item(0)?.textContent

        val massifName = trkElement
            ?.getElementsByTagName("randogpx:massif")?.item(0)?.textContent
            ?.trim()?.takeIf { it.isNotEmpty() }

        val trkptNodes = document.getElementsByTagName("trkpt")
        val points = (0 until trkptNodes.length).map { i ->
            val node = trkptNodes.item(i) as Element
            val lat = node.getAttribute("lat").toDouble()
            val lon = node.getAttribute("lon").toDouble()
            val elevation = node.getElementsByTagName("ele").item(0)?.textContent?.toDoubleOrNull()
            val timestamp = node.getElementsByTagName("time").item(0)?.textContent?.let(::parseGpxTime)
            ParsedPoint(lat = lat, lon = lon, elevation = elevation, timestampMillis = timestamp)
        }

        return ParsedTrack(
            name = trackName?.trim()?.takeIf { it.isNotEmpty() },
            massifName = massifName,
            points = points
        )
    }

    private fun parseGpxTime(text: String): Long? = try {
        Instant.parse(text).toEpochMilli()
    } catch (e: Exception) {
        try {
            OffsetDateTime.parse(text).toInstant().toEpochMilli()
        } catch (e2: Exception) {
            null
        }
    }
}
