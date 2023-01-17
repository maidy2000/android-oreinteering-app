package com.example.endotastic

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.*

class LocationService : Service() {
    companion object {
        private val TAG = this::class.java.declaringClass!!.simpleName
    }


    private var locationRequest =
        LocationRequest.Builder(2000).setWaitForAccurateLocation(false).setMinUpdateIntervalMillis(1000)
            .setMaxUpdateDelayMillis(1000).setPriority(Priority.PRIORITY_HIGH_ACCURACY).build()


    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private var currentLocation: Location? = null
    private val formatter = Formatter()


    private var counter = 0

    override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
    }

    override fun onCreate() {
        super.onCreate()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                locationReceive(locationResult.lastLocation!!)
            }
        }

        getLastLocation()

    }


    @SuppressLint("MissingPermission")
    private fun getLastLocation() {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.

            return
        }

        Log.d(TAG, "Starting initial location receive")

        fusedLocationClient.lastLocation.addOnCompleteListener { task ->
            if (task.isSuccessful && task.result != null) {
                locationReceive(task.result)
            } else {
                Log.w(TAG, "Failed to receive initial location")
            }
        }
    }

    fun locationReceive(location: Location) {
        Log.d(TAG, "locationReceive")

        if (currentLocation != null) {
            val locationDistance = location.distanceTo(currentLocation!!)
            if (locationDistance in 2.0..50.0) {
                Log.d(TAG, "step of ${locationDistance}m")

                val intent = Intent(C.LOCATION_UPDATE)
                intent.putExtra(C.LOCATION_UPDATE_LAT, location.latitude)
                intent.putExtra(C.LOCATION_UPDATE_LON, location.longitude)
                intent.putExtra(C.LOCATION_UPDATE_ACC, location.accuracy)
                intent.putExtra(C.LOCATION_UPDATE_ALT, location.altitude)
                intent.putExtra(C.LOCATION_UPDATE_VAC, location.verticalAccuracyMeters)
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)

            }
        }
        currentLocation = location
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")

        showNotification()

        requestLocationUpdates()

        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
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
        fusedLocationClient.requestLocationUpdates(
            locationRequest, locationCallback!!, Looper.myLooper()
        )
    }

    private fun showNotification() {
        Log.d(TAG, "showNotification")
//        val notifyView = RemoteViews(packageName, R.layout.map_notification)

//        notifyView.setTextViewText(R.id.textViewTotalDistance,
//            viewModel.totalDistance.value?.let { formatter.formatDistance(it) })
//
//        notifyView.setTextViewText(R.id.textViewTotalTimeElapsed,
//            viewModel.totalTimeElapsed.value?.let { formatter.formatTime(it) })
//
//        notifyView.setTextViewText(R.id.textViewTotalPace,
//            viewModel.totalDistance.value?.let { distance ->
//                viewModel.totalTimeElapsed.value?.let { time ->
//                    formatter.formatPace(
//                        time,
//                        distance
//                    )
//                }
//            })

//        notifyView.setTextViewText(R.id.textViewDistanceCoveredFromWaypoint, formatter.formatDistance())
//        notifyView.setTextViewText(R.id.textViewDirectDistanceFromWaypoint, viewModel.totalDistance.value.toString())
//        notifyView.setTextViewText(R.id.textViewWaypointPace, viewModel.totalDistance.value.toString())
//
//        notifyView.setTextViewText(R.id.textViewDistanceCoveredFromCheckpoint, viewModel.totalDistance.value.toString())
//        notifyView.setTextViewText(R.id.textViewDirectDistanceFromCheckpoint, viewModel.totalDistance.value.toString())
//        notifyView.setTextViewText(R.id.textViewCheckpointPace, viewModel.totalDistance.value.toString())


        val builder =
            NotificationCompat.Builder(applicationContext, C.NOTIFICATION_CHANNEL).setSmallIcon(R.drawable.map)
                .setPriority(NotificationCompat.PRIORITY_MIN).setOngoing(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

//        builder.setContent(notifyView)

        startForeground(C.NOTIFICATION_ID, builder.build())
    }


    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")

        fusedLocationClient.removeLocationUpdates(locationCallback!!)

        NotificationManagerCompat.from(applicationContext).cancelAll()
    }

}