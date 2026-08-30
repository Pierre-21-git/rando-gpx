package fr.pierre.randogpx.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Un massif montagneux, purement défini par son nom : sert uniquement à classer les
 * randonnées (pas de FK stricte en base vers [Hike] — voir [HikeRepository.deleteMassif],
 * qui détache manuellement les randonnées concernées avant suppression, pour rester
 * simple sur un usage personnel à faible volume).
 */
@Entity(tableName = "massifs")
data class Massif(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String
)
