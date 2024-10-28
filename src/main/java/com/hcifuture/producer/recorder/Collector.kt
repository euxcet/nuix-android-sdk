package com.hcifuture.producer.recorder

import com.hcifuture.producer.sensor.NuixSensor
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * Collector
 * Flows
 */

interface Collector {
    fun start(file: File)
    /**
     * Warning: when the stop function is called, the data may not been
     * fully written to the saveFile.
     */
    fun stop(): File?

    /**
     * The stopAsync function is recommended for use as it ensures that all data is
     * fully written to the saveFile.
     */
    suspend fun stopAsync(): File?
    suspend fun save()
    val sensors: List<NuixSensor>
    val suffixName: String
    val flows: List<Flow<Any>>
}