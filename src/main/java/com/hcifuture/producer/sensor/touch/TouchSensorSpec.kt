package com.hcifuture.producer.sensor.touch

import com.hcifuture.producer.sensor.NuixSensor
import com.hcifuture.producer.sensor.NuixSensorSpec

class TouchSensorSpec {
    companion object {
        fun touchFlowName(sensor: NuixSensor): String {
            return NuixSensorSpec.flowName(sensor, "touch_shared")
        }
    }
}