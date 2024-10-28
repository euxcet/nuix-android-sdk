package com.hcifuture.producer.sensor.audio

import com.hcifuture.producer.sensor.NuixSensor
import com.hcifuture.producer.sensor.NuixSensorSpec

class AudioSensorSpec {
    companion object {
        fun audioFlowName(sensor: NuixSensor): String {
            return NuixSensorSpec.flowName(sensor, "audio_shared")
        }
    }
}