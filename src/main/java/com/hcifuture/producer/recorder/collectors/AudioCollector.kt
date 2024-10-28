package com.hcifuture.producer.recorder.collectors

import com.hcifuture.producer.recorder.Collector
import com.hcifuture.producer.sensor.NuixSensor
import com.hcifuture.producer.sensor.audio.AudioSensor
import kotlinx.coroutines.flow.Flow
import java.io.File

class AudioCollector(
    override val sensors: List<NuixSensor>,
    override val flows: List<Flow<Any>>,
    override val suffixName: String,
) : Collector {
    private lateinit var saveFile: File
    override fun start(file: File) {
        saveFile = file
        for (sensor in sensors) {
            if (sensor is AudioSensor) {
                sensor.startRecord(file)
                break
            }
        }
    }

    override fun stop(): File? {
        for (sensor in sensors) {
            if (sensor is AudioSensor) {
                sensor.stopRecord()
                break
            }
        }
        return saveFile
    }

    override suspend fun stopAsync(): File? {
        return stop()
    }

    /**
     * The audio will be saved as a file in the AudioSensor class.
     */
    override suspend fun save() {
    }
}
