package com.example.endotastic

import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.endotastic.enums.PointOfInterestType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
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

    var polylineOptions = MutableLiveData(PolylineOptions().width(10f).color(Color.RED))

    private var currentLocation: Location? = null

    companion object {
        private val TAG = this::class.java.declaringClass!!.simpleName
    }

    fun startEndWorkout() {
        if (workout.isActive) {
            stopwatch.stop()
            workout.isActive = false
        } else {
            stopwatch.start()
            workout.isActive = true
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
        if (workout.isActive) {
            updateDistances(lat, lng)
        }

        currentLocation = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = lat
            longitude = lng
        }
//        updateLocation()
        if (workout.isActive) {
            drawPolyLine(lat, lng)
            checkPointsOfInterest()
            updateTime()
        }
    }

    private fun drawPolyLine(lat: Double, lng: Double) {
        workout.polyline?.remove()
        val updatePolylineOptions = polylineOptions.value?.add(LatLng(lat, lng))
        polylineOptions.value = updatePolylineOptions
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

    private fun updateDistances(lat: Double, lng: Double) {
        if (!isWorkoutActive()) return
        val updatedLocation = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = lat
            longitude = lng
        }
        if (currentLocation == null) return
        val addedDistance = currentLocation!!.distanceTo(updatedLocation).toInt()
        totalDistance.value = totalDistance.value?.plus(addedDistance)

        updateLastWaypoint(updatedLocation, addedDistance)
        updateLastCheckpoint(updatedLocation, addedDistance)
    }

    private fun updateLastWaypoint(updatedLocation: Location, addedDistance: Int) {
        val lastWaypoint = workout.lastVisitedWaypoint
        if (lastWaypoint != null) {
            val totalDistance = lastWaypoint.distanceCoveredFrom + addedDistance
            lastWaypoint.distanceCoveredFrom = totalDistance
            distanceCoveredFromWaypoint.value = totalDistance

            directDistanceFromWaypoint.value =
                updatedLocation.distanceTo(lastWaypoint.getLocation()).toInt()
            Log.d(TAG, "fromWaypoint: ${lastWaypoint.distanceCoveredFrom}")
        }
    }

    private fun updateLastCheckpoint(updatedLocation: Location, addedDistance: Int) {
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
        return PointOfInterest(type, latitude, longitude)
    }

    fun savePointOfInterest(point: PointOfInterest) {
        Log.d(TAG, "savePointOfInterest ${point.type}")
        if (point.type == PointOfInterestType.Checkpoint) {
            workout.checkpoints.add(point)
        } else if (point.type == PointOfInterestType.Waypoint) {
            workout.currentWaypoint = point
        }
    }

    fun removeCurrentWaypointMarker() {
        workout.currentWaypoint?.marker?.remove()
    }

    fun setPolyline(polyline: Polyline) {
        workout.polyline = polyline
    }
}
