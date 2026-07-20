package com.hcifuture.producer.recorder

import android.content.Context
import android.util.Log
import com.hcifuture.producer.common.network.http.HttpService
import com.hcifuture.producer.common.utils.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

sealed class UploadEvent {
    data class Succeeded(val batchId: String) : UploadEvent()
    data class Failed(val batchId: String, val message: String) : UploadEvent()
}

class Uploader(
    private val context: Context,
    private val fileDataset: FileDataset,
    private val httpService: HttpService,
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var job: Job? = null
    private val _eventFlow = MutableSharedFlow<UploadEvent>(extraBufferCapacity = 64)
    val eventFlow = _eventFlow.asSharedFlow()
    private val pendingBatches = ConcurrentHashMap<String, MutableSet<String>>()
    private val zipSources = ConcurrentHashMap<String, Set<String>>()
    private val uploading = ConcurrentHashMap.newKeySet<String>()

    init {
        start()
    }

    fun start() {
        job = scope.launch {
            while (true) {
                compress()
                upload()
                delay(5000)
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    fun enqueue(batchId: String, files: List<File>): Boolean {
        if (files.isEmpty() || files.any { !it.isFile || it.length() <= 0L }) {
            return false
        }
        val paths = ConcurrentHashMap.newKeySet<String>()
        paths.addAll(files.map { it.absolutePath })
        pendingBatches[batchId] = paths
        fileDataset.addDataFiles(files)
        return true
    }

    /**
     * When connected to WiFi, attempt to upload compressed files.
     */
    private fun upload() {
        for (zip in fileDataset.getZipFiles(10)) {
            val zipPath = zip.absolutePath
            if (NetworkUtils.isWifiConnected(context) && uploading.add(zipPath)) {
                val filePart = MultipartBody.Part
                    .createFormData("file", zip.name, zip.asRequestBody("multipart/form-data".toMediaTypeOrNull()))
                val path = fileDataset.getFolderPath(zip).toRequestBody("text/plain".toMediaTypeOrNull())
                httpService.uploadFile(filePart, path).enqueue(object: Callback<Any> {
                    override fun onResponse(call: Call<Any>, response: Response<Any>) {
                        uploading.remove(zipPath)
                        if (response.isSuccessful) {
                            val sourcePaths = zipSources.remove(zipPath).orEmpty()
                            fileDataset.removeZipFile(zip)
                            markUploaded(sourcePaths)
                        } else {
                            notifyUploadFailed(
                                zipSources[zipPath].orEmpty(),
                                "HTTP ${response.code()}"
                            )
                        }
                    }

                    override fun onFailure(call: Call<Any>, t: Throwable) {
                        uploading.remove(zipPath)
                        val message = t.message ?: t.javaClass.simpleName
                        Log.e("Nuix", "Upload failed: $message")
                        notifyUploadFailed(zipSources[zipPath].orEmpty(), message)
                    }
                })
            }
        }
    }

    private fun markUploaded(uploadedPaths: Set<String>) {
        for ((batchId, remainingPaths) in pendingBatches.entries) {
            remainingPaths.removeAll(uploadedPaths)
            if (remainingPaths.isEmpty() && pendingBatches.remove(batchId, remainingPaths)) {
                _eventFlow.tryEmit(UploadEvent.Succeeded(batchId))
            }
        }
    }

    private fun notifyUploadFailed(sourcePaths: Set<String>, message: String) {
        for ((batchId, remainingPaths) in pendingBatches.entries) {
            if (remainingPaths.any { it in sourcePaths }) {
                _eventFlow.tryEmit(UploadEvent.Failed(batchId, message))
            }
        }
    }

    private fun compress() {
        val files = fileDataset.getDataFiles(10)
        if (files.isNotEmpty()) {
            val zipFile = fileDataset.prepareZipFile()
            val compressedFiles = mutableListOf<File>()
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { out ->
                for (file in files) {
                    try {
                        FileInputStream(file).use { fi ->
                            BufferedInputStream(fi).use { origin ->
                                val entry = ZipEntry(fileDataset.getPath(file))
                                out.putNextEntry(entry)
                                origin.copyTo(out, 1024)
                            }
                        }
                        compressedFiles.add(file)
                    }
                    catch(e: Exception) {
                        Log.e("Nuix", "ERROR ${e.message}")
                    }
                }
            }
            if (compressedFiles.isEmpty()) {
                zipFile.delete()
                return
            }
            for (file in compressedFiles) {
                fileDataset.removeDataFile(file)
            }
            zipSources[zipFile.absolutePath] = compressedFiles.map { it.absolutePath }.toSet()
            fileDataset.addZipFile(zipFile)
        }
    }
}
