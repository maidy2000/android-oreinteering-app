package com.example.endotastic

import com.google.android.gms.maps.model.Polyline

class Workout {
    var isActive : Boolean = false
    var checkpoints: MutableList<PointOfInterest> = mutableListOf()
    var lastVisitedWaypoint: PointOfInterest? = null
    var lastVisitedCheckpoint: PointOfInterest? = null
    var currentWaypoint: PointOfInterest? = null
    var polyline : Polyline? = null
}