package com.hcifuture.producer.sensor.video

import com.hcifuture.producer.sensor.NuixSensor
import com.hcifuture.producer.sensor.NuixSensorSpec

class VideoSensorSpec {
    companion object {
        fun videoFlowName(sensor: NuixSensor): String {
            return NuixSensorSpec.flowName(sensor, "video_shared")
        }
    }
}