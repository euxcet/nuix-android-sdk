package com.hcifuture.producer.sensor

import kotlinx.coroutines.flow.Flow

interface NuixSensorProvider {
    val requireScan: Boolean
    fun get(): List<NuixSensor>
    fun scan(): Flow<List<NuixSensor>>
}