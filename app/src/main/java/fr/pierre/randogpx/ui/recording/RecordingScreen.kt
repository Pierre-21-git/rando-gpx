package fr.pierre.randogpx.ui.recording

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.pierre.randogpx.R
import fr.pierre.randogpx.RandoApplication
import fr.pierre.randogpx.location.LocationTrackingService
import fr.pierre.randogpx.map.OsmdroidConfigurator
import fr.pierre.randogpx.ui.ViewModelFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecordingScreen(
    onFinished: (Long) -> Unit,
    viewModel: RecordingViewModel = viewModel(
        factory = ViewModelFactory.from(
            LocalContext.current.applicationContext as RandoApplication
        )
    )
) {
    val context = LocalContext.current
    val boundService by viewModel.boundService.collectAsState()
    val trackingState by (boundService?.state
        ?: remember { MutableStateFlowPlaceholder() }).collectAsState()
    val trackPoints by viewModel.trackPoints.collectAsState()
    var showStopConfirmation by remember { mutableStateOf(false) }

    // Le service de localisation au premier plan (foreground) ne doit être démarré
    // qu'une fois la permission de localisation accordée : sur Android 14+, démarrer
    // un foreground service de type "location" sans la permission déjà accordée lève
    // une SecurityException immédiate (ForegroundServiceStartNotAllowedException).
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasLocationPermission = results[Manifest.permission.ACCESS_FINE_LOCATION] == true
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            val permissions = buildList {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    // Suivi en arrière-plan (écran éteint / app en tâche de fond) : sur Android 10+,
    // c'est une permission distincte qu'il faut demander séparément de la localisation
    // "en cours d'utilisation", après coup et avec une justification claire — la demander
    // en même temps que le reste est refusé silencieusement par le système sur beaucoup
    // d'appareils. Optionnelle : l'app reste utilisable au premier plan sans elle.
    var hasBackgroundLocationPermission by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasBackgroundLocationPermission = granted }

    // Optimisation batterie : sur de nombreux OEM (Xiaomi, Huawei...), le Foreground
    // Service peut être tué malgré tout si l'app n'est pas exemptée. On ne peut que
    // rediriger vers les réglages système (aucune permission spéciale requise pour
    // ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, contrairement à la demande directe).
    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    var isIgnoringBatteryOptimizations by remember {
        mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName))
    }

    // Ces deux réglages ne se demandent pas via un dialogue applicatif classique mais en
    // renvoyant l'utilisateur vers les paramètres système : on ne peut donc connaître le
    // résultat qu'en revérifiant à chaque retour au premier plan de l'écran.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasBackgroundLocationPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED
                isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(context.packageName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Liaison (bind) au Foreground Service, une fois la permission de localisation acquise.
    DisposableEffect(hasLocationPermission) {
        if (!hasLocationPermission) return@DisposableEffect onDispose {}

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val service = (binder as LocationTrackingService.LocalBinder).getService()
                viewModel.onServiceConnected(service)
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                viewModel.onServiceDisconnected()
            }
        }
        val intent = Intent(context, LocationTrackingService::class.java)
        context.startForegroundService(intent)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)

        onDispose {
            context.unbindService(connection)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResourceCompat(R.string.screen_recording)) }) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // --- Carte live avec osmdroid intégré via AndroidView (interop Compose/View) ---
            // Le tracé cumulatif est maintenu par une unique Polyline ajoutée une fois aux
            // overlays, dont les points sont remplacés à chaque recomposition (nouveau point
            // GPS) — remember garantit qu'on réutilise la même instance plutôt que d'empiler
            // des overlays à chaque mise à jour.
            val trackPolyline = remember {
                Polyline().apply {
                    outlinePaint.strokeWidth = 8f
                    outlinePaint.color = fr.pierre.randogpx.ui.theme.TrackColorArgb
                }
            }
            AndroidView(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(OsmdroidConfigurator.openTopoMapSource)
                        setMultiTouchControls(true)
                        controller.setZoom(15.0)
                        overlays.add(trackPolyline)
                    }
                },
                update = { mapView ->
                    if (trackPoints.isNotEmpty()) {
                        trackPolyline.setPoints(trackPoints.map { GeoPoint(it.lat, it.lon) })
                        mapView.invalidate()
                    }
                    trackingState.lastLocation?.let { location ->
                        val point = GeoPoint(location.latitude, location.longitude)
                        mapView.controller.animateTo(point)
                    }
                }
            )

            // --- Stats live ---
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatColumn(label = stringResourceCompat(R.string.recording_stat_points), value = trackingState.pointCount.toString())
                StatColumn(
                    label = stringResourceCompat(R.string.recording_stat_state),
                    value = when {
                        !trackingState.isRecording -> stringResourceCompat(R.string.recording_stat_state_stopped)
                        trackingState.isPaused -> stringResourceCompat(R.string.recording_stat_state_paused)
                        else -> stringResourceCompat(R.string.recording_stat_state_active)
                    }
                )
                StatColumn(
                    label = stringResourceCompat(R.string.recording_stat_speed),
                    value = trackingState.lastLocation
                        ?.takeIf { it.hasSpeed() }
                        ?.let { stringResourceCompat(R.string.format_speed_kmh, it.speed * 3.6f) }
                        ?: "--"
                )
            }

            if (!hasLocationPermission) {
                Text(
                    stringResourceCompat(R.string.recording_location_permission_required),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // --- Conseils optionnels : n'empêchent pas d'enregistrer, mais fiabilisent
            // le suivi écran éteint / longue durée en tâche de fond. ---
            if (hasLocationPermission && !hasBackgroundLocationPermission) {
                AdviceRow(
                    text = stringResourceCompat(R.string.advice_background_location_text),
                    actionLabel = stringResourceCompat(R.string.action_enable),
                    onAction = { backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }
                )
            }
            if (!isIgnoringBatteryOptimizations) {
                AdviceRow(
                    text = stringResourceCompat(R.string.advice_battery_text),
                    actionLabel = stringResourceCompat(R.string.action_settings),
                    onAction = {
                        context.startActivity(
                            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        )
                    }
                )
            }

            // --- Contrôles ---
            val newHikeName = defaultHikeName()
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!trackingState.isRecording) {
                    Button(
                        onClick = {
                            if (hasLocationPermission) {
                                viewModel.startHike(newHikeName)
                            } else {
                                permissionLauncher.launch(
                                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                                )
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResourceCompat(R.string.action_start))
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            if (trackingState.isPaused) viewModel.resumeHike() else viewModel.pauseHike()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (trackingState.isPaused)
                                stringResourceCompat(R.string.action_resume)
                            else stringResourceCompat(R.string.action_pause)
                        )
                    }
                    Button(
                        onClick = { showStopConfirmation = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResourceCompat(R.string.action_stop))
                    }
                }
            }
        }
    }

    if (showStopConfirmation) {
        AlertDialog(
            onDismissRequest = { showStopConfirmation = false },
            title = { Text(stringResourceCompat(R.string.dialog_stop_recording_title)) },
            text = { Text(stringResourceCompat(R.string.dialog_stop_recording_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showStopConfirmation = false
                    viewModel.stopHike(onFinished)
                }) {
                    Text(stringResourceCompat(R.string.action_stop), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showStopConfirmation = false }) {
                    Text(stringResourceCompat(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun AdviceRow(text: String, actionLabel: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun defaultHikeName(): String = stringResourceCompat(
    R.string.default_hike_name_format,
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
)

@Composable
private fun stringResourceCompat(id: Int): String =
    androidx.compose.ui.res.stringResource(id)

@Composable
private fun stringResourceCompat(id: Int, vararg formatArgs: Any): String =
    androidx.compose.ui.res.stringResource(id, *formatArgs)

/** Placeholder utilisé tant que le service n'est pas encore lié (évite un crash de collectAsState). */
private fun MutableStateFlowPlaceholder() =
    kotlinx.coroutines.flow.MutableStateFlow(LocationTrackingService.TrackingState())
