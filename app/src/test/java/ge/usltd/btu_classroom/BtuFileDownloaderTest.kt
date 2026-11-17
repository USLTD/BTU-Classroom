package ge.usltd.btu_classroom

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import ge.usltd.btu_classroom.core.auth.SessionManager
import ge.usltd.btu_classroom.core.download.BtuFileDownloader
import ge.usltd.btu_classroom.core.download.CourseDownloadResult
import ge.usltd.btu_classroom.core.network.BtuHttpClient
import ge.usltd.btu_classroom.core.preferences.UserPreferencesStore
import ge.usltd.btu_classroom.core.security.SessionCipher
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class,
  shadows = [HostFileProviderPathStrategy::class], instrumentedPackages = ["androidx.core.content"])
class BtuFileDownloaderTest {
  private lateinit var context: Context
  private lateinit var server: MockWebServer
  private lateinit var client: OkHttpClient
  private lateinit var preferences: UserPreferencesStore
  private lateinit var sessionManager: SessionManager
  private lateinit var dataStoreScope: CoroutineScope
  private lateinit var downloader: BtuFileDownloader

  private val origin: String get() = "http://127.0.0.1:${server.port}"
  private val syllabusUrl: String get() = "$origin/ge/student/me/courseSilabusFile/1001/47"
  private val pdfBytes = "%PDF-1.7\nfixture document\n%%EOF".toByteArray()

