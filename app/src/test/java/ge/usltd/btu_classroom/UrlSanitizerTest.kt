package ge.usltd.btu_classroom

import ge.usltd.btu_classroom.core.auth.UrlSanitizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlSanitizerTest {

  @Test
  fun `detects disallowed_useragent without exposing query parameters`() {
    val parsed = UrlSanitizer.inspect(
      "https://accounts.google.com/signin/oauth/error?error=disallowed_useragent&code=secret123",
    )
    assertNotNull(parsed)
    assertEquals("accounts.google.com", parsed?.host)
    assertTrue(parsed?.hasDisallowedUserAgent == true)
    assertEquals("disallowed_useragent", parsed?.oauthErrorCode)
  }

  @Test
  fun `redacts session cookie and oauth codes from diagnostic messages`() {
    val secretSession = "btu_secret_session_abc123"
    val raw = "Debug argus_session=$secretSession code=4/0AeanS0_secret access_token=ya29.secret"
    val redacted = UrlSanitizer.redactSensitiveTokens(raw, secretSession)

    assertFalse(redacted.contains(secretSession))
    assertFalse(redacted.contains("4/0AeanS0_secret"))
    assertFalse(redacted.contains("ya29.secret"))
    assertTrue(redacted.contains("<REDACTED"))
  }
}
