package com.hcifuture.producer.recorder

import android.content.Context
import com.hcifuture.producer.recorder.recorders.AllDataRecorder
import com.hcifuture.producer.recorder.recorders.CogRecorder
import com.hcifuture.producer.recorder.recorders.ImuRecorder
import com.hcifuture.producer.recorder.triggers.FixedDurationTrigger
import com.hcifuture.producer.sensor.NuixSensorManager
import com.hcifuture.producer.sensor.internal.InternalSensorSpec
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton


/**
 * Add creation functions for different Recorders in this RecorderProvider class.
 * A method for creating Recorders using configuration files(yaml) will be added later.
 */
@Singleton
class RecorderProvider @Inject constructor(
    @ApplicationContext val context: Context,
    val nuixSensorManager: NuixSensorManager,
    val fileDatasetProvider: FileDatasetProvider,
    val uploaderProvider: UploaderProvider,
) {
    fun createAllDataRecorder(): Recorder {
        return AllDataRecorder.create(
            context,
            nuixSensorManager,
            fileDatasetProvider,
            uploaderProvider,
            "All",
        )
    }

    fun createImuRecorder(): Recorder {
        return ImuRecorder.create(
            context,
            nuixSensorManager,
            fileDatasetProvider,
            uploaderProvider,
            "Imu",
        )
    }

    fun createCogRecorder(): Recorder {
        return CogRecorder.create(
            context,
            nuixSensorManager,
            fileDatasetProvider,
            uploaderProvider,
            "Cog",
        )
    }
}
