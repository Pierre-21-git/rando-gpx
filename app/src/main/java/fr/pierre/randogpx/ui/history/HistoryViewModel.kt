package fr.pierre.randogpx.ui.history

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.pierre.randogpx.data.Hike
import fr.pierre.randogpx.data.HikeRepository
import fr.pierre.randogpx.data.Massif
import fr.pierre.randogpx.data.TrackPoint
import fr.pierre.randogpx.gpx.GpxExporter
import fr.pierre.randogpx.gpx.GpxImporter
import fr.pierre.randogpx.location.GpsFilter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class HistoryViewModel(private val repository: HikeRepository) : ViewModel() {

    val hikes: StateFlow<List<Hike>> = repository.getAllHikes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val massifs: StateFlow<List<Massif>> = repository.getAllMassifs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Conservés dans le ViewModel (et non en `remember` côté composable) pour survivre à
    // un aller-retour de navigation vers le détail d'une randonnée : le ViewModel reste
    // en vie tant que l'écran Historique reste dans la pile de navigation.
    private val _selectedMassifId = MutableStateFlow<Long?>(null)
    val selectedMassifId: StateFlow<Long?> = _selectedMassifId.asStateFlow()

    private val _selectedYear = MutableStateFlow<Int?>(null)
    val selectedYear: StateFlow<Int?> = _selectedYear.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _startDateMillis = MutableStateFlow<Long?>(null)
    val startDateMillis: StateFlow<Long?> = _startDateMillis.asStateFlow()

    private val _endDateMillis = MutableStateFlow<Long?>(null)
    val endDateMillis: StateFlow<Long?> = _endDateMillis.asStateFlow()

    private val _filtersExpanded = MutableStateFlow(false)
    val filtersExpanded: StateFlow<Boolean> = _filtersExpanded.asStateFlow()

    private val _importResult = MutableSharedFlow<ImportSummary>()
    val importResult: SharedFlow<ImportSummary> = _importResult

    private val _exportResult = MutableSharedFlow<ExportResult>()
    val exportResult: SharedFlow<ExportResult> = _exportResult

    fun selectMassifFilter(massifId: Long?) {
        _selectedMassifId.value = massifId
    }

    fun selectYearFilter(year: Int?) {
        _selectedYear.value = year
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectStartDate(millis: Long?) {
        _startDateMillis.value = millis
    }

    fun selectEndDate(millis: Long?) {
        _endDateMillis.value = millis
    }

    fun toggleFiltersExpanded() {
        _filtersExpanded.value = !_filtersExpanded.value
    }

    /** Résultat d'un import (un ou plusieurs fichiers, épars ou dans un zip) : compte simple, pas de détail par fichier. */
    data class ImportSummary(val successCount: Int, val errorCount: Int)

    sealed class ExportResult {
        data class Success(val count: Int) : ExportResult()
        data class Error(val message: String) : ExportResult()
    }

    fun deleteHike(hikeId: Long) {
        viewModelScope.launch { repository.deleteHike(hikeId) }
    }

    fun deleteHikes(hikeIds: Set<Long>) {
        viewModelScope.launch { repository.deleteHikes(hikeIds) }
    }

    fun assignMassifToHikes(hikeIds: Set<Long>, massifId: Long?) {
        viewModelScope.launch { repository.setMassifForHikes(hikeIds, massifId) }
    }

    /**
     * Importe un ou plusieurs fichiers sélectionnés d'un coup (sélecteur système en mode
     * multi-sélection) : chaque `uri` est soit un `.gpx` direct, soit un `.zip` en contenant
     * plusieurs (détecté par sa signature binaire, pas par son extension — plus fiable via SAF).
     */
    fun importMultiple(context: Context, uris: List<Uri>) {
        viewModelScope.launch {
            var successCount = 0
            var errorCount = 0
            for (uri in uris) {
                try {
                    if (isZipFile(context, uri)) {
                        val (s, e) = importZip(context, uri)
                        successCount += s
                        errorCount += e
                    } else {
                        val track = GpxImporter.importFromUri(context, uri)
                        if (importParsedTrack(track)) successCount++ else errorCount++
                    }
                } catch (e: Exception) {
                    errorCount++
                }
            }
            _importResult.emit(ImportSummary(successCount, errorCount))
        }
    }

    private suspend fun importZip(context: Context, uri: Uri): Pair<Int, Int> {
        var successCount = 0
        var errorCount = 0
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".gpx", ignoreCase = true)) {
                        try {
                            val xml = zip.readBytes().toString(Charsets.UTF_8)
                            if (importParsedTrack(GpxImporter.parse(xml))) successCount++ else errorCount++
                        } catch (e: Exception) {
                            errorCount++
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        return successCount to errorCount
    }

    private fun isZipFile(context: Context, uri: Uri): Boolean {
        return context.contentResolver.openInputStream(uri)?.use { input ->
            val header = ByteArray(2)
            input.read(header) == 2 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()
        } ?: false
    }

    private suspend fun importParsedTrack(track: GpxImporter.ParsedTrack): Boolean {
        if (track.points.isEmpty()) return false
        val name = track.name ?: "GPX"
        // L'altitude brute d'un GPX importé est aussi bruitée que celle d'un enregistrement
        // live (voire plus, selon l'appareil/l'app d'origine) : on lui applique le même filtre
        // anti-bruit que LocationTrackingService, plutôt que de la sommer telle quelle dans le
        // calcul de dénivelé.
        val elevationFilter = GpsFilter()
        val points = track.points.map {
            val smoothed = it.elevation?.let { ele ->
                elevationFilter.processAltitude(altitude = ele, hasAltitude = true, verticalAccuracyMeters = null)
            }
            TrackPoint(
                hikeId = 0,
                lat = it.lat,
                lon = it.lon,
                altitudeGps = it.elevation ?: 0.0,
                altitudeCorrected = smoothed,
                timestamp = it.timestampMillis ?: 0L
            )
        }
        val startTime = track.points.first().timestampMillis ?: System.currentTimeMillis()
        val endTime = track.points.last().timestampMillis ?: startTime
        val hikeId = repository.importHike(name, startTime, endTime, points)
        track.massifName?.let { massifName ->
            val massifId = repository.getOrCreateMassifByName(massifName)
            repository.setHikeMassif(hikeId, massifId)
        }
        return true
    }

    /** Exporte [hikes] en un `.gpx` par randonnée, créés directement dans le dossier [treeUri] choisi par l'utilisateur. */
    fun exportAllToFolder(context: Context, treeUri: Uri, hikes: List<Hike>, massifs: List<Massif>) {
        viewModelScope.launch {
            try {
                val massifById = massifs.associateBy { it.id }
                val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(
                    treeUri, DocumentsContract.getTreeDocumentId(treeUri)
                )
                var count = 0
                for (hike in hikes) {
                    val points = repository.getPointsForHike(hike.id)
                    val massifName = hike.massifId?.let { massifById[it]?.name }
                    val xml = GpxExporter.buildGpxXml(hike, points, massifName)
                    val fileUri = DocumentsContract.createDocument(
                        context.contentResolver, parentDocUri, "application/gpx+xml", GpxExporter.fileName(hike)
                    ) ?: continue
                    context.contentResolver.openOutputStream(fileUri)?.use { it.write(xml.toByteArray()) }
                    count++
                }
                _exportResult.emit(ExportResult.Success(count))
            } catch (e: Exception) {
                _exportResult.emit(ExportResult.Error(e.message ?: e.toString()))
            }
        }
    }

    /** Exporte [hikes] en un unique fichier `.zip` (un `.gpx` par randonnée en son sein) à l'emplacement [uri] choisi. */
    fun exportAllToZip(context: Context, uri: Uri, hikes: List<Hike>, massifs: List<Massif>) {
        viewModelScope.launch {
            try {
                val massifById = massifs.associateBy { it.id }
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    ZipOutputStream(out).use { zip ->
                        for (hike in hikes) {
                            val points = repository.getPointsForHike(hike.id)
                            val massifName = hike.massifId?.let { massifById[it]?.name }
                            val xml = GpxExporter.buildGpxXml(hike, points, massifName)
                            zip.putNextEntry(ZipEntry(GpxExporter.fileName(hike)))
                            zip.write(xml.toByteArray())
                            zip.closeEntry()
                        }
                    }
                }
                _exportResult.emit(ExportResult.Success(hikes.size))
            } catch (e: Exception) {
                _exportResult.emit(ExportResult.Error(e.message ?: e.toString()))
            }
        }
    }
}
