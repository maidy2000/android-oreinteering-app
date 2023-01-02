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
import java.util.*
import java.util.concurrent.TimeUnit


class MapsActivity : AppCompatActivity(), OnMapReadyCallback {
    companion object {
        private val TAG = this::class.java.declaringClass!!.simpleName
    }

    private lateinit var buttonStartStop: ImageButton
    lateinit var textViewTotalTimeElapsed: TextView

    private lateinit var mMap: GoogleMap
    private var drawingPolylineActive = false
    private var startDrawingNewPolyline = false

    private var polylineOptions = PolylineOptions().width(10f).color(Color.RED)
    private var currentPolyline: Polyline? = null
    private var polylines: MutableList<Polyline> = mutableListOf()

    private lateinit var stopwatch: Stopwatch
    private var timer: Timer = Timer()

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
            // TODO format time hh:mm:ss
            textViewTotalTimeElapsed.text = stopwatch.elapsed(TimeUnit.SECONDS).toString()
        }
    }

    //    private inner class UIHandler : Handler(Looper.getMainLooper()) {
//        override fun handleMessage(msg: Message) {
//            super.handleMessage(msg)
//            textViewTotalTimeElapsed.text = stopwatch.elapsed(TimeUnit.SECONDS).toString()
//        }
//    }

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
        drawingPolylineActive = !drawingPolylineActive

        if (drawingPolylineActive) {
            startDrawingNewPolyline = true
            polylineOptions = PolylineOptions().width(10f).color(Color.RED)
            buttonStartStop.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.pause))
        } else {
            if (currentPolyline != null) {
                polylines.add(currentPolyline!!)
            }
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

        if (drawingPolylineActive) {
            if (currentPolyline != null && !startDrawingNewPolyline) {
                currentPolyline!!.remove()
            }
            startDrawingNewPolyline = !startDrawingNewPolyline

            polylineOptions.add(latLng)

            currentPolyline = mMap.addPolyline(polylineOptions)
        }
        mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng))
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