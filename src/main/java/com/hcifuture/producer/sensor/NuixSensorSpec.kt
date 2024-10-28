package com.hcifuture.producer.sensor

import com.hcifuture.producer.common.utils.FunctionUtils

class NuixSensorSpec {
    companion object {
        fun flowName(sensor: NuixSensor, suffix: String): String {
            return "${sensor.name}#${suffix}"
        }

        fun proxyFlowName(sensor: NuixSensor, flow: String): String {
            return "${sensor.name}#${flow.split('#').last()}"
        }

        fun lifecycleFlowName(sensor: NuixSensor): String {
            return flowName(sensor, "lifecycle_state")
        }
        fun refriedLifecycleFlow(value: Any): NuixSensorState =
            FunctionUtils.reifiedValue<NuixSensorState>(value)
    }
}