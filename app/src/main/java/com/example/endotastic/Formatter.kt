package com.example.endotastic

import android.util.Log

class Formatter {
    companion object {
        private val TAG = this::class.java.declaringClass!!.simpleName
    }

    fun formatDistance(distance: Int): String {
        if (distance <= 0) return "--.---"
        val kilometers = distance / 1000
        val meters = distance % 1000
        val m = (if (meters < 10) "00" else if (meters < 100) "0" else "") + meters
        Log.d(TAG, "$kilometers.$meters=$distance")
        return "$kilometers.${m}"
    }

    fun formatPace(seconds: Long, meters: Int): String {
        if (meters <= 0) return "--:--"
        var pace = (seconds.toDouble() / meters.toDouble())
        pace = pace * 1000 / 60

        val min = pace / 1
        val sec = pace % 1 * 60

        val MM = (if (min.toInt() < 10) "0" else "") + min.toInt()
        val SS = (if (sec < 10) "0" else "") + sec.toInt()
        return "$MM.${SS}"
    }

    fun formatTime(elapsed: Long): String {
        val secondsLeft: Long = elapsed % 3600 % 60
        val minutes = Math.floor((elapsed % 3600 / 60).toDouble()).toInt()
        val hours = Math.floor((elapsed / 3600).toDouble()).toInt()

        val HH = (if (hours < 10) "0" else "") + hours
        val MM = (if (minutes < 10) "0" else "") + minutes
        val SS = (if (secondsLeft < 10) "0" else "") + secondsLeft

        return "$HH:$MM:$SS"
    }
}