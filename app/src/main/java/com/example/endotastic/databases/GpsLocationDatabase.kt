package com.example.endotastic.databases

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.endotastic.daos.GpsLocationDao
import com.example.endotastic.repositories.gpsLocation.GpsLocation

@Database(entities = [GpsLocation::class], version = 1, exportSchema = true)
abstract class GpsLocationDatabase : RoomDatabase(){

    abstract fun gpsLocationDao(): GpsLocationDao

    companion object {
        @Volatile
        private var INSTANCE: GpsLocationDatabase? = null

        fun getDatabase(context: Context): GpsLocationDatabase {
            val tempInstance = INSTANCE
            if (tempInstance != null) {
                return tempInstance
            }
            synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GpsLocationDatabase::class.java,
                    "gps_location_database"
                ).build()
                INSTANCE = instance
                return instance
            }
        }
    }
}