package com.tonapp.rando.ui.detail

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.tonapp.rando.data.HikeRepository
import com.tonapp.rando.data.TrackPoint

/**
 * Affiche le profil d'élévation (altitude en fonction de la distance cumulée) d'une
 * randonnée, via MPAndroidChart (libre, Apache 2.0).
 */
@Composable
fun ElevationChart(points: List<TrackPoint>, modifier: Modifier = Modifier) {
    if (points.size < 2) return

    val entries = remember(points) { buildElevationEntries(points) }

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
            val dataSet = LineDataSet(entries, "Altitude (m)").apply {
                setDrawCircles(false)
                lineWidth = 2f
                setDrawFilled(true)
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
