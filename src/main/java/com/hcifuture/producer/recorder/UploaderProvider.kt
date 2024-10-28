package com.hcifuture.producer.recorder

import android.content.Context
import com.hcifuture.producer.common.network.http.HttpService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UploaderProvider @Inject constructor(
    @ApplicationContext val context: Context,
    private val httpService: HttpService
) {
    fun create(fileDataset: FileDataset): Uploader {
        return Uploader(context, fileDataset, httpService)
    }
}
