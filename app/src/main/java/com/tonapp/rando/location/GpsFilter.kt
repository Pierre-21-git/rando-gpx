package com.tonapp.rando.location

import android.location.Location
import kotlin.math.abs

/**
 * Filtre le bruit des mesures GPS avant qu'elles ne soient utilisées pour calculer
 * le dénivelé. Le GPS grand public a une précision verticale 2-3x moins bonne que
 * l'horizontale : sans filtrage, l'altitude "brute" oscille en permanence même à
 * l'arrêt, ce qui peut doubler le dénivelé cumulé calculé.
 *
 * Deux mécanismes complémentaires :
 *  1. Moyenne glissante sur une fenêtre de N dernières mesures d'altitude
 *  2. Seuil de variation minimale : un changement d'altitude sous ce seuil entre
 *     deux points consécutifs est considéré comme du bruit et ignoré
 */
class GpsFilter(
    private val windowSize: Int = 5,
    private val minAltitudeDeltaMeters: Double = 2.5
) {
    private val altitudeWindow = ArrayDeque<Double>()
    private var lastAcceptedAltitude: Double? = null

    /**
     * À appeler pour chaque nouvelle Location reçue du LocationManager.
     * Retourne l'altitude lissée à utiliser, ou null si la mesure est écartée
     * (précision insuffisante).
     */
    fun processAltitude(location: Location): Double? = processAltitude(
        altitude = location.altitude,
        hasAltitude = location.hasAltitude(),
        // On ignore les mesures dont la précision verticale annoncée est trop mauvaise,
        // si l'info est disponible (VerticalAccuracyMeters, API 26+).
        verticalAccuracyMeters = if (location.hasVerticalAccuracy()) location.verticalAccuracyMeters else null
    )

    /**
     * Cœur du filtrage, indépendant du type Android [Location] pour rester testable en
     * JVM pur (JUnit, sans Robolectric). Voir [processAltitude] (surcharge Location) pour
     * l'usage courant depuis le service de tracking.
     */
    fun processAltitude(altitude: Double, hasAltitude: Boolean, verticalAccuracyMeters: Float?): Double? {
        if (!hasAltitude) return null

        if (verticalAccuracyMeters != null && verticalAccuracyMeters > 25f) {
            return lastAcceptedAltitude
        }

        altitudeWindow.addLast(altitude)
        if (altitudeWindow.size > windowSize) altitudeWindow.removeFirst()
        val smoothed = altitudeWindow.average()

        val last = lastAcceptedAltitude
        return if (last == null || abs(smoothed - last) >= minAltitudeDeltaMeters) {
            lastAcceptedAltitude = smoothed
            smoothed
        } else {
            // Variation sous le seuil de bruit : on garde la dernière valeur acceptée,
            // ce qui évite de cumuler du dénivelé sur du bruit de mesure.
            last
        }
    }

    fun reset() {
        altitudeWindow.clear()
        lastAcceptedAltitude = null
    }
}
