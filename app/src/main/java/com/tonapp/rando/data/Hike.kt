package com.tonapp.rando.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Représente une randonnée enregistrée.
 * Les points GPS associés sont stockés séparément dans [TrackPoint],
 * reliés par [hikeId] pour permettre un chargement paresseux (lazy loading).
 */
@Entity(tableName = "hikes")
data class Hike(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val startTime: Long,
    val endTime: Long? = null,
    // Statistiques pré-calculées à la fin de l'enregistrement, pour éviter
    // de recalculer sur toute la liste de points à chaque affichage de l'historique.
    val distanceMeters: Double = 0.0,
    val elevationGainMeters: Double = 0.0,
    val elevationLossMeters: Double = 0.0,
    val averageSpeedKmh: Double = 0.0
)
