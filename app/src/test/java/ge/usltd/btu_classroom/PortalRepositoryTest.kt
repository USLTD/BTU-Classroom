package ge.usltd.btu_classroom

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ge.usltd.btu_classroom.core.auth.SessionManager
import ge.usltd.btu_classroom.core.database.BtuDatabase
import ge.usltd.btu_classroom.core.database.CourseRepository
import ge.usltd.btu_classroom.core.database.SyncOutcome
import ge.usltd.btu_classroom.core.network.BtuHttpClient
import ge.usltd.btu_classroom.core.parsing.PageCompatibilityRegistry
import ge.usltd.btu_classroom.core.portal.PortalContent
import ge.usltd.btu_classroom.core.portal.PortalModule
import ge.usltd.btu_classroom.core.portal.PortalRepository
import ge.usltd.btu_classroom.core.portal.PortalSnapshotEntity
import ge.usltd.btu_classroom.core.preferences.UserPreferencesStore
import ge.usltd.btu_classroom.core.security.SessionCipher
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PortalRepositoryTest {
  private lateinit var database: BtuDatabase
  private lateinit var server: MockWebServer
  private lateinit var preferences: UserPreferencesStore
  private lateinit var sessionManager: SessionManager
  private lateinit var repository: PortalRepository
  private lateinit var courses: CourseRepository
  private lateinit var dataStoreScope: CoroutineScope
  private lateinit var preferenceFile: File

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    database = Room.inMemoryDatabaseBuilder(context, BtuDatabase::class.java).allowMainThreadQueries().build()
    server = MockWebServer().also { it.start() }
    preferenceFile = File(context.filesDir, "portal_${System.nanoTime()}.preferences_pb")
    dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val dataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope, produceFile = { preferenceFile })
    preferences = UserPreferencesStore(dataStore, object : SessionCipher {
      override fun encrypt(plaintext: String): String = "fixture:$plaintext"
      override fun decrypt(encodedBlob: String): String? = encodedBlob.takeIf { it.startsWith("fixture:") }?.removePrefix("fixture:")
    })
    val client = BtuHttpClient(
      baseOrigin = "http://127.0.0.1:${server.port}",
      allowLocalHttpForTesting = true,
      client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).build(),
    )
    sessionManager = SessionManager(client, preferences)
    repository = PortalRepository(database, client, sessionManager, preferences)
    courses = CourseRepository(database, client, sessionManager, preferences, PageCompatibilityRegistry())
  }

  @After
  fun tearDown() {
    server.shutdown()
    database.close()
    dataStoreScope.cancel()
    preferenceFile.delete()
  }

  @Test
  fun `verified read-only page is parsed cached and requested with approved GET path`() = runTest {
    sessionManager.onSessionReadyFromBrowser("portal_fixture_session")
    server.enqueue(htmlResponse(fixture("portal-exams-georgian.html")))

    assertEquals(SyncOutcome.Success, repository.refresh(PortalModule.EXAMS))

    val cached = repository.observe(PortalModule.EXAMS).first()
    assertEquals(2, cached?.content?.sections?.single()?.rows?.size)
    assertEquals("SUCCESS", cached?.status)
    assertNotNull(cached?.lastSuccessAtMillis)
    val request = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS))
    assertEquals("GET", request.method)
    assertEquals(PortalModule.EXAMS.path, request.path)
    assertEquals("argus_session=portal_fixture_session", request.getHeader("Cookie"))
    val payload = requireNotNull(database.portalDao().get(PortalModule.EXAMS.id)?.payloadJson)
    assertFalse(payload.contains("<table"))
    assertFalse(payload.contains("argus_session"))
    assertEquals(cached?.content, Json.decodeFromString<PortalContent>(payload))
  }

  @Test
  fun `changed page and unavailable server preserve previous content and freshness`() = runTest {
    seedSnapshot()
    sessionManager.onSessionReadyFromBrowser("portal_fixture_session")
    server.enqueue(htmlResponse("<html><body><main>Unrecognized new page</main></body></html>"))

    assertTrue(repository.refresh(PortalModule.EXAMS) is SyncOutcome.UnsupportedStructure)
    val changed = repository.observe(PortalModule.EXAMS).first()
    assertEquals("Cached examinations", changed?.content?.title)
    assertEquals(1000L, changed?.lastSuccessAtMillis)
    assertEquals("UNSUPPORTED_STRUCTURE", changed?.status)

    server.enqueue(MockResponse().setResponseCode(503))
    assertTrue(repository.refresh(PortalModule.EXAMS) is SyncOutcome.OfflineOrFailed)
    val unavailable = repository.observe(PortalModule.EXAMS).first()
    assertEquals(changed?.content, unavailable?.content)
    assertEquals(1000L, unavailable?.lastSuccessAtMillis)
    assertEquals("TEMPORARILY_UNAVAILABLE", unavailable?.status)
  }

  @Test
  fun `unauthenticated response retains cache and expires native session`() = runTest {
    seedSnapshot()
    sessionManager.onSessionReadyFromBrowser("portal_fixture_session")
    server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/ge/login/index"))

    assertEquals(SyncOutcome.SessionExpired, repository.refresh(PortalModule.EXAMS))

    assertNull(sessionManager.currentSessionValue())
    val cached = repository.observe(PortalModule.EXAMS).first()
    assertEquals("Cached examinations", cached?.content?.title)
    assertEquals(1000L, cached?.lastSuccessAtMillis)
    assertEquals("SESSION_EXPIRED", cached?.status)
  }

  @Test
  fun `refresh without session makes no request or cache entry`() = runTest {
    assertEquals(SyncOutcome.SessionExpired, repository.refresh(PortalModule.PROGRAM))
    assertEquals(0, server.requestCount)
    assertNull(database.portalDao().get(PortalModule.PROGRAM.id))
  }

  @Test
  fun `clearing academic data clears every portal module in the same operation`() = runTest {
    seedSnapshot()
    database.portalDao().upsert(PortalSnapshotEntity(PortalModule.PROGRAM.id, null, null, 1000, "UNSUPPORTED_STRUCTURE", null))

    courses.clearAllAcademicData()

    assertNull(database.portalDao().get(PortalModule.EXAMS.id))
    assertNull(database.portalDao().get(PortalModule.PROGRAM.id))
  }

  @Test
  fun `in-flight portal response cannot restore data after clear even when the cookie value is reused`() = runTest {
    seedSnapshot()
    sessionManager.onSessionReadyFromBrowser("portal_fixture_session")
    val requestStarted = CountDownLatch(1)
    val releaseResponse = CountDownLatch(1)
    val responseHtml = fixture("portal-exams-georgian.html")
    server.dispatcher = object : Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse {
        requestStarted.countDown()
        releaseResponse.await(5, TimeUnit.SECONDS)
        return htmlResponse(responseHtml)
      }
    }
    val refresh = async(Dispatchers.IO) { repository.refresh(PortalModule.EXAMS) }
    try {
      assertTrue(withContext(Dispatchers.IO) { requestStarted.await(5, TimeUnit.SECONDS) })
      sessionManager.clearLocalCredentialsAndCache(
        clearAcademicCache = { courses.clearAllAcademicData() },
        resetBrowserStorage = { complete -> complete() },
      )
      sessionManager.onSessionReadyFromBrowser("portal_fixture_session")
    } finally {
      releaseResponse.countDown()
    }

    assertEquals(SyncOutcome.SessionExpired, refresh.await())
    assertNull(database.portalDao().get(PortalModule.EXAMS.id))
    assertEquals("portal_fixture_session", sessionManager.currentSessionValue())
  }

  @Test
  fun `corrupt cached payload exposes recoverable state without crashing observers`() = runTest {
    database.portalDao().upsert(PortalSnapshotEntity(PortalModule.CALENDAR.id, "not JSON", 1000, 1000, "SUCCESS", null))

    val page = repository.observe(PortalModule.CALENDAR).first()

    assertNull(page?.content)
    assertEquals("CACHE_UNREADABLE", page?.status)
    assertNotNull(page?.message)
    assertEquals(1000L, page?.lastSuccessAtMillis)
  }

  private suspend fun seedSnapshot() {
    database.portalDao().upsert(PortalSnapshotEntity(
      moduleId = PortalModule.EXAMS.id,
      payloadJson = Json.encodeToString(PortalContent(title = "Cached examinations")),
      lastSuccessAtMillis = 1000,
      lastAttemptAtMillis = 1000,
      status = "SUCCESS",
      message = null,
    ))
  }

  private fun htmlResponse(html: String) = MockResponse().setResponseCode(200)
    .addHeader("Content-Type", "text/html; charset=utf-8").setBody(html)

  private fun fixture(name: String): String = requireNotNull(javaClass.getResource("/fixtures/$name")).readText()
}
