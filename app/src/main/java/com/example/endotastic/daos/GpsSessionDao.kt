package com.example.endotastic.daos

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.endotastic.repositories.gpsSession.GpsSession

@Dao
interface GpsSessionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) //todo kas muuta
    fun addGpsSession(gpsSession: GpsSession): Long

    @Query("SELECT * FROM gps_session_table ORDER BY id ASC")
    fun readAllData(): LiveData<List<GpsSession>>

    @Query("SELECT * FROM gps_session_table ORDER BY id DESC LIMIT 1")
    fun getGpsSessionWithLargestId(): GpsSession

    @Update
    fun updateGpsSession(gpsSession: GpsSession)

}