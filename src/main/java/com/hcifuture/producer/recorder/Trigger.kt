package com.hcifuture.producer.recorder

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class TriggerEvent {
    Idle,
    Begin,
    End,
}

interface Trigger {
    val eventFlow: StateFlow<TriggerEvent>
    var job: Job?
    val sampleCount: Int

    fun start()

    fun stop() {
        job?.cancel()
    }

    suspend fun join() {
        job?.join()
    }
}
