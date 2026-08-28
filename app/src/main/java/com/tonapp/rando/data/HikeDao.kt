package com.tonapp.rando.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HikeDao {

    @Insert
    suspend fun insertHike(hike: Hike): Long

    @Update
    suspend fun updateHike(hike: Hike)

    @Query("SELECT * FROM hikes ORDER BY startTime DESC")
    fun getAllHikes(): Flow<List<Hike>>

    @Query("SELECT * FROM hikes WHERE id = :hikeId")
    suspend fun getHikeById(hikeId: Long): Hike?

    @Query("DELETE FROM hikes WHERE id = :hikeId")
    suspend fun deleteHike(hikeId: Long)
}

@Dao
interface TrackPointDao {

    @Insert
    suspend fun insertPoint(point: TrackPoint): Long

    @Insert
    suspend fun insertPoints(points: List<TrackPoint>)

    @Query("SELECT * FROM track_points WHERE hikeId = :hikeId ORDER BY timestamp ASC")
    suspend fun getPointsForHike(hikeId: Long): List<TrackPoint>

    /** Version réactive, utilisée pour maintenir le tracé cumulatif à jour sur la carte live. */
    @Query("SELECT * FROM track_points WHERE hikeId = :hikeId ORDER BY timestamp ASC")
    fun getPointsForHikeFlow(hikeId: Long): Flow<List<TrackPoint>>

    @Query("SELECT * FROM track_points WHERE hikeId = :hikeId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastPointForHike(hikeId: Long): TrackPoint?
}
