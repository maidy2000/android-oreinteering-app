package com.example.endotastic

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.os.*
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.endotastic.enums.CameraMode
import com.example.endotastic.enums.PointType
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.firebase.crashlytics.buildtools.reloc.com.google.common.base.Stopwatch
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt


class MapsActivity : AppCompatActivity(), OnMapReadyCallback {
    companion object {
        private val TAG = this::class.java.declaringClass!!.simpleName
    }

    private lateinit var buttonStartStop: ImageButton
    private lateinit var buttonAddCheckpoint: ImageButton
    private lateinit var buttonAddWaypoint: ImageButton
    private lateinit var buttonToggleCameraDirection: ImageButton
    private lateinit var buttonConfirm: ImageButton
    private lateinit var buttonCancel: ImageButton

    lateinit var textViewTotalTimeElapsed: TextView
    private lateinit var textViewTotalDistance: TextView
    private lateinit var textViewTotalPace: TextView

    private lateinit var imageViewCheckpointPointer : ImageView
    private lateinit var imageViewWaypointPointer : ImageView

    private lateinit var mMap: GoogleMap
    private var isWorkoutActive = false
    private var startDrawingNewPolyline = false

    private var polylineOptions = PolylineOptions().width(10f).color(Color.RED)
    private var currentPolyline: Polyline? = null
    private var polylines: MutableList<Polyline> = mutableListOf()

    private lateinit var stopwatch: Stopwatch
    private var timer: Timer = Timer()
    private var handler: UIHandler = UIHandler()

    private var currentLocation: Location? = null
    private var totalDistance: Float = 0f
    private var distanceCoveredFromCheckpoint = 0f
    private var directDistanceFromCheckpoint = 0f
    private var distanceCoveredFromWaypoint = 0f
    private var directDistanceFromWaypoint = 0f

    private var totalPace = 0.0
    private var checkpointPace = 0.0
    private var waypointPace = 0.0

    private var currentCamera : Camera = Camera()

    private var userLocationMarker: Marker? = null

    private var broadcastReceiver = InnerBroadcastReceiver()
    private var broadcastReceiverIntentFilter = IntentFilter()

    @RequiresApi(Build.VERSION_CODES.M)

    //region Activity lifecycle


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment

        mapFragment.getMapAsync(this)

        createNotificationChannel()
        startLocationService()

        stopwatch = Stopwatch.createUnstarted()

        buttonStartStop = findViewById(R.id.imageButtonStartStop)
        buttonStartStop.setOnClickListener { startStopWorkout() }
        buttonAddCheckpoint = findViewById(R.id.imageButtonAddCheckpoint)
        buttonAddCheckpoint.setOnClickListener { startAddPoint(PointType.Checkpoint) }
        buttonAddWaypoint = findViewById(R.id.imageButtonAddWaypoint)
        buttonAddWaypoint.setOnClickListener { startAddPoint(PointType.Waypoint) }

        buttonConfirm = findViewById(R.id.imageButtonConfirm)
        buttonCancel = findViewById(R.id.imageButtonCancel)

        imageViewCheckpointPointer = findViewById(R.id.imageViewCheckpoint)
        imageViewWaypointPointer = findViewById(R.id.imageViewWaypoint)

        buttonToggleCameraDirection = findViewById(R.id.imageButtonToggleCameraDirection)
        buttonToggleCameraDirection.setOnClickListener { toggleCameraDirection() }

