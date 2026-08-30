package fr.pierre.randogpx.ui.map

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import fr.pierre.randogpx.R
import fr.pierre.randogpx.map.OfflineTileManager
import fr.pierre.randogpx.map.OsmdroidConfigurator
import org.osmdroid.views.MapView

/**
 * Écran de sélection d'une zone à télécharger pour un usage hors-ligne.
 *
 * Plutôt que de faire dessiner un rectangle arbitraire à la main (geste délicat à
 * fiabiliser sur une carte qui gère déjà pan/zoom), l'utilisateur cadre directement
 * la zone voulue en déplaçant/zoomant la carte : le rectangle téléchargé est la zone
 * visible (`MapView.boundingBox`), matérialisée par un cadre indicatif à l'écran.
 * C'est l'approche retenue par plusieurs apps de cartes hors-ligne (ex. Organic Maps,
 * OsmAnd "télécharger la zone affichée").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineAreaScreen(onBack: () -> Unit) {
    var minZoom by remember { mutableFloatStateOf(12f) }
    var maxZoom by remember { mutableFloatStateOf(16f) }
    var estimatedTiles by remember { mutableStateOf<Int?>(null) }
    var downloadState by remember { mutableStateOf<DownloadUiState>(DownloadUiState.Idle) }
    val mapViewState = remember { mutableStateOf<MapView?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_offline_area)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_desc_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(OsmdroidConfigurator.openTopoMapSource)
                            setMultiTouchControls(true)
                            controller.setZoom(13.0)
                            mapViewState.value = this
                        }
                    }
                )
                // Cadre purement indicatif : la zone réellement téléchargée est celle
                // visible sur la carte (mapView.boundingBox), pas ce rectangle précis.
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(0.85f)
                        .fillMaxHeight(0.85f)
                        .border(2.dp, MaterialTheme.colorScheme.primary)
                )
            }

            Text(
                stringResource(R.string.offline_area_instructions),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp)
            )

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.offline_area_zoom_min, minZoom.toInt()))
                Slider(
                    value = minZoom,
                    onValueChange = {
                        minZoom = it
                        if (maxZoom < minZoom) maxZoom = minZoom
                        estimatedTiles = null
                    },
                    valueRange = 8f..17f,
                    steps = 8
                )
                Text(stringResource(R.string.offline_area_zoom_max, maxZoom.toInt()))
                Slider(
                    value = maxZoom,
                    onValueChange = {
                        maxZoom = it
                        if (minZoom > maxZoom) minZoom = maxZoom
                        estimatedTiles = null
                    },
                    valueRange = 8f..17f,
                    steps = 8
                )
            }

            estimatedTiles?.let { count ->
                Text(
                    stringResource(R.string.offline_area_tiles_estimate, count),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val mapView = mapViewState.value ?: return@OutlinedButton
                        val manager = OfflineTileManager(mapView)
                        estimatedTiles = manager.estimateTileCount(
                            mapView.boundingBox, minZoom.toInt(), maxZoom.toInt()
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.action_estimate))
                }

                Button(
                    onClick = {
                        val mapView = mapViewState.value ?: return@Button
                        val manager = OfflineTileManager(mapView)
                        downloadState = DownloadUiState.InProgress(0)
                        manager.downloadArea(
                            mapView.boundingBox,
                            minZoom.toInt(),
                            maxZoom.toInt(),
                            object : OfflineTileManager.DownloadCallback {
                                override fun onProgress(current: Int, total: Int) {
                                    downloadState = DownloadUiState.InProgress(current)
                                }
                                override fun onComplete() {
                                    downloadState = DownloadUiState.Done
                                }
                                override fun onError(message: String) {
                                    downloadState = DownloadUiState.Error(message)
                                }
                            }
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.action_download))
                }
            }

            when (val state = downloadState) {
                is DownloadUiState.InProgress -> Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    LinearProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                is DownloadUiState.Done -> Text(
                    stringResource(R.string.offline_area_download_done),
                    modifier = Modifier.padding(16.dp)
                )
                is DownloadUiState.Error -> Text(
                    stringResource(R.string.offline_area_download_error, state.message),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
                DownloadUiState.Idle -> {}
            }
        }
    }
}

private sealed class DownloadUiState {
    data object Idle : DownloadUiState()
    data class InProgress(val progress: Int) : DownloadUiState()
    data object Done : DownloadUiState()
    data class Error(val message: String) : DownloadUiState()
}
