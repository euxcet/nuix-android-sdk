package com.hcifuture.producer.recorder.collectors

import com.hcifuture.producer.common.utils.FunctionUtils.reifiedValue
import com.hcifuture.producer.recorder.Collector
import com.hcifuture.producer.sensor.NuixSensor
import com.hcifuture.producer.sensor.data.BytesData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream

class BytesDataCollector(
    override val sensors: List<NuixSensor>,
    override val flows: List<Flow<Any>>,
    override val suffixName: String,
) : Collector {
    private val scope = CoroutineScope(Dispatchers.Default)
    private val scopeIO = CoroutineScope(Dispatchers.IO)
    var data = ByteArray(0)
    private lateinit var job: Job
    private lateinit var saveFile: File

    override fun start(file: File) {
        saveFile = file
        job = scope.launch {
            merge(*flows.toTypedArray())
                .map { reifiedValue<BytesData>(it) }
                .collect {
                    data += it.toBytes()
                }
        }
    }

    /**
     * Warning: when the stop function is called, the data may not been
     * fully written to the saveFile.
     */
    override fun stop(): File {
        scope.launch {
            stopAsync()
        }
        return saveFile
    }

    override suspend fun stopAsync(): File {
        job.cancel()
        job.join()
        save()
        return saveFile
    }

    override suspend fun save() {
        DataOutputStream(FileOutputStream(saveFile)).use { outputStream ->
            outputStream.write(data)
        }
        data = ByteArray(0)
    }
}