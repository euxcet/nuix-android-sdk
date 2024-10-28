package com.hcifuture.producer.sensor.data


enum class RingV2StatusType {
    BATTERY_LEVEL,
    BATTERY_STATUS,
    HARDWARE_VERSION,
    SOFTWARE_VERSION,
}

data class RingV2StatusData (
    val type: RingV2StatusType,
    val batteryLevel: Int = 0,
    val batteryStatus: Int = 0,
    val softwareVersion: String = "",
    val hardwareVersion: String = "",
)