package com.hcifuture.producer.sensor.location

import android.location.LocationManager
import com.hcifuture.producer.sensor.NuixSensor
import com.hcifuture.producer.sensor.NuixSensorSpec

class LocationSensorSpec {

    companion object {

        const val TYPE_GPS = 1
        const val TYPE_NETWORK = 2

        fun providerNameToType(providerName: String): Int {
            return when (providerName) {
                LocationManager.GPS_PROVIDER -> TYPE_GPS
                LocationManager.NETWORK_PROVIDER -> TYPE_NETWORK
                else -> 0
            }
        }

        fun locationFlowName(sensor: NuixSensor): String {
            return NuixSensorSpec.flowName(sensor, "location_shared")
        }
    }
}