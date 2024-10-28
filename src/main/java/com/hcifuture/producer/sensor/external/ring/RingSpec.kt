package com.hcifuture.producer.sensor.external.ring

import com.hcifuture.producer.common.utils.FunctionUtils
import com.hcifuture.producer.sensor.NuixSensor
import com.hcifuture.producer.sensor.NuixSensorSpec
import com.hcifuture.producer.sensor.data.RingImuData
import com.hcifuture.producer.sensor.data.RingTouchData
import com.hcifuture.producer.sensor.data.RingV2AudioData
import com.hcifuture.producer.sensor.data.RingV2StatusData
import com.hcifuture.producer.sensor.data.RingV2TouchEventData
import com.hcifuture.producer.sensor.data.RingV2TouchRawData

class RingSpec {
    companion object {
        fun imuFlowName(sensor: NuixSensor): String {
            return NuixSensorSpec.flowName(sensor, "imu")
        }

        fun touchEventFlowName(sensor: NuixSensor): String {
            return NuixSensorSpec.flowName(sensor, "touch_event")
        }

        fun touchRawFlowName(sensor: NuixSensor): String {
            return NuixSensorSpec.flowName(sensor, "touch_raw")
        }

        fun audioFlowName(sensor: NuixSensor): String {
            return NuixSensorSpec.flowName(sensor, "audio")
        }

        fun ppgFlowName(sensor: NuixSensor): String {
            return NuixSensorSpec.flowName(sensor, "ppg")
        }

        fun statusFlowName(sensor: NuixSensor): String {
            return NuixSensorSpec.flowName(sensor, "status")
        }

        fun refriedImuFlow(value: Any): RingImuData =
            FunctionUtils.reifiedValue<RingImuData>(value)

        fun refriedTouchEventFlow(value: Any): RingV2TouchEventData =
            FunctionUtils.reifiedValue<RingV2TouchEventData>(value)

        fun refriedTouchRawFlow(value: Any): RingV2TouchRawData =
            FunctionUtils.reifiedValue<RingV2TouchRawData>(value)

        fun refriedAudioFlow(value: Any): RingV2AudioData =
            FunctionUtils.reifiedValue<RingV2AudioData>(value)

        fun refriedStatusFlow(value: Any): RingV2StatusData =
            FunctionUtils.reifiedValue<RingV2StatusData>(value)
    }
}