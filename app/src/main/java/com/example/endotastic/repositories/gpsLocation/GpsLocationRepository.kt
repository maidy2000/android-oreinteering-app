package com.example.endotastic.repositories.gpsLocation

import androidx.lifecycle.LiveData
import com.example.endotastic.daos.GpsLocationDao

class GpsLocationRepository(private val gpsLocationDao: GpsLocationDao) {

    suspend fun getAllBySessionId(gpsSessionId: Int): List<GpsLocation> {
        return gpsLocationDao.getAllBySessionId(gpsSessionId)
    }

    suspend fun addGpsLocation(gpsLocation: GpsLocation) {
        gpsLocationDao.addGpsLocation(gpsLocation)
    }

    suspend fun addGpsLocations(gpsLocations: List<GpsLocation>) {
        gpsLocationDao.addGpsLocations(gpsLocations)
    }

}