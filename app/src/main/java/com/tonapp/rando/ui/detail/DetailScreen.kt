package com.tonapp.rando.ui.detail

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tonapp.rando.R
import com.tonapp.rando.RandoApplication
import com.tonapp.rando.gpx.GpxExporter
import com.tonapp.rando.ui.ViewModelFactory

@Composable
fun DetailScreen(
    hikeId: Long,
    onBack: () -> Unit,
    viewModel: DetailViewModel = viewModel(
        factory = ViewModelFactory.from(
            LocalContext.current.applicationContext as RandoApplication
        )
    )
) {
    val context = LocalContext.current
    val hike by viewModel.hike.collectAsState()
    val points by viewModel.points.collectAsState()

    LaunchedEffect(hikeId) { viewModel.load(hikeId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResourceCompat(R.string.screen_detail)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    val currentHike = hike
                    if (currentHike != null && points.isNotEmpty()) {
                        IconButton(onClick = {
                            val file = GpxExporter.export(context, currentHike, points)
                            val uri = GpxExporter.getShareableUri(context, file)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/gpx+xml"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Exporter le GPX"))
                        }) {
                            Icon(Icons.Default.Share, contentDescription = stringResourceCompat(R.string.action_export_gpx))
                        }
                    }
                }
            )
        }
    ) { padding ->
        val currentHike = hike
        if (currentHike == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(currentHike.name, style = MaterialTheme.typography.headlineSmall)

            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                StatBlock("Distance", "%.1f km".format(currentHike.distanceMeters / 1000.0))
                StatBlock("Dénivelé +", "%.0f m".format(currentHike.elevationGainMeters))
                StatBlock("Dénivelé -", "%.0f m".format(currentHike.elevationLossMeters))
                StatBlock("Vitesse moy.", "%.1f km/h".format(currentHike.averageSpeedKmh))
            }

            Text("Profil d'élévation", style = MaterialTheme.typography.titleMedium)
            ElevationChart(points = points)
        }
    }
}

@Composable
private fun StatBlock(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun stringResourceCompat(id: Int): String =
    androidx.compose.ui.res.stringResource(id)
