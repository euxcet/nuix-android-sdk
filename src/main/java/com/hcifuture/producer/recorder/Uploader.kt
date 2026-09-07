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
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID
import kotlin.math.max
import kotlin.random.Random
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

sealed class UploadEvent {
    data class Succeeded(val batchId: String) : UploadEvent()
    data class Failed(val batchId: String, val message: String) : UploadEvent()
    data class Blocked(val batchId: String, val message: String) : UploadEvent()
}

private data class PersistedUploadState(
    val pendingBatches: Map<String, List<String>> = emptyMap(),
    val zipSources: Map<String, List<String>> = emptyMap(),
)

private sealed class UploadAttempt {
    data object Success : UploadAttempt()
    data class Retry(val minimumDelayMs: Long = 0L) : UploadAttempt()
    data object NoNetwork : UploadAttempt()
    data object NoWork : UploadAttempt()
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
        private const val MAX_RETRY_AFTER_SECONDS = 3_600L
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
    private val blockedZips = ConcurrentHashMap<String, String>()
    private val zipHashCache = ConcurrentHashMap<String, Pair<String, String>>()

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
                    when (val attempt = uploadNext()) {
                        UploadAttempt.Success -> failureCount = 0
                        is UploadAttempt.Retry -> {
                            val retryDelay = retryDelayWithJitter(failureCount, attempt.minimumDelayMs)
                            failureCount += 1
                            delay(retryDelay)
                        }
                        UploadAttempt.NoNetwork -> delay(NO_NETWORK_DELAY_MS)
                        UploadAttempt.NoWork -> {
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
        val zip = fileDataset.getZipFiles(Int.MAX_VALUE)
            .firstOrNull { !blockedZips.containsKey(it.absolutePath) }
            ?: return UploadAttempt.NoWork
        if (!NetworkUtils.isWifiConnected(context)) return UploadAttempt.NoNetwork

        val uploadId = zip.nameWithoutExtension
        val contentSha256 = zipSha256(zip)
            ?: return blockUpload(zip, "无法计算ZIP校验值")

        val filePart = MultipartBody.Part.createFormData(
            "file",
            zip.name,
            zip.asRequestBody("multipart/form-data".toMediaTypeOrNull()),
        )
        val path = fileDataset.getFolderPath(zip).toRequestBody("text/plain".toMediaTypeOrNull())
        return try {
            val response = httpService.uploadFile(uploadId, contentSha256, filePart, path).execute()
            if (response.isSuccessful) {
                if (completeUploadedZip(zip)) UploadAttempt.Success else UploadAttempt.Retry()
            } else {
                response.errorBody()?.close()
                val code = response.code()
                val message = "HTTP $code"
                notifyUploadFailed(zipSources[zip.absolutePath].orEmpty(), message)
                when {
                    code == 408 || code == 425 || code == 429 || code in 500..599 -> {
                        UploadAttempt.Retry(parseRetryAfterMillis(response.headers()["Retry-After"]))
                    }
                    else -> blockUpload(zip, message)
                }
            }
        } catch (e: Exception) {
            val message = e.message ?: e.javaClass.simpleName
            Log.e(TAG, "Upload failed: $message")
            notifyUploadFailed(zipSources[zip.absolutePath].orEmpty(), message)
            UploadAttempt.Retry()
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
        blockedZips.remove(zip.absolutePath)
        zipHashCache.remove(zip.absolutePath)
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

    private fun blockUpload(zip: File, message: String): UploadAttempt {
        blockedZips[zip.absolutePath] = message
        val sourcePaths = zipSources[zip.absolutePath].orEmpty()
        for ((batchId, remainingPaths) in pendingBatches.entries) {
            if (remainingPaths.any { it in sourcePaths }) {
                _eventFlow.tryEmit(UploadEvent.Blocked(batchId, message))
            }
        }
        Log.e(TAG, "Upload blocked for ${zip.name}: $message")
        return UploadAttempt.NoWork
    }

    @Synchronized
    private fun compress() {
        val assignedPaths = zipSources.values.flatten().toSet()
        val pendingPaths = pendingBatches.values.flatten().toSet()

        // Keep a capture batch together whenever possible. Legacy standalone files are still
        // supported, but they are never mixed into a named capture batch.
        val batchFiles = pendingBatches.entries
            .sortedBy { it.key }
            .asSequence()
            .map { (_, paths) ->
                paths.asSequence()
                    .filterNot { it in assignedPaths }
                    .map(::File)
                    .toList()
            }
            .firstOrNull { candidates ->
                candidates.isNotEmpty() && candidates.all { it.isFile && it.length() > 0L }
            }
        val files = batchFiles ?: fileDataset.getDataFiles(Int.MAX_VALUE)
            .asSequence()
            .filterNot { it.absolutePath in pendingPaths || it.absolutePath in assignedPaths }
            .take(10)
            .toList()
        if (files.isEmpty()) return

        val uploadId = UUID.randomUUID().toString()
        val zipFile = fileDataset.prepareZipFile(uploadId)
        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { out ->
                for (file in files.sortedBy { it.absolutePath }) {
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
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create upload archive $uploadId", e)
            zipFile.delete()
            return
        }

        if (!isUsableZip(zipFile)) {
            zipFile.delete()
            return
        }

        val sourcePaths = files.map { it.absolutePath }.toSet()
        zipSources[zipFile.absolutePath] = sourcePaths
        fileDataset.addZipFile(zipFile)
        if (!persistState()) {
            zipSources.remove(zipFile.absolutePath)
            fileDataset.removeZipFile(zipFile)
            return
        }

        // The durable queue now points to a complete ZIP, so source files can be reclaimed safely.
        files.forEach(fileDataset::removeDataFile)
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
                    blockedZips.remove(zipPath)
                    zipHashCache.remove(zipPath)
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

    private fun zipSha256(file: File): String? {
        val cacheKey = "${file.length()}:${file.lastModified()}"
        zipHashCache[file.absolutePath]?.let { (cachedKey, cachedHash) ->
            if (cachedKey == cacheKey) return cachedHash
        }
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            BufferedInputStream(FileInputStream(file)).use { input ->
                val buffer = ByteArray(128 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }.also { hash ->
                zipHashCache[file.absolutePath] = cacheKey to hash
            }
        }.onFailure { error ->
            Log.e(TAG, "Failed to hash ${file.absolutePath}", error)
        }.getOrNull()
    }

    private fun retryDelayWithJitter(failureCount: Int, minimumDelayMs: Long): Long {
        val base = RETRY_DELAYS_MS[failureCount.coerceAtMost(RETRY_DELAYS_MS.lastIndex)]
        val jittered = (base * Random.nextDouble(0.8, 1.2)).toLong()
        return max(jittered, minimumDelayMs)
    }

    private fun parseRetryAfterMillis(value: String?): Long {
        val seconds = value?.trim()?.toLongOrNull() ?: return 0L
        return seconds.coerceIn(1L, MAX_RETRY_AFTER_SECONDS) * 1_000L
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
