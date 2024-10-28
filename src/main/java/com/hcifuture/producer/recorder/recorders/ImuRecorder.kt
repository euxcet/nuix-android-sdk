package com.hcifuture.producer.recorder.recorders

import android.content.Context
import com.hcifuture.producer.recorder.Collector
import com.hcifuture.producer.recorder.FileDatasetProvider
import com.hcifuture.producer.recorder.Recorder
import com.hcifuture.producer.recorder.UploaderProvider
import com.hcifuture.producer.recorder.triggers.FixedDurationTrigger
import com.hcifuture.producer.sensor.NuixSensorManager
import com.hcifuture.producer.sensor.internal.InternalSensorSpec

class ImuRecorder {
    companion object {
        fun create(
            context: Context,
            nuixSensorManager: NuixSensorManager,
            fileDatasetProvider: FileDatasetProvider,
            uploaderProvider: UploaderProvider,
            datasetName: String,
        ): Recorder {
            val fileDataset = fileDatasetProvider.create(datasetName)
            val trigger = FixedDurationTrigger(
                sampleCount = 600,
                sampleLength = 60 * 1000,
                sampleInterval = 500,
            )
            val uploader = uploaderProvider.create(fileDataset)
            val collectors: MutableList<Collector> = mutableListOf()
            nuixSensorManager.internalSensors()
                .filter {
                    it.name in listOf(
                        InternalSensorSpec.accelerometer,
                        InternalSensorSpec.gyroscope,
                    )
                }
                .onEach {
                    collectors.addAll(it.defaultCollectors.values)
                }
            nuixSensorManager.ringV1s()
                .onEach {
                    collectors.addAll(it.defaultCollectors.values)
                }
            return Recorder(
                collectors = collectors,
                trigger = trigger,
                fileDataset = fileDataset,
                uploader = uploader,
            )
        }
    }
}