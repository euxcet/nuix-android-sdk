package com.hcifuture.producer.sensor.internal

import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import androidx.annotation.RequiresApi
import com.hcifuture.producer.sensor.NuixSensorProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InternalSensorProvider @Inject constructor(
    sensorManager: SensorManager
): NuixSensorProvider {
    override val requireScan: Boolean = false
    private val sensors = listOf(
        InternalSensor(sensorManager, InternalSensorConfig(Sensor.TYPE_ACCELEROMETER)),
        InternalSensor(sensorManager, InternalSensorConfig(Sensor.TYPE_GRAVITY)),
        InternalSensor(sensorManager, InternalSensorConfig(Sensor.TYPE_GYROSCOPE)),
        InternalSensor(sensorManager, InternalSensorConfig(Sensor.TYPE_LIGHT)),
        InternalSensor(sensorManager, InternalSensorConfig(Sensor.TYPE_MAGNETIC_FIELD)),
    )

    override fun get(): List<InternalSensor> {
        return sensors.toList()
    }

    override fun scan(): Flow<List<InternalSensor>> {
        return listOf(sensors).asFlow()
    }
}