package com.tonapp.rando.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Utilise le baromètre de l'appareil s'il est disponible, pour affiner les variations
 * relatives d'altitude (plus précis que le GPS sur les petites variations, et sans
 * dépendance réseau). Recalé périodiquement sur l'altitude GPS+EGM96 pour la valeur
 * absolue, car le baromètre ne donne qu'une pression relative.
 *
 * De nombreux téléphones milieu de gamme n'ont pas ce capteur (ex. Moto G84, Honor 400
 * Lite testés pour ce projet) : le code appelant DOIT prévoir le cas [isAvailable] == false
 * et fonctionner uniquement sur le pipeline GPS + [GpsFilter] + [Egm96Corrector] dans ce cas.
 */
class BarometerHelper(context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val pressureSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)

    val isAvailable: Boolean get() = pressureSensor != null

    private var referenceAltitude: Double? = null
    private var lastPressureHpa: Float? = null
    private var onAltitudeChanged: ((Double) -> Unit)? = null

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val pressureHpa = event.values[0]
            lastPressureHpa = pressureHpa
            val ref = referenceAltitude ?: return
            // SensorManager.getAltitude attend la pression au niveau de la mer en hPa
            // et retourne une altitude relative à cette référence.
            val refPressureAtSeaLevel = SensorManager.PRESSURE_STANDARD_ATMOSPHERE *
                Math.pow(1 - (ref / 44330.0), 5.255)
            val altitude = SensorManager.getAltitude(refPressureAtSeaLevel.toFloat(), pressureHpa)
            onAltitudeChanged?.invoke(altitude.toDouble())
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    /**
     * Démarre l'écoute du baromètre. [referenceGpsAltitude] doit être la dernière
     * altitude fiable connue (GPS + EGM96), utilisée comme point de recalage initial.
     */
    fun start(referenceGpsAltitude: Double, onAltitudeChanged: (Double) -> Unit) {
        if (pressureSensor == null) return
        this.referenceAltitude = referenceGpsAltitude
        this.onAltitudeChanged = onAltitudeChanged
        sensorManager.registerListener(listener, pressureSensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    /** Recale la référence (à appeler périodiquement, ex. toutes les 5 min, avec l'altitude GPS+EGM96). */
    fun recalibrate(referenceGpsAltitude: Double) {
        referenceAltitude = referenceGpsAltitude
    }

    fun stop() {
        sensorManager.unregisterListener(listener)
    }
}
