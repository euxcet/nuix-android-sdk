package com.hcifuture.producer.recorder

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.hcifuture.producer.common.network.http.HttpService
import com.hcifuture.producer.common.utils.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

sealed class UploadEvent {
    data class Succeeded(val batchId: String) : UploadEvent()
    data class Failed(val batchId: String, val message: String) : UploadEvent()
}

private data class PersistedUploadState(
    val pendingBatches: Map<String, List<String>> = emptyMap(),
    val zipSources: Map<String, List<String>> = emptyMap(),
)

private enum class UploadAttempt {
    SUCCESS,
    FAILED,
    NO_NETWORK,
    NO_WORK,
}

class Uploader(
    private val context: Context,
    private val fileDataset: FileDataset,
    private val httpService: HttpService,
) {
    companion object {
        private const val TAG = "NuixUploader"
        private const val STATE_PREFERENCES = "nuix_upload_queue_v2"
        private const val IDLE_DELAY_MS = 2_000L
        private const val NO_NETWORK_DELAY_MS = 10_000L
        private val RETRY_DELAYS_MS = longArrayOf(5_000L, 15_000L, 30_000L, 60_000L)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val gson = Gson()
    private val statePreferences = context.getSharedPreferences(STATE_PREFERENCES, Context.MODE_PRIVATE)
    private val stateKey = fileDataset.root.absolutePath
    private val _eventFlow = MutableSharedFlow<UploadEvent>(replay = 64, extraBufferCapacity = 64)
    val eventFlow = _eventFlow.asSharedFlow()
    private val pendingBatches = ConcurrentHashMap<String, MutableSet<String>>()
    private val zipSources = ConcurrentHashMap<String, Set<String>>()

    init {
        restoreState()
        start()
    }

    @Synchronized
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            var failureCount = 0
            while (isActive) {
                try {
                    compress()
                    when (uploadNext()) {
                        UploadAttempt.SUCCESS -> failureCount = 0
                        UploadAttempt.FAILED -> {
                            val delayIndex = failureCount.coerceAtMost(RETRY_DELAYS_MS.lastIndex)
                            failureCount += 1
                            delay(RETRY_DELAYS_MS[delayIndex])
                        }
                        UploadAttempt.NO_NETWORK -> delay(NO_NETWORK_DELAY_MS)
                        UploadAttempt.NO_WORK -> {
                            failureCount = 0
                            delay(IDLE_DELAY_MS)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Upload queue iteration failed", e)
                    delay(RETRY_DELAYS_MS.first())
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    @Synchronized
    fun enqueue(batchId: String, files: List<File>): Boolean {
        if (files.isEmpty() || files.any { !it.isFile || it.length() <= 0L }) {
            return false
        }
        val paths = concurrentPathSet(files.map { it.absolutePath })
        pendingBatches[batchId] = paths
        if (!persistState()) {
            pendingBatches.remove(batchId)
            return false
        }
        fileDataset.addDataFiles(files)
        return true
    }

    fun pendingBatchIds(): Set<String> = pendingBatches.keys.toSet()

    /** Upload exactly one archive at a time so large videos do not compete for weak uplinks. */
    private fun uploadNext(): UploadAttempt {
        val zip = fileDataset.getZipFiles(1).firstOrNull() ?: return UploadAttempt.NO_WORK
        if (!NetworkUtils.isWifiConnected(context)) return UploadAttempt.NO_NETWORK

        val filePart = MultipartBody.Part.createFormData(
            "file",
            zip.name,
            zip.asRequestBody("multipart/form-data".toMediaTypeOrNull()),
        )
        val path = fileDataset.getFolderPath(zip).toRequestBody("text/plain".toMediaTypeOrNull())
        return try {
            val response = httpService.uploadFile(filePart, path).execute()
            if (response.isSuccessful) {
                if (completeUploadedZip(zip)) UploadAttempt.SUCCESS else UploadAttempt.FAILED
            } else {
                response.errorBody()?.close()
                val message = "HTTP ${response.code()}"
                notifyUploadFailed(zipSources[zip.absolutePath].orEmpty(), message)
                UploadAttempt.FAILED
            }
        } catch (e: Exception) {
            val message = e.message ?: e.javaClass.simpleName
            Log.e(TAG, "Upload failed: $message")
            notifyUploadFailed(zipSources[zip.absolutePath].orEmpty(), message)
            UploadAttempt.FAILED
        }
    }

    @Synchronized
    private fun completeUploadedZip(zip: File): Boolean {
        val sourcePaths = zipSources.remove(zip.absolutePath).orEmpty()
        val pendingSnapshot = pendingBatches.entries.associate { (batchId, paths) ->
            batchId to paths.toSet()
        }
        val completedBatchIds = mutableListOf<String>()
        for ((batchId, remainingPaths) in pendingBatches.entries) {
            remainingPaths.removeAll(sourcePaths)
            if (remainingPaths.isEmpty() && pendingBatches.remove(batchId, remainingPaths)) {
                completedBatchIds.add(batchId)
            }
        }

        if (!persistState()) {
            Log.e(TAG, "Failed to persist successful upload state for ${zip.name}")
            pendingBatches.clear()
            pendingSnapshot.forEach { (batchId, paths) ->
                pendingBatches[batchId] = concurrentPathSet(paths)
            }
            zipSources[zip.absolutePath] = sourcePaths
            return false
        }

        // Keep the archive until the server has returned success and the local queue state is durable.
        fileDataset.removeZipFile(zip)
        completedBatchIds.forEach { batchId ->
            _eventFlow.tryEmit(UploadEvent.Succeeded(batchId))
        }
        return true
    }

    private fun notifyUploadFailed(sourcePaths: Set<String>, message: String) {
        for ((batchId, remainingPaths) in pendingBatches.entries) {
            if (remainingPaths.any { it in sourcePaths }) {
                _eventFlow.tryEmit(UploadEvent.Failed(batchId, message))
            }
        }
    }

    @Synchronized
    private fun compress() {
        val files = fileDataset.getDataFiles(10)
        if (files.isEmpty()) return

        val zipFile = fileDataset.prepareZipFile()
        val compressedFiles = mutableListOf<File>()
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { out ->
            for (file in files) {
                try {
                    FileInputStream(file).use { input ->
                        BufferedInputStream(input).use { origin ->
                            out.putNextEntry(ZipEntry(fileDataset.getPath(file)))
                            try {
                                origin.copyTo(out, 64 * 1024)
                            } finally {
                                out.closeEntry()
                            }
                        }
                    }
                    compressedFiles.add(file)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to compress ${file.absolutePath}", e)
                }
            }
        }

        if (compressedFiles.isEmpty()) {
            zipFile.delete()
            return
        }

        val sourcePaths = compressedFiles.map { it.absolutePath }.toSet()
        zipSources[zipFile.absolutePath] = sourcePaths
        fileDataset.addZipFile(zipFile)
        if (!persistState()) {
            zipSources.remove(zipFile.absolutePath)
            fileDataset.removeZipFile(zipFile)
            return
        }

        // The durable queue now points to a complete ZIP, so source files can be reclaimed safely.
        compressedFiles.forEach(fileDataset::removeDataFile)
    }

    @Synchronized
    private fun restoreState() {
        runCatching {
            val json = statePreferences.getString(stateKey, null) ?: return
            val state = gson.fromJson(json, PersistedUploadState::class.java) ?: return
            state.pendingBatches.forEach { (batchId, paths) ->
                pendingBatches[batchId] = concurrentPathSet(paths)
            }
            state.zipSources.forEach { (zipPath, paths) ->
                zipSources[zipPath] = paths.toSet()
            }

            var changed = false
            zipSources.toMap().forEach { (zipPath, sourcePaths) ->
                val zip = File(zipPath)
                if (isUsableZip(zip)) {
                    fileDataset.addZipFile(zip)
                    // Handles a process death after queue persistence but before source cleanup.
                    sourcePaths.forEach { sourcePath ->
                        fileDataset.removeDataFile(File(sourcePath))
                    }
                } else {
                    zipSources.remove(zipPath)
                    changed = true
                }
            }
            if (changed) persistState()
        }.onFailure { error ->
            Log.e(TAG, "Failed to restore upload queue", error)
        }
    }

    private fun isUsableZip(file: File): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        return runCatching {
            ZipFile(file).use { zip -> zip.entries().hasMoreElements() }
        }.getOrDefault(false)
    }

    @Synchronized
    private fun persistState(): Boolean {
        val state = PersistedUploadState(
            pendingBatches = pendingBatches.entries.associate { (batchId, paths) ->
                batchId to paths.toList()
            },
            zipSources = zipSources.entries.associate { (zipPath, paths) ->
                zipPath to paths.toList()
            },
        )
        return statePreferences.edit().putString(stateKey, gson.toJson(state)).commit()
    }

    private fun concurrentPathSet(paths: Collection<String>): MutableSet<String> {
        return ConcurrentHashMap.newKeySet<String>().apply { addAll(paths) }
    }
}
