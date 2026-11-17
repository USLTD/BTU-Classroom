package ge.usltd.btu_classroom.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ge.usltd.btu_classroom.core.security.SessionCipher
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.btucmDataStore: DataStore<Preferences> by preferencesDataStore(
  name = "btucm_preferences",
)

class UserPreferencesStore(
  private val dataStore: DataStore<Preferences>,
  private val cipher: SessionCipher,
) {
  constructor(context: Context, cipher: SessionCipher) : this(
    dataStore = context.applicationContext.btucmDataStore,
    cipher = cipher,
  )

  val preferencesFlow: Flow<UserPreferences> = dataStore.data
    .catch { exception ->
      if (exception is IOException) {
        emit(emptyPreferences())
      } else {
        throw exception
      }
    }
    .map { prefs ->
      UserPreferences(
        rememberLogin = prefs[KEY_REMEMBER_LOGIN] ?: false,
        language = AppLanguage.fromCode(prefs[KEY_LANGUAGE]),
        themeMode = AppThemeMode.fromCode(prefs[KEY_THEME_MODE]),
        dynamicColor = prefs[KEY_DYNAMIC_COLOR] ?: false,
        backgroundSync = prefs[KEY_BACKGROUND_SYNC] ?: false,
        notificationsEnabled = prefs[KEY_NOTIFICATIONS_ENABLED] ?: false,
      )
    }

  suspend fun getCurrentPreferences(): UserPreferences = preferencesFlow.first()

  suspend fun setRememberLogin(remember: Boolean) {
    dataStore.edit { prefs ->
      prefs[KEY_REMEMBER_LOGIN] = remember
      if (!remember) {
        prefs.remove(KEY_ENCRYPTED_SESSION)
      }
    }
  }

  suspend fun setLanguage(language: AppLanguage) {
    dataStore.edit { prefs ->
      prefs[KEY_LANGUAGE] = language.code
    }
  }

  suspend fun setThemeMode(themeMode: AppThemeMode) {
    dataStore.edit { prefs ->
      prefs[KEY_THEME_MODE] = themeMode.code
    }
  }

  suspend fun setDynamicColor(enabled: Boolean) {
    dataStore.edit { prefs ->
      prefs[KEY_DYNAMIC_COLOR] = enabled
    }
  }

  suspend fun setBackgroundSync(enabled: Boolean) {
    dataStore.edit { it[KEY_BACKGROUND_SYNC] = enabled }
  }

  suspend fun setNotificationsEnabled(enabled: Boolean) {
    dataStore.edit { it[KEY_NOTIFICATIONS_ENABLED] = enabled }
  }

  suspend fun saveEncryptedSession(sessionCookie: String, studentKey: String? = null) {
    val encrypted = cipher.encrypt(sessionCookie) ?: return
    dataStore.edit { prefs ->
      if (prefs[KEY_REMEMBER_LOGIN] != true) return@edit
      prefs[KEY_ENCRYPTED_SESSION] = encrypted
      if (!studentKey.isNullOrBlank()) {
        prefs[KEY_LAST_STUDENT_KEY] = studentKey
      }
    }
  }

  suspend fun readDecryptedSession(): String? {
    val prefs = dataStore.data
      .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
      .first()
    if (prefs[KEY_REMEMBER_LOGIN] != true) {
      return null
    }
    val blob = prefs[KEY_ENCRYPTED_SESSION] ?: return null
    val decrypted = cipher.decrypt(blob)
    if (decrypted == null) {
      clearEncryptedSession()
    }
    return decrypted
  }

  suspend fun readLastStudentKey(): String {
    val prefs = dataStore.data
      .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
      .first()
    return prefs[KEY_LAST_STUDENT_KEY]?.ifBlank { null } ?: DEFAULT_STUDENT_KEY
  }

  suspend fun setLastStudentKey(studentKey: String) {
    if (studentKey.isBlank()) return
    dataStore.edit { prefs ->
      prefs[KEY_LAST_STUDENT_KEY] = studentKey
    }
  }

  suspend fun clearEncryptedSession() {
    dataStore.edit { prefs ->
      prefs.remove(KEY_ENCRYPTED_SESSION)
    }
  }

  suspend fun clearSessionAndIdentity() {
    dataStore.edit { prefs ->
      prefs.remove(KEY_ENCRYPTED_SESSION)
      prefs.remove(KEY_LAST_STUDENT_KEY)
      prefs[KEY_ACADEMIC_DATA_GENERATION] = (prefs[KEY_ACADEMIC_DATA_GENERATION] ?: 0L) + 1L
    }
  }

  suspend fun readAcademicDataGeneration(): Long = dataStore.data
    .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
    .first()[KEY_ACADEMIC_DATA_GENERATION] ?: 0L

  companion object {
    const val DEFAULT_STUDENT_KEY = "default_student"
    private val KEY_REMEMBER_LOGIN = booleanPreferencesKey("remember_login")
    private val KEY_LANGUAGE = stringPreferencesKey("app_language")
    private val KEY_THEME_MODE = stringPreferencesKey("app_theme_mode")
    private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    private val KEY_BACKGROUND_SYNC = booleanPreferencesKey("background_sync")
    private val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    private val KEY_ENCRYPTED_SESSION = stringPreferencesKey("encrypted_session_blob")
    private val KEY_LAST_STUDENT_KEY = stringPreferencesKey("last_student_key")
    private val KEY_ACADEMIC_DATA_GENERATION = longPreferencesKey("academic_data_generation")
  }
}
