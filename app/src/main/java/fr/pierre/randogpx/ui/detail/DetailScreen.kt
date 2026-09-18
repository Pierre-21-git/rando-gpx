package fr.pierre.randogpx.ui.detail

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.pierre.randogpx.R
import fr.pierre.randogpx.RandoApplication
import fr.pierre.randogpx.data.Massif
import fr.pierre.randogpx.data.TrackPoint
import fr.pierre.randogpx.gpx.GpxExporter
import fr.pierre.randogpx.map.OsmdroidConfigurator
import fr.pierre.randogpx.ui.ViewModelFactory
import fr.pierre.randogpx.ui.components.simpleVerticalScrollbar
import fr.pierre.randogpx.ui.theme.ForestGreen
import fr.pierre.randogpx.ui.theme.TrackColorArgb
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun DetailScreen(
    hikeId: Long,
    onBack: () -> Unit,
    onManageMassifs: () -> Unit,
    viewModel: DetailViewModel = viewModel(
        factory = ViewModelFactory.from(
            LocalContext.current.applicationContext as RandoApplication
        )
    )
) {
    val context = LocalContext.current
    val hike by viewModel.hike.collectAsState()
    val points by viewModel.points.collectAsState()
    val massifs by viewModel.massifs.collectAsState()

    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(hikeId) { viewModel.load(hikeId) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gpx+xml")
    ) { uri ->
        val currentHike = hike
        if (uri != null && currentHike != null) {
            val massifName = massifs.find { it.id == currentHike.massifId }?.name
            val xml = GpxExporter.buildGpxXml(currentHike, points, massifName)
            context.contentResolver.openOutputStream(uri)?.use { it.write(xml.toByteArray()) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_detail)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_desc_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
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

        if (showRenameDialog) {
            RenameDialog(
                initialName = currentHike.name,
                onDismiss = { showRenameDialog = false },
                onConfirm = {
                    viewModel.renameHike(it)
                    showRenameDialog = false
                }
            )
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text(stringResource(R.string.dialog_delete_hike_title)) },
                text = { Text(stringResource(R.string.dialog_delete_hike_message)) },
                confirmButton = {
                    TextButton(onClick = { viewModel.deleteHike(onBack) }) {
                        Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.action_cancel)) }
                }
            )
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Spacer(Modifier.height(16.dp))
            HeroHeader(
                name = currentHike.name,
                dateText = formatDate(currentHike.startTime),
                massifs = massifs,
                selectedMassifId = currentHike.massifId,
                onRenameClick = { showRenameDialog = true },
                onMassifSelected = { viewModel.setMassif(it) },
                onManageMassifs = onManageMassifs
            )

            val scrollState = rememberScrollStateCompat()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .simpleVerticalScrollbar(scrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(16.dp))
                if (points.size >= 2) {
                    Text(
                        stringResource(R.string.detail_map_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    HikeRouteMap(points = points)
                }

                StatsGrid(
                    distanceKm = currentHike.distanceMeters / 1000.0,
                    elevationGain = currentHike.elevationGainMeters,
                    elevationLoss = currentHike.elevationLossMeters,
                    avgSpeedKmh = currentHike.averageSpeedKmh,
                    movingSpeedKmh = currentHike.movingSpeedKmh,
                    minAltitude = currentHike.minAltitudeMeters,
                    maxAltitude = currentHike.maxAltitudeMeters,
                    durationMillis = currentHike.endTime?.minus(currentHike.startTime)
                )

                if (points.isNotEmpty()) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(stringResource(R.string.detail_elevation_profile_title), style = MaterialTheme.typography.titleMedium)
                        ElevationChart(points = points)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val safeName = currentHike.name.replace(Regex("[^A-Za-z0-9_-]"), "_")
                                exportLauncher.launch("$safeName.gpx")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.action_export_gpx))
                        }
                        Button(
                            onClick = {
                                val massifName = massifs.find { it.id == currentHike.massifId }?.name
                                val file = GpxExporter.export(context, currentHike, points, massifName)
                                val uri = GpxExporter.getShareableUri(context, file)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/gpx+xml"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(
                                    Intent.createChooser(shareIntent, context.getString(R.string.share_gpx_chooser_title))
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.action_share))
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun HeroHeader(
    name: String,
    dateText: String,
    massifs: List<Massif>,
    selectedMassifId: Long?,
    onRenameClick: () -> Unit,
    onMassifSelected: (Long?) -> Unit,
    onManageMassifs: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = ForestGreen)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onRenameClick) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.content_desc_edit_name), tint = Color.White)
                }
            }
            Text(dateText, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
            Spacer(Modifier.height(12.dp))
            MassifSelector(
                massifs = massifs,
                selectedMassifId = selectedMassifId,
                onMassifSelected = onMassifSelected,
                onManageMassifs = onManageMassifs
            )
        }
    }
}

