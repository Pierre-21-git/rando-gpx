package fr.pierre.randogpx.ui.history

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
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
import java.util.TimeZone
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
    val searchQuery by viewModel.searchQuery.collectAsState()
    val startDateMillis by viewModel.startDateMillis.collectAsState()
    val endDateMillis by viewModel.endDateMillis.collectAsState()
    val filtersExpanded by viewModel.filtersExpanded.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showExportFormatChoice by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showBulkDeleteConfirmation by remember { mutableStateOf(false) }
    var showMassifAssignDialog by remember { mutableStateOf(false) }
    val isSelectionMode = selectedIds.isNotEmpty()

    BackHandler(enabled = isSelectionMode) { selectedIds = emptySet() }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.importMultiple(context, uris)
    }
    val exportFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
        treeUri?.let { viewModel.exportAllToFolder(context, it, hikes, massifs) }
    }
    val exportZipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { viewModel.exportAllToZip(context, it, hikes, massifs) }
    }

    LaunchedEffect(Unit) {
        viewModel.importResult.collect { result ->
            val message = context.getString(R.string.import_gpx_bulk_summary, result.successCount, result.errorCount)
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.exportResult.collect { result ->
            val message = when (result) {
                is HistoryViewModel.ExportResult.Success ->
                    context.resources.getQuantityString(R.plurals.export_all_success, result.count, result.count)
                is HistoryViewModel.ExportResult.Error ->
                    context.getString(R.string.export_all_error, result.message)
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    if (showExportFormatChoice) {
        AlertDialog(
            onDismissRequest = { showExportFormatChoice = false },
            title = { Text(stringResource(R.string.dialog_export_all_title)) },
            text = { Text(stringResource(R.string.dialog_export_all_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showExportFormatChoice = false
                    exportZipLauncher.launch(zipFileName())
                }) {
                    Text(stringResource(R.string.action_export_zip))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showExportFormatChoice = false
                    exportFolderLauncher.launch(null)
                }) {
                    Text(stringResource(R.string.action_export_separate_files))
                }
            }
        )
    }

    if (showBulkDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirmation = false },
            title = { Text(stringResource(R.string.dialog_delete_hikes_title)) },
            text = {
                Text(pluralStringResource(R.plurals.dialog_delete_hikes_message, selectedIds.size, selectedIds.size))
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteHikes(selectedIds)
                    selectedIds = emptySet()
                    showBulkDeleteConfirmation = false
                }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteConfirmation = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showMassifAssignDialog) {
        AlertDialog(
            onDismissRequest = { showMassifAssignDialog = false },
            title = { Text(stringResource(R.string.dialog_assign_massif_title)) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            viewModel.assignMassifToHikes(selectedIds, null)
                            selectedIds = emptySet()
                            showMassifAssignDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.massif_none), modifier = Modifier.fillMaxWidth())
                    }
                    massifs.forEach { massif ->
                        TextButton(
                            onClick = {
                                viewModel.assignMassifToHikes(selectedIds, massif.id)
                                selectedIds = emptySet()
                                showMassifAssignDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(massif.name, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMassifAssignDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    val years = remember(hikes) { hikes.map { yearOf(it.startTime) }.distinct().sortedDescending() }
    val filteredHikes = remember(hikes, selectedMassifId, selectedYear, searchQuery, startDateMillis, endDateMillis) {
        hikes.filter { hike ->
            val massifMatches = when (selectedMassifId) {
                null -> true
                NO_MASSIF_FILTER_ID -> hike.massifId == null
                else -> hike.massifId == selectedMassifId
            }
            val yearMatches = selectedYear == null || yearOf(hike.startTime) == selectedYear
            val nameMatches = searchQuery.isBlank() || hike.name.contains(searchQuery, ignoreCase = true)
            val afterStart = startDateMillis == null || hike.startTime >= startDateMillis!!
            val beforeEnd = endDateMillis == null || hike.startTime <= endDateMillis!!
            massifMatches && yearMatches && nameMatches && afterStart && beforeEnd
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text(pluralStringResource(R.plurals.selection_count, selectedIds.size, selectedIds.size)) },
                    navigationIcon = {
                        IconButton(onClick = { selectedIds = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_cancel))
                        }
                    },
                    actions = {
                        IconButton(onClick = { selectedIds = filteredHikes.map { it.id }.toSet() }) {
                            Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.action_select_all))
                        }
                        IconButton(onClick = { showMassifAssignDialog = true }) {
                            Icon(Icons.Default.Terrain, contentDescription = stringResource(R.string.action_assign_massif))
                        }
                        IconButton(onClick = { showBulkDeleteConfirmation = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.screen_history)) },
                    actions = {
                        OverflowMenu(
                            onImportGpx = { importLauncher.launch(arrayOf("*/*")) },
                            onExportAllGpx = { showExportFormatChoice = true },
                            onManageMassifs = onManageMassifs,
                            onOpenOfflineAreas = onOpenOfflineAreas,
                            onOpenAbout = onOpenAbout
                        )
                    }
                )
            }
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                ExtendedFloatingActionButton(
                    onClick = onStartNewHike,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.action_new_hike)) }
                )
            }
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
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterRow(
                            massifs = massifs,
                            years = years,
                            selectedMassifId = selectedMassifId,
                            selectedYear = selectedYear,
                            onMassifSelected = { viewModel.selectMassifFilter(it) },
                            onYearSelected = { viewModel.selectYearFilter(it) },
                            filtersExpanded = filtersExpanded,
                            onToggleFilters = { viewModel.toggleFiltersExpanded() }
                        )
                        if (filtersExpanded) {
                            SearchField(
                                query = searchQuery,
                                onQueryChange = { viewModel.updateSearchQuery(it) }
                            )
                            DateRangeRow(
                                startDateMillis = startDateMillis,
                                endDateMillis = endDateMillis,
                                onStartDateSelected = { viewModel.selectStartDate(it) },
                                onEndDateSelected = { viewModel.selectEndDate(it) }
                            )
                        }
                    }
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
                            isSelected = hike.id in selectedIds,
                            selectionModeActive = isSelectionMode,
                            onClick = {
                                if (isSelectionMode) {
                                    selectedIds = if (hike.id in selectedIds) selectedIds - hike.id else selectedIds + hike.id
                                } else {
                                    onOpenHike(hike.id)
                                }
                            },
                            onLongClick = {
                                selectedIds = if (hike.id in selectedIds) selectedIds - hike.id else selectedIds + hike.id
                            }
                        )
                    }
                }
            }
        }
    }
}

