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
import com.example.endotastic.enums.PointOfInterestType
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.firebase.crashlytics.buildtools.reloc.com.google.common.base.Stopwatch
import java.util.*


class MapActivity : AppCompatActivity(), OnMapReadyCallback {
    companion object {
        private val TAG = this::class.java.declaringClass!!.simpleName
    }

    private lateinit var viewModel : MapActivityViewModel
    private val formatter : Formatter = Formatter()

    private lateinit var buttonStartStop: ImageButton
    private lateinit var buttonAddCheckpoint: ImageButton
    private lateinit var buttonAddWaypoint: ImageButton
    private lateinit var buttonToggleCameraDirection: ImageButton
    private lateinit var buttonConfirm: ImageButton
    private lateinit var buttonCancel: ImageButton

    lateinit var textViewTotalTimeElapsed: TextView
    private lateinit var textViewTotalDistance: TextView
    private lateinit var textViewTotalPace: TextView

    private lateinit var textViewDistanceCoveredFromCheckpoint: TextView
    private lateinit var textViewDirectDistanceFromCheckpoint: TextView
    private lateinit var textViewCheckpointPace: TextView
    private lateinit var textViewDistanceCoveredFromWaypoint: TextView
    private lateinit var textViewDirectDistanceFromWaypoint: TextView
    private lateinit var textViewWaypointPace: TextView

    private lateinit var textViewCameraMode: TextView

    private lateinit var imageViewCheckpointPointer: ImageView
    private lateinit var imageViewWaypointPointer: ImageView

    private lateinit var mMap: GoogleMap
//    private var isWorkoutActive = false
    private var startDrawingNewPolyline = false

    private var markers : MutableList<Marker> = mutableListOf()

    private var polylineOptions = PolylineOptions().width(10f).color(Color.RED)
    private var currentPolyline: Polyline? = null
    private var polylines: MutableList<Polyline> = mutableListOf()

    private lateinit var stopwatch: Stopwatch
    private var timer: Timer = Timer()
//    private var handler: UIHandler = UIHandler()

    private var currentLocation: Location? = C.TALLINN_LOCATION
    private var totalDistance = 0

    private var currentCamera: Camera = Camera()

    private var unvisitedPointOfInterests: MutableList<PointOfInterest> = mutableListOf()
    private var visitedCheckpoints: MutableList<PointOfInterest> = mutableListOf()
    private var lastVisitedCheckpoint: PointOfInterest? = null

    private var currentWaypoint: PointOfInterest? = null
    private var lastVisitedWaypoint: PointOfInterest? = null

    private var userLocationMarker: Marker? = null

    private var broadcastReceiver = InnerBroadcastReceiver()
    private var broadcastReceiverIntentFilter = IntentFilter()

    @RequiresApi(Build.VERSION_CODES.M)

    //region Activity lifecycle


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
        viewModel = ViewModelProvider(this).get(MapActivityViewModel::class.java)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment

        mapFragment.getMapAsync(this)

        createNotificationChannel()
        startLocationService()

        stopwatch = Stopwatch.createUnstarted()

        //region buttons
        buttonStartStop = findViewById(R.id.imageButtonStartStop)
        buttonStartStop.setOnClickListener { startEndWorkout() }

        buttonAddCheckpoint = findViewById(R.id.imageButtonAddCheckpoint)
        buttonAddCheckpoint.setOnClickListener { startAddPointOfInterest(PointOfInterestType.Checkpoint) }
        buttonAddWaypoint = findViewById(R.id.imageButtonAddWaypoint)
        buttonAddWaypoint.setOnClickListener { startAddPointOfInterest(PointOfInterestType.Waypoint) }

        buttonConfirm = findViewById(R.id.imageButtonConfirm)
        buttonCancel = findViewById(R.id.imageButtonCancel)
        //endregion

        imageViewCheckpointPointer = findViewById(R.id.imageViewCheckpoint)
        imageViewWaypointPointer = findViewById(R.id.imageViewWaypoint)

        buttonToggleCameraDirection = findViewById(R.id.imageButtonToggleCameraDirection)
        buttonToggleCameraDirection.setOnClickListener { toggleCameraDirection() }

