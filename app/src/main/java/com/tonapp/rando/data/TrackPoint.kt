package com.tonapp.rando.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Un point GPS individuel d'une randonnée.
 *
 * [altitudeGps] : altitude brute renvoyée par le GPS (référence ellipsoïde WGS84)
 * [altitudeCorrected] : altitude après filtrage du bruit + correction géoïde EGM96
 *   (référence : niveau moyen de la mer). C'est cette valeur qui doit être utilisée
 *   pour l'affichage et l'export GPX une fois disponible ; sinon fallback sur altitudeGps.
 */
@Entity(
    tableName = "track_points",
    foreignKeys = [
        ForeignKey(
            entity = Hike::class,
            parentColumns = ["id"],
            childColumns = ["hikeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("hikeId")]
)
data class TrackPoint(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val hikeId: Long,
    val lat: Double,
    val lon: Double,
    val altitudeGps: Double,
    val altitudeCorrected: Double? = null,
    val timestamp: Long,
    val speed: Float? = null
)
