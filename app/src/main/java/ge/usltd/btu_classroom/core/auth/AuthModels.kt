package ge.usltd.btu_classroom.core.auth

import ge.usltd.btu_classroom.core.network.VerificationStatus
import java.net.URI

sealed interface AuthState {
  data object Unknown : AuthState
  data object SignedOut : AuthState
  data object LoggingIn : AuthState
  data object Verifying : AuthState
  data object Authenticated : AuthState
  data object Expired : AuthState
  data class Error(val reason: String) : AuthState
}

enum class GeckoInitStatus(val label: String) {
  NOT_INITIALIZED("Not Initialized"),
  INITIALIZING("Initializing..."),
  READY("Initialized"),
  FAILED("Failed"),
}

enum class ExtensionInstallStatus(val label: String) {
  NOT_INSTALLED("Not Installed"),
  INSTALLING("Installing..."),
  INSTALLED("Installed"),
  FAILED("Failed"),
}

enum class GoogleLoginStatus(val label: String) {
  IDLE("Idle"),
  BTU_LOGIN_LOADED("BTU Login Page Loaded"),
  OAUTH_IN_PROGRESS("OAuth Navigation In Progress"),
  GOOGLE_LOGIN_PAGE_LOADED("Google Login Page Loaded"),
  REJECTED_DISALLOWED_USERAGENT("Rejected (disallowed_useragent)"),
  BTU_CALLBACK_OBSERVED("BTU Callback Observed"),
  AUTHENTICATED_PAGE_REACHED("Authenticated BTU Page Reached"),
  FAILED("Authentication / Network Failure"),
}

enum class LogLevel {
  INFO,
  SUCCESS,
  WARNING,
  ERROR,
}

data class DiagnosticLogEntry(
  val id: Long,
  val timestamp: String,
  val message: String,
  val level: LogLevel = LogLevel.INFO,
)

data class AuthTelemetryState(
  val geckoInitStatus: GeckoInitStatus = GeckoInitStatus.NOT_INITIALIZED,
  val extensionInstallStatus: ExtensionInstallStatus = ExtensionInstallStatus.NOT_INSTALLED,
  val googleLoginStatus: GoogleLoginStatus = GoogleLoginStatus.IDLE,
  val btuCallbackObserved: Boolean = false,
  val sessionCaptured: Boolean = false,
  val verificationStatus: VerificationStatus = VerificationStatus.IDLE,
  val lastHttpStatusCode: Int? = null,
  val isOfflineFallback: Boolean = false,
  val showExternalBrowserFallback: Boolean = false,
  val isBrowserModalVisible: Boolean = false,
  val hasActiveBrowserSession: Boolean = false,
  val browserCanGoBack: Boolean = false,
  val browserLoadProgress: Int = 0,
  val browserStageLabel: String = "Idle",
  val isReauthPromptVisible: Boolean = false,
  val isBusy: Boolean = false,
  val errorMessage: String? = null,
  val logs: List<DiagnosticLogEntry> = emptyList(),
)

/**
 * Inspects navigation URIs in memory and extracts only non-sensitive metadata.
 * Query parameters (such as OAuth authorization codes or tokens) are never exposed in logs.
 */
object UrlSanitizer {

  data class ParsedNavigation(
    val host: String,
    val path: String,
    val hasDisallowedUserAgent: Boolean,
    val oauthErrorCode: String?,
  )

  private val SAFE_OAUTH_ERRORS = setOf(
    "disallowed_useragent",
    "access_denied",
    "invalid_request",
    "unauthorized_client",
    "unsupported_response_type",
    "invalid_scope",
    "server_error",
    "temporarily_unavailable",
    "org_internal",
    "admin_policy_enforced",
  )

  fun inspect(rawUrl: String?): ParsedNavigation? {
    if (rawUrl.isNullOrBlank()) return null
    return try {
      val uri = URI(rawUrl)
      val host = (uri.host ?: "").lowercase()
      val path = uri.path ?: "/"
      val query = uri.rawQuery.orEmpty()
      val lowerAll = rawUrl.lowercase()

      val disallowed = lowerAll.contains("disallowed_useragent")
      val extractedError = extractSafeErrorParam(query)
        ?: if (disallowed) "disallowed_useragent" else null

      ParsedNavigation(
        host = host,
        path = path,
        hasDisallowedUserAgent = disallowed,
        oauthErrorCode = extractedError,
      )
    } catch (_: Exception) {
      null
    }
  }

  private fun extractSafeErrorParam(rawQuery: String): String? {
    if (rawQuery.isBlank()) return null
    for (pair in rawQuery.split('&')) {
      val idx = pair.indexOf('=')
      if (idx <= 0) continue
      val key = pair.substring(0, idx).lowercase()
      val value = pair.substring(idx + 1).lowercase()
      if (key == "error" && value in SAFE_OAUTH_ERRORS) {
        return value
      }
    }
    return null
  }

  fun redactSensitiveTokens(text: String, activeSession: String? = null): String {
    var sanitized = text
    if (!activeSession.isNullOrBlank()) {
      sanitized = sanitized.replace(activeSession, "<REDACTED_SESSION>")
    }
    sanitized = sanitized.replace(
      Regex("""(argus_session|code|access_token|id_token|refresh_token)=([^&\s;]+)""", RegexOption.IGNORE_CASE),
      "$1=<REDACTED>",
    )
    return sanitized
  }
}
