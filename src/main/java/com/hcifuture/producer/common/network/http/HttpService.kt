package com.hcifuture.producer.common.network.http

import com.hcifuture.producer.BuildConfig
import com.hcifuture.producer.common.network.bean.CharacterResult
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Url

interface HttpService {
    companion object {
        const val url = BuildConfig.NUIX_SERVER_BASE
    }

    @Multipart
    @POST("/record")
    fun uploadFile(
        @Url url:String,
        @Part file: MultipartBody.Part,
        @Part("path") path: RequestBody,
    ): Call<Any>

    @Multipart
    @POST("/record")
    fun uploadFile(
        @Part file: MultipartBody.Part,
        @Part("path") path: RequestBody,
    ): Call<Any>

    @Multipart
    @POST("/detect")
    fun detectCharacter(
        @Part("trajectory") trajectory: RequestBody,
    ): Call<CharacterResult>
}