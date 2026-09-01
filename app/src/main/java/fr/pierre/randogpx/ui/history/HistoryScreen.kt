package fr.pierre.randogpx.ui.history

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.pierre.randogpx.R
import fr.pierre.randogpx.RandoApplication
import fr.pierre.randogpx.data.Hike
import fr.pierre.randogpx.data.Massif
import fr.pierre.randogpx.ui.ViewModelFactory
import fr.pierre.randogpx.ui.components.simpleVerticalScrollbar
import fr.pierre.randogpx.ui.theme.ForestGreen
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun HistoryScreen(
    onStartNewHike: () -> Unit,
    onOpenHike: (Long) -> Unit,
    onOpenOfflineAreas: () -> Unit,
    onManageMassifs: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: HistoryViewModel = viewModel(
        factory = ViewModelFactory.from(
            LocalContext.current.applicationContext as RandoApplication
        )
    )
) {
    val context = LocalContext.current
    val hikes by viewModel.hikes.collectAsState()
    val massifs by viewModel.massifs.collectAsState()
    val selectedMassifId by viewModel.selectedMassifId.collectAsState()
    val selectedYear by viewModel.selectedYear.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.importGpx(context, it) }
    }

    LaunchedEffect(Unit) {
        viewModel.importResult.collect { result ->
            val message = when (result) {
                is HistoryViewModel.ImportResult.Success ->
                    context.getString(R.string.import_gpx_success, result.hikeName)
                is HistoryViewModel.ImportResult.Error ->
                    context.getString(R.string.import_gpx_error, result.message)
                HistoryViewModel.ImportResult.EmptyTrack ->
                    context.getString(R.string.import_gpx_empty_track)
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    val years = remember(hikes) { hikes.map { yearOf(it.startTime) }.distinct().sortedDescending() }
    val filteredHikes = remember(hikes, selectedMassifId, selectedYear) {
        hikes.filter { hike ->
            val massifMatches = when (selectedMassifId) {
                null -> true
                NO_MASSIF_FILTER_ID -> hike.massifId == null
                else -> hike.massifId == selectedMassifId
            }
            massifMatches && (selectedYear == null || yearOf(hike.startTime) == selectedYear)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_history)) },
                actions = {
                    OverflowMenu(
                        onImportGpx = { importLauncher.launch("*/*") },
                        onManageMassifs = onManageMassifs,
                        onOpenOfflineAreas = onOpenOfflineAreas,
                        onOpenAbout = onOpenAbout
                    )
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onStartNewHike,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.action_new_hike)) }
            )
        }
    ) { padding ->
        if (hikes.isEmpty()) {
            EmptyHistory(modifier = Modifier.fillMaxSize().padding(padding))
        } else {
            val listState = rememberLazyListState()
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SummaryCard(filteredHikes)
                    FilterRow(
                        massifs = massifs,
                        years = years,
                        selectedMassifId = selectedMassifId,
                        selectedYear = selectedYear,
                        onMassifSelected = { viewModel.selectMassifFilter(it) },
                        onYearSelected = { viewModel.selectYearFilter(it) }
                    )
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .simpleVerticalScrollbar(listState),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredHikes, key = { it.id }) { hike ->
                        HikeCard(
                            hike = hike,
                            massifName = massifs.find { it.id == hike.massifId }?.name,
                            onClick = { onOpenHike(hike.id) }
                        )
                    }
                }
            }
        }
    }
}

/** Regroupe les actions secondaires de l'écran (import, massifs, cartes hors ligne, à propos). */
@Composable
private fun OverflowMenu(
    onImportGpx: () -> Unit,
    onManageMassifs: () -> Unit,
    onOpenOfflineAreas: () -> Unit,
    onOpenAbout: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.content_desc_more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_import_gpx)) },
                leadingIcon = { Icon(Icons.Default.FileUpload, contentDescription = null) },
                onClick = { expanded = false; onImportGpx() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_manage_massifs)) },
                leadingIcon = { Icon(Icons.Default.Layers, contentDescription = null) },
                onClick = { expanded = false; onManageMassifs() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.screen_offline_area)) },
                leadingIcon = { Icon(Icons.Default.Map, contentDescription = null) },
                onClick = { expanded = false; onOpenOfflineAreas() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_about)) },
                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                onClick = { expanded = false; onOpenAbout() }
            )
        }
    }
}

