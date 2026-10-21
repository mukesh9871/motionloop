package com.motionloop.app.core.di

import android.content.Context
import com.motionloop.ml.DepthEstimator
import com.motionloop.ml.Segmenter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** One model instance per process: loading a model is slow and they hold native memory. */
@Module
@InstallIn(SingletonComponent::class)
object MlModule {
    @Provides
    @Singleton
    fun segmenter(@ApplicationContext context: Context): Segmenter = Segmenter(context)

    @Provides
    @Singleton
    fun depthEstimator(@ApplicationContext context: Context): DepthEstimator = DepthEstimator(context)
}
