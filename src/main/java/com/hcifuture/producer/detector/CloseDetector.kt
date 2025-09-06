package com.hcifuture.producer.detector

import android.content.res.AssetManager
import com.hcifuture.producer.detector.taptap.CloseAction
import com.hcifuture.producer.detector.taptap.SingleIMUData
import com.hcifuture.producer.detector.taptap.TopTapAction
import com.hcifuture.producer.sensor.NuixSensor
import com.hcifuture.producer.sensor.NuixSensorManager
import com.hcifuture.producer.sensor.data.InternalSensorData
import com.hcifuture.producer.sensor.internal.InternalSensorSpec
import com.hcifuture.producer.sensor.internal.InternalSensorSpec.Companion.eventFlowName
import jakarta.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

class CloseDetector @Inject constructor(
    private val assetManager: AssetManager,
    private val nuixSensorManager: NuixSensorManager,
){
    private val scope = CoroutineScope(Dispatchers.Default)
    private var detectJob: Job? = null
    val eventFlow = MutableSharedFlow<Boolean>(replay = 0)
    private val closeAction: CloseAction = CloseAction() { _ ->
        scope.launch {
            eventFlow.emit(true)
        }
    }

    private inline fun <reified T> collectFlow(
        sensor: NuixSensor,
        crossinline flowNameProvider: (NuixSensor) -> String,
        crossinline callbackSelector: ((T) -> Unit)
    ) {
        scope.launch {
            sensor.getFlow<T>(flowNameProvider(sensor))?.collect { data ->
                callbackSelector.invoke(data)
            }
        }
    }

    fun start() {
        closeAction.start()
        detectJob = scope.launch {
            nuixSensorManager.internalSensors()
                .filter { it.name == InternalSensorSpec.proximity || it.name == InternalSensorSpec.gyroscope }
                .forEach {
                    collectFlow<InternalSensorData>(it, InternalSensorSpec::eventFlowName) { data ->
                        closeAction.onIMUSensorEvent(SingleIMUData(
                            data.data,
                            "",
                            data.type,
                            data.timestamp
                        ))
                    }
                }
        }
    }

    fun stop() {
        closeAction.stop()
        detectJob?.cancel()
    }
}
