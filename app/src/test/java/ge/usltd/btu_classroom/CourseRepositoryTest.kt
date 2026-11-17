package ge.usltd.btu_classroom

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ge.usltd.btu_classroom.core.auth.AuthState
import ge.usltd.btu_classroom.core.auth.SessionManager
import ge.usltd.btu_classroom.core.database.BtuDatabase
import ge.usltd.btu_classroom.core.database.CourseRepository
import ge.usltd.btu_classroom.core.database.SyncOutcome
import ge.usltd.btu_classroom.core.database.entities.GradeEntity
import ge.usltd.btu_classroom.core.network.BtuHttpClient
import ge.usltd.btu_classroom.core.parsing.BtuModuleId
import ge.usltd.btu_classroom.core.parsing.ModuleCompatibilityStatus
import ge.usltd.btu_classroom.core.parsing.PageCompatibilityRegistry
import ge.usltd.btu_classroom.core.preferences.UserPreferencesStore
import ge.usltd.btu_classroom.core.security.SessionCipher
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CourseRepositoryTest {

  private lateinit var context: Context
  private lateinit var database: BtuDatabase
  private lateinit var server: MockWebServer
  private lateinit var preferencesStore: UserPreferencesStore
  private lateinit var sessionManager: SessionManager
  private lateinit var compatibilityRegistry: PageCompatibilityRegistry
  private lateinit var repository: CourseRepository
  private lateinit var tempDataStoreFile: File
  private lateinit var dataStoreScope: CoroutineScope

  private val fakeCipher = object : SessionCipher {
    override fun encrypt(plaintext: String): String = "enc:$plaintext"
    override fun decrypt(encodedBlob: String): String? =
      if (encodedBlob.startsWith("enc:")) encodedBlob.removePrefix("enc:") else null
  }

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    database = Room.inMemoryDatabaseBuilder(context, BtuDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    server = MockWebServer()
    server.start()

    tempDataStoreFile = File(context.filesDir, "test_prefs_${System.nanoTime()}.preferences_pb")
    dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val dataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope, produceFile = { tempDataStoreFile })
    preferencesStore = UserPreferencesStore(dataStore, fakeCipher)

    val okClient = OkHttpClient.Builder()
      .followRedirects(false)
      .followSslRedirects(false)
      .connectTimeout(5, TimeUnit.SECONDS)
      .readTimeout(5, TimeUnit.SECONDS)
      .build()

    val httpClient = BtuHttpClient(
      baseOrigin = "http://127.0.0.1:${server.port}",
      allowLocalHttpForTesting = true,
      client = okClient,
    )
    sessionManager = SessionManager(httpClient, preferencesStore)
    compatibilityRegistry = PageCompatibilityRegistry()
    repository = CourseRepository(
      database = database,
      httpClient = httpClient,
      sessionManager = sessionManager,
      preferencesStore = preferencesStore,
      compatibilityRegistry = compatibilityRegistry,
    )
  }

  @After
  fun tearDown() {
    database.close()
    server.shutdown()
    dataStoreScope.cancel()
    tempDataStoreFile.delete()
  }

  @Test
  fun `syncs courses to Room preserves cache on network or structure failures and clears on sign out`() = runTest {
    val coursesHtml = """
      <html><body>
        <table>
          <thead><tr><th>კოდი</th><th>კურსის დასახელება</th><th>კრედიტი</th><th>ქულა</th></tr></thead>
          <tbody>
            <tr>
              <td>CS101</td>
              <td><a href="/ge/student/me/course/index/101">პროგრამირების საფუძვლები</a></td>
              <td>6</td>
              <td>95</td>
            </tr>
          </tbody>
        </table>
      </body></html>
    """.trimIndent()

    sessionManager.onSessionReadyFromBrowser("valid_session_abc")
    server.enqueue(MockResponse().setResponseCode(200).addHeader("Content-Type", "text/html").setBody(coursesHtml))
    val verifyResult = sessionManager.verifyCurrentSession(onAuthenticatedReleaseBrowser = {})
    assertEquals(AuthState.Authenticated, sessionManager.authState.value)

    val syncOutcome = repository.refreshSelectedCourses(verifyResult.html)
    assertEquals(SyncOutcome.Success, syncOutcome)

    val cachedCourses = repository.observeCourses().first()
    assertEquals(1, cachedCourses.size)
    assertEquals("პროგრამირების საფუძვლები", cachedCourses[0].course.title)
    assertEquals(95.0, cachedCourses[0].enrollment?.totalScore)

    val initialMeta = repository.observeCoursesSyncMetadata().first()
    assertNotNull(initialMeta?.lastSuccessAtMillis)

    server.enqueue(MockResponse().setResponseCode(200).setBody("<html><body><div>New unknown UI</div></body></html>"))
    val brokenOutcome = repository.refreshSelectedCourses()
    assertTrue(brokenOutcome is SyncOutcome.UnsupportedStructure)
    assertEquals(1, repository.observeCourses().first().size)
    assertEquals(
      ModuleCompatibilityStatus.PARSER_UPDATE_REQUIRED,
      compatibilityRegistry.entries.value[BtuModuleId.SELECTED_COURSES]?.status,
    )
    assertEquals(
      initialMeta?.lastSuccessAtMillis,
      repository.observeCoursesSyncMetadata().first()?.lastSuccessAtMillis,
    )

    server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/ge/login/index"))
    server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/ge/login/index"))
    val logoutResult = sessionManager.signOutRemoteAndClearLocal(
      clearAcademicCache = { repository.clearAllAcademicData() },
      resetBrowserStorage = { it() },
    )
    assertTrue(logoutResult.success)
    assertEquals(AuthState.SignedOut, sessionManager.authState.value)
    assertTrue(repository.observeCourses().first().isEmpty())
    assertNull(preferencesStore.readDecryptedSession())
  }

  @Test
  fun `list refresh preserves matching course details and removes grades for removed courses`() = runTest {
    seedCourses()
    val cached = repository.observeCourses().first().single().course
    val detailed = cached.copy(
      lecturer = "Example lecturer",
      syllabusSummary = "Verified syllabus",
      resourcesJson = "[{\"title\":\"Syllabus\",\"url\":\"https://classroom.btu.edu.ge/ge/document/42\"}]",
      detailSyncedAtMillis = 1234,
      detailStatus = "COMPATIBLE",
    )
    database.courseDao().updateCourseDetailAndGrades(
      detailed, null, listOf(grade(cached.courseKey, cached.courseId, cached.semesterKey)),
    )

    assertEquals(SyncOutcome.Success, repository.refreshSelectedCourses(courseTable(score = "")))
    val refreshed = repository.observeCourses().first().single()
    assertEquals("Example lecturer", refreshed.course.lecturer)
    assertEquals("Verified syllabus", refreshed.course.syllabusSummary)
    assertEquals(1234L, refreshed.course.detailSyncedAtMillis)
    assertNotNull(refreshed.course.resourcesJson)
    assertNull(refreshed.enrollment?.totalScore)
    assertEquals(1, repository.observeCourseGrades(cached.courseKey).first().size)

    assertEquals(SyncOutcome.Success, repository.refreshSelectedCourses(courseTable(id = "202", code = "CS202")))
    assertTrue(repository.observeCourseGrades(cached.courseKey).first().isEmpty())
  }

  @Test
  fun `valid empty snapshot clears courses grades semesters and stale identity`() = runTest {
    seedCourses()
    val cached = repository.observeCourses().first().single().course
    database.courseDao().insertGrades(listOf(grade(cached.courseKey, cached.courseId, cached.semesterKey)))
    val emptyHtml = """
      <html><body><table>
        <thead><tr><th>კოდი</th><th>კურსის დასახელება</th><th>კრედიტი</th><th>ქულა</th></tr></thead>
        <tbody></tbody>
      </table></body></html>
    """.trimIndent()

    assertEquals(SyncOutcome.Empty, repository.refreshSelectedCourses(emptyHtml))
    assertTrue(repository.observeCourses().first().isEmpty())
    assertTrue(repository.observeSemesters().first().isEmpty())
    assertTrue(repository.observeCourseGrades(cached.courseKey).first().isEmpty())
    assertNull(repository.observeStudent().first())
    val metadata = repository.observeCoursesSyncMetadata().first()
    assertEquals("EMPTY", metadata?.outcomeStatus)
    assertNotNull(metadata?.lastSuccessAtMillis)
    assertTrue(repository.hasCachedCourses())
  }

  @Test
  fun `missing session preserves cache and last successful timestamp`() = runTest {
    seedCourses()
    val successAt = repository.observeCoursesSyncMetadata().first()?.lastSuccessAtMillis
    sessionManager.handleServerSessionExpired("Expired for test")

    assertEquals(SyncOutcome.SessionExpired, repository.refreshSelectedCourses())
    assertEquals(1, repository.observeCourses().first().size)
    assertEquals(successAt, repository.observeCoursesSyncMetadata().first()?.lastSuccessAtMillis)
    assertEquals("SESSION_EXPIRED", repository.observeCoursesSyncMetadata().first()?.outcomeStatus)
  }

  @Test
  fun `missing detail link does not construct an unverified portal request`() = runTest {
    seedCourses()
    val cached = repository.observeCourses().first().single().course

    val outcome = repository.refreshCourseDetail(cached.courseKey, cached.courseId, null)

    assertTrue(outcome is SyncOutcome.UnsupportedStructure)
    assertEquals(0, server.requestCount)
  }

  @Test
  fun `failed detail parsing preserves last successful detail sync time and content`() = runTest {
    seedCourses()
    val cached = repository.observeCourses().first().single().course
    database.courseDao().insertCourses(listOf(cached.copy(
      detailSyncedAtMillis = 1234,
      lecturer = "Example lecturer",
      detailStatus = "COMPATIBLE",
    )))
    server.enqueue(MockResponse().setResponseCode(200).addHeader("Content-Type", "text/html")
      .setBody("<html><body><h1>New portal</h1></body></html>"))

    val outcome = repository.refreshCourseDetail(cached.courseKey, cached.courseId, cached.detailPath)

    assertTrue(outcome is SyncOutcome.UnsupportedStructure)
    val after = repository.observeCourseDetail(cached.courseKey).first()?.course
    assertEquals(1234L, after?.detailSyncedAtMillis)
    assertEquals("Example lecturer", after?.lecturer)
    assertEquals("UNSUPPORTED_STRUCTURE", after?.detailStatus)
  }

  @Test
  fun `malformed detail grade table preserves complete cached grade snapshot`() = runTest {
    seedCourses()
    val cached = repository.observeCourses().first().single().course
    val originalGrade = grade(cached.courseKey, cached.courseId, cached.semesterKey)
    database.courseDao().updateCourseDetailAndGrades(
      cached.copy(detailSyncedAtMillis = 1234), null, listOf(originalGrade),
    )
    server.enqueue(MockResponse().setResponseCode(200).addHeader("Content-Type", "text/html")
      .setBody("""
        <main><h1 class="course-title">Example course</h1>
          <table><thead><tr><th>Category</th><th>Score</th><th>Max</th></tr></thead>
            <tbody><tr><td>Midterm</td><td>12</td><td>20</td></tr>
              <tr><td>Final</td></tr></tbody>
          </table>
        </main>
      """.trimIndent()))

    val outcome = repository.refreshCourseDetail(cached.courseKey, cached.courseId, cached.detailPath)

    assertTrue(outcome is SyncOutcome.UnsupportedStructure)
    assertEquals(listOf(originalGrade), repository.observeCourseGrades(cached.courseKey).first())
    assertEquals(1234L, repository.observeCourseDetail(cached.courseKey).first()?.course?.detailSyncedAtMillis)
  }

  @Test
  fun `detail without grade table updates metadata while preserving cached grades`() = runTest {
    seedCourses()
    val cached = repository.observeCourses().first().single().course
    val originalGrade = grade(cached.courseKey, cached.courseId, cached.semesterKey)
    database.courseDao().insertGrades(listOf(originalGrade))
    server.enqueue(MockResponse().setResponseCode(200).addHeader("Content-Type", "text/html")
      .setBody("""
        <main><h1 class="course-title">Updated title</h1>
          <dl><dt>Lecturer</dt><dd>New lecturer</dd></dl>
        </main>
      """.trimIndent()))

    val outcome = repository.refreshCourseDetail(cached.courseKey, cached.courseId, cached.detailPath)

    assertEquals(SyncOutcome.Success, outcome)
    assertEquals(listOf(originalGrade), repository.observeCourseGrades(cached.courseKey).first())
    assertEquals("New lecturer", repository.observeCourseDetail(cached.courseKey).first()?.course?.lecturer)
  }

  @Test
  fun `explicit valid empty detail grade table clears cached grades`() = runTest {
    seedCourses()
    val cached = repository.observeCourses().first().single().course
    database.courseDao().insertGrades(listOf(grade(cached.courseKey, cached.courseId, cached.semesterKey)))
    server.enqueue(MockResponse().setResponseCode(200).addHeader("Content-Type", "text/html")
      .setBody("""
        <main><h1 class="course-title">Example course</h1>
          <table><thead><tr><th>Category</th><th>Score</th><th>Max</th></tr></thead><tbody></tbody></table>
        </main>
      """.trimIndent()))

    val outcome = repository.refreshCourseDetail(cached.courseKey, cached.courseId, cached.detailPath)

    assertEquals(SyncOutcome.Success, outcome)
    assertTrue(repository.observeCourseGrades(cached.courseKey).first().isEmpty())
  }

  @Test
  fun `linked verified score and file pages merge authoritative grades without overwriting course credits`() = runTest {
    val cached = seedVerifiedCourse()
    server.enqueue(htmlResponse(fixture("verified-course-index.html")))
    server.enqueue(htmlResponse(fixture("verified-course-scores.html")))
    server.enqueue(htmlResponse(fixture("verified-course-files.html")))

    assertEquals(SyncOutcome.Success, repository.refreshCourseDetail(cached.courseKey, cached.courseId, cached.detailPath))

    val detail = requireNotNull(repository.observeCourseDetail(cached.courseKey).first())
    assertEquals(6.0, detail.course.credits)
    assertEquals(3.0, detail.enrollment?.totalScore)
    val grades = repository.observeCourseGrades(cached.courseKey).first()
    assertEquals(3.0, grades.first().score)
    assertTrue(grades.any { it.score == null && it.maxScore == 40.0 })
    val resources = repository.decodeResources(detail.course.resourcesJson)
    assertEquals(3, resources.size)
    assertTrue(resources.any { it.url == "https://classroom.btu.edu.ge/ge/student/me/courseSilabusFile/1001/47" })
    assertTrue(resources.any { it.url == "https://classroom.btu.edu.ge/uploads/71/1001.docx" })
    assertTrue(resources.none { it.url.contains("/lector/") || it.url.contains("/messages/") })
    assertEquals("COMPATIBLE", detail.course.detailStatus)
    val paths = List(3) { requireNotNull(server.takeRequest(5, TimeUnit.SECONDS)).path }
    assertEquals(listOf(
      "/ge/student/me/course/index/1001",
      "/ge/student/me/course/scores/1001/47",
      "/ge/student/me/course/files/1001/47",
    ), paths)
  }

  @Test
  fun `malformed linked score page preserves cached grade snapshot while valid file page can update`() = runTest {
    val cached = seedVerifiedCourse()
    val originalGrade = grade(cached.courseKey, cached.courseId, cached.semesterKey)
    database.courseDao().updateCourseDetailAndGrades(
      cached.copy(detailSyncedAtMillis = 1234), null, listOf(originalGrade),
    )
    server.enqueue(htmlResponse(fixture("verified-course-index.html")))
    server.enqueue(htmlResponse("<main><h1>Changed score page</h1></main>"))
    server.enqueue(htmlResponse(fixture("verified-course-files.html")))

    val outcome = repository.refreshCourseDetail(cached.courseKey, cached.courseId, cached.detailPath)

    assertTrue(outcome is SyncOutcome.UnsupportedStructure)
    assertEquals(listOf(originalGrade), repository.observeCourseGrades(cached.courseKey).first())
    val detail = requireNotNull(repository.observeCourseDetail(cached.courseKey).first())
    assertEquals(95.0, detail.enrollment?.totalScore)
    assertEquals(1234L, detail.course.detailSyncedAtMillis)
    assertEquals(3, repository.decodeResources(detail.course.resourcesJson).size)
    assertEquals("UNSUPPORTED_STRUCTURE", detail.course.detailStatus)
  }

  @Test
  fun `unavailable linked files preserve cached resources and freshness while successful scores update`() = runTest {
    val cached = seedVerifiedCourse()
    database.courseDao().insertCourses(listOf(cached.copy(
      detailSyncedAtMillis = 1234,
      resourcesJson = "[{\"title\":\"Cached notes\",\"url\":\"https://classroom.btu.edu.ge/uploads/71/9000.pdf\"}]",
    )))
    server.enqueue(htmlResponse(fixture("verified-course-index.html")))
    server.enqueue(htmlResponse(fixture("verified-course-scores.html")))
    server.enqueue(MockResponse().setResponseCode(503))

    val outcome = repository.refreshCourseDetail(cached.courseKey, cached.courseId, cached.detailPath)

    assertTrue(outcome is SyncOutcome.OfflineOrFailed)
    val detail = requireNotNull(repository.observeCourseDetail(cached.courseKey).first())
    assertEquals(3.0, detail.enrollment?.totalScore)
    assertEquals(6.0, detail.course.credits)
    assertEquals(1234L, detail.course.detailSyncedAtMillis)
    val resources = repository.decodeResources(detail.course.resourcesJson)
    assertTrue(resources.any { it.url.endsWith("/9000.pdf") })
    assertTrue(resources.any { it.url.contains("/courseSilabusFile/1001/47") })
    assertEquals("TEMPORARILY_UNAVAILABLE", detail.course.detailStatus)
  }

  @Test
  fun `linked score authentication failure retains cache and stops before fetching files`() = runTest {
    val cached = seedVerifiedCourse()
    val originalGrade = grade(cached.courseKey, cached.courseId, cached.semesterKey)
    database.courseDao().insertGrades(listOf(originalGrade))
    server.enqueue(htmlResponse(fixture("verified-course-index.html")))
    server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/ge/login/index"))

    assertEquals(SyncOutcome.SessionExpired, repository.refreshCourseDetail(cached.courseKey, cached.courseId, cached.detailPath))

    assertNull(sessionManager.currentSessionValue())
    assertEquals(listOf(originalGrade), repository.observeCourseGrades(cached.courseKey).first())
    assertEquals(2, server.requestCount)
  }

  @Test
  fun `course index with no linked score or file page makes no guessed child requests`() = runTest {
    val cached = seedVerifiedCourse()
    val originalGrade = grade(cached.courseKey, cached.courseId, cached.semesterKey)
    database.courseDao().insertGrades(listOf(originalGrade))
    val index = fixture("verified-course-index.html")
      .replace("<h2>BAI - Sample course</h2>", "<h2 class='course-title'>BAI - Sample course</h2>")
      .replace(Regex("<li><a[^>]+/(scores|files)/[^<]+</a></li>"), "")
    server.enqueue(htmlResponse(index))

    assertEquals(SyncOutcome.Success, repository.refreshCourseDetail(cached.courseKey, cached.courseId, cached.detailPath))

    assertEquals(1, server.requestCount)
    assertEquals(listOf(originalGrade), repository.observeCourseGrades(cached.courseKey).first())
  }

  @Test
  fun `course navigation for another course is rejected without fetching child pages`() = runTest {
    val cached = seedVerifiedCourse()
    server.enqueue(htmlResponse(fixture("verified-course-index.html")
      .replace("/scores/1001/47", "/scores/9999/47")))

    val outcome = repository.refreshCourseDetail(cached.courseKey, cached.courseId, cached.detailPath)

    assertTrue(outcome is SyncOutcome.UnsupportedStructure)
    assertEquals(1, server.requestCount)
    assertEquals(95.0, repository.observeCourseDetail(cached.courseKey).first()?.enrollment?.totalScore)
  }

  private suspend fun seedVerifiedCourse(): ge.usltd.btu_classroom.core.database.entities.CourseEntity {
    sessionManager.onSessionReadyFromBrowser("valid_session_for_tests")
    assertEquals(SyncOutcome.Success, repository.refreshSelectedCourses(courseTable(id = "1001")))
    return repository.observeCourses().first().single().course
  }

  private fun htmlResponse(html: String) = MockResponse().setResponseCode(200)
    .addHeader("Content-Type", "text/html; charset=utf-8").setBody(html)

  private fun fixture(name: String): String = requireNotNull(javaClass.getResource("/fixtures/$name")).readText()

  private suspend fun seedCourses() {
    sessionManager.onSessionReadyFromBrowser("valid_session_for_tests")
    assertEquals(SyncOutcome.Success, repository.refreshSelectedCourses(courseTable()))
  }

  private fun courseTable(id: String = "101", code: String = "CS101", score: String = "95") = """
    <html><body><table>
      <thead><tr><th>კოდი</th><th>კურსის დასახელება</th><th>კრედიტი</th><th>ქულა</th></tr></thead>
      <tbody><tr><td>$code</td><td><a href="/ge/student/me/course/index/$id">Example course</a></td><td>6</td><td>$score</td></tr></tbody>
    </table></body></html>
  """.trimIndent()

  private fun grade(courseKey: String, courseId: String, semesterKey: String) = GradeEntity(
    gradeKey = "$courseKey::0",
    courseKey = courseKey,
    studentKey = UserPreferencesStore.DEFAULT_STUDENT_KEY,
    semesterKey = semesterKey,
    courseId = courseId,
    categoryName = "Midterm",
    score = null,
    maxScore = 30.0,
    notes = null,
    sortOrder = 0,
  )
}
