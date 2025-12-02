package com.hcifuture.producer.utils

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.hcifuture.producer.BuildConfig
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LogUtils private constructor(val context: Context) {

    companion object {
        private const val LOG_DIR = "logs"
        private const val DATE_FORMAT = "yyyy-MM-dd"
        private const val TIME_FORMAT = "HH:mm:ss.SSS"
        private const val BUFFER_SIZE = 8192
        private const val FLUSH_THRESHOLD = 10

        // Log levels
        const val VERBOSE = "VERBOSE"
        const val DEBUG = "DEBUG"
        const val INFO = "INFO"
        const val WARN = "WARN"
        const val ERROR = "ERROR"

        @SuppressLint("StaticFieldLeak")
        private var instance: LogUtils? = null
        private val scope = CoroutineScope(Dispatchers.IO)
        
        // 使用单生产者-消费者队列模式
        private val logChannel = Channel<LogEntry>(10000)
        private var timeoutJob: kotlinx.coroutines.Job? = null
        private var isInitialized = false
        
        /**
         * Initialize the FileLogUtils with application context
         */
        fun init(ctx: Context) {
            if (isInitialized) {
                Log.w("LogUtils", "LogUtils already initialized, skipping")
                return
            }
            instance = LogUtils(ctx)
            isInitialized = true
            // 启动单一消费者协程处理日志写入
            scope.launch {
                instance?.processLogEntries()
            }
        }

        fun destroy() {
            instance?.apply {
                // 关闭 channel，不再接受新日志
                logChannel.close()
                // 取消定时刷新任务
                timeoutJob?.cancel()
                
                // 使用 runBlocking 确保所有日志都被写入
                kotlinx.coroutines.runBlocking {
                    // 等待 channel 中的所有日志被处理完成
                    var waitCount = 0
                    while (!logChannel.isEmpty && waitCount < 100) { // 最多等待 10 秒
                        delay(100)
                        waitCount++
                    }
                    
                    // 最后刷新缓冲区并关闭 writer
                    mutex.withLock {
                        flushBuffer()
                        writer?.close()
                        writer = null
                    }
                }
            }
            instance = null
            isInitialized = false
        }

        // 定义日志条目数据类（更新：增加对 throwable 支持）
        private data class LogEntry(val timestamp: Long, val level: String, val tag: String, val message: String, val throwable: Throwable? = null)
        
        private fun log(level: String, tag: String, message: String, throwable: Throwable? = null) {
            scope.launch {
                try {
                    logChannel.send(LogEntry(System.currentTimeMillis(), level, tag, message, throwable))
                } catch (e: Exception) {
                    // Channel 已关闭，忽略
                    if (BuildConfig.DEBUG) {
                        Log.w("LogUtils", "Failed to send log, channel may be closed: ${e.message}")
                    }
                }
            }
            if (BuildConfig.DEBUG) {
                // 同时输出到控制台
                val printLevel = when (level) {
                    VERBOSE -> Log.VERBOSE
                    DEBUG -> Log.DEBUG
                    INFO -> Log.INFO
                    WARN -> Log.WARN
                    ERROR -> Log.ERROR
                    else -> Log.INFO
                }
                Log.println(printLevel, tag, message)
            }
        }

        // Convenience methods for different log levels
        fun v(tag: String, message: String) = log(VERBOSE, tag, message)
        fun d(tag: String, message: String) = log(DEBUG, tag, message)
        fun i(tag: String, message: String) = log(INFO, tag, message)
        fun w(tag: String, message: String) = log(WARN, tag, message)
        // 更新：e 方法现在接受 throwable 参数
        fun e(tag: String, message: String, throwable: Throwable? = null) = log(ERROR, tag, message, throwable)

        // 新增：定义刷新超时时间（5秒）
        private const val FLUSH_TIMEOUT_MS = 5000L

        fun getLogFiles(context: Context): List<File> {
            val logDir = File(context.filesDir, LOG_DIR)
            return logDir.listFiles { file ->
                file.isFile && file.name.startsWith("log_") && file.name.endsWith(".txt")
            }?.toList() ?: emptyList()
        }
    }

    private val dateFormat by lazy { SimpleDateFormat(DATE_FORMAT, Locale.getDefault()) }
    private val timeFormat by lazy { SimpleDateFormat(TIME_FORMAT, Locale.getDefault()) }
    private val dateTimeFormat by lazy { SimpleDateFormat("$DATE_FORMAT $TIME_FORMAT", Locale.getDefault()) }
    private var logBuffer = mutableListOf<String>()
    private var writer: BufferedWriter? = null
    private var currentLogFileDate: String? = null
    private val mutex = Mutex()

    /**
     * Get log file for current date
     */
    private fun getLogFile(): File {
        val logDir = File(context.filesDir, LOG_DIR)
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
        
        val date = dateFormat.format(Date())
        return File(logDir, "log_$date.txt")
    }

    /**
     * 处理日志条目的单一消费者协程（更新：增加定时刷新机制）
     */
    private suspend fun processLogEntries() {
        var lastFlushTime = System.currentTimeMillis()
        timeoutJob = scope.launch {
            while (true) {
                delay(1000) // 每秒检查一次
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastFlushTime >= FLUSH_TIMEOUT_MS) {
                    mutex.withLock {
                        if (logBuffer.isNotEmpty()) {
                            flushBuffer()
                            lastFlushTime = currentTime
                        }
                    }
                }
            }
        }

        for (entry in logChannel) {
            try {
                val time = dateTimeFormat.format(Date(entry.timestamp))
                var logEntry = "[$time][${entry.tag}][${entry.level}]${entry.message}\n"
                entry.throwable?.let {
                    logEntry += "Throwable: ${it.message}\n"
                    logEntry += "Stack trace:\n${it.stackTraceToString()}\n"
                }

                mutex.withLock {
                    logBuffer.add(logEntry)
                    if (logBuffer.size >= FLUSH_THRESHOLD) {
                        flushBuffer()
                        lastFlushTime = System.currentTimeMillis()
                    }
                }
            } catch (e: Exception) {
                Log.e("FileLogUtils", "Error processing log entry: $e")
            }
        }

        // 停止定时任务
        timeoutJob?.cancel()
    }

    /**
     * Flush buffered logs to file (must be called with mutex locked)
     */
    private fun flushBuffer() {
        try {
            val currentDate = dateFormat.format(Date())
            
            // 检查日期是否变更，如果变更则关闭旧 writer 并创建新的
            if (currentDate != currentLogFileDate) {
                writer?.close()
                writer = null
                currentLogFileDate = currentDate
            }
            
            if (writer == null) {
                writer = BufferedWriter(FileWriter(getLogFile(), true), BUFFER_SIZE)
            }
            
            logBuffer.forEach { entry ->
                writer?.write(entry)
            }
            writer?.flush()
            logBuffer.clear()
        } catch (e: Exception) {
            Log.e("FileLogUtils", "Error flushing log buffer: $e")
        }
    }
}
