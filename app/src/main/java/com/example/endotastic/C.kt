package com.example.endotastic

import android.location.Location
import android.location.LocationManager

class C {
    companion object {
        const val NOTIFICATION_CHANNEL = "Default Channel"
        const val NOTIFICATION_ID = 1234
        const val LOCATION_UPDATE = "taltech.LOCATION_UPDATE"
        const val LOCATION_UPDATE_LAT = "taltech.LOCATION_UPDATE_LAT"
        const val LOCATION_UPDATE_LON = "taltech.LOCATION_UPDATE_LON"
        const val ACCEPTABLE_POINT_DISTANCE = 20f // should be 5-10m. For testing purposes is 20m
        val TALLINN_LOCATION = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = 59.436962
            longitude = 24.753574
        }
    }
}