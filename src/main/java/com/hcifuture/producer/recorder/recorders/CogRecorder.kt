package com.hcifuture.producer.recorder.recorders

import android.content.Context
import com.hcifuture.producer.recorder.Collector
import com.hcifuture.producer.recorder.FileDatasetProvider
import com.hcifuture.producer.recorder.Recorder
import com.hcifuture.producer.recorder.UploaderProvider
import com.hcifuture.producer.recorder.triggers.FixedDurationTrigger
import com.hcifuture.producer.sensor.NuixSensorManager
import com.hcifuture.producer.sensor.internal.InternalSensorSpec
import com.hcifuture.producer.sensor.external.ring.RingSpec
import com.hcifuture.producer.sensor.external.ring.ringV2.RingV2

class CogRecorder {
    companion object {
        fun create(
            context: Context,
            nuixSensorManager: NuixSensorManager,
            fileDatasetProvider: FileDatasetProvider,
            uploaderProvider: UploaderProvider,
            datasetName: String,
            userId: String?,
            taskId: String?,
        ): Recorder {
            val fileDataset = fileDatasetProvider.create(datasetName, userId, taskId)
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
            nuixSensorManager.videos().onEach {
                collectors.addAll(it.defaultCollectors.values)
            }
            nuixSensorManager.defaultRing.target?.let { ring ->
                if (ring is RingV2) {
                    // The 0x3C PPG container already carries ACC/GYR/temperature.
                    // Do not create the independent 0x40 IMU file because this
                    // recording mode never starts that stream and it stays empty.
                    collectors.addAll(
                        ring.defaultCollectors.filterKeys { key ->
                            key == RingSpec.ppgFlowName(ring) ||
                                key == RingSpec.rawPpgFlowName(ring)
                        }.values
                    )
                } else {
                    collectors.addAll(ring.defaultCollectors.values)
                }
            }
            return Recorder(
                collectors = collectors,
                trigger = null,
                fileDataset = fileDataset,
                uploader = uploader,
            )
        }
    }
}
