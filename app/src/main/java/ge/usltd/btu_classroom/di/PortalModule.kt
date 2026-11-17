package ge.usltd.btu_classroom.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ge.usltd.btu_classroom.core.auth.SessionManager
import ge.usltd.btu_classroom.core.database.BtuDatabase
import ge.usltd.btu_classroom.core.network.BtuHttpClient
import ge.usltd.btu_classroom.core.portal.PortalDao
import ge.usltd.btu_classroom.core.portal.PortalPageParser
import ge.usltd.btu_classroom.core.portal.PortalRepository
import ge.usltd.btu_classroom.core.preferences.UserPreferencesStore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PortalModule {
  @Provides
  fun providePortalDao(database: BtuDatabase): PortalDao = database.portalDao()

  @Provides
  @Singleton
  fun providePortalPageParser(): PortalPageParser = PortalPageParser()

  @Provides
  @Singleton
  fun providePortalRepository(
    database: BtuDatabase,
    httpClient: BtuHttpClient,
    sessionManager: SessionManager,
    preferencesStore: UserPreferencesStore,
    parser: PortalPageParser,
  ): PortalRepository = PortalRepository(database, httpClient, sessionManager, preferencesStore, parser)
}
