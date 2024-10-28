package com.hcifuture.producer.recorder.collectors

import com.hcifuture.producer.recorder.Collector
import com.hcifuture.producer.sensor.NuixSensor
import com.hcifuture.producer.sensor.video.VideoSensor
import kotlinx.coroutines.flow.Flow
import java.io.File

class VideoCollector(
    override val sensors: List<NuixSensor>,
    override val flows: List<Flow<Any>>,
    override val suffixName: String,
) : Collector {
    private lateinit var saveFile: File

    override fun start(file: File) {
        saveFile = file
        for (sensor in sensors) {
            if (sensor is VideoSensor) {
                sensor.startRecord(file, withAudio = true)
                break
            }
        }
    }

    override fun stop(): File {
        for (sensor in sensors) {
            if (sensor is VideoSensor) {
                sensor.stopRecord()
                break
            }
        }
        return saveFile
    }

    override suspend fun stopAsync(): File {
        return stop()
    }

    /**
     * The video will be saved as a file in the CameraSensor class.
     */
    override suspend fun save() {
    }
}