@Composable
private fun FilterRow(
    massifs: List<Massif>,
    years: List<Int>,
    selectedMassifId: Long?,
    selectedYear: Int?,
    onMassifSelected: (Long?) -> Unit,
    onYearSelected: (Int?) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterDropdown(
            label = stringResource(R.string.filter_massif_label),
            selectedLabel = when (selectedMassifId) {
                null -> stringResource(R.string.filter_all_massifs)
                NO_MASSIF_FILTER_ID -> stringResource(R.string.filter_no_massif)
                else -> massifs.find { it.id == selectedMassifId }?.name ?: stringResource(R.string.filter_all_massifs)
            },
            options = listOf(
                null to stringResource(R.string.filter_all_massifs),
                NO_MASSIF_FILTER_ID to stringResource(R.string.filter_no_massif)
            ) + massifs.map { it.id to it.name },
            onSelected = onMassifSelected,
            modifier = Modifier.weight(1f)
        )
        FilterDropdown(
            label = stringResource(R.string.filter_year_label),
            selectedLabel = selectedYear?.toString() ?: stringResource(R.string.filter_all_years),
            options = listOf(null to stringResource(R.string.filter_all_years)) +
                years.map { it to it.toString() },
            onSelected = onYearSelected,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun <T> FilterDropdown(
    label: String,
    selectedLabel: String,
    options: List<Pair<T, String>>,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        AssistChip(
            onClick = { expanded = true },
            label = {
                Text(
                    "$label : $selectedLabel",
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, optionLabel) ->
                DropdownMenuItem(
                    text = { Text(optionLabel) },
                    onClick = {
                        onSelected(value)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun EmptyHistory(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Hiking,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.history_empty_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.history_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Bandeau récapitulatif en tête de liste : nombre de randonnées et cumul distance/dénivelé (filtres appliqués). */
@Composable
private fun SummaryCard(hikes: List<Hike>) {
    val totalDistanceKm = hikes.sumOf { it.distanceMeters } / 1000.0
    val totalGain = hikes.sumOf { it.elevationGainMeters }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = ForestGreen)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SummaryItem(pluralStringResource(R.plurals.history_summary_hikes, hikes.size, hikes.size))
            SummaryItem(stringResource(R.string.format_distance_km, totalDistanceKm))
            SummaryItem(stringResource(R.string.format_elevation_gain, totalGain))
        }
    }
}

@Composable
private fun SummaryItem(value: String) {
    Text(
        value,
        color = androidx.compose.ui.graphics.Color.White,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun HikeCard(hike: Hike, massifName: String?, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(hike.name, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatDate(hike.startTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (massifName != null) {
                    Text(
                        " · $massifName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatChip(Icons.Default.Straighten, stringResource(R.string.format_distance_km, hike.distanceMeters / 1000.0))
                StatChip(Icons.AutoMirrored.Filled.TrendingUp, stringResource(R.string.format_elevation_gain, hike.elevationGainMeters))
                StatChip(Icons.Default.Terrain, stringResource(R.string.format_altitude_m, hike.maxAltitudeMeters))
                StatChip(Icons.Default.Schedule, formatDuration(hike))
            }
        }
    }
}

@Composable
private fun StatChip(icon: ImageVector, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(4.dp))
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

private fun formatDuration(hike: Hike): String {
    val end = hike.endTime ?: return "--"
    val millis = end - hike.startTime
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    return if (hours > 0) "${hours}h${minutes.toString().padStart(2, '0')}" else "${minutes}min"
}

private fun formatDate(timestampMillis: Long): String =
    SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(timestampMillis))

private fun yearOf(timestampMillis: Long): Int =
    Calendar.getInstance().apply { timeInMillis = timestampMillis }.get(Calendar.YEAR)

/** Sentinelle du filtre massif pour "Aucun massif", distincte de `null` qui veut dire "Tous" — les id réels démarrent à 1. */
private const val NO_MASSIF_FILTER_ID = -1L
