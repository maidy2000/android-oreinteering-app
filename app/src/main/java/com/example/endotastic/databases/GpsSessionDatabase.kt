package com.example.endotastic.databases

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.endotastic.daos.GpsSessionDao
import com.example.endotastic.repositories.gpsSession.GpsSession

@Database(
    entities = [GpsSession::class],
    version = 1,
    exportSchema = true
//    autoMigrations = [
//        AutoMigration(from = 1, to = 2)
//    ]
)
abstract class GpsSessionDatabase : RoomDatabase() {

    abstract fun gpsSessionDao(): GpsSessionDao

    companion object {
        @Volatile
        private var INSTANCE: GpsSessionDatabase? = null

        fun getDatabase(context: Context): GpsSessionDatabase {
            val tempInstance = INSTANCE
            if (tempInstance != null) {
                return tempInstance
            }
            synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GpsSessionDatabase::class.java,
                    "gps_session_database"
                ).build()
                INSTANCE = instance
                return instance
            }
        }
    }
}