package com.example.endotastic

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.example.endotastic.repositories.gpsSession.GpsSession
import com.example.endotastic.databases.GpsSessionDatabase
import com.example.endotastic.repositories.gpsSession.GpsSessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val readAllData: LiveData<List<GpsSession>>
    private val repository: GpsSessionRepository

    init {
        val gpsSessionDao = GpsSessionDatabase.getDatabase(application).gpsSessionDao()
        repository = GpsSessionRepository(gpsSessionDao)
        readAllData = repository.readAllData
    }

    fun addGpsSession(gpsSession: GpsSession){
        viewModelScope.launch(Dispatchers.IO) {
            repository.addGpsSession(gpsSession)
        }
    }
}