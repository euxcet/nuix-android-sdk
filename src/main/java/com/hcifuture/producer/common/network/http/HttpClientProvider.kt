package com.hcifuture.producer.common.network.http

import android.util.Log
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object HttpClientProvider {
    private const val CONNECT_TIMEOUT_SECONDS = 30L
    private const val READ_TIMEOUT_SECONDS = 120L
    private const val WRITE_TIMEOUT_SECONDS = 300L
    private lateinit var httpService: HttpService

    val retrofit: HttpService
        get() {
            if (!HttpClientProvider::httpService.isInitialized) {
                httpService = Retrofit.Builder()
                    .client(okHttp)
                    .addConverterFactory(GsonConverterFactory.create())
                    .baseUrl(HttpService.url)
                    .build()
                    .create(HttpService::class.java)
            }
            return httpService
        }

    val okHttp: OkHttpClient
        get() {
            return OkHttpClient.Builder().run {
                connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                build()
            }
        }
}
