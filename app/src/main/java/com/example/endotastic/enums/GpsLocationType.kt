package com.example.endotastic.enums

enum class GpsLocationType(val id: String) {
    LOC("00000000-0000-0000-0000-000000000001"), // Regular periodic location update
    WP("00000000-0000-0000-0000-000000000002"), // Waypoint - temporary location, used as navigation aid
    CP("00000000-0000-0000-0000-000000000003") // Checkpoint - found on terrain the location marked on the paper map
}