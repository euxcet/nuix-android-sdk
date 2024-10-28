package com.hcifuture.producer.sensor.video

import android.content.Context
import com.hcifuture.producer.sensor.NuixSensor
import com.hcifuture.producer.sensor.NuixSensorProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoProvider @Inject constructor(
    @ApplicationContext val context: Context
) : NuixSensorProvider {
    override val requireScan: Boolean = false

    private val sensors = listOf(
        VideoSensor(context)
    )

    override fun get(): List<NuixSensor> {
        return sensors.toList()
    }

    override fun scan(): Flow<List<NuixSensor>> {
        return listOf(sensors).asFlow()
    }

}