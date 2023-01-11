package com.example.endotastic

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class MapPoint(
    private val workoutId : Int,
    private val latitude : Double,
    private val longitude : Double,
    private val elapsedFromStart : Long
    ) {
    @PrimaryKey(autoGenerate = true)
    var id : Int = 0
}