package com.hcifuture.producer.detector

import android.content.res.AssetManager
import android.util.Log
import com.hcifuture.producer.detector.taptap.SingleIMUData
import com.hcifuture.producer.detector.taptap.TapTapAction
import com.hcifuture.producer.sensor.NuixSensor
import com.hcifuture.producer.sensor.NuixSensorManager
import com.hcifuture.producer.sensor.data.InternalSensorData
import com.hcifuture.producer.sensor.internal.InternalSensorSpec
import jakarta.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

class TapTapDetector @Inject constructor(
    private val assetManager: AssetManager,
    private val nuixSensorManager: NuixSensorManager,
){
    private val scope = CoroutineScope(Dispatchers.Default)
    private var detectJob: Job? = null
    val eventFlow = MutableSharedFlow<Boolean>(replay = 0)
    private val taptapAction: TapTapAction = TapTapAction(assetManager, "tap7cls_pixel4.tflite") { _ ->
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
        taptapAction.start()
        detectJob = scope.launch {
            nuixSensorManager.internalSensors()
                .filter { it.name == InternalSensorSpec.accelerometer || it.name == InternalSensorSpec.gyroscope }
                .forEach {
                    collectFlow<InternalSensorData>(it, InternalSensorSpec::eventFlowName) { data ->
                        taptapAction.onIMUSensorEvent(SingleIMUData(
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
        taptapAction.stop()
        detectJob?.cancel()
    }
}