package com.hcifuture.producer.sensor.touch

import android.graphics.Path
import com.hcifuture.producer.recorder.Collector
import com.hcifuture.producer.recorder.collectors.BytesDataCollector
import com.hcifuture.producer.sensor.NuixSensor
import com.hcifuture.producer.sensor.NuixSensorSpec
import com.hcifuture.producer.sensor.data.TouchSensorData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TouchSensor(
) : NuixSensor() {
    private var scope = CoroutineScope(Job() + Dispatchers.Default)
    override val name: String = "Touch"
    private val _touchFlow = MutableSharedFlow<TouchSensorData>()
    override val flows = mapOf(
        TouchSensorSpec.touchFlowName(this) to _touchFlow.asSharedFlow(),
        NuixSensorSpec.lifecycleFlowName(this) to lifecycleFlow.asStateFlow(),
    )
    override val defaultCollectors = mapOf<String, Collector>(
        TouchSensorSpec.touchFlowName(this) to
            BytesDataCollector(listOf(this), listOf(_touchFlow.asSharedFlow()), "${name}.bin")
    )

    /**
     * The data of the TouchSensor is passed from the NuixSensorManager, so manual control of
     * connect and disconnect is not required. If there is a need for controlling the switch
     * in the future, modifications can be made here.
     */
    override fun connect() {
    }

    override fun disconnect() {
    }

    fun onTouchEvent(path: Path) {
        synchronized(this) {
            scope.launch {
                _touchFlow.emit(TouchSensorData(
                    path
                ))
            }
        }
    }
}