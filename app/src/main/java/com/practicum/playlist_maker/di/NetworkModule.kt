package com.practicum.playlist_maker.di

import com.practicum.playlist_maker.search.data.api.PdfServerApiService
import com.practicum.playlist_maker.network.ServerUrlUpdater
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton
import android.content.Context

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply { 
            level = HttpLoggingInterceptor.Level.BODY 
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(loggingInterceptor: HttpLoggingInterceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, @ApplicationContext context: Context): Retrofit {
        // Используем URL из настроек или DuckDNS
        val serverUrl = context.getSharedPreferences("PROVITA_SETTINGS", Context.MODE_PRIVATE)
            .getString("SERVER_URL", ServerUrlUpdater.getDuckDnsUrl())
            ?: ServerUrlUpdater.getDuckDnsUrl()
        
        return Retrofit.Builder()
            .baseUrl(serverUrl) // Используем динамический URL
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun providePdfServerApiService(retrofit: Retrofit): PdfServerApiService {
        return retrofit.create(PdfServerApiService::class.java)
    }
} 