package com.hcifuture.producer.recorder

import android.content.Context
import android.util.Log
import com.hcifuture.producer.common.network.http.HttpService
import com.hcifuture.producer.common.utils.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class Uploader(
    private val context: Context,
    private val fileDataset: FileDataset,
    private val httpService: HttpService,
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var job: Job? = null

    init {
        start()
    }

    fun start() {
        job = scope.launch {
            while (true) {
                upload()
                compress()
                delay(5000)
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    /**
     * When connected to WiFi, attempt to upload compressed files.
     */
    private fun upload() {
        for (zip in fileDataset.getZipFiles(10)) {
            if (NetworkUtils.isWifiConnected(context)) {
                val filePart = MultipartBody.Part
                    .createFormData("file", zip.name, zip.asRequestBody("multipart/form-data".toMediaTypeOrNull()))
                val path = fileDataset.getFolderPath(zip).toRequestBody("text/plain".toMediaTypeOrNull())
                httpService.uploadFile(filePart, path).enqueue(object: Callback<Any> {
                    override fun onResponse(call: Call<Any>, response: Response<Any>) {
                        fileDataset.removeZipFile(zip)
                    }

                    override fun onFailure(call: Call<Any>, t: Throwable) {
                        Log.e("Test", t.message.toString())
                    }
                })
            }
        }
    }

    private fun compress() {
        val files = fileDataset.getDataFiles(10)
        if (files.isNotEmpty()) {
            val zipFile = fileDataset.prepareZipFile()
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { out ->
                for (file in files) {
                    FileInputStream(file).use { fi ->
                        BufferedInputStream(fi).use { origin ->
                            val entry = ZipEntry(fileDataset.getPath(file))
                            out.putNextEntry(entry)
                            origin.copyTo(out, 1024)
                        }
                    }
                }
            }
            for (file in files) {
                fileDataset.removeDataFile(file)
            }
            fileDataset.addZipFile(zipFile)
        }
    }
}
