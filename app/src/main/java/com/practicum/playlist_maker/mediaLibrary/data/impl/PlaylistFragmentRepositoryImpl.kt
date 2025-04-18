package com.practicum.playlist_maker.mediaLibrary.data.impl

import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.practicum.playlist_maker.player.data.db.entity.LibraryEntity
import com.practicum.playlist_maker.player.domain.model.Library
import com.practicum.playlist_maker.mediaLibrary.domain.api.PlaylistFragmentRepository
import com.practicum.playlist_maker.player.data.db.AppDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.lang.reflect.Type
import java.util.*

class PlaylistFragmentRepositoryImpl(private val appDatabase: AppDatabase) :
    PlaylistFragmentRepository {

    override suspend fun deletePlaylist(playlist: Library) {
        appDatabase.libraryDao().deleteLibrary(convertFromLibraryToLibraryEntity(playlist))
    }

    override fun getPlaylists(): Flow<List<Library>> = flow {
        emit(convertFromLibraryEntitiesToLibraries(appDatabase.libraryDao().getLibraries()))
    }

    private fun convertFromLibraryToLibraryEntity(library: Library): LibraryEntity {
        return LibraryEntity(
            id = library.id,
            name = library.name,
            description = library.description,
            path = library.pathUri.toString(),
            fileIds = Gson().toJson(library.fileIds),
            countOfFiles = library.countOfFiles,
        )
    }

    private fun convertFromLibraryEntitiesToLibraries(libraryEntities: List<LibraryEntity>): List<Library> {
        val type: Type = object : TypeToken<List<Int?>?>() {}.type
        return libraryEntities.map { libraryEntity ->
            Library(
                id = libraryEntity.id,
                name = libraryEntity.name,
                description = libraryEntity.description,
                pathUri = Uri.parse(libraryEntity.path),
                fileIds = Gson().fromJson(
                    libraryEntity.fileIds,
                    type
                ),
                countOfFiles = libraryEntity.countOfFiles
            )
        }
    }
}