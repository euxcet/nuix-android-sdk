package com.hcifuture.producer.sensor.internal

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

import com.hcifuture.producer.recorder.Collector
import com.hcifuture.producer.recorder.collectors.BytesDataCollector
import com.hcifuture.producer.sensor.NuixSensor
import com.hcifuture.producer.sensor.NuixSensorSpec
import com.hcifuture.producer.sensor.NuixSensorState
import com.hcifuture.producer.sensor.data.InternalSensorData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class InternalSensorConfig(
    var sensorType: Int,
    var sensorDelay: Int = SensorManager.SENSOR_DELAY_FASTEST,
)

class InternalSensor(
    private val sensorManager: SensorManager,
    private val config: InternalSensorConfig,
) : NuixSensor(), SensorEventListener {
    private var scope = CoroutineScope(Job() + Dispatchers.Default)
    private val _eventFlow = MutableSharedFlow<InternalSensorData>()
    override val name = when (config.sensorType) {
        Sensor.TYPE_ACCELEROMETER -> InternalSensorSpec.accelerometer
        Sensor.TYPE_ACCELEROMETER_UNCALIBRATED -> InternalSensorSpec.accelerometerUncalibrated
        Sensor.TYPE_ACCELEROMETER_LIMITED_AXES -> InternalSensorSpec.accelerometerLimitedAxes
        Sensor.TYPE_ACCELEROMETER_LIMITED_AXES_UNCALIBRATED -> InternalSensorSpec.accelerometerLimitedAxesUncalibrated
        Sensor.TYPE_AMBIENT_TEMPERATURE -> InternalSensorSpec.ambientTemperature
        Sensor.TYPE_DEVICE_PRIVATE_BASE -> InternalSensorSpec.devicePrivateBase
        Sensor.TYPE_GAME_ROTATION_VECTOR -> InternalSensorSpec.gameRotationVector
        Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> InternalSensorSpec.geomagneticRotationVector
        Sensor.TYPE_GRAVITY -> InternalSensorSpec.gravity
        Sensor.TYPE_GYROSCOPE -> InternalSensorSpec.gyroscope
        Sensor.TYPE_GYROSCOPE_LIMITED_AXES -> InternalSensorSpec.gyroscopeLimitedAxes
        Sensor.TYPE_GYROSCOPE_LIMITED_AXES_UNCALIBRATED -> InternalSensorSpec.gyroscopeLimitedAxesUncalibrated
        Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> InternalSensorSpec.gyroscopeUncalibrated
        Sensor.TYPE_HEADING -> InternalSensorSpec.heading
        Sensor.TYPE_HEAD_TRACKER -> InternalSensorSpec.headTracker
        Sensor.TYPE_HEART_BEAT -> InternalSensorSpec.heartBeat
        Sensor.TYPE_HEART_RATE -> InternalSensorSpec.heartRate
        Sensor.TYPE_HINGE_ANGLE -> InternalSensorSpec.hingeAngle
        Sensor.TYPE_LIGHT -> InternalSensorSpec.typeLight
        Sensor.TYPE_LINEAR_ACCELERATION -> InternalSensorSpec.linearAcceleration
        Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT -> InternalSensorSpec.lowLatencyOffbodyDetect
        Sensor.TYPE_MAGNETIC_FIELD -> InternalSensorSpec.magneticField
        Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> InternalSensorSpec.magneticFieldUncalibrated
        Sensor.TYPE_MOTION_DETECT -> InternalSensorSpec.motionDetect
        else -> "UNKNOWN"
    }
    override val flows = mapOf(
        InternalSensorSpec.eventFlowName(this) to _eventFlow.asSharedFlow(),
        NuixSensorSpec.lifecycleFlowName(this) to lifecycleFlow.asStateFlow(),
    )
    override val defaultCollectors: Map<String, Collector> = mapOf<String, Collector>(
        InternalSensorSpec.eventFlowName(this) to
                BytesDataCollector(listOf(this), listOf(_eventFlow.asSharedFlow()), "${name}.bin")
    )

    override fun connect() {
        if (!connectable()) return
        status =
            if (sensorManager.registerListener(
                this,
                sensorManager.getDefaultSensor(config.sensorType),
                config.sensorDelay
            )) {
                NuixSensorState.CONNECTED
            } else {
                NuixSensorState.DISCONNECTED
            }
    }

    override fun disconnect() {
        if (status != NuixSensorState.CONNECTED) {
            return
        }
        sensorManager.unregisterListener(
            this,
            sensorManager.getDefaultSensor(config.sensorType)
        )
        status = NuixSensorState.DISCONNECTED
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null) {
            synchronized(this) {
                scope.launch {
                    _eventFlow.emit(InternalSensorData(
                        event.sensor.type,
                        event.values!!.toList(),
                        event.timestamp,
                    ))
                }
            }
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
    }
}