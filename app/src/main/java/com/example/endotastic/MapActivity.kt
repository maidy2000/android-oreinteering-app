package com.example.endotastic

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
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
import androidx.lifecycle.ViewModelProvider
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.endotastic.enums.CameraMode
import com.example.endotastic.enums.GpsLocationType
import com.example.endotastic.repositories.gpsLocation.GpsLocation
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import java.util.*


class MapActivity : AppCompatActivity(), OnMapReadyCallback, OnMapsSdkInitializedCallback {
    companion object {
        private val TAG = this::class.java.declaringClass!!.simpleName
    }

    private lateinit var buttonCancel: ImageButton
    private lateinit var buttonConfirm: ImageButton
    private lateinit var buttonStartStop: ImageButton
    private lateinit var buttonAddWaypoint: ImageButton
    private lateinit var buttonAddCheckpoint: ImageButton
    private lateinit var buttonToggleCameraDirection: ImageButton

    private lateinit var textViewTotalPace: TextView
    private lateinit var textViewTotalDistance: TextView
    private lateinit var textViewTotalTimeElapsed: TextView

    private lateinit var textViewCameraMode: TextView // TODO: This is here for testing
    private lateinit var textViewWaypointPace: TextView
    private lateinit var textViewCheckpointPace: TextView
    private lateinit var textViewDirectDistanceFromWaypoint: TextView
    private lateinit var textViewDistanceCoveredFromWaypoint: TextView
    private lateinit var textViewDirectDistanceFromCheckpoint: TextView
    private lateinit var textViewDistanceCoveredFromCheckpoint: TextView

    private lateinit var imageViewWaypointPointer: ImageView
    private lateinit var imageViewCheckpointPointer: ImageView

    private lateinit var mMap: GoogleMap
    private lateinit var viewModel: MapActivityViewModel

    private var currentCamera: Camera = Camera()
    private val formatter: Formatter = Formatter()
    private var broadcastReceiver = InnerBroadcastReceiver()
    private var broadcastReceiverIntentFilter = IntentFilter()
    private var currentLocation: Location? = C.TALLINN_LOCATION

    @RequiresApi(Build.VERSION_CODES.M)

    //region Activity lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        MapsInitializer.initialize(applicationContext, MapsInitializer.Renderer.LATEST, this)

        viewModel = ViewModelProvider(this)[MapActivityViewModel::class.java]
        viewModel.createLauncherIntent(Intent(this, MapActivity::class.java))

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment

        mapFragment.getMapAsync(this)

        createNotificationChannel()
        startLocationService()

        //region findViewById
        buttonCancel = findViewById(R.id.imageButtonCancel)
        buttonStartStop = findViewById(R.id.buttonStartStop)
        buttonConfirm = findViewById(R.id.imageButtonConfirm)
        textViewTotalPace = findViewById(R.id.textViewTotalPace)
        buttonAddWaypoint = findViewById(R.id.buttonAddWaypoint)
        textViewWaypointPace = findViewById(R.id.textViewWaypointPace)
        imageViewWaypointPointer = findViewById(R.id.imageViewWaypoint)
        textViewTotalDistance = findViewById(R.id.textViewTotalDistance)
        buttonAddCheckpoint = findViewById(R.id.buttonAddCheckpointOnNotification)
        textViewCheckpointPace = findViewById(R.id.textViewCheckpointPace)
        imageViewCheckpointPointer = findViewById(R.id.imageViewCheckpoint)
        textViewTotalTimeElapsed = findViewById(R.id.textViewTotalTimeElapsed)
        buttonToggleCameraDirection = findViewById(R.id.imageButtonToggleCameraDirection)
        textViewDirectDistanceFromWaypoint = findViewById(R.id.textViewDirectDistanceFromWaypoint)
        textViewDistanceCoveredFromWaypoint = findViewById(R.id.textViewDistanceCoveredFromWaypoint)
        textViewDirectDistanceFromCheckpoint = findViewById(R.id.textViewDirectDistanceFromCheckpoint)
        textViewDistanceCoveredFromCheckpoint = findViewById(R.id.textViewDistanceCoveredFromCheckpoint)
        //endregion

        //region setOnClickListener
        buttonStartStop.setOnClickListener { viewModel.startEndGpsSession() }