/** Regroupe les actions secondaires de l'écran (import/export, massifs, cartes hors ligne, à propos). */
@Composable
private fun OverflowMenu(
    onImportGpx: () -> Unit,
    onExportAllGpx: () -> Unit,
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
                text = { Text(stringResource(R.string.action_export_all_gpx)) },
                leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                onClick = { expanded = false; onExportAllGpx() }
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
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        placeholder = { Text(stringResource(R.string.filter_search_placeholder), style = MaterialTheme.typography.bodySmall) },
        textStyle = MaterialTheme.typography.bodySmall,
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_cancel), modifier = Modifier.size(16.dp))
                }
            }
        },
        singleLine = true
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeRow(
    startDateMillis: Long?,
    endDateMillis: Long?,
    onStartDateSelected: (Long?) -> Unit,
    onEndDateSelected: (Long?) -> Unit
) {
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(
            onClick = { showStartPicker = true },
            label = {
                Text(
                    startDateMillis?.let { formatDateOnly(it) } ?: stringResource(R.string.filter_start_date_label),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            },
            leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp)) },
            modifier = Modifier.weight(1f)
        )
        AssistChip(
            onClick = { showEndPicker = true },
            label = {
                Text(
                    endDateMillis?.let { formatDateOnly(it) } ?: stringResource(R.string.filter_end_date_label),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            },
            leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp)) },
            modifier = Modifier.weight(1f)
        )
    }

    if (showStartPicker) {
        DateFilterPickerDialog(
            initialMillis = startDateMillis,
            onDismiss = { showStartPicker = false },
            onConfirm = { picked ->
                onStartDateSelected(picked?.let(::startOfDayMillis))
                showStartPicker = false
            },
            onClear = {
                onStartDateSelected(null)
                showStartPicker = false
            }
        )
    }
    if (showEndPicker) {
        DateFilterPickerDialog(
            initialMillis = endDateMillis,
            onDismiss = { showEndPicker = false },
            onConfirm = { picked ->
                onEndDateSelected(picked?.let(::endOfDayMillis))
                showEndPicker = false
            },
            onClear = {
                onEndDateSelected(null)
                showEndPicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateFilterPickerDialog(
    initialMillis: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long?) -> Unit,
    onClear: () -> Unit
) {
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(datePickerState.selectedDateMillis) }) {
                Text(stringResource(R.string.action_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onClear) {
                Text(stringResource(R.string.action_clear_filter))
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

@Composable
private fun FilterRow(
    massifs: List<Massif>,
    years: List<Int>,
    selectedMassifId: Long?,
    selectedYear: Int?,
    onMassifSelected: (Long?) -> Unit,
    onYearSelected: (Int?) -> Unit,
    filtersExpanded: Boolean,
    onToggleFilters: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
        IconButton(onClick = onToggleFilters, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = if (filtersExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = stringResource(if (filtersExpanded) R.string.action_hide_filters else R.string.action_show_filters)
            )
        }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HikeCard(
    hike: Hike,
    massifName: String?,
    isSelected: Boolean,
    selectionModeActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = if (isSelected) {
            CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.elevatedCardColors()
        }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectionModeActive) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(hike.name, style = MaterialTheme.typography.titleMedium)
            }
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

private fun formatDateOnly(timestampMillis: Long): String =
    SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(timestampMillis))

private fun yearOf(timestampMillis: Long): Int =
    Calendar.getInstance().apply { timeInMillis = timestampMillis }.get(Calendar.YEAR)

/**
 * Le sélecteur Material3 renvoie un timestamp à minuit UTC pour le jour choisi ; on le
 * reconvertit vers le début/la fin de ce même jour calendaire dans le fuseau local, pour
 * comparer correctement à `Hike.startTime` (qui suit la même convention que [yearOf]).
 */
private fun startOfDayMillis(pickedUtcMidnightMillis: Long): Long {
    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = pickedUtcMidnightMillis }
    return Calendar.getInstance().apply {
        clear()
        set(utcCal.get(Calendar.YEAR), utcCal.get(Calendar.MONTH), utcCal.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
    }.timeInMillis
}

private fun endOfDayMillis(pickedUtcMidnightMillis: Long): Long {
    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = pickedUtcMidnightMillis }
    return Calendar.getInstance().apply {
        clear()
        set(utcCal.get(Calendar.YEAR), utcCal.get(Calendar.MONTH), utcCal.get(Calendar.DAY_OF_MONTH), 23, 59, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis
}

private fun zipFileName(): String =
    "randogpx_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())}.zip"

/** Sentinelle du filtre massif pour "Aucun massif", distincte de `null` qui veut dire "Tous" — les id réels démarrent à 1. */
private const val NO_MASSIF_FILTER_ID = -1L
