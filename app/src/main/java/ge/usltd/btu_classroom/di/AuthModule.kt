package ge.usltd.btu_classroom.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ge.usltd.btu_classroom.core.auth.SessionManager
import ge.usltd.btu_classroom.core.network.BtuHttpClient
import ge.usltd.btu_classroom.core.preferences.UserPreferencesStore
import ge.usltd.btu_classroom.core.security.AndroidKeystoreSessionCipher
import ge.usltd.btu_classroom.core.security.SessionCipher
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {
  @Provides
  @Singleton
  fun provideSessionCipher(): SessionCipher = AndroidKeystoreSessionCipher()

  @Provides
  @Singleton
  fun provideOkHttpClient(): OkHttpClient = BtuHttpClient.buildDefaultClient()

  @Provides
  @Singleton
  fun provideHttpClient(client: OkHttpClient): BtuHttpClient = BtuHttpClient(client = client)

  @Provides
  @Singleton
  fun provideSessionManager(client: BtuHttpClient, preferences: UserPreferencesStore): SessionManager =
    SessionManager(client, preferences)
}