        buttonToggleCameraDirection.setOnClickListener { toggleCameraDirection() }
        buttonAddWaypoint.setOnClickListener { startAddGpsLocation(GpsLocationType.WP) }
        buttonAddCheckpoint.setOnClickListener { startAddGpsLocation(GpsLocationType.CP) }
        //endregion

        //region observe

        // isCurrentSessionActive
        viewModel.isCurrentSessionActive.observe(this) {
            startEndGpsSession()
        }

        // startAddLocation
        viewModel.addLocation.observe(this) {
            if (it == null || it == GpsLocationType.LOC) return@observe
            startAddGpsLocation(it)
            viewModel.addLocation.value = null
        }

        // totalTimeElapsed & totalPace
        viewModel.totalTimeElapsed.observe(this) {
            textViewTotalTimeElapsed.text = formatter.formatTime(it)
            textViewTotalPace.text = viewModel.totalDistance.value?.let { distance ->
                formatter.formatPace(
                    it,
                    distance
                )
            }
        }

        // totalDistance & totalPace
        viewModel.totalDistance.observe(this) {
            textViewTotalDistance.text = formatter.formatDistance(it)
            textViewTotalPace.text =
                viewModel.totalTimeElapsed.value?.let { time -> formatter.formatPace(time, it) }
        }

        // distanceCoveredFromCheckpoint & checkpointPace
        viewModel.distanceCoveredFromCheckpoint.observe(this) {
            textViewDistanceCoveredFromCheckpoint.text = formatter.formatDistance(it)
            val visitedAt = viewModel.getLastVisitedCheckpointVisitedAt()
            textViewCheckpointPace.text =
                visitedAt?.let { _visitedAt -> formatter.formatPace(_visitedAt, it) }
        }

        // directDistanceFromCheckpoint
        viewModel.directDistanceFromCheckpoint.observe(this) {
            textViewDirectDistanceFromCheckpoint.text = formatter.formatDistance(it)
        }

        // distanceCoveredFromWaypoint & waypointPace
        viewModel.distanceCoveredFromWaypoint.observe(this) {
            textViewDistanceCoveredFromWaypoint.text = formatter.formatDistance(it)
            val visitedAt = viewModel.getLastVisitedWaypointVisitedAt()
            textViewWaypointPace.text =
                visitedAt?.let { _visitedAt -> formatter.formatPace(_visitedAt, it) }
        }

        // directDistanceFromWaypoint
        viewModel.directDistanceFromWaypoint.observe(this) {
            textViewDirectDistanceFromWaypoint.text = formatter.formatDistance(it)
        }
        textViewCameraMode = findViewById(R.id.textViewCameraMode) // todo remove later

        // trigger redraw of points of interests
        viewModel.updatePointsOfInterest.observe(this) {
            if (it) {
                viewModel.updatePointsOfInterest.value = false
                redrawPointsOfInterest()
            }
        }

