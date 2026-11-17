package ge.usltd.btu_classroom

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import ge.usltd.btu_classroom.core.auth.AuthState
import ge.usltd.btu_classroom.core.auth.SessionManager
import ge.usltd.btu_classroom.core.auth.LocalCleanupException
import ge.usltd.btu_classroom.core.network.BtuHttpClient
import ge.usltd.btu_classroom.core.network.VerificationStatus
import ge.usltd.btu_classroom.core.preferences.UserPreferencesStore
import ge.usltd.btu_classroom.core.security.SessionCipher
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SessionManagerTest {
  private lateinit var server: MockWebServer
  private lateinit var preferences: UserPreferencesStore
  private lateinit var manager: SessionManager
  private lateinit var storeScope: CoroutineScope
  private lateinit var dataStore: DataStore<Preferences>
  private val cipher = object : SessionCipher {
    override fun encrypt(plaintext: String) = plaintext
    override fun decrypt(encodedBlob: String) = encodedBlob
  }

  @Before
  fun setup() {
    server = MockWebServer().apply { start() }
    val context = ApplicationProvider.getApplicationContext<Application>()
    storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    dataStore = PreferenceDataStoreFactory.create(scope = storeScope) {
      File(context.filesDir, "session_${System.nanoTime()}.preferences_pb")
    }
    preferences = UserPreferencesStore(dataStore, cipher)
    manager = SessionManager(BtuHttpClient(
      baseOrigin = "http://127.0.0.1:${server.port}", allowLocalHttpForTesting = true,
    ), preferences)
  }

  @After
  fun cleanup() {
    server.shutdown()
    storeScope.cancel()
  }

  @Test
  fun `temporary portal failure during restore preserves saved session`() = runTest {
    preferences.setRememberLogin(true)
    preferences.saveEncryptedSession("saved_session")
    server.enqueue(MockResponse().setResponseCode(503).setBody("Maintenance"))
    manager.restoreAndVerifyOnLaunch(hasCachedAcademicData = true)
    assertEquals("saved_session", preferences.readDecryptedSession())
    assertEquals(AuthState.Authenticated, manager.authState.value)
    assertEquals(true, manager.telemetry.value.isOfflineFallback)
    assertFalse(manager.telemetry.value.isBusy)
  }

  @Test
  fun `authoritative rejection during restore clears saved secret`() = runTest {
    preferences.setRememberLogin(true)
    preferences.saveEncryptedSession("saved_session")
    server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/ge/login/index"))
    manager.restoreAndVerifyOnLaunch(hasCachedAcademicData = true)
    assertNull(preferences.readDecryptedSession())
    assertNull(manager.currentSessionValue())
    assertEquals(AuthState.Expired, manager.authState.value)
  }

  @Test
  fun `remote logout failure still clears local credentials and cache`() = runTest {
    preferences.setRememberLogin(true)
    preferences.saveEncryptedSession("active_session")
    manager.onSessionReadyFromBrowser("active_session")
    server.enqueue(MockResponse().setResponseCode(503))
    var cleared = false
    var browserReset = false
    val result = manager.signOutRemoteAndClearLocal(
      clearAcademicCache = { cleared = true },
      resetBrowserStorage = { completion -> browserReset = true; completion() },
    )
    assertFalse(result.success)
    assertEquals(true, cleared)
    assertEquals(true, browserReset)
    assertNull(preferences.readDecryptedSession())
    assertNull(manager.currentSessionValue())
    assertEquals(AuthState.SignedOut, manager.authState.value)
    assertEquals(VerificationStatus.IDLE, manager.telemetry.value.verificationStatus)
  }

  @Test
  fun `local clear waits for browser reset before enabling signed out state`() = runTest {
    manager.beginInteractiveLogin()
    manager.onSessionReadyFromBrowser("active_session")
    val callbackReady = CompletableDeferred<() -> Unit>()
    val clearing = async {
      manager.clearLocalCredentialsAndCache(
        clearAcademicCache = {},
        resetBrowserStorage = { callbackReady.complete(it) },
      )
    }
    val completeReset = callbackReady.await()
    assertFalse(clearing.isCompleted)
    assertEquals(AuthState.LoggingIn, manager.authState.value)
    assertEquals(true, manager.telemetry.value.isBusy)
    completeReset()
    clearing.await()
    assertEquals(AuthState.SignedOut, manager.authState.value)
    assertFalse(manager.telemetry.value.isBusy)
  }

  @Test
  fun `browser reset exception finishes local logout with an honest failure notice`() = runTest {
    manager.onSessionReadyFromBrowser("active_session")
    manager.clearLocalCredentialsAndCache(
      clearAcademicCache = {},
      resetBrowserStorage = { throw IllegalStateException("Browser unavailable") },
    )
    assertNull(manager.currentSessionValue())
    assertEquals(AuthState.SignedOut, manager.authState.value)
    assertFalse(manager.telemetry.value.isBusy)
    assertNotNull(manager.telemetry.value.errorMessage)
  }

  @Test
  fun `cache cleanup failure still clears credentials resets browser and publishes signed out state`() = runTest {
    preferences.setRememberLogin(true)
    preferences.saveEncryptedSession("active_session")
    manager.onSessionReadyFromBrowser("active_session")
    val privatePath = "/private/student/course.pdf"
    var browserResetAttempted = false
    val failure = runCatching {
      manager.clearLocalCredentialsAndCache(
        clearAcademicCache = { throw IOException("Cannot delete $privatePath") },
        resetBrowserStorage = { complete -> browserResetAttempted = true; complete() },
      )
    }.exceptionOrNull()
    assertTrue(failure is LocalCleanupException)
    // Coroutine stack recovery can wrap the safe exception in another copy of the same type.
    generateSequence(failure) { it.cause }.forEach { cause ->
      assertTrue(cause is LocalCleanupException)
      assertEquals(LocalCleanupException.MESSAGE, cause.message)
      assertTrue(cause.suppressed.isEmpty())
    }
    assertNull(manager.currentSessionValue())
    assertNull(preferences.readDecryptedSession())
    assertEquals(AuthState.SignedOut, manager.authState.value)
    assertFalse(manager.telemetry.value.isBusy)
    assertTrue(browserResetAttempted)
    assertEquals(LocalCleanupException.MESSAGE, manager.telemetry.value.errorMessage)
    assertTrue(manager.telemetry.value.logs.none { privatePath in it.message || "wiped" in it.message })
  }

  @Test
  fun `failed preference deletion blocks restore and late cookies until cleanup succeeds`() = runTest {
    preferences.setRememberLogin(true)
    preferences.saveEncryptedSession("still_persisted_session")
    var updatesFail = true
    val faultStore = object : DataStore<Preferences> {
      override val data = dataStore.data
      override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        if (updatesFail) throw IOException("Simulated storage failure")
        return dataStore.updateData(transform)
      }
    }
    val faultPreferences = UserPreferencesStore(faultStore, cipher)
    val blockedManager = SessionManager(BtuHttpClient(
      baseOrigin = "http://127.0.0.1:${server.port}", allowLocalHttpForTesting = true,
    ), faultPreferences)
    blockedManager.onSessionReadyFromBrowser("still_persisted_session")
    val failure = runCatching {
      blockedManager.clearLocalCredentialsAndCache({}, { complete -> complete() })
    }.exceptionOrNull()
    assertTrue(failure is LocalCleanupException)
    assertEquals("still_persisted_session", preferences.readDecryptedSession())
    blockedManager.onSessionReadyFromBrowser("late_browser_cookie")
    blockedManager.onAnonymousSessionDetected("late_anonymous_cookie")
    assertNull(blockedManager.currentSessionValue())
    server.enqueue(MockResponse().setResponseCode(200).setBody(
      "<table><tr><th>კურსის დასახელება</th></tr><tr><td><a href='/ge/student/me/course/index/42'>Course</a></td></tr></table>",
    ))
    assertNull(blockedManager.restoreAndVerifyOnLaunch(hasCachedAcademicData = true))
    assertEquals(0, server.requestCount)
    assertEquals(AuthState.SignedOut, blockedManager.authState.value)

    updatesFail = false
    blockedManager.clearLocalCredentialsAndCache({}, { complete -> complete() })
    preferences.saveEncryptedSession("new_session")
    assertEquals(VerificationStatus.AUTHENTICATED,
      blockedManager.restoreAndVerifyOnLaunch(hasCachedAcademicData = true)?.status)
    assertEquals(1, server.requestCount)
  }
}
