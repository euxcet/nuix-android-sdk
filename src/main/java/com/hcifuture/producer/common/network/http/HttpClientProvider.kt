package com.hcifuture.producer.common.network.http

import android.util.Log
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object HttpClientProvider {
    private const val DEFAULT_TIMEOUT = 20000
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
                connectTimeout(DEFAULT_TIMEOUT.toLong(), TimeUnit.MILLISECONDS)
                readTimeout(DEFAULT_TIMEOUT.toLong(), TimeUnit.MILLISECONDS)
                writeTimeout(DEFAULT_TIMEOUT.toLong(), TimeUnit.MILLISECONDS)
                build()
            }
        }
}