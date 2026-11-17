package ge.usltd.btu_classroom.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ge.usltd.btu_classroom.core.auth.SessionManager
import ge.usltd.btu_classroom.core.download.BtuFileDownloader
import ge.usltd.btu_classroom.core.preferences.UserPreferencesStore
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object DownloadModule {
  @Provides
  @Singleton
  fun provideFileDownloader(
    @ApplicationContext context: Context,
    sessionManager: SessionManager,
    preferencesStore: UserPreferencesStore,
    client: OkHttpClient,
  ): BtuFileDownloader = BtuFileDownloader(context, sessionManager, preferencesStore, client)
}
