package com.motionloop.app.core.di

import com.motionloop.app.core.common.DefaultDispatcher
import com.motionloop.app.core.common.IoDispatcher
import com.motionloop.app.data.media.MediaStoreSaver
import com.motionloop.app.data.project.ProjectRepositoryImpl
import com.motionloop.app.domain.repository.MediaRepository
import com.motionloop.app.domain.repository.ProjectRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
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
}
