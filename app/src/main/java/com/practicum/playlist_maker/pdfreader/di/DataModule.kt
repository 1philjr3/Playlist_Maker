package com.practicum.playlist_maker.pdfreader.di

import android.content.Context
import com.practicum.playlist_maker.pdfreader.room.AppDatabase
import com.practicum.playlist_maker.pdfreader.room.Dao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideDatabaseDao(database: AppDatabase): Dao {
        return database.getDao()
    }


}