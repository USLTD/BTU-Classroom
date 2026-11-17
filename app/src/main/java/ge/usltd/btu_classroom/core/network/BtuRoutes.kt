package ge.usltd.btu_classroom.core.network

import java.net.URI
import java.util.Locale

object BtuRoutes {
  const val APPROVED_HOST = "classroom.btu.edu.ge"
  const val BASE_ORIGIN = "https://classroom.btu.edu.ge"
  const val SESSION_COOKIE_NAME = "argus_session"

  const val LOGIN_INDEX = "/ge/login/index"
  const val LOGIN_OAUTH = "/ge/login/oauth"
  const val LOGIN_LOGOUT = "/ge/login/logout"
  const val STUDENT_COURSES = "/ge/student/me/courses"
  const val STUDENT_SCHEDULE = "/ge/student/me/schedule"
  const val STUDENT_EXAMS = "/ge/exams/list"

  private const val BLOCKED_UPDATE_BALANCE_PATH = "/ge/student/me/updBalance"
  private val LOCALE_PREFIX = Regex("^/(?:ge|en)/", RegexOption.IGNORE_CASE)

  fun loginUrl(locale: Locale = GEORGIAN_PORTAL_LOCALE): String = requireNotNull(forLocale(LOGIN_INDEX, locale))

  fun coursesUrl(locale: Locale = GEORGIAN_PORTAL_LOCALE): String = requireNotNull(forLocale(STUDENT_COURSES, locale))

  fun scheduleUrl(locale: Locale = GEORGIAN_PORTAL_LOCALE): String = requireNotNull(forLocale(STUDENT_SCHEDULE, locale))

  fun logoutUrl(): String = "$BASE_ORIGIN$LOGIN_LOGOUT"

  fun courseDetailPath(courseId: String): String {
    val cleanId = courseId.trim().trim('/')
    return "/ge/student/me/course/index/$cleanId"
  }

  fun isBlockedMutationPath(path: String): Boolean {
    return canonicalLocalePath(path).contains(BLOCKED_UPDATE_BALANCE_PATH, ignoreCase = true)
  }

  /** Keeps native fetch/cache contracts stable while browser links follow the selected UI language. */
  fun forLocale(hrefOrPath: String, locale: Locale): String? {
    val safeUrl = resolveSafeBtuUrl(hrefOrPath) ?: return null
    val uri = URI(safeUrl)
    val language = if (locale.language == "ka" || locale.language == "ge") "ge" else "en"
    val localizedPath = LOCALE_PREFIX.replaceFirst(uri.rawPath, "/$language/")
    return URI(buildString {
      append(uri.scheme).append("://").append(uri.rawAuthority).append(localizedPath)
      uri.rawQuery?.let { append('?').append(it) }
    }).toASCIIString()
  }

  fun canonicalLocalePath(path: String): String = LOCALE_PREFIX.replaceFirst(path, "/ge/")

  private val GEORGIAN_PORTAL_LOCALE = Locale.forLanguageTag("ka")

  /**
   * Resolves relative or absolute hrefs strictly against the approved BTU origin.
   * Rejects non-HTTPS schemes, external hosts, and restricted balance-update paths.
   */
  fun resolveSafeBtuUrl(
    hrefOrPath: String?,
    approvedOrigin: String = BASE_ORIGIN,
    allowLocalHttpForTesting: Boolean = false,
  ): String? {
    val raw = hrefOrPath?.trim() ?: return null
    if (raw.isEmpty() || raw.startsWith("#")) return null

    val lower = raw.lowercase()
    if (lower.startsWith("javascript:") || lower.startsWith("data:") || lower.startsWith("mailto:") || lower.startsWith("tel:")) {
      return null
    }

    return try {
      val baseUri = URI(approvedOrigin)
      val resolved = if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
        URI(raw).normalize()
      } else {
        val normalizedPath = if (raw.startsWith("/")) raw else "/$raw"
        baseUri.resolve(normalizedPath).normalize()
      }

      val scheme = resolved.scheme?.lowercase() ?: return null
      val host = resolved.host?.lowercase() ?: return null
      val expectedHost = baseUri.host?.lowercase() ?: APPROVED_HOST

      val isValidScheme = if (allowLocalHttpForTesting && (host == "localhost" || host == "127.0.0.1")) {
        scheme == "http" || scheme == "https"
      } else {
        scheme == "https"
      }
      if (!isValidScheme) return null
      if (host != expectedHost) return null
      if (resolved.rawUserInfo != null || resolved.rawFragment != null) return null
      val expectedPort = if (baseUri.port != -1) baseUri.port else if (baseUri.scheme == "https") 443 else 80
      val resolvedPort = if (resolved.port != -1) resolved.port else if (scheme == "https") 443 else 80
      if (resolvedPort != expectedPort) return null

      val path = resolved.path ?: "/"
      if (isBlockedMutationPath(path)) return null

      resolved.toString()
    } catch (_: Exception) {
      null
    }
  }
}
