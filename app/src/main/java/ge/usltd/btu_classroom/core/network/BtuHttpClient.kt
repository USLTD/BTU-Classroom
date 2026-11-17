package ge.usltd.btu_classroom.core.network

import ge.usltd.btu_classroom.core.auth.SessionCookieValidator
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody

/**
 * Centralized OkHttp client for all authenticated requests to BTU Classroom.
 * Enforces approved origins, HTTPS in production, disabled redirects, timeouts, bounded body reads,
 * and sanitized telemetry callbacks without ever logging cookie secrets or HTML payloads.
 */
class BtuHttpClient(
  private val baseOrigin: String = BtuRoutes.BASE_ORIGIN,
  private val allowLocalHttpForTesting: Boolean = false,
  private val client: OkHttpClient = buildDefaultClient(),
  private val maxResponseBytes: Long = DEFAULT_MAX_RESPONSE_BYTES,
  private val onDiagnosticEvent: ((String) -> Unit)? = null,
) {

  suspend fun verifySession(sessionValue: String): VerificationResult = withContext(Dispatchers.IO) {
    if (!SessionCookieValidator.isValid(sessionValue)) {
      return@withContext VerificationResult(
        status = VerificationStatus.UNAUTHENTICATED,
        httpStatusCode = null,
        errorMessage = "No argus_session cookie available to verify.",
        logSummary = "Verification skipped: no session in memory",
      )
    }

    val url = BtuRoutes.resolveSafeBtuUrl(
      hrefOrPath = BtuRoutes.STUDENT_COURSES,
      approvedOrigin = baseOrigin,
      allowLocalHttpForTesting = allowLocalHttpForTesting,
    ) ?: return@withContext VerificationResult(
      status = VerificationStatus.UNEXPECTED_RESPONSE,
      httpStatusCode = null,
      errorMessage = "Verification URL failed approved-origin check.",
      logSummary = "Blocked invalid verification origin",
    )

    val request = Request.Builder()
      .url(url)
      .get()
      .header("Cookie", "${BtuRoutes.SESSION_COOKIE_NAME}=$sessionValue")
      .build()

    val startNanos = System.nanoTime()
    try {
      client.newCall(request).execute().use { response ->
        val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L
        val code = response.code
        val location = response.header("Location")
        val cfMitigated = response.header("cf-mitigated")
        val serverHeader = response.header("Server")
        val contentType = response.header("Content-Type")
        val body = readBoundedBody(response.body)
          ?: return@withContext VerificationResult(
            status = VerificationStatus.UNEXPECTED_RESPONSE,
            httpStatusCode = code,
            errorMessage = "Response exceeded maximum allowed size ($maxResponseBytes bytes).",
            logSummary = "UNEXPECTED_RESPONSE (body exceeded size cap)",
          )

        onDiagnosticEvent?.invoke("GET ${BtuRoutes.STUDENT_COURSES} -> HTTP $code (${elapsedMs}ms)")

        ResponseClassifier.classifyCoursesVerification(
          statusCode = code,
          locationHeader = location,
          cfMitigatedHeader = cfMitigated,
          serverHeader = serverHeader,
          contentTypeHeader = contentType,
          body = body,
          latencyMs = elapsedMs,
        )
      }
    } catch (e: IOException) {
      val reason = e.javaClass.simpleName
      onDiagnosticEvent?.invoke("GET ${BtuRoutes.STUDENT_COURSES} -> IOException ($reason)")
      VerificationResult(
        status = VerificationStatus.NETWORK_ERROR,
        httpStatusCode = null,
        errorMessage = "Network error during verification ($reason)",
        logSummary = "NETWORK_ERROR ($reason)",
      )
    }
  }

  suspend fun fetchHtml(pathOrRelativeUrl: String, sessionValue: String): HttpFetchResult =
    withContext(Dispatchers.IO) {
      if (!SessionCookieValidator.isValid(sessionValue)) {
        return@withContext HttpFetchResult.SessionExpired(
          statusCode = null,
          reason = "No active session in memory.",
        )
      }

      val resolvedUrl = BtuRoutes.resolveSafeBtuUrl(
        hrefOrPath = pathOrRelativeUrl,
        approvedOrigin = baseOrigin,
        allowLocalHttpForTesting = allowLocalHttpForTesting,
      ) ?: return@withContext HttpFetchResult.UnexpectedResponse(
        statusCode = null,
        reason = "Blocked unsafe or unapproved BTU path.",
      )

      val request = Request.Builder()
        .url(resolvedUrl)
        .get()
        .header("Cookie", "${BtuRoutes.SESSION_COOKIE_NAME}=$sessionValue")
        .build()

      val safePath = sanitizePathForLog(pathOrRelativeUrl)
      val startNanos = System.nanoTime()
      try {
        client.newCall(request).execute().use { response ->
          val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L
          val code = response.code
          val location = response.header("Location")
          val cfMitigated = response.header("cf-mitigated")
          val serverHeader = response.header("Server")
          val contentType = response.header("Content-Type")
          val body = readBoundedBody(response.body)
            ?: return@withContext HttpFetchResult.UnexpectedResponse(
              statusCode = code,
              reason = "Response exceeded maximum allowed size ($maxResponseBytes bytes).",
            )

          onDiagnosticEvent?.invoke("GET $safePath -> HTTP $code (${elapsedMs}ms)")

          ResponseClassifier.classifyHtmlFetch(
            statusCode = code,
            locationHeader = location,
            cfMitigatedHeader = cfMitigated,
            serverHeader = serverHeader,
            contentTypeHeader = contentType,
            body = body,
            latencyMs = elapsedMs,
          )
        }
      } catch (e: IOException) {
        val reason = e.javaClass.simpleName
        onDiagnosticEvent?.invoke("GET $safePath -> IOException ($reason)")
        HttpFetchResult.NetworkError(reason = "Network error ($reason)")
      }
    }

  suspend fun logout(sessionValue: String?): LogoutResult = withContext(Dispatchers.IO) {
    if (!SessionCookieValidator.isValid(sessionValue)) {
      return@withContext LogoutResult(false, null, "No valid remote session to revoke; local data will still be cleared.")
    }
    val resolvedUrl = BtuRoutes.resolveSafeBtuUrl(
      hrefOrPath = BtuRoutes.LOGIN_LOGOUT,
      approvedOrigin = baseOrigin,
      allowLocalHttpForTesting = allowLocalHttpForTesting,
    ) ?: return@withContext LogoutResult(
      success = false,
      httpStatusCode = null,
      detail = "Logout URL failed origin validation",
    )

    val requestBuilder = Request.Builder()
      .url(resolvedUrl)
      .get()
    if (!sessionValue.isNullOrBlank()) {
      requestBuilder.header("Cookie", "${BtuRoutes.SESSION_COOKIE_NAME}=$sessionValue")
    }
    try {
      client.newCall(requestBuilder.build()).execute().use { response ->
        val code = response.code
        onDiagnosticEvent?.invoke("GET ${BtuRoutes.LOGIN_LOGOUT} -> HTTP $code")
        if (code !in 200..399) {
          return@withContext LogoutResult(false, code, "Remote BTU logout returned HTTP $code")
        }
        val verification = verifySession(requireNotNull(sessionValue))
        val confirmed = verification.status == VerificationStatus.UNAUTHENTICATED
        LogoutResult(confirmed, code, if (confirmed) "Remote logout confirmed by a fresh session check" else "Local logout complete; remote logout could not be confirmed")
      }
    } catch (e: IOException) {
      val reason = e.javaClass.simpleName
      onDiagnosticEvent?.invoke("GET ${BtuRoutes.LOGIN_LOGOUT} -> IOException ($reason)")
      LogoutResult(
        success = false,
        httpStatusCode = null,
        detail = "Remote BTU logout failed ($reason)",
      )
    }
  }

  private fun readBoundedBody(body: ResponseBody?): String? {
    if (body == null) return ""
    val contentLength = body.contentLength()
    if (contentLength > maxResponseBytes) {
      return null
    }
    val source = body.source()
    val buffer = okio.Buffer()
    var totalRead = 0L
    while (true) {
      val read = source.read(buffer, 8192L)
      if (read == -1L) break
      totalRead += read
      if (totalRead > maxResponseBytes) {
        return null
      }
    }
    val charset = body.contentType()?.charset(StandardCharsets.UTF_8) ?: StandardCharsets.UTF_8
    return buffer.readString(charset)
  }

  private fun sanitizePathForLog(pathOrUrl: String): String {
    return pathOrUrl.substringBefore('?').substringBefore('#')
  }

  companion object {
    const val DEFAULT_MAX_RESPONSE_BYTES = 4L * 1024L * 1024L

    fun buildDefaultClient(): OkHttpClient {
      return OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()
    }
  }
}