        textViewTotalTimeElapsed = findViewById(R.id.textViewTotalTimeElapsed)
        textViewTotalDistance = findViewById(R.id.textViewTotalDistance)
        textViewTotalPace = findViewById(R.id.textViewTotalPace)

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "No access to location, requesting permission!!")

            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ), 0
            )

            return
        }

        broadcastReceiverIntentFilter.addAction(C.LOCATION_UPDATE)
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")

        LocalBroadcastManager.getInstance(this)
            .registerReceiver(broadcastReceiver, broadcastReceiverIntentFilter)
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
    }

    //endregion

    //region Stopwatch

    private fun startStopWorkout() {
        isWorkoutActive = !isWorkoutActive
        startStopStopWatch()
        startStopDrawingPolyline()
    }

    private fun startStopStopWatch() {
        if (stopwatch.isRunning) {
            stopwatch.stop()
        } else {
            stopwatch.start()
            val updateStopwatchTask = UpdateStopwatchTask()
            timer.schedule(updateStopwatchTask, 0, 10)
        }
    }

    private inner class UpdateStopwatchTask : TimerTask() {
        override fun run() {
            // siin peaks vb UIHandlerit jooksutama hoopis
            // format time hh:mm:ss
            handler.sendEmptyMessage(0)
        }
    }

    private inner class UIHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            var elapsed = formatTime(stopwatch.elapsed(TimeUnit.SECONDS))
            textViewTotalTimeElapsed.text = elapsed
        }

        private fun formatTime(elapsed: Long): String {
            val secondsLeft: Long = elapsed % 3600 % 60
            val minutes = Math.floor((elapsed % 3600 / 60).toDouble()).toInt()
            val hours = Math.floor((elapsed / 3600).toDouble()).toInt()

            val HH = (if (hours < 10) "0" else "") + hours
            val MM = (if (minutes < 10) "0" else "") + minutes
            val SS = (if (secondsLeft < 10) "0" else "") + secondsLeft

            return "$HH:$MM:$SS"
        }
    }

    //endregion

    //region Location

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // Add a marker in Sydney and move the camera
//        mMap.moveCamera(CameraUpdateFactory.zoomBy(14f))
//        val sydney = LatLng(-34.0, 151.0)
//        mMap.addMarker(MarkerOptions().position(sydney).title("Marker in Sydney"))
//        mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney))
    }

    private fun startStopDrawingPolyline() {
        if (isWorkoutActive) {
            startDrawingNewPolyline = true
            polylineOptions = PolylineOptions().width(10f).color(Color.RED)
            buttonStartStop.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.pause))
        } else {
            if (currentPolyline != null) {
                polylines.add(currentPolyline!!)
            }
            startDrawingNewPolyline = true
            buttonStartStop.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.play))
        }
    }

    private fun startLocationService() {
        Log.d(TAG, "startLocationService")
        val intent = Intent(applicationContext, LocationService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // long running bgr service without ui (with notification)
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    fun updateLocation(lat: Double, lng: Double) {
        Log.d(TAG, "updateLocation, ${lat} ${lng}")
        if (mMap == null) return

        val latLng = LatLng(lat, lng)

        updateUserLocationMarker(latLng)
        if (isWorkoutActive) {
            drawPolyLine(latLng)
            updateDistances(latLng)
            updatePaces()
        }

        currentLocation = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = latLng.latitude
            longitude = latLng.longitude
        }
        updateCamera()
    }

    private fun drawPolyLine(latLng: LatLng) {
        if (currentPolyline != null && !startDrawingNewPolyline) {
            currentPolyline!!.remove()
            startDrawingNewPolyline = false
        }
        polylineOptions.add(latLng)
        currentPolyline = mMap.addPolyline(polylineOptions)
    }

    private fun updateUserLocationMarker(latLng: LatLng) {
        if (userLocationMarker != null) {
            userLocationMarker!!.remove()
        }

        val userIconOptions = MarkerOptions()
            .position(latLng)
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN))

        userLocationMarker = mMap.addMarker(userIconOptions)
    }

    //endregion

    //region Time, distance, pace
    private fun updatePaces() {
        val totalTime = stopwatch.elapsed(TimeUnit.SECONDS)
        textViewTotalPace.text = formatPace(totalTime, totalDistance)
        //todo update other paces
    }

    private fun formatPace(seconds: Long, meters: Float): String {
        if (meters <= 0) return "--:--min/km"

        var pace = (seconds / meters).toDouble()
        pace = pace * 1000 / 60

        val min = pace / 1
        val sec = pace % 1 * 60
        Log.d(TAG, "formatPace $min:$sec")

        val MM = (if (min.toInt() < 10) "0" else "") + min.toInt()
        val SS = (if (sec < 10) "0" else "") + sec.toInt()
        return "$MM.${SS}min/km"
    }

    private fun updateDistances(latLng: LatLng) {
        val updatedLocation = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = latLng.latitude
            longitude = latLng.longitude
        }
        if (currentLocation != null) {
            totalDistance += currentLocation!!.distanceTo(updatedLocation)
            val distance = formatDistance(totalDistance.roundToInt())
            textViewTotalDistance.text = distance
        }
        //todo update other distances, update notification
    }

    private fun formatDistance(distance: Int): String {
        val kilometers = distance / 1000
        val decameters = distance % 1000 / 10
        val km = (if (kilometers < 10) "0" else "") + kilometers
        val dam = (if (decameters < 10) "0" else "") + decameters
        Log.d(TAG, "$km.$dam=$distance")
        return "$km.${dam}km"
    }

    //endregion

    //region Camera

    private fun toggleCameraDirection() {
        //todo teha camera object
        when (currentCamera.mode) {
            CameraMode.NORTH_UP -> currentCamera.mode = CameraMode.DIRECTION_UP
            CameraMode.DIRECTION_UP -> currentCamera.mode = CameraMode.FREE
            CameraMode.FREE -> currentCamera.mode = CameraMode.NORTH_UP
            CameraMode.USER_CHOSEN_UP -> currentCamera.mode = CameraMode.USER_CHOSEN_UP
        }
        Log.d(TAG, "toggleCameraDirection: ${currentCamera.mode}")
        updateCamera()
    }

    private fun updateCamera() {
        mMap.uiSettings.isRotateGesturesEnabled = currentCamera.isRotateEnabled()
        Log.d(TAG, "updateCamera: ${currentCamera.mode}")

        if (currentCamera.mode == CameraMode.FREE) return

        var cameraPositionBuilder = CameraPosition.Builder()
        val latLng = LatLng(currentLocation!!.latitude, currentLocation!!.longitude)
        cameraPositionBuilder.target(latLng)

        if (currentCamera.getBearing() != null) {
            cameraPositionBuilder.bearing(currentCamera.getBearing()!!)
        }

        cameraPositionBuilder = cameraPositionBuilder.zoom(currentCamera.zoom)
        mMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPositionBuilder.build()))
    }

    //endregion

    //region Checkpoints, waypoints
    private fun startAddPoint(type: PointType) {
        /*
        tuleb salvestada eelnevad kaamera seaded
        ajutiselt muuta kaamera seaded vabaks
        lisada ekraani keskele wp/cp märk või näiteks rist, mis aitaks asukohta määrata
        lisada UI'le confirm ja cancel nupud
         */
        Log.d(TAG, "startAddPoint: $type")
        val savedCamera = currentCamera
        currentCamera = Camera(CameraMode.FREE, savedCamera.zoom)
        showPointer(type)
        buttonConfirm.setOnClickListener {
            confirmPoint(type)
            endAddPoint(savedCamera)
        }
        buttonCancel.setOnClickListener { endAddPoint(savedCamera)}
        buttonConfirm.visibility = View.VISIBLE
        buttonCancel.visibility = View.VISIBLE
    }

    private class Point(val type: PointType, val latLng: LatLng) {
        private var visited = false

        init {
            if (type == PointType.Checkpoint) {
                //todo save to db
            }
        }

        fun getIconDrawable(): Int {
            return when (type) {
                PointType.Waypoint -> {
                    R.drawable.pin_drop
                }
                PointType.Checkpoint -> {
                    R.drawable.flag
                }
            }
        }
    }

    // lisatakse valitud asukohta kollane unreached staatusega CP/WP (vajadusel salvestatakse andmebaasi)
    private fun confirmPoint(type: PointType) {
        val point = Point(type, mMap.cameraPosition.target)
        val pointMarker = MarkerOptions().position(point.latLng).icon(bitmapFromVector(this, point.getIconDrawable()))
        mMap.addMarker(pointMarker)
    }

    private fun endAddPoint(savedCamera: Camera) {
        buttonCancel.visibility = View.GONE
        buttonConfirm.visibility = View.GONE
        imageViewWaypointPointer.visibility = View.GONE
        imageViewCheckpointPointer.visibility = View.GONE
        currentCamera = savedCamera
    }

    private fun showPointer(type: PointType) {
        if (type == PointType.Waypoint) {
            imageViewWaypointPointer.visibility = View.VISIBLE
        } else if (type == PointType.Checkpoint) {
            imageViewCheckpointPointer.visibility = View.VISIBLE
        }
    }

    //endregion
    
    private fun bitmapFromVector(context: Context, vectorResId: Int): BitmapDescriptor? {
        // below line is use to generate a drawable.
        val vectorDrawable = ContextCompat.getDrawable(context, vectorResId)

        // below line is use to set bounds to our vector drawable.
        vectorDrawable!!.setBounds(
            0,
            0,
            vectorDrawable.intrinsicWidth,
            vectorDrawable.intrinsicHeight
        )

        // below line is use to create a bitmap for our
        // drawable which we have added.
        val bitmap = Bitmap.createBitmap(
            vectorDrawable.intrinsicWidth,
            vectorDrawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )

        // below line is use to add bitmap in our canvas.
        val canvas = Canvas(bitmap)

        // below line is use to draw our
        // vector drawable in canvas.
        vectorDrawable.draw(canvas)

        // after generating our bitmap we are returning our bitmap.
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                C.NOTIFICATION_CHANNEL,
                C.NOTIFICATION_CHANNEL,
                NotificationManager.IMPORTANCE_MIN
            )
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private inner class InnerBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(p0: Context?, p1: Intent?) {
            Log.d(TAG, "onRecieve")
            when (p1!!.action) {
                C.LOCATION_UPDATE -> {
                    updateLocation(
                        p1.getDoubleExtra(C.LOCATION_UPDATE_LAT, 0.0),
                        p1.getDoubleExtra(C.LOCATION_UPDATE_LON, 0.0)
                    )
                }
            }
        }

    }


}