        //region textViews
        // totalTimeElapsed & totalPace
        textViewTotalTimeElapsed = findViewById(R.id.textViewTotalTimeElapsed)
        textViewTotalPace = findViewById(R.id.textViewTotalPace)
        viewModel.totalTimeElapsed.observe(this) {
            textViewTotalTimeElapsed.text = formatter.formatTime(it)
            textViewTotalPace.text = viewModel.totalDistance.value?.let { distance -> formatter.formatPace(it, distance) }
        }

        // totalDistance & totalPace
        textViewTotalDistance = findViewById(R.id.textViewTotalDistance)
        viewModel.totalDistance.observe(this) {
            textViewTotalDistance.text = formatter.formatDistance(it)
            textViewTotalPace.text = viewModel.totalTimeElapsed.value?.let { time -> formatter.formatPace(time, it) }
        }

        // trigger redraw of points of interests
        viewModel.updatePointsOfInterest.observe(this) {
            if (it) {
                viewModel.updatePointsOfInterest.value = false
                redrawPointsOfInterest()
            }
        }


        // distanceCoveredFromCheckpoint & checkpointPace
        textViewDistanceCoveredFromCheckpoint = findViewById(R.id.textViewDistanceCoveredFromCheckpoint)
        textViewCheckpointPace = findViewById(R.id.textViewCheckpointPace)
        viewModel.distanceCoveredFromCheckpoint.observe(this) {
            textViewDistanceCoveredFromCheckpoint.text = formatter.formatDistance(it)
            val visitedAt = viewModel.getLastVisitedCheckpointVisitedAt()
            textViewCheckpointPace.text = visitedAt?.let { _visitedAt -> formatter.formatPace(_visitedAt, it) }
        }

        // directDistanceFromCheckpoint
        textViewDirectDistanceFromCheckpoint = findViewById(R.id.textViewDirectDistanceFromCheckpoint)
        viewModel.directDistanceFromCheckpoint.observe(this) {
            textViewDirectDistanceFromCheckpoint.text = formatter.formatDistance(it)
        }

        // distanceCoveredFromWaypoint & waypointPace
        textViewDistanceCoveredFromWaypoint = findViewById(R.id.textViewDistanceCoveredFromWaypoint)
        textViewWaypointPace = findViewById(R.id.textViewWaypointPace)
        viewModel.distanceCoveredFromWaypoint.observe(this) {
            textViewDistanceCoveredFromWaypoint.text = formatter.formatDistance(it)
            val visitedAt = viewModel.getLastVisitedWaypointVisitedAt()
            textViewWaypointPace.text = visitedAt?.let { _visitedAt -> formatter.formatPace(_visitedAt, it) }
        }

        // directDistanceFromWaypoint
        textViewDirectDistanceFromWaypoint = findViewById(R.id.textViewDirectDistanceFromWaypoint)
        viewModel.directDistanceFromWaypoint.observe(this) {
            textViewDirectDistanceFromWaypoint.text = formatter.formatDistance(it)
        }
        textViewCameraMode = findViewById(R.id.textViewCameraMode) // todo remove later
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
    }

    override fun onResume() {
        super.onResume()
        //Log.d(TAG, "onResume")

        LocalBroadcastManager.getInstance(this)
            .registerReceiver(broadcastReceiver, broadcastReceiverIntentFilter)
    }

    override fun onPause() {
        super.onPause()
        //Log.d(TAG, "onPause")
    }

    override fun onDestroy() {
        super.onDestroy()
        //Log.d(TAG, "onDestroy")
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
    }

    private fun redrawPointsOfInterest() {
        mMap.clear()
        for (point in viewModel.getPointsOfInterests()) {
            Log.d(TAG, "redrawPointsOfInterest, id=${point.id} type=${point.type}")
            drawPoint(point)
        }
    }

    //endregion

    //region Stopwatch

    private fun startEndWorkout() {
        viewModel.startEndWorkout()
        if (viewModel.isWorkoutActive()) {
            buttonStartStop.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.pause))
        } else {
            buttonStartStop.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.play))
        }
    }

//    private fun startStopStopWatch() {
//        if (stopwatch.isRunning) {
//            stopwatch.stop()
//        } else {
//            stopwatch.start()
//            val updateStopwatchTask = UpdateStopwatchTask()
//            timer.schedule(updateStopwatchTask, 0, 10)
//        }
//    }

    private inner class UpdateStopwatchTask : TimerTask() {
        override fun run() {
            // siin peaks vb UIHandlerit jooksutama hoopis
//            handler.sendEmptyMessage(0)
        }
    }

