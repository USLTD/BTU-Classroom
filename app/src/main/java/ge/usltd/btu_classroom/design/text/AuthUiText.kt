package ge.usltd.btu_classroom.design.text

import androidx.annotation.StringRes
import ge.usltd.btu_classroom.R
import ge.usltd.btu_classroom.core.auth.AuthTelemetryState
import ge.usltd.btu_classroom.core.auth.GeckoInitStatus
import ge.usltd.btu_classroom.core.auth.GoogleLoginStatus
import ge.usltd.btu_classroom.core.auth.LocalCleanupException
import ge.usltd.btu_classroom.core.network.VerificationStatus
import java.util.Locale

/** Raw failure detail remains in diagnostics; user-facing messages use translated resources. */
@StringRes
fun authErrorResource(reason: String, telemetry: AuthTelemetryState = AuthTelemetryState()): Int {
  val message = reason.trim().lowercase(Locale.ROOT)
  return when {
    reason == LocalCleanupException.MESSAGE -> R.string.local_cleanup_failed
    message.startsWith("sign-in browser data could not be cleared") ||
      message.startsWith("sign-in browser reset could not be confirmed") -> R.string.ux_auth_browser_cleanup
    message.contains("disallowed_useragent") ||
      telemetry.googleLoginStatus == GoogleLoginStatus.REJECTED_DISALLOWED_USERAGENT -> R.string.ux_auth_google_blocked
    message.startsWith("oauth reported error: access_denied") -> R.string.ux_auth_google_denied
    message.startsWith("failed to initialize geckoruntime") ||
      message.startsWith("webextension install") -> R.string.ux_auth_browser_unavailable
    message.startsWith("navigation failed on ") || message.startsWith("network error") ||
      message.startsWith("offline — unable to verify session") -> R.string.ux_auth_network_error
    message.startsWith("cloudflare challenge") -> R.string.ux_auth_verification_blocked
    message.startsWith("btu classroom returned server error") ||
      message == "btu is temporarily unavailable" -> R.string.ux_auth_unavailable
    message.contains("argus_session cookie") || message == "session rejected" ||
      message.startsWith("server rejected session") || message.startsWith("server redirected to login") ||
      message.startsWith("endpoint returned a login page") -> R.string.ux_auth_session_verification
    telemetry.verificationStatus == VerificationStatus.NETWORK_ERROR -> R.string.ux_auth_network_error
    telemetry.verificationStatus == VerificationStatus.CLOUDFLARE_BLOCKED -> R.string.ux_auth_verification_blocked
    telemetry.verificationStatus in setOf(VerificationStatus.UNAUTHENTICATED, VerificationStatus.UNEXPECTED_RESPONSE) ->
      R.string.ux_auth_session_verification
    else -> R.string.ux_auth_failure
  }
}

@StringRes
fun authBrowserStageResource(telemetry: AuthTelemetryState): Int = when {
  telemetry.googleLoginStatus == GoogleLoginStatus.REJECTED_DISALLOWED_USERAGENT -> R.string.ux_auth_stage_blocked
  telemetry.googleLoginStatus == GoogleLoginStatus.FAILED -> R.string.ux_auth_stage_failed
  telemetry.verificationStatus == VerificationStatus.VERIFYING -> R.string.auth_verifying_session
  telemetry.geckoInitStatus == GeckoInitStatus.INITIALIZING -> R.string.browser_initializing
  telemetry.googleLoginStatus in setOf(GoogleLoginStatus.OAUTH_IN_PROGRESS, GoogleLoginStatus.GOOGLE_LOGIN_PAGE_LOADED) ->
    R.string.ux_auth_stage_google
  telemetry.googleLoginStatus in setOf(GoogleLoginStatus.BTU_CALLBACK_OBSERVED, GoogleLoginStatus.AUTHENTICATED_PAGE_REACHED) ->
    R.string.ux_auth_stage_finishing
  else -> R.string.ux_auth_stage_signin
}
