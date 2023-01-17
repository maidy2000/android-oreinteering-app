package com.example.endotastic

import android.app.Application
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.endotastic.databases.GpsLocationDatabase
import com.example.endotastic.databases.GpsSessionDatabase
import com.example.endotastic.enums.GpsLocationType
import com.example.endotastic.repositories.gpsLocation.GpsLocation
import com.example.endotastic.repositories.gpsLocation.GpsLocationRepository
import com.example.endotastic.repositories.gpsSession.GpsSession
import com.example.endotastic.repositories.gpsSession.GpsSessionRepository
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.firebase.crashlytics.buildtools.reloc.com.google.common.base.Stopwatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class MapActivityViewModel(application: Application) : AndroidViewModel(application) {

    private val formatter: Formatter = Formatter()

    private val sessionRepository: GpsSessionRepository
    private val locationRepository: GpsLocationRepository

    private lateinit var mapIntent: Intent

    private var currentSession: GpsSession = GpsSession(
        0, generateSessionName(), "Session on ${LocalDateTime.now()}", LocalDateTime.now().toString()
    )

    private val broadcastReceiver = InnerBroadcastReceiver()
    private val broadcastReceiverIntentFilter = IntentFilter()

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

    var isCurrentSessionActive = MutableLiveData(currentSession.isActive)
        private set

    var addLocation = MutableLiveData<GpsLocationType?>(null)
        private set

    var polylineOptions = MutableLiveData(PolylineOptions().width(10f).color(Color.RED))
    private var notificationManagerCompat: NotificationManagerCompat

    private var currentLocation: Location? = null
    private var locationList = mutableListOf<GpsLocation>()

    companion object {
        private val TAG = this::class.java.declaringClass!!.simpleName
    }


    init {
        val gpsSessionDao = GpsSessionDatabase.getDatabase(application).gpsSessionDao()
        sessionRepository = GpsSessionRepository(gpsSessionDao)

        val gpsLocationDao = GpsLocationDatabase.getDatabase(application).gpsLocationDao()
        locationRepository = GpsLocationRepository(gpsLocationDao)

        notificationManagerCompat = NotificationManagerCompat.from(application)
        broadcastReceiverIntentFilter.addAction(C.PLAY_PAUSE)
        application.registerReceiver(broadcastReceiver, broadcastReceiverIntentFilter)
    }

    fun startEndGpsSession() {
        if (currentSession.isActive) {
            currentSession.isActive = false
            stopwatch.stop()
            currentSession.endedAt = LocalDateTime.now().toString()
            updateGpsSession(currentSession)
            addLocationList()
            // TODO finish screen and reset
        } else {
            stopwatch.start()
            currentSession = GpsSession(
                0, generateSessionName(), "Session on ${LocalDateTime.now()}", LocalDateTime.now().toString()
            )
            addGpsSession(currentSession)
            currentSession.isActive = true
        }
        isCurrentSessionActive.value = currentSession.isActive
        Log.d(TAG, "change isActive=${currentSession.isActive}")
    }

    private fun addGpsSession(gpsSession: GpsSession) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = sessionRepository.addGpsSession(gpsSession)
            Log.d(TAG, "session id=${currentSession.id} replaced with id=${id}")
            currentSession.id = id.toInt()
        }
    }

    private fun updateGpsSession(gpsSession: GpsSession) {
        viewModelScope.launch(Dispatchers.IO) {
            sessionRepository.updateGpsSession(gpsSession)
        }
    }

    private fun showNotification() {
        Log.d(TAG, "showNotification")
        val notifyView = RemoteViews(getApplication<Application>().packageName, R.layout.map_notification)

        notifyView.setTextViewText(
            R.id.textViewTotalDistance,
            totalDistance.value?.let { formatter.formatDistance(it) })

        notifyView.setTextViewText(
            R.id.textViewTotalTimeElapsed,
            totalTimeElapsed.value?.let { formatter.formatTime(it) })

        notifyView.setTextViewText(R.id.textViewTotalPace, totalDistance.value?.let { distance ->
            totalTimeElapsed.value?.let { time ->
                formatter.formatPace(
                    time, distance
                )
            }
        })

        notifyView.setTextViewText(R.id.textViewDistanceCoveredFromWaypoint,
            distanceCoveredFromWaypoint.value?.let { formatter.formatDistance(it) })
        notifyView.setTextViewText(R.id.textViewDirectDistanceFromWaypoint,
            directDistanceFromWaypoint.value?.let { formatter.formatDistance(it) })
        notifyView.setTextViewText(R.id.textViewWaypointPace, totalTimeElapsed.value?.let { time ->
            distanceCoveredFromWaypoint.value?.let { distance ->
                formatter.formatPace(
                    time, distance
                )
            }
        })

        notifyView.setTextViewText(R.id.textViewDistanceCoveredFromCheckpoint,
            distanceCoveredFromCheckpoint.value?.let { formatter.formatDistance(it) })
        notifyView.setTextViewText(R.id.textViewDirectDistanceFromCheckpoint, directDistanceFromCheckpoint.value?.let {
            formatter.formatDistance(
                it
            )
        })
        notifyView.setTextViewText(R.id.textViewCheckpointPace, totalTimeElapsed.value?.let { time ->
            distanceCoveredFromCheckpoint.value?.let { distance ->
                formatter.formatPace(
                    time, distance
                )
            }
        })

        val openingPendingIntent = PendingIntent.getActivity(getApplication(), 0, mapIntent, FLAG_IMMUTABLE)

        val startStopIntent = Intent(C.PLAY_PAUSE)
        val startStopPendingIntent = PendingIntent.getBroadcast(getApplication(), 0, startStopIntent, FLAG_IMMUTABLE)
        notifyView.setOnClickPendingIntent(R.id.buttonStartStopOnNotification, startStopPendingIntent)

        val builder = NotificationCompat.Builder(getApplication(), C.NOTIFICATION_CHANNEL).setSmallIcon(R.drawable.map)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle()).setCustomContentView(notifyView)
            .setPriority(NotificationCompat.PRIORITY_MIN).setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setContentIntent(openingPendingIntent)

        notificationManagerCompat.notify(C.NOTIFICATION_ID, builder.build())
    }


    private inner class InnerBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(p0: Context?, p1: Intent?) {
            Log.d(TAG, "onReceive ${p1.toString()}")
            startEndGpsSession()
        }
    }


    private fun generateSessionName(): String {
        var result = ""
        val startTime = LocalDateTime.now()
        result = result.plus(startTime.dayOfWeek.name)
        if (23 <= startTime.hour || startTime.hour < 5) {
            result = result.plus(" night session")
        } else if (5 <= startTime.hour || startTime.hour < 11) {
            result = result.plus(" morning session")
        } else if (11 <= startTime.hour || startTime.hour < 17) {
            result = result.plus(" noon session")
        } else if (17 <= startTime.hour || startTime.hour < 23) {
            result = result.plus(" evening session")
        }
        return result
    }


    fun getPointsOfInterests(): List<GpsLocation> {
        val allPoints = ArrayList(currentSession.checkpoints)
        currentSession.currentWaypoint?.let { allPoints.add(it) }
        currentSession.lastVisitedWaypoint?.let { allPoints.add(it) }
//        Log.d(TAG, "getPointsOfInterests wop=${gpsSession.checkpoints.count()} returned=${allPoints.count()}")
        return allPoints
    }

    fun getLastVisitedWaypointVisitedAt() = currentSession.lastVisitedWaypoint?.visitedAt
    fun getLastVisitedCheckpointVisitedAt() = currentSession.lastVisitedCheckpoint?.visitedAt


    fun locationUpdateIn(lat: Double, lng: Double, acc: Float, alt: Double, vac: Float) {
        //todo filter incoming data
        val locationIn = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = lat
            longitude = lng
            accuracy = acc
            altitude = alt
            verticalAccuracyMeters = vac
        }

        if (currentSession.isActive) {
            updateDistances(locationIn)
        }

        currentLocation = locationIn

        if (currentSession.isActive) {
            drawPolyLine(lat, lng)
            checkPointsOfInterest()
            updateTime()
            createGpsLocation(GpsLocationType.LOC, lat, lng, acc, alt, vac)
            Log.d(TAG, "gpsSessionId=${currentSession.id}")
        }
        showNotification()
    }

    private fun drawPolyLine(lat: Double, lng: Double) {
        currentSession.polyline?.remove()
        val updatePolylineOptions = polylineOptions.value?.add(LatLng(lat, lng))
        polylineOptions.value = updatePolylineOptions
    }

    private fun checkPointsOfInterest() {
        val visited = mutableListOf<GpsLocation>()
        val points = currentSession.checkpoints.toMutableList()
        currentSession.currentWaypoint?.let { points.add(it) }
        for (point in points) {
            if (currentLocation == null) return
            if (point.isVisited) continue

            val results = FloatArray(1)
            Location.distanceBetween(
                currentLocation!!.latitude, currentLocation!!.longitude, point.latitude, point.longitude, results
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
                currentLocation!!.latitude, currentLocation!!.longitude, point.latitude, point.longitude, results
            )
            val distance = results[0]
            point.distanceCoveredFrom = distance.toInt()
            Log.d(TAG, "visited ${point.typeId}")

            if (point.typeId == GpsLocationType.CP.id) {
                currentSession.lastVisitedCheckpoint = point
            } else if (point.typeId == GpsLocationType.WP.id) {
                currentSession.lastVisitedWaypoint = point
                currentSession.currentWaypoint = null
            }
            updatePointsOfInterest.value = true
        }
    }

    private fun updateTime() {
        totalTimeElapsed.value = stopwatch.elapsed(TimeUnit.SECONDS)
    }

    private fun updateDistances(locationIn: Location) {
        if (!currentSession.isActive) return

        if (currentLocation == null) return
        val addedDistance = currentLocation!!.distanceTo(locationIn).toInt()
        totalDistance.value = totalDistance.value?.plus(addedDistance)

        updateLastWaypoint(locationIn, addedDistance)
        updateLastCheckpoint(locationIn, addedDistance)
    }

    private fun updateLastWaypoint(updatedLocation: Location, addedDistance: Int) {
        val lastWaypoint = currentSession.lastVisitedWaypoint
        if (lastWaypoint != null) {
            val totalDistance = lastWaypoint.distanceCoveredFrom + addedDistance
            lastWaypoint.distanceCoveredFrom = totalDistance
            distanceCoveredFromWaypoint.value = totalDistance

            directDistanceFromWaypoint.value = updatedLocation.distanceTo(lastWaypoint.getLocation()).toInt()
            Log.d(TAG, "fromWaypoint: ${lastWaypoint.distanceCoveredFrom}")
        }
    }

    private fun updateLastCheckpoint(updatedLocation: Location, addedDistance: Int) {
        val lastCheckpoint = currentSession.lastVisitedCheckpoint
        if (lastCheckpoint != null) {
            val totalDistance = lastCheckpoint.distanceCoveredFrom + addedDistance
            lastCheckpoint.distanceCoveredFrom = totalDistance
            distanceCoveredFromCheckpoint.value = totalDistance

            directDistanceFromCheckpoint.value = updatedLocation.distanceTo(lastCheckpoint.getLocation()).toInt()
            Log.d(TAG, "fromCheckpoint: ${lastCheckpoint.distanceCoveredFrom}")
        }
    }

    fun createGpsLocation(
        type: GpsLocationType,
        latitude: Double,
        longitude: Double,
        accuracy: Float = 0f,
        altitude: Double = 0.0,
        verticalAccuracy: Float = 0f
    ): GpsLocation {
        val gpsLocation = GpsLocation(
            id = 0,
            typeId = type.id,
            latitude = latitude,
            longitude = longitude,
            accuracy = accuracy,
            altitude = altitude,
            verticalAccuracy = verticalAccuracy,
            gpsSessionId = currentSession.id,
            recordedAt = LocalDateTime.now().toString()
        )
        addGpsLocation(gpsLocation)
        return gpsLocation
    }

    private fun addGpsLocation(gpsLocation: GpsLocation) {
        if (gpsLocation.typeId == GpsLocationType.LOC.id) {
            locationList.add(gpsLocation)
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                locationRepository.addGpsLocation(gpsLocation)
            }
        }

        if (locationList.size >= 20) {
            addLocationList()
        }
    }

    private fun addLocationList() {
        viewModelScope.launch(Dispatchers.IO) {
            locationRepository.addGpsLocations(locationList)
        }
        locationList.clear()
    }

    fun savePointOfInterest(point: GpsLocation) {
        Log.d(TAG, "savePointOfInterest ${point.typeId}")
        if (point.typeId == GpsLocationType.CP.id) {
            currentSession.checkpoints.add(point)
        } else if (point.typeId == GpsLocationType.WP.id) {
            currentSession.currentWaypoint = point
        }
    }

    fun removeCurrentWaypointMarker() {
        currentSession.currentWaypoint?.marker?.remove()
    }

    fun setPolyline(polyline: Polyline) {
        currentSession.polyline = polyline
    }

    fun createLauncherIntent(intent: Intent) {
        mapIntent = intent
        mapIntent.action = Intent.ACTION_MAIN
        mapIntent.addCategory(Intent.CATEGORY_LAUNCHER)
        mapIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
}