  @Before
  fun setUp() = runBlocking {
    context = ApplicationProvider.getApplicationContext()
    // Each test gets a new data directory; attaching invalidates AndroidX's cached prior-test roots.
    val providerInfo = requireNotNull(context.packageManager.resolveContentProvider(
      "${context.packageName}.files", PackageManager.GET_META_DATA))
    FileProvider().attachInfo(context, providerInfo)
    server = MockWebServer().apply { start() }
    client = OkHttpClient.Builder()
      .followRedirects(false)
      .followSslRedirects(false)
      .connectTimeout(3, TimeUnit.SECONDS)
      .readTimeout(3, TimeUnit.SECONDS)
      .callTimeout(5, TimeUnit.SECONDS)
      .build()
    dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val dataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) {
      File(context.filesDir, "download_${System.nanoTime()}.preferences_pb")
    }
    preferences = UserPreferencesStore(dataStore, object : SessionCipher {
      override fun encrypt(plaintext: String): String = "fixture:$plaintext"
      override fun decrypt(encodedBlob: String): String? =
        encodedBlob.takeIf { it.startsWith("fixture:") }?.removePrefix("fixture:")
    })
    sessionManager = SessionManager(
      BtuHttpClient(baseOrigin = origin, allowLocalHttpForTesting = true, client = client),
      preferences,
    ).apply { onSessionReadyFromBrowser("fixture_cookie") }
    downloader = newDownloader()
    downloader.clearDownloads()
  }

  @After
  fun tearDown() = runBlocking {
    downloader.clearDownloads()
    server.shutdown()
    dataStoreScope.coroutineContext[Job]?.cancelAndJoin()
    client.dispatcher.executorService.shutdownNow()
    client.connectionPool.evictAll()
  }

  @Test
  fun `verified syllabus is stored privately and opened through a content URI`() = runBlocking {
    server.enqueue(pdfResponse())

    val result = downloader.download(syllabusUrl, "Sample syllabus.pdf")

    assertTrue("Expected a successful fixture download, got $result; published files=${cachedFiles().size}",
      result is CourseDownloadResult.Success)
    result as CourseDownloadResult.Success
    assertEquals("content", result.uri.scheme)
    assertEquals("${context.packageName}.files", result.uri.authority)
    assertEquals("application/pdf", result.mimeType)
    val actual = context.contentResolver.openInputStream(result.uri)?.use { it.readBytes() }
    assertNotNull(actual)
    assertArrayEquals(pdfBytes, actual)
    val request = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS))
    assertEquals("/ge/student/me/courseSilabusFile/1001/47", request.path)
    assertEquals("argus_session=fixture_cookie", request.getHeader("Cookie"))
    assertEquals(1, cachedFiles().size)
  }

  @Test
  fun `download cache has a registered private file provider`() {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files",
      File(context.cacheDir, "downloads/fixture.pdf"))
    assertEquals("content", uri.scheme)
    assertEquals("${context.packageName}.files", uri.authority)
    assertEquals("/downloads/fixture.pdf", uri.path)
  }

  @Test
  fun `file provider does not expose cache root sibling directories or traversal paths`() {
    val authority = "${context.packageName}.files"
    for (relativePath in listOf("downloads", "downloads-other/fixture.pdf", "downloads/../fixture.pdf")) {
      assertThrows(relativePath, IllegalArgumentException::class.java) {
        FileProvider.getUriForFile(context, authority, File(context.cacheDir, relativePath))
      }
    }
  }

  @Test
  fun `unsafe routes and external URLs never receive a session cookie`() = runBlocking {
    val invalidUrls = listOf(
      "https://example.com/uploads/1/1.pdf",
      "https://classroom.btu.edu.ge@attacker.example/uploads/1/1.pdf",
      "$origin/ge/student/me/updBalance",
      "$origin/ge/messages/compose/1001",
      "$origin/uploads/1/1.apk",
      "$origin/uploads/1/%2e%2e/1.pdf",
      "$origin/uploads/1/1.pdf#fragment",
    )
    invalidUrls.forEach { url ->
      assertEquals(url, CourseDownloadResult.InvalidResource, downloader.download(url, "Sample"))
    }
    assertEquals(0, server.requestCount)
    assertTrue(cachedFiles().isEmpty())
  }

  @Test
  fun `declared oversized document is rejected before publication`() = runBlocking {
    val bytes = "%PDF-1.7\n${"x".repeat(128)}".toByteArray()
    server.enqueue(MockResponse().addHeader("Content-Type", "application/pdf").setBody(Buffer().write(bytes)))

    assertEquals(CourseDownloadResult.TooLarge, newDownloader(64L).download(syllabusUrl, "Large.pdf"))
    assertTrue(cachedFiles().isEmpty())
  }

  @Test
  fun `chunked response cannot evade the document byte limit`() = runBlocking {
    val bytes = "%PDF-1.7\n${"x".repeat(128)}".toByteArray()
    server.enqueue(MockResponse().addHeader("Content-Type", "application/pdf")
      .setChunkedBody(Buffer().write(bytes), 8))

    assertEquals(CourseDownloadResult.TooLarge, newDownloader(64L).download(syllabusUrl, "Large.pdf"))
    assertTrue(cachedFiles().isEmpty())
  }

  @Test
  fun `external redirects are not followed and never disclose the cookie`() = runBlocking {
    val external = MockWebServer().apply { start() }
    try {
      server.enqueue(MockResponse().setResponseCode(302)
        .addHeader("Location", "http://127.0.0.1:${external.port}/uploads/1/1.pdf"))

      assertEquals(CourseDownloadResult.Unavailable, downloader.download(syllabusUrl, "Sample.pdf"))
      assertNull(external.takeRequest(250, TimeUnit.MILLISECONDS))
      assertTrue(cachedFiles().isEmpty())
    } finally {
      external.shutdown()
    }
  }

  @Test
  fun `a login redirect is reported as an expired session`() = runBlocking {
    server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/ge/login/index"))

    assertEquals(CourseDownloadResult.SessionExpired, downloader.download(syllabusUrl, "Sample.pdf"))
    assertTrue(cachedFiles().isEmpty())
  }

  @Test
  fun `English login redirects expire the download session without following them`() = runBlocking {
    val paths = listOf("/en/login/index", "/en/login/index/", "/en/login/oauth/", "/en/login/oauth/?next=student")
    for (path in paths) {
      server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", path))
      assertEquals(path, CourseDownloadResult.SessionExpired, downloader.download(syllabusUrl, "Sample.pdf"))
    }
    assertEquals(paths.size, server.requestCount)
    assertTrue(cachedFiles().isEmpty())
  }

  @Test
  fun `HTML is never published as a document`() = runBlocking {
    server.enqueue(MockResponse().addHeader("Content-Type", "text/html")
      .setBody("<html><div id='loginForm'>ავტორიზაცია</div></html>"))

    assertFalse(downloader.download(syllabusUrl, "Sample.pdf") is CourseDownloadResult.Success)
    assertTrue(cachedFiles().isEmpty())
  }

  @Test
  fun `a PDF content type cannot disguise an invalid document signature`() = runBlocking {
    server.enqueue(MockResponse().addHeader("Content-Type", "application/pdf").setBody("not a PDF"))

    assertFalse(downloader.download(syllabusUrl, "Sample.pdf") is CourseDownloadResult.Success)
    assertTrue(cachedFiles().isEmpty())
  }

  @Test
  fun `server supplied filenames cannot escape the private download directory`() = runBlocking {
    server.enqueue(pdfResponse().addHeader("Content-Disposition", "attachment; filename=\"../../escaped-fixture.pdf\""))

    val result = downloader.download(syllabusUrl, "../../escaped-fixture.pdf")

    assertTrue(result is CourseDownloadResult.Success)
    assertFalse(File(context.cacheDir, "escaped-fixture.pdf").exists())
    val directory = File(context.cacheDir, "downloads").canonicalFile
    assertTrue(cachedFiles().all { it.canonicalFile.parentFile == directory })
  }

  @Test
  fun `clearing downloads removes published cached documents`() = runBlocking {
    server.enqueue(pdfResponse())
    assertTrue(downloader.download(syllabusUrl, "Sample.pdf") is CourseDownloadResult.Success)
    assertTrue(cachedFiles().isNotEmpty())

    downloader.clearDownloads()

    assertTrue(cachedFiles().isEmpty())
  }

  @Test
  fun `a partial response cannot replace a previously cached complete document`() = runBlocking {
    server.enqueue(pdfResponse())
    val complete = downloader.download(syllabusUrl, "Sample.pdf")
    assertTrue(complete is CourseDownloadResult.Success)
    complete as CourseDownloadResult.Success
    server.enqueue(MockResponse().setResponseCode(206)
      .addHeader("Content-Type", "application/pdf")
      .addHeader("Content-Range", "bytes 0-29/100")
      .setBody("%PDF-1.7\npartial replacement"))

    val result = downloader.download(syllabusUrl, "Sample.pdf")

    assertEquals(CourseDownloadResult.Unavailable, result)
    assertEquals(1, cachedFiles().size)
    val retained = context.contentResolver.openInputStream(complete.uri)?.use { it.readBytes() }
    assertArrayEquals(pdfBytes, retained)
  }

  @Test
  fun `a saved document stays available offline after the remote session expires`() = runBlocking {
    server.enqueue(pdfResponse())
    val downloaded = downloader.download(syllabusUrl, "Sample.pdf")
    assertTrue(downloaded is CourseDownloadResult.Success)
    sessionManager.handleServerSessionExpired("fixture session expired")
    assertNull(sessionManager.currentSessionValue())

    val cached = downloader.getCached(syllabusUrl, "Sample.pdf")

    assertNotNull(cached)
    requireNotNull(cached)
    assertTrue(cached.fromCache)
    assertEquals((downloaded as CourseDownloadResult.Success).uri, cached.uri)
    assertArrayEquals(pdfBytes, context.contentResolver.openInputStream(cached.uri)?.use { it.readBytes() })
    assertEquals(1, server.requestCount)
  }

  @Test
  fun `a new account generation receives a distinct document URI and cache lookup`() = runBlocking {
    server.enqueue(pdfResponse())
    val old = downloader.download(syllabusUrl, "Sample.pdf")
    assertTrue(old is CourseDownloadResult.Success)
    preferences.clearSessionAndIdentity()
    sessionManager.onSessionReadyFromBrowser("new_fixture_cookie")
    val newBytes = "%PDF-1.7\nnew account fixture\n%%EOF".toByteArray()
    server.enqueue(MockResponse().addHeader("Content-Type", "application/pdf")
      .setBody(Buffer().write(newBytes)))

    val current = downloader.download(syllabusUrl, "Sample.pdf")

    assertTrue(current is CourseDownloadResult.Success)
    current as CourseDownloadResult.Success
    assertFalse((old as CourseDownloadResult.Success).uri == current.uri)
    val cached = requireNotNull(downloader.getCached(syllabusUrl, "Sample.pdf"))
    assertTrue(cached.fromCache)
    assertEquals(current.uri, cached.uri)
    assertArrayEquals(newBytes, context.contentResolver.openInputStream(cached.uri)?.use { it.readBytes() })
    assertEquals(2, server.requestCount)
  }

  @Test
  fun `an account wipe hides the prior cached document before physical cleanup finishes`() = runBlocking {
    server.enqueue(pdfResponse())
    assertTrue(downloader.download(syllabusUrl, "Sample.pdf") is CourseDownloadResult.Success)
    assertNotNull(downloader.getCached(syllabusUrl, "Sample.pdf"))
    preferences.clearSessionAndIdentity()

    assertNull(downloader.getCached(syllabusUrl, "Sample.pdf"))
    downloader.clearDownloads()

    assertTrue(cachedFiles().isEmpty())
    assertNull(downloader.getCached(syllabusUrl, "Sample.pdf"))
    assertEquals(1, server.requestCount)
  }

  @Test
  fun `a download finishing after an account wipe cannot republish private data`() = runBlocking {
    val reachedServer = CountDownLatch(1)
    val releaseResponse = CountDownLatch(1)
    server.dispatcher = object : Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse {
        reachedServer.countDown()
        return if (releaseResponse.await(5, TimeUnit.SECONDS)) pdfResponse()
        else MockResponse().setResponseCode(503)
      }
    }
    val downloading = async(Dispatchers.IO) { downloader.download(syllabusUrl, "Sample.pdf") }
    try {
      assertTrue(reachedServer.await(5, TimeUnit.SECONDS))
      preferences.clearSessionAndIdentity()
      releaseResponse.countDown()

      val result = withTimeout(6_000L) { downloading.await() }
      assertFalse(result is CourseDownloadResult.Success)
      assertTrue(cachedFiles().isEmpty())
    } finally {
      releaseResponse.countDown()
      downloading.cancelAndJoin()
    }
  }

  @Test
  fun `cancelled downloads retain cancellation and remove partial files`() = runBlocking {
    server.enqueue(pdfResponse().setBodyDelay(2, TimeUnit.SECONDS))
    val downloading = async(Dispatchers.IO) { downloader.download(syllabusUrl, "Sample.pdf") }
    assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))

    withTimeout(5_000L) { downloading.cancelAndJoin() }

    assertTrue(downloading.isCancelled)
    assertTrue(cachedFiles().isEmpty())
  }

  private fun newDownloader(maxFileBytes: Long = 50L * 1024 * 1024) = BtuFileDownloader(
    context = context,
    sessionManager = sessionManager,
    preferencesStore = preferences,
    client = client,
    baseOrigin = origin,
    allowLocalHttpForTesting = true,
    maxFileBytes = maxFileBytes,
  )

  private fun pdfResponse() = MockResponse()
    .addHeader("Content-Type", "application/pdf")
    .setBody(Buffer().write(pdfBytes))

  private fun cachedFiles(): List<File> = File(context.cacheDir, "downloads")
    .listFiles()?.filter { it.isFile }.orEmpty()
}
