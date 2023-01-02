package com.example.endotastic

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.os.*
import android.util.Log
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.ActivityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.firebase.crashlytics.buildtools.reloc.com.google.common.base.Stopwatch
import java.text.DecimalFormatSymbols
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt


class MapsActivity : AppCompatActivity(), OnMapReadyCallback {
    companion object {
        private val TAG = this::class.java.declaringClass!!.simpleName
    }

    private lateinit var buttonStartStop: ImageButton
    lateinit var textViewTotalTimeElapsed: TextView
    private lateinit var textViewTotalDistance: TextView
    private lateinit var textViewTotalPace: TextView

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
    private var totalDistance = 0f
    private var distanceCoveredFromCheckpoint = 0f
    private var directDistanceFromCheckpoint = 0f
    private var distanceCoveredFromWaypoint = 0f
    private var directDistanceFromWaypoint = 0f

    private var totalPace = 0.0
    private var checkpointPace = 0.0
    private var waypointPace = 0.0



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
        buttonStartStop.setOnClickListener {
            startStopWorkout()
        }
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
        mMap.moveCamera(CameraUpdateFactory.zoomBy(14f))
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
        mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng))
    }

    private fun updatePaces() {
        val seconds = stopwatch.elapsed(TimeUnit.SECONDS)
        val km = totalDistance.toInt()
        textViewTotalPace.text = formatPace(seconds, km)
    }

    private fun formatPace(seconds: Long, meters: Int): String {
        if (meters == 0) return "--:--min/km"

        var pace = seconds.toDouble() / meters.toDouble()
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