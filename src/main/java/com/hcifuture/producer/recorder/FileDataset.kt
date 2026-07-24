package com.hcifuture.producer.recorder

import android.util.Log
import com.hcifuture.producer.common.utils.FileUtils.Companion.loadVisibleFile
import java.io.File
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.Date
import java.util.Locale

class FileDataset(
    val root: File,
    val name: String,
    val userId: String?,
    val taskId: String?,
) {
    private val dataFiles: MutableSet<File> = Collections.synchronizedSet(mutableSetOf())
    private val zipFiles: MutableSet<File> = Collections.synchronizedSet(mutableSetOf())
    private val tmpDir: File
    init {
        if (!root.exists()) {
            root.mkdirs()
        }
        tmpDir = File(root, ".tmp")
        if (!tmpDir.exists()) {
            tmpDir.mkdirs()
        }
        addDataFiles(loadVisibleFile(root))
        addZipFiles(loadVisibleFile(tmpDir))
    }

    fun exists(file: File): Boolean {
        return true
    }

    fun removeDataFile(file: File) {
        if (file.exists()) {
            file.delete()
        }
        dataFiles.remove(file)
    }

    fun removeDataFiles(files: List<File>) {
        for (file in files) {
            removeDataFile(file)
        }
    }

    fun removeZipFile(file: File) {
        if (file.exists()) {
            file.delete()
        }
        zipFiles.remove(file)
    }

    fun removeZipFiles(files: List<File>) {
        for (file in files) {
            removeZipFile(file)
        }
    }

    fun addDataFiles(files: List<File>) {
        for (file in files) {
            addDataFile(file)
        }
    }

    fun addDataFile(file: File) {
        dataFiles.add(file)
    }

    fun addZipFiles(files: List<File>) {
        for (file in files) {
            addZipFile(file)
        }
    }

    fun addZipFile(file: File) {
        zipFiles.add(file)
    }

    fun getDataFiles(count: Int): List<File> {
        return synchronized(dataFiles) { dataFiles.sortedBy { it.absolutePath }.take(count) }
    }

    fun getDataFile(): File {
        return dataFiles.first()
    }

    fun getZipFiles(count: Int): List<File> {
        return synchronized(zipFiles) { zipFiles.sortedBy { it.name }.take(count) }
    }

    fun getZipFile(): File {
        return zipFiles.first()
    }

    fun getFolderPath(file: File): String {
        return file.relativeTo(root.parentFile!!).parentFile!!.absolutePath
    }

    fun getPath(file: File): String {
        return file.relativeTo(root.parentFile!!).absolutePath
    }

    fun prepareFiles(path: Array<out String>, collectors: List<Collector>): List<File> {
        val dir = File(root, path.joinToString(separator = File.separator))
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val formatter = DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm_ss")
        val timestamp = LocalDateTime.now().format(formatter)
        val currentUserId = path.getOrNull(0) ?: userId
        val currentTaskId = path.getOrNull(1) ?: taskId
        val files = collectors.map {
            File(dir, "${currentUserId}_${currentTaskId}_${timestamp}_${it.suffixName}".replace(":", ""))
        }
        assert(files.all { !it.exists() })
        return files
    }

    fun prepareZipFile(): File {
        return File(tmpDir, "${System.currentTimeMillis()}.zip")
    }

    fun quarantineFiles(files: List<File>, reason: String): List<File> {
        val safeReason = reason.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val failedDir = File(root, ".failed/${System.currentTimeMillis()}_$safeReason")
        if (!failedDir.exists()) {
            failedDir.mkdirs()
        }
        return files.mapNotNull { file ->
            if (!file.exists()) {
                null
            } else {
                val target = File(failedDir, file.name)
                if (file.renameTo(target)) {
                    target
                } else {
                    Log.e("Nuix", "Failed to quarantine ${file.absolutePath}")
                    null
                }
            }
        }
    }
}
