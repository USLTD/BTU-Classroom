package ge.usltd.btu_classroom.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ge.usltd.btu_classroom.core.auth.SessionManager
import ge.usltd.btu_classroom.core.database.BtuDatabase
import ge.usltd.btu_classroom.core.database.CourseRepository
import ge.usltd.btu_classroom.core.database.dao.CourseDao
import ge.usltd.btu_classroom.core.database.dao.StudentDao
import ge.usltd.btu_classroom.core.database.dao.SyncMetadataDao
import ge.usltd.btu_classroom.core.network.BtuHttpClient
import ge.usltd.btu_classroom.core.parsing.PageCompatibilityRegistry
import ge.usltd.btu_classroom.core.preferences.UserPreferencesStore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): BtuDatabase =
        Room.databaseBuilder(
            context,
            BtuDatabase::class.java,
            "btu_classroom.db"
        ).addMigrations(BtuDatabase.MIGRATION_1_2).build()

    @Provides
    fun provideCourseDao(
        database: BtuDatabase
    ): CourseDao = database.courseDao()

    @Provides
    fun provideStudentDao(
        database: BtuDatabase
    ): StudentDao = database.studentDao()

    @Provides
    fun provideSyncMetadataDao(
        database: BtuDatabase
    ): SyncMetadataDao = database.syncMetadataDao()

    @Provides
    @Singleton
    fun provideCompatibilityRegistry(): PageCompatibilityRegistry = PageCompatibilityRegistry()

    @Provides
    @Singleton
    fun provideCourseRepository(
        database: BtuDatabase,
        httpClient: BtuHttpClient,
        sessionManager: SessionManager,
        preferencesStore: UserPreferencesStore,
        compatibilityRegistry: PageCompatibilityRegistry,
    ): CourseRepository = CourseRepository(
        database, httpClient, sessionManager, preferencesStore, compatibilityRegistry,
    )
}
