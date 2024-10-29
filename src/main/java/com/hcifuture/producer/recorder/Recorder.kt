package com.hcifuture.producer.recorder

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.io.File

sealed class RecorderEvent {
    data class Start(val sampleCount: Int) : RecorderEvent()
    data object Stop : RecorderEvent()
    data class StartSample(val sampleId: Int) : RecorderEvent()
    data class StopSample(val sampleId: Int, val files: List<File>) : RecorderEvent()
}

/**
 * @param collectors are all the collectors that need to be recorded.
 * @param trigger is to provide events for controlling the recorder (e.g., start).
 * @param fileDataset describes the format of the dataset.
 * @param uploader is used to send data to the server.
 */
class Recorder(
    private val collectors: List<Collector>,
    private val trigger: Trigger?,
    private val fileDataset: FileDataset,
    private val uploader: Uploader,
) {
    private var scope = CoroutineScope(Job() + Dispatchers.Default)
    private var scopeIO = CoroutineScope(Job() + Dispatchers.IO)
    private lateinit var listenJob: Job
    private var sampleId: Int = 0
    private var recordingSample: Boolean = false
    val eventFlow = MutableSharedFlow<RecorderEvent>()

    suspend fun start(vararg path: String) {
        if (trigger != null) {
            onStart()
            trigger.start()
            handleTriggerEvent(path)
            trigger.join()
            onStop()
        } else {
            startSample(path)
        }
    }

    fun stop() {
        stopSample()
        trigger?.stop()
    }

    private fun handleTriggerEvent(path: Array<out String>) {
        listenJob = scope.launch {
            trigger?.eventFlow?.collect { event ->
                when (event) {
                    TriggerEvent.Idle -> {}
                    TriggerEvent.Begin -> startSample(path)
                    TriggerEvent.End -> stopSample()
                }
            }
        }
    }

    private fun onStart() {
        scope.launch {
            if (trigger != null) {
                eventFlow.emit(RecorderEvent.Start(trigger.sampleCount))
            } else {
                eventFlow.emit(RecorderEvent.Start(-1))
            }
        }
        sampleId = 0
        recordingSample = false
    }

    private fun onStop() {
        listenJob.cancel()
        scope.launch {
            eventFlow.emit(RecorderEvent.Stop)
        }
    }

    /**
     * TODO: Check if there are any bugs when startSample and stopSample are called continuously
     *       in a short period of time.
     */
    private fun startSample(path: Array<out String>) {
        if (recordingSample) {
            return
        }
        recordingSample = true
        scope.launch {
            eventFlow.emit(RecorderEvent.StartSample(sampleId))
        }
        sampleId += 1
        val files = fileDataset.prepareFiles(path, collectors)
        for ((collector, file) in collectors.zip(files)) {
            collector.start(file)
        }
    }

    private fun stopSample() {
        if (!recordingSample) {
            return
        }
        recordingSample = false
        scope.launch {
            val files = mutableListOf<File>()
            for (collector in collectors) {
                val file = collector.stopAsync()
                file?.let {
                    fileDataset.addDataFile(file)
                    files.add(file)
                }
            }
            eventFlow.emit(RecorderEvent.StopSample(sampleId, files))
        }
    }
}
