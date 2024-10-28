package com.hcifuture.producer.common.di


import com.hcifuture.producer.common.network.http.HttpClientProvider
import com.hcifuture.producer.common.network.http.HttpService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class NetworkModule {

    @Singleton
    @Provides
    fun provideHttpService(): HttpService = HttpClientProvider.retrofit

    @Singleton
    @Provides
    fun provideOkHttp(): OkHttpClient = HttpClientProvider.okHttp
}