@Composable
private fun MassifSelector(
    massifs: List<Massif>,
    selectedMassifId: Long?,
    onMassifSelected: (Long?) -> Unit,
    onManageMassifs: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = massifs.find { it.id == selectedMassifId }?.name ?: stringResource(R.string.massif_none)

    Box {
        AssistChip(
            onClick = { expanded = true },
            label = { Text(selectedName) },
            leadingIcon = { Icon(Icons.Default.Terrain, contentDescription = null, modifier = Modifier.size(18.dp)) },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = Color.White.copy(alpha = 0.15f),
                labelColor = Color.White,
                leadingIconContentColor = Color.White
            )
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.massif_none)) },
                onClick = { onMassifSelected(null); expanded = false }
            )
            massifs.forEach { massif ->
                DropdownMenuItem(
                    text = { Text(massif.name) },
                    onClick = { onMassifSelected(massif.id); expanded = false }
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_manage_massifs)) },
                onClick = { expanded = false; onManageMassifs() }
            )
        }
    }
}

@Composable
private fun RenameDialog(initialName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_rename_hike_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.hike_name_label)) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

/**
 * Carte statique du parcours complet : tracé + marqueurs départ/arrivée, cadrée
 * automatiquement sur l'étendue des points une fois la carte mesurée.
 */
@Composable
private fun HikeRouteMap(points: List<TrackPoint>) {
    var hasZoomedToRoute by remember(points) { mutableStateOf(false) }

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp)),
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(OsmdroidConfigurator.openTopoMapSource)
                setMultiTouchControls(true)
                overlays.add(OsmdroidConfigurator.copyrightOverlay(ctx))

                val geoPoints = points.map { GeoPoint(it.lat, it.lon) }
                overlays.add(
                    Polyline().apply {
                        outlinePaint.strokeWidth = 8f
                        outlinePaint.color = TrackColorArgb
                        setPoints(geoPoints)
                    }
                )
                overlays.add(Marker(this).apply { position = geoPoints.first() })
                overlays.add(Marker(this).apply { position = geoPoints.last() })
            }
        },
        update = { mapView ->
            if (!hasZoomedToRoute) {
                val geoPoints = points.map { GeoPoint(it.lat, it.lon) }
                mapView.post {
                    mapView.zoomToBoundingBox(
                        org.osmdroid.util.BoundingBox.fromGeoPoints(geoPoints),
                        false,
                        64
                    )
                }
                hasZoomedToRoute = true
            }
        }
    )
}

@Composable
private fun StatsGrid(
    distanceKm: Double,
    elevationGain: Double,
    elevationLoss: Double,
    avgSpeedKmh: Double,
    movingSpeedKmh: Double,
    minAltitude: Double,
    maxAltitude: Double,
    durationMillis: Long?
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(Icons.Default.Straighten, stringResource(R.string.stat_distance), stringResource(R.string.format_distance_km, distanceKm), Modifier.weight(1f))
            StatTile(Icons.Default.Schedule, stringResource(R.string.stat_duration), formatDuration(durationMillis), Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(Icons.Default.Speed, stringResource(R.string.stat_avg_speed), stringResource(R.string.format_speed_kmh, avgSpeedKmh), Modifier.weight(1f))
            StatTile(Icons.Default.Speed, stringResource(R.string.stat_moving_speed), stringResource(R.string.format_speed_kmh, movingSpeedKmh), Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(Icons.AutoMirrored.Filled.TrendingUp, stringResource(R.string.stat_elevation_gain), stringResource(R.string.format_elevation_gain, elevationGain), Modifier.weight(1f))
            StatTile(Icons.AutoMirrored.Filled.TrendingUp, stringResource(R.string.stat_elevation_loss), stringResource(R.string.format_elevation_loss, elevationLoss), Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(Icons.Default.Terrain, stringResource(R.string.stat_min_altitude), stringResource(R.string.format_altitude_m, minAltitude), Modifier.weight(1f))
            StatTile(Icons.Default.Terrain, stringResource(R.string.stat_max_altitude), stringResource(R.string.format_altitude_m, maxAltitude), Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatTile(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatDuration(millis: Long?): String {
    if (millis == null) return "--"
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    return if (hours > 0) "${hours}h${minutes.toString().padStart(2, '0')}" else "${minutes}min"
}

private fun formatDate(timestampMillis: Long): String =
    SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale.getDefault()).format(Date(timestampMillis))

@Composable
private fun rememberScrollStateCompat() = androidx.compose.foundation.rememberScrollState()
