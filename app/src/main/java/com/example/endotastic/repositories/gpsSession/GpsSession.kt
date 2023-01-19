package com.example.endotastic.repositories.gpsSession

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.example.endotastic.repositories.gpsLocation.GpsLocation
import com.google.android.gms.maps.model.Polyline

@Entity(tableName = "gps_session_table")
data class GpsSession(
    @PrimaryKey(autoGenerate = true) var id: Int,
    val name: String,
    val description: String,
    val startedAt: String,
    var onlineSessionId: String? = null
) {
    var endedAt: String? = null

    @Ignore
    var isActive: Boolean = false

    @Ignore
    var checkpoints: MutableList<GpsLocation> = mutableListOf()

    @Ignore
    var lastVisitedWaypoint: GpsLocation? = null

    @Ignore
    var lastVisitedCheckpoint: GpsLocation? = null

    @Ignore
    var currentWaypoint: GpsLocation? = null

    @Ignore
    var polyline: Polyline? = null
}