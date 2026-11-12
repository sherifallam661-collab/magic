package com.mediagrab.app.di

import android.content.Context
import androidx.room.Room
import com.mediagrab.app.data.local.MediaGrabDatabase
import com.mediagrab.app.data.local.dao.DownloadDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MediaGrabDatabase {
        return Room.databaseBuilder(
            context,
            MediaGrabDatabase::class.java,
            "mediagrab.db"
        ).build()
    }

    @Provides
    fun provideDownloadDao(database: MediaGrabDatabase): DownloadDao = database.downloadDao()
}
