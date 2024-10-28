package com.hcifuture.producer.recorder.triggers

import com.hcifuture.producer.recorder.Trigger
import com.hcifuture.producer.recorder.TriggerEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FixedDurationTrigger(
    override val sampleCount: Int,
    private val sampleLength: Long,
    private val sampleInterval: Long,
) : Trigger {
    private val _eventFlow = MutableStateFlow(TriggerEvent.Idle)
    override val eventFlow = _eventFlow.asStateFlow()
    override var job: Job? = null
    private var scope = CoroutineScope(Job() + Dispatchers.Default)

    override fun start() {
        job = scope.launch {
            repeat(sampleCount) {
                _eventFlow.emit(TriggerEvent.Begin)
                delay(sampleLength)
                _eventFlow.emit(TriggerEvent.End)
                delay(sampleInterval)
            }
        }
    }
}
