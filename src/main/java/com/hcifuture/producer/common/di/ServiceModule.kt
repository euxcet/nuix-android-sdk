package com.hcifuture.producer.common.di

import android.content.Context
import android.content.res.AssetManager
import android.hardware.SensorManager
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {

    @Provides
    @Singleton
    fun provideSensorManager(@ApplicationContext context: Context): SensorManager {
        return context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

//    @RequiresApi(Build.VERSION_CODES.S)
//    @Provides
//    @Singleton
//    fun provideVibratorManager(@ApplicationContext context: Context): VibratorManager {
//        return context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
//    }

    @Provides
    @Singleton
    fun provideVibratorManager(@ApplicationContext context: Context): Vibrator {
        return context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    @Provides
    @Singleton
    fun provideAssetManager(@ApplicationContext context: Context): AssetManager {
        return context.assets
    }
}