        // update polyline
        viewModel.polylineOptions.observe(this) {
            Log.d(TAG, "updatePolyline")
            if (this::mMap.isInitialized) {
                val polyline = mMap.addPolyline(it)
                viewModel.setPolyline(polyline)
            }
        }
        //endregion

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
        broadcastReceiverIntentFilter.addAction(C.PLAY_PAUSE)
    }


    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")

        //todo kas seda on vaja?
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
        //todo destroy notification and LocationService
    }

    //endregion

    private fun redrawPointsOfInterest() {
        mMap.clear()
        for (point in viewModel.getPointsOfInterests()) {
            Log.d(TAG, "redrawPointsOfInterest, type=${point.typeId}")
            drawPoint(point)
        }
    }

    private fun startEndGpsSession() {
        if (viewModel.isCurrentSessionActive.value == true) {
            buttonStartStop.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.pause))
        } else {
            buttonStartStop.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.play))
        }
    }

    //region Location

    @SuppressLint("MissingPermission")
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }

        mMap.isMyLocationEnabled = true
        redrawPointsOfInterest()
        viewModel.polylineOptions.value?.let { mMap.addPolyline(it) }
    }

    private fun startLocationService() {
        //Log.d(TAG, "startLocationService")
        val intent = Intent(applicationContext, LocationService::class.java)

//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            // long running bgr service without ui (with notification)
//            startForegroundService(intent)
//        } else {
        startService(intent)
//        }
    }

    fun locationUpdateIn(lat: Double, lng: Double, acc: Float, alt: Double, vac: Float) {
        Log.d(TAG, "updateLocation, ${lat} ${lng}")
        viewModel.locationUpdateIn(lat, lng, acc, alt, vac)

        if (!this::mMap.isInitialized) return
        currentLocation = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = lat
            longitude = lng
        }

        updateCamera()
    }

    //endregion

    //region Camera

    private fun toggleCameraDirection() {
        when (currentCamera.mode) {
            CameraMode.NORTH_UP -> currentCamera.mode = CameraMode.DIRECTION_UP
            CameraMode.DIRECTION_UP -> currentCamera.mode = CameraMode.FREE
            CameraMode.FREE -> currentCamera.mode = CameraMode.NORTH_UP
            CameraMode.USER_CHOSEN_UP -> currentCamera.mode = CameraMode.USER_CHOSEN_UP
        }
        //Log.d(TAG, "toggleCameraDirection: ${currentCamera.mode}")
        textViewCameraMode.text = currentCamera.mode.name
        updateCamera()
    }

    private fun updateCamera() {
        mMap.uiSettings.isRotateGesturesEnabled = currentCamera.isRotateEnabled()
        //Log.d(TAG, "updateCamera: ${currentCamera.mode}")

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
    private fun startAddGpsLocation(type: GpsLocationType) {
        //Log.d(TAG, "startAddPoint: $type")
//        val savedCamera = currentCamera
//        currentCamera = Camera(CameraMode.FREE, savedCamera.zoom)
        showPointer(type)
        buttonConfirm.setOnClickListener {
            var pointOfInterest = viewModel.createGpsLocation(
                type = type,
                latitude = mMap.cameraPosition.target.latitude,
                longitude = mMap.cameraPosition.target.longitude
            )
            pointOfInterest = drawPoint(pointOfInterest)
            viewModel.savePointOfInterest(pointOfInterest)
            endAddPoint()
        }
        buttonCancel.setOnClickListener { endAddPoint() }
        buttonConfirm.visibility = View.VISIBLE
        buttonCancel.visibility = View.VISIBLE
    }

    private fun drawPoint(gpsLocation: GpsLocation): GpsLocation {
        gpsLocation.marker?.remove()

        if (gpsLocation.typeId == GpsLocationType.WP.id) {
            viewModel.removeCurrentWaypointMarker()
        }

        val pointMarker = MarkerOptions()
            .position(LatLng(gpsLocation.latitude, gpsLocation.longitude))
            .icon(gpsLocation.getIcon(this))

        val marker = mMap.addMarker(pointMarker)
        gpsLocation.marker = marker
        return gpsLocation
    }

    private fun endAddPoint(/*savedCamera: Camera*/) {
        buttonCancel.visibility = View.GONE
        buttonConfirm.visibility = View.GONE
        clearPointers()
//        currentCamera = savedCamera
    }

    private fun showPointer(type: GpsLocationType) {
        clearPointers()
        if (type == GpsLocationType.WP) {
            imageViewWaypointPointer.visibility = View.VISIBLE
        } else if (type == GpsLocationType.CP) {
            imageViewCheckpointPointer.visibility = View.VISIBLE
        }
    }

    private fun clearPointers() {
        imageViewWaypointPointer.visibility = View.GONE
        imageViewCheckpointPointer.visibility = View.GONE
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
            Log.d(TAG, "onReceive ${p1.toString()}")
            when (p1!!.action) {
                C.LOCATION_UPDATE -> {
                    locationUpdateIn(
                        p1.getDoubleExtra(C.LOCATION_UPDATE_LAT, 0.0),
                        p1.getDoubleExtra(C.LOCATION_UPDATE_LON, 0.0),
                        p1.getFloatExtra(C.LOCATION_UPDATE_ACC, 0f),
                        p1.getDoubleExtra(C.LOCATION_UPDATE_ALT, 0.0),
                        p1.getFloatExtra(C.LOCATION_UPDATE_VAC, 0f),
                    )
                }
            }
        }
    }

    override fun onMapsSdkInitialized(renderer: MapsInitializer.Renderer) {
        when (renderer) {
            MapsInitializer.Renderer.LATEST -> Log.d("MapsDemo", "The latest version of the renderer is used.")
            MapsInitializer.Renderer.LEGACY -> Log.d("MapsDemo", "The legacy version of the renderer is used.")
        }
    }


}