package com.example.endotastic.repositories.gpsLocation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.example.endotastic.R
import com.example.endotastic.enums.GpsLocationType
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Marker

@Entity(tableName = "gps_location_table")
data class GpsLocation(
    @PrimaryKey(autoGenerate = true)
    val id: Int,
    val typeId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val altitude: Double,
    val verticalAccuracy: Float,
    val gpsSessionId: Int,
    val recordedAt: String
) {

    @Ignore
    var marker: Marker? = null
    @Ignore
    var isVisited: Boolean = false
    @Ignore
    var secondsToReach: Long? = null
    @Ignore
    var distanceCoveredFrom: Int = 0

    private fun getLat() = latitude
    private fun getLng() = longitude

    fun getLocation() : Location {
        return Location(LocationManager.GPS_PROVIDER).apply {
            latitude = getLat()
            longitude = getLng()
        }
    }

    fun getIcon(context: Context): BitmapDescriptor? {
        return when (typeId) {
            GpsLocationType.WP.id -> {
                if (isVisited) {
                    BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                } else {
                    BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)
                }
            }
            GpsLocationType.CP.id -> {
                if (isVisited) {
                    bitmapFromVector(context, R.drawable.flag_green)
                } else {
                    bitmapFromVector(context, R.drawable.flag_red)
                }
            }
            else -> {return null}
        }
    }

    private fun bitmapFromVector(context: Context, vectorResId: Int): BitmapDescriptor {
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

}