package com.example.endotastic.repositories.gpsSession

import androidx.lifecycle.LiveData
import com.example.endotastic.daos.GpsSessionDao

class GpsSessionRepository(private val gpsSessionDao: GpsSessionDao) {

    val readAllData: LiveData<List<GpsSession>> = gpsSessionDao.readAllData()

    suspend fun addGpsSession(gpsSession: GpsSession): Long {
        return gpsSessionDao.addGpsSession(gpsSession)
    }

    suspend fun getGpsSessionWithLargestId(): GpsSession {
        return gpsSessionDao.getGpsSessionWithLargestId()
    }

    suspend fun updateGpsSession(gpsSession: GpsSession) {
        gpsSessionDao.updateGpsSession(gpsSession)
    }

}