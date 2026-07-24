package com.hcifuture.producer.recorder

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

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
    val uploadEventFlow = uploader.eventFlow
    private var currentPath: Array<out String>? = null

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
        scope.launch {
            val files = stopSampleAndAwait()
            if (files.isNotEmpty()) {
                uploader.enqueue("legacy-${UUID.randomUUID()}", files)
            }
        }
        trigger?.stop()
    }

    suspend fun stopAndAwait(): List<File> {
        val files = stopSampleAndAwait()
        trigger?.stop()
        return files
    }

    fun enqueueForUpload(batchId: String, files: List<File>): Boolean {
        return uploader.enqueue(batchId, files)
    }

    fun isUploadPending(batchId: String): Boolean {
        return batchId in uploader.pendingBatchIds()
    }

    fun quarantineFiles(files: List<File>, reason: String): List<File> {
        return fileDataset.quarantineFiles(files, reason)
    }

    private fun handleTriggerEvent(path: Array<out String>) {
        listenJob = scope.launch {
            trigger?.eventFlow?.collect { event ->
                when (event) {
                    TriggerEvent.Idle -> {}
                    TriggerEvent.Begin -> startSample(path)
                    TriggerEvent.End -> {
                        val files = stopSampleAndAwait()
                        if (files.isNotEmpty()) {
                            uploader.enqueue("trigger-${UUID.randomUUID()}", files)
                        }
                    }
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
        currentPath = path
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

    private suspend fun stopSampleAndAwait(): List<File> {
        if (!recordingSample) {
            return emptyList()
        }
        recordingSample = false
        val files = mutableListOf<File>()
        for (collector in collectors) {
            val file = collector.stopAsync()
            file?.let {
                files.add(file)
            }
        }
        eventFlow.emit(RecorderEvent.StopSample(sampleId, files))
        delay(2000)
        return files
    }

    fun getStoragePath(): File {
        return if (currentPath == null) {
            fileDataset.root
        } else {
            File(fileDataset.root, currentPath!!.joinToString(separator = File.separator))
        }
    }

    fun uploadFile(file: File) {
        fileDataset.addDataFile(file)
    }
}