//    private inner class UIHandler : Handler(Looper.getMainLooper()) {
//        override fun handleMessage(msg: Message) {
//            super.handleMessage(msg)
//            val totalElapsed = formatTime(stopwatch.elapsed(TimeUnit.SECONDS))
//            textViewTotalTimeElapsed.text = totalElapsed
//        }
//
//
//    }

    //endregion

    //region Location

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        redrawPointsOfInterest()
    }

    private fun startStopDrawingPolyline() {
        if (viewModel.isWorkoutActive()) {
            startDrawingNewPolyline = true
            polylineOptions = PolylineOptions().width(10f).color(Color.RED)
//            buttonStartStop.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.pause))
        } else {
            if (currentPolyline != null) {
                polylines.add(currentPolyline!!)
            }
            startDrawingNewPolyline = true
//            buttonStartStop.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.play))
        }
    }

    private fun startLocationService() {
        //Log.d(TAG, "startLocationService")
        val intent = Intent(applicationContext, LocationService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // long running bgr service without ui (with notification)
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    fun locationUpdateIn(lat: Double, lng: Double) {
        viewModel.locationUpdateIn(lat, lng)
        //todo drawing o map
        Log.d(TAG, "updateLocation, ${lat} ${lng}")
        if (mMap == null) return

        val latLng = LatLng(lat, lng)
        updateUserLocationMarker(latLng)
        if (viewModel.isWorkoutActive()) {
            drawPolyLine(latLng)
//            updateDistances(latLng)
//            updatePaces()
        }

        currentLocation = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = latLng.latitude
            longitude = latLng.longitude
        }
//
//        if (isWorkoutActive) {
//            checkPoints()
//        }
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
//    private fun updatePaces() {
//        val totalTime = stopwatch.elapsed(TimeUnit.SECONDS)
//        textViewTotalPace.text = formatter.formatPace(totalTime, totalDistance)
//
//        if (lastVisitedWaypoint != null) {
//            //Log.d(TAG, "updatePaces: waypoint")
//            textViewWaypointPace.text = formatter.formatPace(totalTime - lastVisitedWaypoint!!.visitedAt!!, lastVisitedWaypoint!!.totalDistanceFrom)
//        }
//        if (lastVisitedCheckpoint != null) {
//            //Log.d(TAG, "updatePaces: checkpoint")
//            textViewCheckpointPace.text = formatter.formatPace(totalTime - lastVisitedCheckpoint!!.visitedAt!!, lastVisitedCheckpoint!!.totalDistanceFrom)
//        }
//    }



//    private fun updateDistances(latLng: LatLng) {
//        val updatedLocation = Location(LocationManager.GPS_PROVIDER).apply {
//            latitude = latLng.latitude
//            longitude = latLng.longitude
//        }
//        if (currentLocation == null) return
//
//        val addedDistance = currentLocation!!.distanceTo(updatedLocation).toInt()
//
//        totalDistance += currentLocation!!.distanceTo(updatedLocation).toInt()
//        textViewTotalDistance.text = formatDistance(totalDistance)
//
//        // update waypoint distances
//        if (lastVisitedWaypoint != null) {
//            lastVisitedWaypoint!!.totalDistanceFrom += addedDistance
//            textViewDistanceCoveredFromWaypoint.text = formatDistance(lastVisitedWaypoint!!.totalDistanceFrom)
//
//            val directDistance = currentLocation!!.distanceTo(lastVisitedWaypoint!!.location).toInt()
//            textViewDirectDistanceFromWaypoint.text = formatDistance(directDistance)
//        }
//
//        // update checkpoint distances
//        if (lastVisitedCheckpoint != null) {
//            lastVisitedCheckpoint!!.totalDistanceFrom += addedDistance
//            textViewDistanceCoveredFromCheckpoint.text = formatDistance(lastVisitedCheckpoint!!.totalDistanceFrom)
//
//            val directDistance = currentLocation!!.distanceTo(lastVisitedCheckpoint!!.location).toInt()
//            textViewDirectDistanceFromCheckpoint.text = formatDistance(directDistance)
//        }
//        //todo update notification
//    }

//    private fun formatDistance(distance: Int): String {
//        val kilometers = distance / 1000
//        val decameters = distance % 1000 / 10
//        val km = (if (kilometers < 10) "0" else "") + kilometers
//        val dam = (if (decameters < 10) "0" else "") + decameters
//        //Log.d(TAG, "$km.$dam=$distance")
//        return "$km.${dam}km"
//    }

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
    private fun startAddPointOfInterest(type: PointOfInterestType) {
        //Log.d(TAG, "startAddPoint: $type")
//        val savedCamera = currentCamera
//        currentCamera = Camera(CameraMode.FREE, savedCamera.zoom)
        showPointer(type)
        buttonConfirm.setOnClickListener {
            var pointOfInterest = viewModel.createPointOfInterest(
                type = type,
                latitude = mMap.cameraPosition.target.latitude,
                longitude = mMap.cameraPosition.target.longitude)
            pointOfInterest = drawPoint(pointOfInterest)
            viewModel.savePointOfInterest(pointOfInterest)
            endAddPoint()
        }
        buttonCancel.setOnClickListener { endAddPoint() }
        buttonConfirm.visibility = View.VISIBLE
        buttonCancel.visibility = View.VISIBLE
    }
//
//    private fun confirmPoint(type: PointOfInterestType) {
//        val location = (Location(LocationManager.GPS_PROVIDER).apply {
//            latitude = mMap.cameraPosition.target.latitude
//            longitude = mMap.cameraPosition.target.longitude
//        })
//        val pointOfInterest = PointOfInterest(type, location)
//        drawPoint(pointOfInterest)
//    }

    private fun drawPoint(pointOfInterest: PointOfInterest): PointOfInterest {
        pointOfInterest.marker?.remove()

        // remove current waypoint as there can be only one waypoint at a time
        if (pointOfInterest.type == PointOfInterestType.Waypoint)
        {
            viewModel.removeCurrentWaypointMarker()
        }

        val pointMarker = MarkerOptions()
            .position(LatLng(pointOfInterest.latitude, pointOfInterest.longitude))
            .icon(pointOfInterest.getIcon(this))

        val marker = mMap.addMarker(pointMarker)
//        marker?.let { markers.add(it) }
        pointOfInterest.marker = marker
        return pointOfInterest
//        unvisitedPointOfInterests.add(pointOfInterest)
//        if (pointOfInterest.type == PointOfInterestType.Waypoint) {
//            currentWaypoint = pointOfInterest
//        }
    }

    private fun endAddPoint(/*savedCamera: Camera*/) {
        buttonCancel.visibility = View.GONE
        buttonConfirm.visibility = View.GONE
        clearPointers()
//        currentCamera = savedCamera
    }

    private fun showPointer(type: PointOfInterestType) {
        clearPointers()
        if (type == PointOfInterestType.Waypoint) {
            imageViewWaypointPointer.visibility = View.VISIBLE
        } else if (type == PointOfInterestType.Checkpoint) {
            imageViewCheckpointPointer.visibility = View.VISIBLE
        }
    }

    private fun clearPointers() {
        imageViewWaypointPointer.visibility = View.GONE
        imageViewCheckpointPointer.visibility = View.GONE
    }

//    private fun checkPoints() {
//        val visited = mutableListOf<PointOfInterest>()
//        for (point in unvisitedPointOfInterests) {
//            val distance = currentLocation?.distanceTo(point.location)
//
//            if (distance != null)
//                if (distance <= C.ACCEPTABLE_POINT_DISTANCE) {
//                    visited.add(point)
//                }
//        }
//        if (visited.isEmpty()) return
//
//        for (point in visited) {
//            point.isVisited = true
//            point.visitedAt = stopwatch.elapsed(TimeUnit.SECONDS)
//            point.totalDistanceFrom = currentLocation!!.distanceTo(point.location).toInt()
//
//            drawPoint(point)
//            if (point.type == PointOfInterestType.Checkpoint) {
//                visitedCheckpoints.add(point)
//                lastVisitedCheckpoint = point
//            } else if (point.type == PointOfInterestType.Waypoint){
//                lastVisitedWaypoint = point
//            }
//            unvisitedPointOfInterests.remove(point)
//        }
//    }

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
            //Log.d(TAG, "onRecieve")
            when (p1!!.action) {
                C.LOCATION_UPDATE -> {
                    //todo saadan siit hoopis viewmodelisse
                    locationUpdateIn(
                        p1.getDoubleExtra(C.LOCATION_UPDATE_LAT, 0.0),
                        p1.getDoubleExtra(C.LOCATION_UPDATE_LON, 0.0)
                    )
                }
            }
        }

    }


}