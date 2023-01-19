package com.example.endotastic

import android.location.Location
import android.location.LocationManager

class C {
    companion object {
        const val NOTIFICATION_CHANNEL = "Default Channel"
        const val API = "https://sportmap.akaver.com/api/v1/"
        const val NOTIFICATION_ID = 322321
        const val START_SESSION = "endotastic.PLAY_PAUSE"
        const val LOCATION_UPDATE = "endotastic.LOCATION_UPDATE"
        const val LOCATION_UPDATE_LAT = "endotastic.LOCATION_UPDATE_LAT"
        const val LOCATION_UPDATE_LON = "endotastic.LOCATION_UPDATE_LON"
        const val LOCATION_UPDATE_ACC = "endotastic.LOCATION_UPDATE_ACC"
        const val LOCATION_UPDATE_ALT = "endotastic.LOCATION_UPDATE_ALT"
        const val LOCATION_UPDATE_VAC = "endotastic.LOCATION_UPDATE_VAC"
        const val ACCEPTABLE_POINT_DISTANCE = 20f // should be 5-10m. For testing purposes is 20m
        val TALLINN_LOCATION = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = 59.436962
            longitude = 24.753574
        }
    }
}