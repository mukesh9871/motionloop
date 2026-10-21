package com.motionloop.app.core.di

import com.motionloop.app.core.common.DefaultDispatcher
import com.motionloop.app.core.common.IoDispatcher
import com.motionloop.app.data.color.LutRepositoryImpl
import com.motionloop.app.data.media.MediaStoreSaver
import com.motionloop.app.data.project.ProjectAssetRepositoryImpl
import com.motionloop.app.data.settings.LocalEntitlements
import com.motionloop.app.domain.pro.Entitlements
import com.motionloop.app.data.project.ProjectRepositoryImpl
import com.motionloop.app.domain.repository.LutRepository
import com.motionloop.app.domain.repository.MediaRepository
import com.motionloop.app.domain.repository.ProjectAssetRepository
import com.motionloop.app.domain.repository.ProjectRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import android.content.Context
import android.media.AudioManager
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {
    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    fun audioManager(@ApplicationContext context: Context): AudioManager = context.getSystemService(AudioManager::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun projectRepository(impl: ProjectRepositoryImpl): ProjectRepository

    @Binds
    @Singleton
    abstract fun mediaRepository(impl: MediaStoreSaver): MediaRepository

    @Binds
    @Singleton
    abstract fun lutRepository(impl: LutRepositoryImpl): LutRepository

    @Binds
    @Singleton
    abstract fun projectAssetRepository(impl: ProjectAssetRepositoryImpl): ProjectAssetRepository

    /** Swap for a Google Play Billing implementation when purchases go live. */
    @Binds
    @Singleton
    abstract fun entitlements(impl: LocalEntitlements): Entitlements
}
