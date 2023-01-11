package com.example.endotastic

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.example.endotastic.enums.PointOfInterestType
import java.util.*

@Entity
data class Workout(internal var isActive : Boolean = false) {
    @PrimaryKey(autoGenerate = true)
    internal var id: Int = 0
    private val createdAt: String = Calendar.getInstance().time.toString()
//    var isActive : Boolean = false
//    var currentLatitude : Double? = null
//    var currentLongitude : Double? = null
    @Ignore
    internal var checkpoints: MutableList<PointOfInterest> = mutableListOf()
    @Ignore
    internal var lastVisitedWaypoint: PointOfInterest? = null
    @Ignore
    internal var lastVisitedCheckpoint: PointOfInterest? = null
    @Ignore
    internal var currentWaypoint: PointOfInterest? = null

//    fun getCurrentWaypoint() : PointOfInterest? = checkpoints.firstOrNull { p -> p.type == PointOfInterestType.Waypoint && !p.isVisited }
//    fun removeWaypoint() {
//        val currentWaypoint = getWaypoint()
//        if (currentWaypoint != null) {
//            pointsOfInterest.remove(currentWaypoint)
//        }
//    }
}