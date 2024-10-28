package com.hcifuture.producer.sensor.internal

import com.hcifuture.producer.common.utils.FunctionUtils.reifiedValue
import com.hcifuture.producer.sensor.NuixSensor
import com.hcifuture.producer.sensor.NuixSensorSpec
import com.hcifuture.producer.sensor.data.InternalSensorData
import java.security.InvalidKeyException

class InternalSensorSpec {
    companion object {
        const val accelerometer: String = "INTERNAL_ACCELEROMETER"
        const val accelerometerUncalibrated: String = "INTERNAL_ACCELEROMETER_UNCALIBRATED"
        const val accelerometerLimitedAxes: String = "INTERNAL_ACCELEROMETER_LIMITED_AXES"
        const val accelerometerLimitedAxesUncalibrated: String = "INTERNAL_ACCELEROMETER_LIMITED_AXES_UNCALIBRATED"
        const val ambientTemperature: String = "INTERNAL_AMBIENT_TEMPERATURE"
        const val devicePrivateBase: String = "INTERNAL_DEVICE_PRIVATE_BASE"
        const val gameRotationVector: String = "INTERNAL_GAME_ROTATION_VECTOR"
        const val geomagneticRotationVector: String = "INTERNAL_GEOMAGNETIC_ROTATION_VECTOR"
        const val gravity: String = "INTERNAL_GRAVITY"
        const val gyroscope: String = "INTERNAL_GYROSCOPE"
        const val gyroscopeLimitedAxes: String = "INTERNAL_GYROSCOPE_LIMITED_AXES"
        const val gyroscopeLimitedAxesUncalibrated: String = "INTERNAL_LIMITED_AXES_UNCALIBRATED"
        const val gyroscopeUncalibrated: String = "INTERNAL_GYROSCOPE_UNCALIBRATED"
        const val heading: String = "INTERNAL_HEADING"
        const val headTracker: String = "INTERNAL_HEAD_TRACKER"
        const val heartBeat: String = "INTERNAL_HEART_BEAT"
        const val heartRate: String = "INTERNAL_HEART_RATE"
        const val hingeAngle: String = "INTERNAL_HINGE_ANGLE"
        const val typeLight: String = "INTERNAL_TYPE_LIGHT"
        const val linearAcceleration: String = "INTERNAL_LINEAR_ACCELERATION"
        const val lowLatencyOffbodyDetect: String = "INTERNAL_LOW_LATENCY_OFFBODY_DETECT"
        const val magneticField: String = "INTERNAL_MAGNETIC_FIELD"
        const val magneticFieldUncalibrated: String = "INTERNAL_MAGNETIC_FIELD_UNCALIBRATED"
        const val motionDetect: String = "INTERNAL_MOTION_DETECT"

        fun eventFlowName(sensor: NuixSensor): String {
            return NuixSensorSpec.flowName(sensor, "event_shared")
        }

        @Deprecated("use eventFlowName(sensor: NuixSensor)", ReplaceWith(""))
        fun eventFlowName(sensor: String): String {
            return sensor + "_event_shared"
        }

        fun refriedEventFlow(value: Any): InternalSensorData =
            reifiedValue<InternalSensorData>(value)
    }
}
