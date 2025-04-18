package com.practicum.playlist_maker.pdfreader.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.practicum.playlist_maker.pdfreader.room.entity.CommentEntity
import com.practicum.playlist_maker.pdfreader.room.entity.HighlightEntity
import com.practicum.playlist_maker.pdfreader.room.entity.BookmarkEntity
import com.practicum.playlist_maker.pdfreader.room.entity.PdfNoteEntity
import com.practicum.playlist_maker.pdfreader.room.entity.PdfTagEntity

@Database(
    [
        PdfNoteEntity::class,
        PdfTagEntity::class,
        CommentEntity::class,
        HighlightEntity::class,
        BookmarkEntity::class,
    ], version = AppDatabase.VERSION
)
@TypeConverters(TypeConverter::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun getDao(): Dao

    companion object {
        private const val DATABASE_NAME = "app_database"
        const val VERSION = 3

        private var instance: AppDatabase? = null

        // Миграция с версии 2 на версию 3 (добавление поля isFavorite)
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE ${PdfNoteEntity.TABLE_NAME} ADD COLUMN ${PdfNoteEntity.FIELD_IS_FAVORITE} INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            if (instance != null) {
                return instance!!
            }

            instance = Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(MIGRATION_2_3)
                .build()

            return instance!!
        }
    }
}