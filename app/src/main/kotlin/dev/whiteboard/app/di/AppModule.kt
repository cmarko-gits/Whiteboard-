package dev.whiteboard.app.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.whiteboard.app.workspace.DocumentFiles
import dev.whiteboard.core.concurrency.AppDispatchers
import dev.whiteboard.core.concurrency.DefaultAppDispatchers
import dev.whiteboard.core.repository.*
import dev.whiteboard.core.usecase.*
import dev.whiteboard.data.*
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun dispatchers(): AppDispatchers = DefaultAppDispatchers

    @Provides @Singleton
    fun database(@ApplicationContext context: Context): WhiteboardDatabase =
        Room.databaseBuilder(context, WhiteboardDatabase::class.java, "whiteboard.db")
            .addMigrations(DatabaseMigrations.FROM_1_TO_2, DatabaseMigrations.FROM_2_TO_3, DatabaseMigrations.FROM_3_TO_4).build()

    @Provides @Singleton
    fun boards(database: WhiteboardDatabase): BoardRepository = LocalBoardRepository(database)

    @Provides @Singleton
    fun folders(database: WhiteboardDatabase): FolderRepository = LocalFolderRepository(database)

    @Provides @Singleton
    fun settings(@ApplicationContext context: Context): SettingsRepository = AppSettings(context)

    @Provides @Singleton
    fun storage(files: DocumentFiles): DocumentStorage = files

    @Provides
    fun persistence(boards: BoardRepository): EditorPersistence = EditorPersistence(boards)

    @Provides
    fun transfers(storage: DocumentStorage): DocumentTransfers = DocumentTransfers(storage)

    @Provides
    fun preferences(settings: SettingsRepository): ManagePreferences = ManagePreferences(settings)

    @Provides
    fun library(boards: BoardRepository, folders: FolderRepository, storage: DocumentStorage, dispatchers: AppDispatchers): ManageLibrary =
        ManageLibrary(boards, folders, storage, dispatchers)
}
