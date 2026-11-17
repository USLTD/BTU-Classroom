package ge.usltd.btu_classroom

import ge.usltd.btu_classroom.core.network.BtuHttpClient
import ge.usltd.btu_classroom.core.network.BtuRoutes
import ge.usltd.btu_classroom.core.network.HttpFetchResult
import ge.usltd.btu_classroom.core.network.VerificationStatus
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BtuHttpClientTest {

  private lateinit var server: MockWebServer
  private lateinit var httpClient: BtuHttpClient
  private val diagnosticLogs = mutableListOf<String>()

  @Before
  fun setUp() {
    server = MockWebServer()
    server.start()
    val okClient = OkHttpClient.Builder()
      .followRedirects(false)
      .followSslRedirects(false)
      .connectTimeout(5, TimeUnit.SECONDS)
      .readTimeout(5, TimeUnit.SECONDS)
      .build()
    diagnosticLogs.clear()
    httpClient = BtuHttpClient(
      baseOrigin = "http://127.0.0.1:${server.port}",
      allowLocalHttpForTesting = true,
      client = okClient,
      maxResponseBytes = 16 * 1024L,
      onDiagnosticEvent = { diagnosticLogs.add(it) },
    )
  }

  @After
  fun tearDown() {
    server.shutdown()
  }

  @Test
  fun `verifySession returns AUTHENTICATED when HTTP 200 has Georgian heading and links`() = runTest {
    val html = """
      <html><body>
        <table>
          <thead><tr><th>კურსის დასახელება</th></tr></thead>
          <tbody><tr><td><a href="/ge/student/me/course/index/12345">Algorithms</a></td></tr></tbody>
        </table>
      </body></html>
    """.trimIndent()
    server.enqueue(MockResponse().setResponseCode(200).addHeader("Content-Type", "text/html").setBody(html))

    val result = httpClient.verifySession("test_cookie_value_123")
    val recorded = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS)) { "No verification request received: $diagnosticLogs" }

    assertEquals("argus_session=test_cookie_value_123", recorded.getHeader("Cookie"))
    assertEquals(VerificationStatus.AUTHENTICATED, result.status)
    assertEquals(200, result.httpStatusCode)
    assertNull(result.errorMessage)
    assertTrue(diagnosticLogs.none { it.contains("test_cookie_value_123") })
  }

  @Test
  fun `verifySession returns AUTHENTICATED when HTTP 200 includes Cloudflare script and courses link`() = runTest {
    val html = """
      <html>
        <head><script src="/cdn-cgi/challenge-platform/scripts/jsd/main.js"></script></head>
        <body>
          <table>
            <thead><tr><th>კურსის დასახელება</th></tr></thead>
            <tbody><tr><td><a href="/ge/student/me/courses/98765">Operating Systems</a></td></tr></tbody>
          </table>
        </body>
      </html>
    """.trimIndent()
    server.enqueue(MockResponse().setResponseCode(200).addHeader("Server", "cloudflare").setBody(html))

    val result = httpClient.verifySession("valid_session_cookie")
    assertEquals(VerificationStatus.AUTHENTICATED, result.status)
  }

  @Test
  fun `verifySession returns CLOUDFLARE_BLOCKED on 403 cf-mitigated challenge`() = runTest {
    server.enqueue(
      MockResponse()
        .setResponseCode(403)
        .addHeader("cf-mitigated", "challenge")
        .addHeader("Server", "cloudflare")
        .setBody("Just a moment..."),
    )

    val result = httpClient.verifySession("test_cookie")
    assertEquals(VerificationStatus.CLOUDFLARE_BLOCKED, result.status)
    assertEquals(403, result.httpStatusCode)
  }

  @Test
  fun `verifySession returns CLOUDFLARE_BLOCKED on HTTP 200 interstitial`() = runTest {
    val challengeHtml = """
      <html>
        <head><title>Just a moment...</title></head>
        <body><form id="challenge-form"><script>window._cf_chl_opt={};</script></form></body>
      </html>
    """.trimIndent()
    server.enqueue(MockResponse().setResponseCode(200).addHeader("Server", "cloudflare").setBody(challengeHtml))

    val result = httpClient.verifySession("test_cookie")
    assertEquals(VerificationStatus.CLOUDFLARE_BLOCKED, result.status)
  }

  @Test
  fun `verifySession returns UNAUTHENTICATED on 302 redirect without following redirect`() = runTest {
    server.enqueue(
      MockResponse()
        .setResponseCode(302)
        .addHeader("Location", "https://classroom.btu.edu.ge/ge/login/index"),
    )

    val result = httpClient.verifySession("expired_cookie")
    assertEquals(VerificationStatus.UNAUTHENTICATED, result.status)
    assertEquals(302, result.httpStatusCode)
    assertEquals(1, server.requestCount)
  }

  @Test
  fun `fetchHtml rejects unexpected content type and oversized payloads`() = runTest {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody("{\"ok\":true}"),
    )
    val wrongType = httpClient.fetchHtml("/ge/student/me/courses", "cookie_1")
    assertTrue(wrongType is HttpFetchResult.UnexpectedResponse)

    val hugeBody = "a".repeat(20_000)
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "text/html")
        .setBody(hugeBody),
    )
    val tooLarge = httpClient.fetchHtml("/ge/student/me/courses", "cookie_1")
    assertTrue(tooLarge is HttpFetchResult.UnexpectedResponse)
  }

  @Test
  fun `fetchHtml classifies 503 as TemporarilyUnavailable and IOException as NetworkError`() = runTest {
    server.enqueue(MockResponse().setResponseCode(503).setBody("Maintenance"))
    val unavailable = httpClient.fetchHtml("/ge/student/me/courses", "cookie_1")
    assertTrue(unavailable is HttpFetchResult.TemporarilyUnavailable)

    server.shutdown()
    val offline = httpClient.fetchHtml("/ge/student/me/courses", "cookie_1")
    assertTrue(offline is HttpFetchResult.NetworkError)
    assertFalse((offline as HttpFetchResult.NetworkError).reason.contains("cookie_1"))
  }

  @Test
  fun `logout calls logout endpoint with session cookie`() = runTest {
    server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/ge/login/index"))
    server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/ge/login/index"))
    val result = httpClient.logout("active_cookie_999")
    val recorded = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS)) { "No logout request received: $diagnosticLogs" }

    assertTrue(result.success)
    assertEquals(302, result.httpStatusCode)
    assertEquals("/ge/login/logout", recorded.path)
    assertEquals("argus_session=active_cookie_999", recorded.getHeader("Cookie"))
    assertEquals("/ge/student/me/courses", server.takeRequest(5, TimeUnit.SECONDS)?.path)
  }

  @Test
  fun `logout is unconfirmed when original session still works`() = runTest {
    server.enqueue(MockResponse().setResponseCode(200).setBody("Done"))
    server.enqueue(MockResponse().setResponseCode(200).setBody(
      "<table><tr><th>კურსის დასახელება</th></tr><tr><td><a href='/ge/student/me/course/index/42'>Course</a></td></tr></table>",
    ))
    assertFalse(httpClient.logout("active_cookie").success)
    assertEquals(2, server.requestCount)
  }

  @Test
  fun `invalid cookie cannot inject extra headers or cookie values`() = runTest {
    assertEquals(VerificationStatus.UNAUTHENTICATED, httpClient.verifySession("valid; other=secret").status)
    assertTrue(httpClient.fetchHtml(BtuRoutes.STUDENT_COURSES, "valid\r\nX-Injected: yes") is HttpFetchResult.SessionExpired)
    assertEquals(0, server.requestCount)
  }

  @Test
  fun `BtuRoutes rejects external hosts, cleartext http, javascript, and blocked balance path`() {
    assertNull(BtuRoutes.resolveSafeBtuUrl("https://evil.example.com/ge/student/me/courses"))
    assertNull(BtuRoutes.resolveSafeBtuUrl("http://classroom.btu.edu.ge/ge/student/me/courses"))
    assertNull(BtuRoutes.resolveSafeBtuUrl("javascript:alert(1)"))
    assertNull(BtuRoutes.resolveSafeBtuUrl("/ge/student/me/updBalance"))
    assertEquals(
      "https://classroom.btu.edu.ge/ge/student/me/course/index/42",
      BtuRoutes.resolveSafeBtuUrl("/ge/student/me/course/index/42"),
    )
  }
}
