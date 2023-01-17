package com.example.endotastic.repositories.gpsLocation

import androidx.lifecycle.LiveData
import com.example.endotastic.daos.GpsLocationDao

class GpsLocationRepository(private val gpsLocationDao: GpsLocationDao) {

    val readAllData: LiveData<List<GpsLocation>> = gpsLocationDao.readAllData()

    fun readDataByGpsSessionId(gpsSessionId: Int): LiveData<List<GpsLocation>> {
        return gpsLocationDao.readDataByGpsSessionId(gpsSessionId)
    }

    suspend fun addGpsLocation(gpsLocation: GpsLocation) {
        gpsLocationDao.addGpsLocation(gpsLocation)
    }

    suspend fun addGpsLocations(gpsLocations: List<GpsLocation>) {
        gpsLocationDao.addGpsLocations(gpsLocations)
    }

}