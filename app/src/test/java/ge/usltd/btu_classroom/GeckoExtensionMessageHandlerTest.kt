package ge.usltd.btu_classroom

import ge.usltd.btu_classroom.core.auth.GeckoExtensionMessageHandler
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class GeckoExtensionMessageHandlerTest {

  @Test
  fun `dispatches hardened session lifecycle events cleanly`() {
    var detectedSession: String? = null
    var readySession: String? = null
    var missingCalled = false
    var expiredReason: String? = null
    var callbackObserved = false
    var authenticatedPageReached = false

    val handler = GeckoExtensionMessageHandler(
      object : GeckoExtensionMessageHandler.Listener {
        override fun onSessionDetected(sessionValue: String?, expirationDateEpochSeconds: Long?) {
          detectedSession = sessionValue
        }

        override fun onSessionReady(sessionValue: String, expirationDateEpochSeconds: Long?) {
          readySession = sessionValue
        }

        override fun onSessionCookieMissing() {
          missingCalled = true
        }

        override fun onSessionExpired(reason: String, expirationDateEpochSeconds: Long?) {
          expiredReason = reason
        }

        override fun onBtuCallbackObserved(hasError: Boolean) {
          callbackObserved = true
        }

        override fun onAuthenticatedPageReached() {
          authenticatedPageReached = true
        }
      },
    )

    handler.dispatchIncomingJson(
      JSONObject()
        .put("type", "SESSION_DETECTED")
        .put("session", "anon_cookie_1"),
    )
    assertEquals("anon_cookie_1", detectedSession)

    handler.dispatchIncomingJson(
      JSONObject()
        .put("type", "BTU_CALLBACK_OBSERVED")
        .put("hasError", false),
    )
    assertTrue(callbackObserved)

    handler.dispatchIncomingJson(JSONObject().put("type", "AUTHENTICATED_PAGE_REACHED"))
    assertTrue(authenticatedPageReached)

    handler.dispatchIncomingJson(
      JSONObject()
        .put("type", "SESSION_READY")
        .put("session", "auth_cookie_99"),
    )
    assertEquals("auth_cookie_99", readySession)

    handler.dispatchIncomingJson(
      JSONObject()
        .put("type", "SESSION_READY")
        .put("session", ""),
    )
    assertTrue(missingCalled)

    missingCalled = false
    handler.dispatchIncomingJson(JSONObject().put("type", "SESSION_READY").put("session", "cookie; injected=secret"))
    assertTrue(missingCalled)
    assertEquals("auth_cookie_99", readySession)

    handler.dispatchIncomingJson(JSONObject().put("type", "SESSION_READY")
      .put("session", "expired_cookie").put("expirationDate", 1L))
    assertEquals("expiration_date_passed", expiredReason)
    assertEquals("auth_cookie_99", readySession)

    handler.dispatchIncomingJson(
      JSONObject()
        .put("type", "SESSION_EXPIRED")
        .put("reason", "expired"),
    )
    assertEquals("expired", expiredReason)

    handler.dispatchIncomingJson(JSONObject().put("type", "OAUTH_TOKEN_CAPTURED").put("tokenPayload", "ignored"))
    handler.dispatchIncomingJson(JSONObject().put("type", "USER_AGENT_UPDATED"))
    assertEquals("auth_cookie_99", readySession)
  }
}
