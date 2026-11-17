package ge.usltd.btu_classroom

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import ge.usltd.btu_classroom.core.preferences.AppLanguage
import ge.usltd.btu_classroom.core.preferences.AppThemeMode
import ge.usltd.btu_classroom.core.preferences.UserPreferencesStore
import ge.usltd.btu_classroom.core.security.SessionCipher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UserPreferencesStoreTest {
    @get:Rule val folder = TemporaryFolder()
    private lateinit var scope: CoroutineScope
    private lateinit var store: UserPreferencesStore

    @Before fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            folder.root.resolve("settings.preferences_pb")
        }
        store = UserPreferencesStore(dataStore, object : SessionCipher {
            override fun encrypt(plaintext: String) = "encrypted:$plaintext"
            override fun decrypt(encodedBlob: String) = encodedBlob.removePrefix("encrypted:")
        })
    }

    @After fun tearDown() { scope.cancel() }

    @Test fun defaultsRequireExplicitPersistenceAndBackgroundConsent() = runBlocking {
        val preferences = store.getCurrentPreferences()
        assertFalse(preferences.rememberLogin)
        assertFalse(preferences.backgroundSync)
        assertFalse(preferences.notificationsEnabled)
        store.saveEncryptedSession("fixture-cookie")
        assertNull(store.readDecryptedSession())
    }

    @Test fun disablingRememberLoginRemovesSavedSession() = runBlocking {
        store.setRememberLogin(true)
        store.saveEncryptedSession("fixture-cookie")
        assertEquals("fixture-cookie", store.readDecryptedSession())
        store.setRememberLogin(false)
        store.setRememberLogin(true)
        assertNull(store.readDecryptedSession())
    }

    @Test fun localWipeRemovesIdentityAndSessionButRetainsAppearance() = runBlocking {
        val generation = store.readAcademicDataGeneration()
        store.setRememberLogin(true)
        store.setLanguage(AppLanguage.KA)
        store.setThemeMode(AppThemeMode.DARK)
        store.saveEncryptedSession("fixture-cookie", "fixture-student")
        store.clearSessionAndIdentity()
        assertEquals(generation + 1L, store.readAcademicDataGeneration())
        assertNull(store.readDecryptedSession())
        assertEquals(UserPreferencesStore.DEFAULT_STUDENT_KEY, store.readLastStudentKey())
        assertEquals(AppLanguage.KA, store.getCurrentPreferences().language)
        assertEquals(AppThemeMode.DARK, store.getCurrentPreferences().themeMode)
    }
}
