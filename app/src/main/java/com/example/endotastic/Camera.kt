package com.example.endotastic

import com.example.endotastic.enums.CameraMode

class Camera(var mode: CameraMode = CameraMode.NORTH_UP, var zoom: Float = 16f) {
    //todo et zoomi saaks timmida

    fun getBearing() : Float? {
        return if (mode == CameraMode.NORTH_UP) {
            0f
        } else {
            //todo kas teiste modede jaoks on ka vaja bearingut?
            // jah, Direction up puhul tuleb arvutada bearing
            null
        }
    }
    fun isRotateEnabled() : Boolean {
        return when (mode) {
            CameraMode.NORTH_UP -> false
            CameraMode.DIRECTION_UP -> false
            CameraMode.FREE -> true
            CameraMode.USER_CHOSEN_UP -> false
        }
    }
}