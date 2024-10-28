package com.hcifuture.producer.sensor.touch

import com.hcifuture.producer.sensor.NuixSensorProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TouchSensorProvider @Inject constructor(
) : NuixSensorProvider {
    override val requireScan: Boolean = false
    private val sensors = listOf(
        TouchSensor()
    )

    override fun get(): List<TouchSensor> {
        return sensors.toList()
    }

    override fun scan(): Flow<List<TouchSensor>> {
        return listOf(sensors).asFlow()
    }
}