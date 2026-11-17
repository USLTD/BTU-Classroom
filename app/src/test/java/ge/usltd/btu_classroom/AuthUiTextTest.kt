package ge.usltd.btu_classroom

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import ge.usltd.btu_classroom.core.auth.AuthTelemetryState
import ge.usltd.btu_classroom.core.auth.GoogleLoginStatus
import ge.usltd.btu_classroom.core.auth.LocalCleanupException
import ge.usltd.btu_classroom.core.network.VerificationStatus
import ge.usltd.btu_classroom.core.preferences.AppLanguage
import ge.usltd.btu_classroom.core.preferences.LocaleHelper
import ge.usltd.btu_classroom.design.text.authBrowserStageResource
import ge.usltd.btu_classroom.design.text.authErrorResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AuthUiTextTest {
  @Test fun googleBrowserBlockHasTranslatedActionableGeorgianMessage() {
    val detail = "Google rejected the embedded browser (disallowed_useragent). You can open BTU Classroom in your external browser below."
    val resource = authErrorResource(detail)
    assertEquals(R.string.ux_auth_google_blocked, resource)
    assertEquals("Google ამ ბრაუზერში შესვლას არ უშვებს. ანგარიშზე წვდომისთვის გახსენით BTU-ის ოფიციალური გვერდი მოწყობილობის ბრაუზერში.",
      context(AppLanguage.KA).getString(resource))
    assertFalse(context(AppLanguage.EN).getString(resource).contains("disallowed_useragent"))
  }

  @Test fun unknownFailureDetailsStayOutOfTheUserFacingMessage() {
    val detail = "Unknown internal failure: private runtime detail"
    val resource = authErrorResource(detail)
    assertEquals(R.string.ux_auth_failure, resource)
    assertEquals("შესვლა ვერ დასრულდა. სცადეთ თავიდან.", context(AppLanguage.KA).getString(resource))
    assertFalse(context(AppLanguage.EN).getString(resource).contains(detail))
  }

  @Test fun cleanupNetworkAndVerificationFailuresKeepTheirRecoveryGuidance() {
    assertEquals(R.string.local_cleanup_failed, authErrorResource(LocalCleanupException.MESSAGE))
    assertEquals(R.string.ux_auth_browser_cleanup,
      authErrorResource("Sign-in browser data could not be cleared. Please retry sign-in."))
    assertEquals(R.string.ux_auth_network_error,
      authErrorResource("Network error during verification (SocketTimeoutException)"))
    assertEquals(R.string.ux_auth_session_verification,
      authErrorResource("Authenticated BTU page was reached, but argus_session cookie was missing in GeckoView."))
    assertEquals(R.string.ux_auth_verification_blocked,
      authErrorResource("Other detail", AuthTelemetryState(verificationStatus = VerificationStatus.CLOUDFLARE_BLOCKED)))
  }

  @Test fun browserStageUsesTheAppLanguageInsteadOfRawDiagnosticLabels() {
    val state = AuthTelemetryState(googleLoginStatus = GoogleLoginStatus.REJECTED_DISALLOWED_USERAGENT,
      browserStageLabel = "Google Rejected (disallowed_useragent)")
    assertEquals("Google-ით შესვლა დაბლოკილია", context(AppLanguage.KA).getString(authBrowserStageResource(state)))
    assertEquals("Google sign-in blocked", context(AppLanguage.EN).getString(authBrowserStageResource(state)))
  }

  private fun context(language: AppLanguage): Context = LocaleHelper.createLocalizedContext(
    ApplicationProvider.getApplicationContext(), language)
}
