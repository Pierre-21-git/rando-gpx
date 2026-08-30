package fr.pierre.randogpx.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import fr.pierre.randogpx.MainActivity
import fr.pierre.randogpx.R
import fr.pierre.randogpx.data.AppDatabase
import fr.pierre.randogpx.data.HikeRepository
import fr.pierre.randogpx.data.TrackPoint
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Service au premier plan (Foreground Service) responsable de l'enregistrement GPS.
 * C'est le composant qui garantit que le tracking continue même écran éteint / app
 * en arrière-plan — condition indispensable pour un usage "téléphone rangé en poche".
 *
 * Utilise LocationManager (API Android standard) plutôt que FusedLocationProvider
 * pour rester 100% libre de dépendance Google Play Services (contrainte F-Droid).
 */
class LocationTrackingService : Service() {

    private val binder = LocalBinder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var locationManager: LocationManager
    private lateinit var repository: HikeRepository
    private lateinit var gpsFilter: GpsFilter
    private lateinit var egm96Corrector: Egm96Corrector
    private var barometerHelper: BarometerHelper? = null

    private var currentHikeId: Long? = null
    private var isPaused = false
    private var recordingStartTime: Long = 0L
    private var cumulativeDistanceMeters = 0.0
    private var lastAcceptedLatLon: Pair<Double, Double>? = null
    private var notificationTickerJob: Job? = null

    private val _state = MutableStateFlow(TrackingState())
    val state: StateFlow<TrackingState> = _state

    data class TrackingState(
        val isRecording: Boolean = false,
        val isPaused: Boolean = false,
        val pointCount: Int = 0,
        val lastLocation: Location? = null,
        val hikeId: Long? = null
    )

    inner class LocalBinder : Binder() {
        fun getService(): LocationTrackingService = this@LocationTrackingService
    }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val db = AppDatabase.getInstance(applicationContext)
        repository = HikeRepository(db.hikeDao(), db.trackPointDao(), db.massifDao())
        gpsFilter = GpsFilter()
        egm96Corrector = Egm96Corrector(applicationContext).apply { preload() }
        barometerHelper = BarometerHelper(applicationContext)
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("0 m", "00:00:00"))
        return START_STICKY
    }

    /** Démarre un nouvel enregistrement. [hikeName] est un nom par défaut, éditable ensuite. */
    fun startRecording(hikeName: String) {
        gpsFilter.reset()
        isPaused = false
        recordingStartTime = System.currentTimeMillis()
        cumulativeDistanceMeters = 0.0
        lastAcceptedLatLon = null
        scope.launch {
            val hikeId = repository.createHike(hikeName, System.currentTimeMillis())
            currentHikeId = hikeId
            _state.update { it.copy(isRecording = true, isPaused = false, pointCount = 0, hikeId = hikeId) }
            requestLocationUpdates()
        }

        // La notification doit afficher une durée "temps réel" même sans fix GPS (immeuble,
        // sous-bois, démarrage à l'intérieur...) : on ne peut pas se contenter de la mettre
        // à jour uniquement depuis onLocationChanged, qui peut ne jamais être appelé pendant
        // un moment.
        notificationTickerJob?.cancel()
        notificationTickerJob = scope.launch {
            while (isActive) {
                updateNotification()
                delay(1_000L)
            }
        }
    }

    fun pauseRecording() {
        isPaused = true
        _state.update { it.copy(isPaused = true) }
    }

    fun resumeRecording() {
        isPaused = false
        _state.update { it.copy(isPaused = false) }
    }

    /** Arrête l'enregistrement, calcule les statistiques finales et libère le GPS. */
    fun stopRecording(onFinished: (hikeId: Long) -> Unit) {
        locationManager.removeUpdates(locationListener)
        barometerHelper?.stop()
        notificationTickerJob?.cancel()
        notificationTickerJob = null
        val hikeId = currentHikeId ?: return
        scope.launch {
            repository.finalizeHike(hikeId, System.currentTimeMillis())
            _state.update { TrackingState() }
            currentHikeId = null
            // onFinished déclenche une navigation Compose (NavController), qui exige le
            // thread principal : cette coroutine tourne sur Dispatchers.IO (voir `scope`).
            withContext(Dispatchers.Main) {
                onFinished(hikeId)
            }
        }
    }

    private fun requestLocationUpdates() {
        // Intervalle : 5s / 5m, ajustable. Sur du GPS pur (pas de Fused), on privilégie
        // GPS_PROVIDER explicitement plutôt que NETWORK_PROVIDER pour la précision terrain.
        // Looper explicite obligatoire : cette méthode est appelée depuis une coroutine sur
        // Dispatchers.IO, un thread sans Looper préparé (sinon RuntimeException au premier fix GPS).
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            MIN_UPDATE_INTERVAL_MS,
            MIN_UPDATE_DISTANCE_M,
            locationListener,
            Looper.getMainLooper()
        )
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (isPaused) return
            val hikeId = currentHikeId ?: return

            val smoothedAltitude = gpsFilter.processAltitude(location) ?: location.altitude
            val correctedAltitude = egm96Corrector.correct(location.latitude, location.longitude, smoothedAltitude)

            lastAcceptedLatLon?.let { (lat, lon) ->
                cumulativeDistanceMeters += HikeRepository.haversineDistanceMeters(
                    lat, lon, location.latitude, location.longitude
                )
            }
            lastAcceptedLatLon = location.latitude to location.longitude

            // Démarre/recale le baromètre si disponible, avec la meilleure altitude connue.
            barometerHelper?.let { baro ->
                if (baro.isAvailable) {
                    baro.recalibrate(correctedAltitude)
                }
            }

            val point = TrackPoint(
                hikeId = hikeId,
                lat = location.latitude,
                lon = location.longitude,
                altitudeGps = location.altitude,
                altitudeCorrected = correctedAltitude,
                timestamp = location.time,
                speed = if (location.hasSpeed()) location.speed else null
            )

            scope.launch {
                repository.addPoint(point)
            }

            _state.update {
                it.copy(pointCount = it.pointCount + 1, lastLocation = location)
            }

            updateNotification()
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private fun updateNotification() {
        val distanceText = if (cumulativeDistanceMeters >= 1000) {
            "%.1f km".format(Locale.getDefault(), cumulativeDistanceMeters / 1000.0)
        } else {
            "%.0f m".format(Locale.getDefault(), cumulativeDistanceMeters)
        }
        val elapsedMillis = System.currentTimeMillis() - recordingStartTime
        val notification = buildNotification(distanceText, formatDuration(elapsedMillis))
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d:%02d".format(Locale.getDefault(), hours, minutes, seconds)
    }

    private fun buildNotification(distanceText: String, durationText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, openAppIntent,
            android.app.PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_recording_title))
            .setContentText("$distanceText · $durationText")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "location_tracking_channel"
        private const val NOTIFICATION_ID = 1
        private const val MIN_UPDATE_INTERVAL_MS = 5_000L
        private const val MIN_UPDATE_DISTANCE_M = 5f
    }
}
