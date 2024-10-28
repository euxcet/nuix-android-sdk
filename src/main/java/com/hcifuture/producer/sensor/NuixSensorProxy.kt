package com.hcifuture.producer.sensor

import com.hcifuture.producer.common.utils.FunctionUtils
import com.hcifuture.producer.recorder.Collector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class NuixSensorProxy(
    override val name: String,
    var target: NuixSensor?,
): NuixSensor() {
    override val defaultCollectors: MutableMap<String, Collector> = mutableMapOf()
    override val flows: MutableMap<String, MutableSharedFlow<Any>> = mutableMapOf()

    private val jobs: MutableList<Job> = mutableListOf()

    init {
        target?.let { switchTarget(it) }
    }

    override fun connect() {
        target?.connect()
    }

    override fun disconnect() {
        target?.disconnect()
    }

    override var status: NuixSensorState
        get() = target?.status ?: NuixSensorState.SCANNING
        set(value) {}

    inline fun<reified T> getProxyFlow(name: String): Flow<T>? {
        if (!flows.contains(name)) {
            flows[name] = MutableSharedFlow()
        }
        return flows[name]?.map { v -> FunctionUtils.reifiedValue<T>(v) }
    }

    /**
     * Keep the flows unchanged.
     */
    private fun listenFlow(targetFlow: Flow<Any>, name: String) {
        if (!flows.contains(name)) {
            flows[name] = MutableSharedFlow()
        }
        jobs.add(CoroutineScope(Dispatchers.Default).launch {
            targetFlow.collect {
                flows[name]?.emit(it)
            }
        })
    }

    fun switchTarget(targetSensor: NuixSensor?) {
        for (job in jobs) {
            job.cancel()
        }
        jobs.clear()
        targetSensor?.let {
            for ((name, flow) in it.flows) {
                listenFlow(flow, NuixSensorSpec.proxyFlowName(this, name))
            }
        }
        target = targetSensor
    }
}