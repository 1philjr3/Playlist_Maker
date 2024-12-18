package com.practicum.playlist_maker

import android.app.Application
import androidx.room.Room
import com.google.firebase.FirebaseApp
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.practicum.playlist_maker.creationPlaylist.di.creationPlaylistModule
import com.practicum.playlist_maker.mediaLibrary.di.mediaLibraryModule
import com.practicum.playlist_maker.mediaLibrary.di.playlistFragmentModule
import com.practicum.playlist_maker.player.data.db.AppDatabase
import com.practicum.playlist_maker.player.di.audioPlayerDataModule
import com.practicum.playlist_maker.player.di.audioPlayerModule
import com.practicum.playlist_maker.player.di.audioPlayerViewModelModule
import com.practicum.playlist_maker.player.di.favoriteTrackInteractorModule
import com.practicum.playlist_maker.search.di.searchDataModule
import com.practicum.playlist_maker.search.di.searchInteractorModule
import com.practicum.playlist_maker.search.di.searchViewModelModule
import com.practicum.playlist_maker.settings.di.settingsInteractorModule
import com.practicum.playlist_maker.settings.di.settingsRepositoryModule
import com.practicum.playlist_maker.settings.di.settingsViewModelModule
import com.practicum.playlist_maker.settings.domain.api.SettingsInteractor
import dagger.hilt.android.HiltAndroidApp
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

const val KEY_FOR_SWITCH = "key_for_switch"
const val SETTINGS_PREFERENCES = "settings_preferences"

@HiltAndroidApp
class App : Application() {

    companion object {
        lateinit var database: AppDatabase
            private set
    }

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)

        // Инициализация PDFBox
        PDFBoxResourceLoader.init(this.applicationContext)

        // Инициализация базы данных Room
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "playlist_maker_db"
        ).fallbackToDestructiveMigration()
            .build()

        // Запуск Koin для внедрения зависимостей
        startKoin {
            androidContext(this@App)
            modules(
                searchDataModule,
                searchInteractorModule,
                searchViewModelModule,
                audioPlayerDataModule,
                audioPlayerModule,
                audioPlayerViewModelModule,
                favoriteTrackInteractorModule,
                settingsInteractorModule,
                settingsRepositoryModule,
                settingsViewModelModule,
                mediaLibraryModule,
                creationPlaylistModule,
                playlistFragmentModule,
            )
        }

        val settingsInteractor: SettingsInteractor by inject()
        settingsInteractor.switchTheme(settingsInteractor.isDarkTheme())
    }
}
