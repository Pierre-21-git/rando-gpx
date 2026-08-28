package com.tonapp.rando.ui.recording

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tonapp.rando.R
import com.tonapp.rando.RandoApplication
import com.tonapp.rando.location.LocationTrackingService
import com.tonapp.rando.map.OsmdroidConfigurator
import com.tonapp.rando.ui.ViewModelFactory
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
            val trackPolyline = remember { Polyline().apply { outlinePaint.strokeWidth = 8f } }
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
                StatColumn(label = "Points", value = trackingState.pointCount.toString())
                StatColumn(
                    label = "État",
                    value = when {
                        !trackingState.isRecording -> "Arrêté"
                        trackingState.isPaused -> "Pause"
                        else -> "Actif"
                    }
                )
            }

            if (!hasLocationPermission) {
                Text(
                    "Autorisation de localisation requise pour enregistrer une randonnée.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // --- Contrôles ---
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!trackingState.isRecording) {
                    Button(
                        onClick = {
                            if (hasLocationPermission) {
                                viewModel.startHike(defaultHikeName())
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
                        onClick = { viewModel.stopHike(onFinished) },
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
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

private fun defaultHikeName(): String =
    "Randonnée du " + SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())

@Composable
private fun stringResourceCompat(id: Int): String =
    androidx.compose.ui.res.stringResource(id)

/** Placeholder utilisé tant que le service n'est pas encore lié (évite un crash de collectAsState). */
private fun MutableStateFlowPlaceholder() =
    kotlinx.coroutines.flow.MutableStateFlow(LocationTrackingService.TrackingState())
