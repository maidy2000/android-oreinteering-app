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
import android.util.Xml
import android.widget.RemoteViews
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.example.endotastic.databases.GpsLocationDatabase
import com.example.endotastic.databases.GpsSessionDatabase
import com.example.endotastic.databases.UserDatabase
import com.example.endotastic.enums.GpsLocationType
import com.example.endotastic.repositories.gpsLocation.GpsLocation
import com.example.endotastic.repositories.gpsLocation.GpsLocationRepository
import com.example.endotastic.repositories.gpsSession.GpsSession
import com.example.endotastic.repositories.gpsSession.GpsSessionRepository
import com.example.endotastic.repositories.user.User
import com.example.endotastic.repositories.user.UserRepository
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.firebase.crashlytics.buildtools.reloc.com.google.common.base.Stopwatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.io.StringWriter
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class MapActivityViewModel(application: Application) : AndroidViewModel(application) {

    private val formatter: Formatter = Formatter()

    private val userRepository: UserRepository
    private val sessionRepository: GpsSessionRepository
    private val locationRepository: GpsLocationRepository

    private lateinit var mapIntent: Intent

    private var currentSession: GpsSession = GpsSession(
        0, "noname", "nodesc", LocalDateTime.MIN.toString()
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

    private lateinit var user: User
    private var currentLocation: Location? = null

    companion object {
        private val TAG = this::class.java.declaringClass!!.simpleName
    }


    init {
        val gpsSessionDao = GpsSessionDatabase.getDatabase(application).gpsSessionDao()
        sessionRepository = GpsSessionRepository(gpsSessionDao)

        val gpsLocationDao = GpsLocationDatabase.getDatabase(application).gpsLocationDao()
        locationRepository = GpsLocationRepository(gpsLocationDao)

        val userDao = UserDatabase.getDatabase(application).getUserDao()
        userRepository = UserRepository(userDao)

        notificationManagerCompat = NotificationManagerCompat.from(application)
        broadcastReceiverIntentFilter.addAction(C.START_SESSION)
        application.registerReceiver(broadcastReceiver, broadcastReceiverIntentFilter)
    }

    fun startEndGpsSession() {
        if (currentSession.isActive) {
            // Stop
            currentSession.isActive = false
            stopwatch.stop()
            currentSession.endedAt = LocalDateTime.now().toString()
            viewModelScope.launch(Dispatchers.IO) {
                val locations = locationRepository.getAllBySessionId(currentSession.id)
                generateGtx(currentSession, locations)
            }
            updateGpsSession(currentSession)
            // TODO finish screen and reset
        } else {
            // Start
            stopwatch.start()
            user = getUser()
            if (currentSession.id == 0) {
                createNewSession()
            }

            currentSession.isActive = true
        }
        isCurrentSessionActive.value = currentSession.isActive
         Log.d(TAG, "change isActive=${currentSession.isActive}")
    }

    private fun generateGtx(session: GpsSession, locations: List<GpsLocation>) {
        val xmlSerializer = Xml.newSerializer()
        val writer = StringWriter()

        xmlSerializer.setOutput(writer)
        xmlSerializer.startDocument("UTF-8", false)
        xmlSerializer.startTag("", "gpx")
        xmlSerializer.attribute("", "version", "1.1")
        xmlSerializer.attribute("", "creator", "endotastic")
        xmlSerializer.attribute("", "xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance")
        xmlSerializer.attribute("", "xmlns", "http://www.topografix.com/GPX/1/1")
        xmlSerializer.attribute("", "xsi:schemaLocation", "http://www.topografix.com/GPX/1/1")

        for (location in locations) {
            xmlSerializer.startTag("", "wpt")
            xmlSerializer.attribute("", "lat", location.latitude.toString())
            xmlSerializer.attribute("", "lon", location.longitude.toString())
            xmlSerializer.startTag("", "ele")
            xmlSerializer.text(location.altitude.toString())
            xmlSerializer.endTag("","ele")
            xmlSerializer.startTag("", "time")
            xmlSerializer.text(location.recordedAt)
            xmlSerializer.endTag("","time")
            xmlSerializer.endTag("","wpt")
        }

        xmlSerializer.endDocument()
        println(writer.toString())

        try {
            val fileName = "gpx_session_at_${session.startedAt}.xml"
            val file = File(getApplication<Application>().getExternalFilesDir(null), fileName)
            val fos = FileOutputStream(file)
            val osw = OutputStreamWriter(fos)
            fos.write(writer.toString().toByteArray())
            fos.close()
            osw.flush()
            osw.close()
            Log.d(TAG, "File writing successful to ${file.absolutePath}")
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }


    private fun createNewSession() {
        currentSession = GpsSession(
            0, generateSessionName(), "Session on ${LocalDateTime.now()}", LocalDateTime.now().toString()
        )
        startOnlineSession()
    }

    private fun startOnlineSession() {
        val url = C.API + "GpsSessions"
        var handler = HttpSingletonHandler.getInstance(getApplication())

        var httpRequest = object : StringRequest(
            Method.POST,
            url,
            Response.Listener { response ->
                 Log.d("Response.Listener", response)
                val json = JSONTokener(response).nextValue() as JSONObject
                val onlineSessionId = json.getString("id")
                currentSession.onlineSessionId = onlineSessionId
                addGpsSession(currentSession)
            },
            Response.ErrorListener { error ->   /*Log.d("Response.ErrorListener", "${error.message} ${error.networkResponse.statusCode}")*/}
        ){
            override fun getBodyContentType(): String {
                return "application/json"
            }

            override fun getHeaders(): MutableMap<String, String> {
                val params = mutableMapOf<String, String>()
                params["Authorization"] = "Bearer " + user.token
                 Log.d(TAG, "getHeaders()")
                return params
            }

            override fun getBody(): ByteArray {
                val params = HashMap<String, Any>()
                params["name"] = currentSession.name
                params["description"] = currentSession.description
                params["recordedAt"] = currentSession.startedAt
                params["paceMin"] = 60
                params["paceMax"] = 100

                var body = JSONObject(params as Map<*, *>).toString()
                 Log.d("getBody", body)

                return body.toByteArray()
            }
        }

        handler.addToRequestQueue(httpRequest)
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

        var sessionStatusText = "Not started"
        if (currentSession.isActive) {
            sessionStatusText = "Active"
        }
        if (!currentSession.isActive && currentSession.id != 0) {
            sessionStatusText = "Ended"
        }
        notifyView.setTextViewText(R.id.textViewSessionStatus, "Session status: $sessionStatusText")

        val openingPendingIntent = PendingIntent.getActivity(getApplication(), 0, mapIntent, FLAG_IMMUTABLE)

        if (!currentSession.isActive && currentSession.id == 0) {
            val startSessionIntent = Intent(C.START_SESSION)
            val startStopPendingIntent =
                PendingIntent.getBroadcast(getApplication(), 0, startSessionIntent, FLAG_IMMUTABLE)
            notifyView.setOnClickPendingIntent(R.id.buttonStartStopOnNotification, startStopPendingIntent)
        }

        val builder = NotificationCompat.Builder(getApplication(), C.NOTIFICATION_CHANNEL).setSmallIcon(R.drawable.map)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle()).setCustomContentView(notifyView)
            .setPriority(NotificationCompat.PRIORITY_MIN).setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setContentIntent(openingPendingIntent)

        notificationManagerCompat.notify(C.NOTIFICATION_ID, builder.build())
    }


    private inner class InnerBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(p0: Context?, p1: Intent?) {
             Log.d(TAG, "onReceive ${p1.toString()}")
            if (!currentSession.isActive) {
                startEndGpsSession()
            }
        }
    }

    private fun generateSessionName(): String {
        var result = ""
        val startTime = LocalDateTime.now()
        result = result.plus(startTime.dayOfWeek.name.lowercase())
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


    fun getLocations(): List<GpsLocation> {
        val allPoints = ArrayList(currentSession.checkpoints)
        currentSession.currentWaypoint?.let { allPoints.add(it) }
        currentSession.lastVisitedWaypoint?.let { allPoints.add(it) }
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

        if (currentSession.isActive && currentSession.id != 0) {
            updateDistances(locationIn)
        }

        currentLocation = locationIn

        if (currentSession.isActive && currentSession.id != 0) {
            drawPolyLine(lat, lng)
            checkLocations()
            updateTime()
            createGpsLocation(GpsLocationType.LOC, lat, lng, acc, alt, vac)
        }
        showNotification()
    }

    private fun drawPolyLine(lat: Double, lng: Double) {
        currentSession.polyline?.remove()
        val updatePolylineOptions = polylineOptions.value?.add(LatLng(lat, lng))
        polylineOptions.value = updatePolylineOptions
    }

    private fun checkLocations() {
        val visited = mutableListOf<GpsLocation>()
        val points = currentSession.checkpoints.toMutableList()
        currentSession.currentWaypoint?.let { points.add(it) }
        for (point in points) {
            if (currentLocation == null) return
            if (point.isVisited) continue

            val distanceResults = FloatArray(1)
            Location.distanceBetween(
                currentLocation!!.latitude, currentLocation!!.longitude, point.latitude, point.longitude, distanceResults
            )
            val distance = distanceResults[0]
             Log.d(TAG, "check POI, distance: $distance")
            if (distance <= C.ACCEPTABLE_POINT_DISTANCE) {
                visited.add(point)
            }
        }

        for (point in visited) {
            markAsVisited(point)
        }
    }

    private fun markAsVisited(point: GpsLocation) {
        point.isVisited = true
        point.visitedAt = stopwatch.elapsed(TimeUnit.SECONDS)
        Log.d("visitedAt", point.latitude.toString() + " " + point.visitedAt.toString())

        val results = FloatArray(1)
        Location.distanceBetween(
            currentLocation!!.latitude, currentLocation!!.longitude, point.latitude, point.longitude, results
        )
        val distance = results[0]
        point.distanceCoveredFrom = distance.toInt()
        Log.d(TAG, "visited ${point.typeId}")

        addLocationOnline(point)

        if (point.typeId == GpsLocationType.CP.id) {
            currentSession.lastVisitedCheckpoint = point
        } else if (point.typeId == GpsLocationType.WP.id) {
            currentSession.lastVisitedWaypoint = point
            currentSession.currentWaypoint = null
            Log.d("markAsVisited", "lastVisited=")
        }
        updatePointsOfInterest.value = true
        Log.d("visitedAt", point.latitude.toString() + " " + point.visitedAt.toString())
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
        if (type == GpsLocationType.LOC) {
            addGpsLocation(gpsLocation)
        }
        return gpsLocation
    }

    private fun addGpsLocation(gpsLocation: GpsLocation) {
        if (currentSession.id == 0) return
        viewModelScope.launch(Dispatchers.IO) {
            locationRepository.addGpsLocation(gpsLocation)
        }
        addLocationOnline(gpsLocation)
    }

    private fun addLocationOnline(location: GpsLocation) {
        val url = C.API + "GpsLocations"
        var handler = HttpSingletonHandler.getInstance(getApplication())

        var httpRequest = object : StringRequest(
            Method.POST,
            url,
            Response.Listener { response ->
                 Log.d("Response.Listener", response)
            },
            Response.ErrorListener { /*error ->
                Log.d("Response.ErrorListener", "${error.message} ${error.networkResponse.statusCode} error=$error")
                 */
            }
        ){
            override fun getBodyContentType(): String {
                return "application/json"
            }

            override fun getHeaders(): MutableMap<String, String> {
                val params = mutableMapOf<String, String>()
                params["Authorization"] = "Bearer " + user.token
                 Log.d(TAG, "getHeaders()")
                return params
            }

            override fun getBody(): ByteArray {
                val params = HashMap<String, Any>()
//                if (location.typeId == GpsLocationType.LOC.id) {
                params["recordedAt"] = LocalDateTime.now().toString() // location.recordedAt
//                } else {
//                    params["recordedAt"] = LocalDateTime.parse(location.recordedAt).plusSeconds(location.visitedAt!!).toString()
//                    Log.d("visitedAt.getBody",  location.latitude.toString() + " " + location.visitedAt.toString())
//                }
                params["latitude"] = location.latitude
                params["longitude"] = location.longitude
                params["accuracy"] = location.accuracy
                params["altitude"] = location.altitude
                params["verticalAccuracy"] = location.verticalAccuracy
                params["gpsLocationTypeId"] = location.typeId
                params["gpsSessionId"] = currentSession.onlineSessionId!!


                var body = JSONObject(params as Map<*, *>).toString()
                 Log.d("getBody", body)

                return body.toByteArray()
            }
        }

        handler.addToRequestQueue(httpRequest)
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

    fun userExists(): Boolean = runBlocking {
        val result = withContext(Dispatchers.Default) { userRepository.getUserExists() }
         Log.d("Login", "returned $result")
        return@runBlocking result
    }

    private fun getUser(): User = runBlocking {
         Log.d("Login", "getUser()")
        val result = withContext(Dispatchers.Default) { userRepository.getUser() }
         Log.d("Login", "returned $result")
        return@runBlocking result
    }


}
