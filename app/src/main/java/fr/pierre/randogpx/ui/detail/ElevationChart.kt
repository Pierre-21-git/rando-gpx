package fr.pierre.randogpx.ui.detail

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import fr.pierre.randogpx.data.HikeRepository
import fr.pierre.randogpx.data.TrackPoint

/**
 * Affiche le profil d'élévation (altitude en fonction de la distance cumulée) d'une
 * randonnée, via MPAndroidChart (libre, Apache 2.0).
 */
@Composable
fun ElevationChart(points: List<TrackPoint>, modifier: Modifier = Modifier) {
    if (points.size < 2) return

    val entries = remember(points) { buildElevationEntries(points) }
    // Seuls les points le plus bas et le plus haut sont annotés : un libellé par point
    // s'empile illisiblement autour des sommets dès que le tracé est un peu dense.
    val labeledEntries = remember(entries) {
        setOfNotNull(entries.minByOrNull { it.y }, entries.maxByOrNull { it.y })
    }
    // MPAndroidChart est une vue Android classique, pas liée au thème Material3 : sans ça,
    // ses libellés d'axes gardent leur couleur par défaut (noir), invisibles sur fond sombre
    // en mode nuit puisque le graphique lui-même n'a pas de fond opaque.
    val labelColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

    AndroidView(
        modifier = modifier.fillMaxWidth().height(220.dp),
        factory = { context ->
            LineChart(context).apply {
                description.isEnabled = false
                xAxis.position = XAxis.XAxisPosition.BOTTOM
                axisRight.isEnabled = false
                legend.isEnabled = false
                setTouchEnabled(true)
                setPinchZoom(true)
            }
        },
        update = { chart ->
            chart.xAxis.textColor = labelColor
            chart.xAxis.gridColor = gridColor
            chart.axisLeft.textColor = labelColor
            chart.axisLeft.gridColor = gridColor
            // Par défaut MPAndroidChart n'affiche plus aucune valeur au-delà de 100 points ;
            // comme on n'en annote que deux, on lève cette limite pour toujours les montrer.
            chart.setMaxVisibleValueCount(Int.MAX_VALUE)
            val dataSet = LineDataSet(entries, "Altitude (m)").apply {
                setDrawCircles(false)
                lineWidth = 2f
                setDrawFilled(true)
                valueTextColor = labelColor
                valueTextSize = 11f
                valueFormatter = object : ValueFormatter() {
                    override fun getPointLabel(entry: Entry?): String =
                        if (entry != null && entry in labeledEntries) "%.0f m".format(entry.y) else ""
                }
            }
            chart.data = LineData(dataSet)
            chart.invalidate()
        }
    )
}

private fun buildElevationEntries(points: List<TrackPoint>): List<Entry> {
    var cumulativeDistance = 0.0
    val entries = mutableListOf<Entry>()

    entries.add(
        Entry(0f, (points.first().altitudeCorrected ?: points.first().altitudeGps).toFloat())
    )

    for (i in 1 until points.size) {
        val prev = points[i - 1]
        val curr = points[i]
        cumulativeDistance += HikeRepository.haversineDistanceMeters(prev.lat, prev.lon, curr.lat, curr.lon)
        val altitude = curr.altitudeCorrected ?: curr.altitudeGps
        entries.add(Entry((cumulativeDistance / 1000.0).toFloat(), altitude.toFloat()))
    }

    return entries
}
