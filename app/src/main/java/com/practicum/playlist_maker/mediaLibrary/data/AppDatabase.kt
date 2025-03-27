//package com.practicum.playlist_maker.mediaLibrary.data
//
//import androidx.room.Database
//import androidx.room.Room
//import androidx.room.RoomDatabase
//import android.content.Context
//import com.practicum.playlist_maker.mediaLibrary.model.Content
//import com.practicum.playlist_maker.mediaLibrary.model.Directory
//
//@Database(entities = [Directory::class, Content::class], version = 1)
//abstract class AppDatabase : RoomDatabase() {
//
//    abstract fun directoryDao(): DirectoryDao
//    abstract fun contentDao(): ContentDao
//
//    companion object {
//        @Volatile
//        private var INSTANCE: AppDatabase? = null
//
//        fun getDatabase(context: Context): AppDatabase {
//            return INSTANCE ?: synchronized(this) {
//                val instance = Room.databaseBuilder(
//                    context.applicationContext,
//                    AppDatabase::class.java,
//                    "file_storage_db"
//                ).build()
//                INSTANCE = instance
//                instance
//            }
//        }
//    }
//}