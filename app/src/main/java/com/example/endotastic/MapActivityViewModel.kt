package com.example.endotastic

import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.endotastic.enums.PointOfInterestType
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.crashlytics.buildtools.reloc.com.google.common.base.Stopwatch
import java.util.concurrent.TimeUnit

class MapActivityViewModel : ViewModel() {

    private val workout: Workout = Workout()
    private val stopwatch: Stopwatch = Stopwatch.createUnstarted()

    var totalDistance = MutableLiveData(0)
        private set

    var distanceCoveredFromWaypoint = MutableLiveData(0)
        private set

    var directDistanceFromWaypoint = MutableLiveData(0)
        private set

    var distanceCoveredFromCheckpoint = MutableLiveData(0)
        private set

    var directDistanceFromCheckpoint = MutableLiveData(0)
        private set

    var updatePointsOfInterest = MutableLiveData(false)

    var totalTimeElapsed = MutableLiveData<Long>(0)
        private set

    private var currentLocation: Location? = null

    companion object {
        private val TAG = this::class.java.declaringClass!!.simpleName
    }

    fun startEndWorkout() {
        if (workout.isActive) {
            //end workout
            stopwatch.stop()
            workout.isActive = false
        } else {
            stopwatch.start()
            workout.isActive = true
//            startDrawingPolyline()
            /*
            Tegelikult hakkan siin salvestama gpsi asukohti (MapPoint), et siis nende jargi
            joonistada polyline
             */
        }
    }


    fun isWorkoutActive() = workout.isActive
    fun getPointsOfInterests(): List<PointOfInterest> {
        val allPoints = ArrayList(workout.checkpoints)
        workout.currentWaypoint?.let { allPoints.add(it) }
        workout.lastVisitedWaypoint?.let { allPoints.add(it) }
//        Log.d(TAG, "getPointsOfInterests wop=${workout.checkpoints.count()} returned=${allPoints.count()}")
        return allPoints
    }

    fun getLastVisitedWaypointVisitedAt() = workout.lastVisitedWaypoint?.visitedAt
    fun getLastVisitedCheckpointVisitedAt() = workout.lastVisitedCheckpoint?.visitedAt


    fun locationUpdateIn(lat: Double, lng: Double) {
        updateLocation(lat, lng)
        if (workout.isActive) {
            checkPointsOfInterest()
            updateDistances(lat, lng)
            updateTime()
        }
    }

    private fun checkPointsOfInterest() {
        val visited = mutableListOf<PointOfInterest>()
        val points = workout.checkpoints.toMutableList()
        workout.currentWaypoint?.let { points.add(it) }
        for (point in points) {
            if (currentLocation == null) return
            if (point.isVisited) continue

            val results = FloatArray(1)
            Location.distanceBetween(
                currentLocation!!.latitude,
                currentLocation!!.longitude,
                point.latitude,
                point.longitude,
                results
            )
            val distance = results[0]
            Log.d(TAG, "check POI, distance: $distance")
            if (distance <= C.ACCEPTABLE_POINT_DISTANCE) {
                visited.add(point)
            }
        }

        if (visited.isEmpty()) return

        for (point in visited) {
            point.isVisited = true
            point.visitedAt = stopwatch.elapsed(TimeUnit.SECONDS)
            val results = FloatArray(1)
            Location.distanceBetween(
                currentLocation!!.latitude,
                currentLocation!!.longitude,
                point.latitude,
                point.longitude,
                results
            )
            val distance = results[0]
            point.distanceCoveredFrom = distance.toInt()
            Log.d(TAG, "visited ${point.type}")

            if (point.type == PointOfInterestType.Checkpoint) {
                workout.lastVisitedCheckpoint = point
            } else if (point.type == PointOfInterestType.Waypoint) {
                workout.lastVisitedWaypoint = point
                workout.currentWaypoint = null
            }
            updatePointsOfInterest.value = true
        }
    }

    private fun updateTime() {
        totalTimeElapsed.value = stopwatch.elapsed(TimeUnit.SECONDS)
    }

    private fun updateLocation(lat: Double, lng: Double) {
        //Log.d(TAG, "updateLocation, ${lat} ${lng}")
        Log.d(TAG, "updateLocation")
        val latLng = LatLng(lat, lng)
        var updatedLocation = MapPoint(workout.id, lat, lng, stopwatch.elapsed(TimeUnit.SECONDS))
        // save to db later
//        updateUserLocationMarker(latLng) // activitys
        if (isWorkoutActive()) {
            updateDistances(lat, lng) // liveData
//            updatePaces() // liveData
        }

        currentLocation = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = latLng.latitude
            longitude = latLng.longitude
        }

//        if (isWorkoutActive) {
//            checkPoints()
//        }
//        updateCamera()
    }

    private fun updateDistances(lat: Double, lng: Double) {
        if (!isWorkoutActive()) return
        val updatedLocation = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = lat
            longitude = lng
        }
        if (currentLocation == null) return
        val addedDistance = currentLocation!!.distanceTo(updatedLocation).toInt()
        totalDistance.value = totalDistance.value?.plus(addedDistance)
        //todo other distances

        val lastWaypoint = workout.lastVisitedWaypoint
        if (lastWaypoint != null) {
            val totalDistance = lastWaypoint.distanceCoveredFrom + addedDistance
            lastWaypoint.distanceCoveredFrom = totalDistance
            distanceCoveredFromWaypoint.value = totalDistance

            directDistanceFromWaypoint.value =
                updatedLocation.distanceTo(lastWaypoint.getLocation()).toInt()
            Log.d(TAG, "fromWaypoint: ${lastWaypoint.distanceCoveredFrom}")
        }

        val lastCheckpoint = workout.lastVisitedCheckpoint
        if (lastCheckpoint != null) {
            val totalDistance = lastCheckpoint.distanceCoveredFrom + addedDistance
            lastCheckpoint.distanceCoveredFrom = totalDistance
            distanceCoveredFromCheckpoint.value = totalDistance

            directDistanceFromCheckpoint.value =
                updatedLocation.distanceTo(lastCheckpoint.getLocation()).toInt()
            Log.d(TAG, "fromCheckpoint: ${lastCheckpoint.distanceCoveredFrom}")
        }
    }

    fun createPointOfInterest(
        type: PointOfInterestType,
        latitude: Double,
        longitude: Double
    ): PointOfInterest {
        return PointOfInterest(workout.id, type, latitude, longitude)
    }

    fun savePointOfInterest(point: PointOfInterest) {
//        if (updatedPoint.type == PointOfInterestType.Waypoint && workout.getCurrentWaypoint() != null) {
//            workout.removeWaypoint()
//        }
        Log.d(TAG, "savePointOfInterest ${point.id}, ${point.type}")
        if (point.type == PointOfInterestType.Checkpoint) {
            workout.checkpoints.add(point)
        } else if (point.type == PointOfInterestType.Waypoint) {
            workout.currentWaypoint = point
        }
    }

    fun removeCurrentWaypointMarker() {
        workout.currentWaypoint?.marker?.remove()
    }
}
