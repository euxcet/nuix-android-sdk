package com.hcifuture.producer.sensor

import com.hcifuture.producer.common.utils.FunctionUtils
import com.hcifuture.producer.recorder.Collector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

enum class NuixSensorState {
    SCANNING,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
}
abstract class NuixSensor {
    abstract fun connect()
    abstract fun disconnect()

    fun toggle() {
        if (status !in listOf(NuixSensorState.CONNECTED, NuixSensorState.CONNECTING)) {
            connect()
        } else {
            disconnect()
        }
    }

    fun connectable(): Boolean {
        return status in listOf(NuixSensorState.SCANNING, NuixSensorState.DISCONNECTED)
    }

    fun disconnectable(): Boolean {
        return status in listOf(NuixSensorState.CONNECTED, NuixSensorState.CONNECTING)
    }

    inline fun<reified T> getFlow(name: String): Flow<T>? {
        return flows[name]?.map { v -> FunctionUtils.reifiedValue<T>(v) }
    }

    /**
     * The names of the sensors should be different from each other.
     */
    abstract val name: String
    open var status: NuixSensorState = NuixSensorState.SCANNING
        set(state) {
            field = state
            CoroutineScope(Dispatchers.Default).launch {
                lifecycleFlow.emit(state)
            }
        }
    abstract val flows: Map<String, Flow<Any>>
    abstract val defaultCollectors: Map<String, Collector>
    protected val lifecycleFlow = MutableStateFlow(NuixSensorState.SCANNING)
}