package ge.usltd.btu_classroom

import ge.usltd.btu_classroom.core.auth.SessionCookieValidator
import ge.usltd.btu_classroom.core.network.BtuRoutes
import ge.usltd.btu_classroom.core.network.ResponseClassifier
import ge.usltd.btu_classroom.core.network.VerificationStatus
import ge.usltd.btu_classroom.core.network.HttpFetchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthSecurityTest {
  @Test
  fun `same host at another port or with userinfo is not the approved origin`() {
    assertNull(BtuRoutes.resolveSafeBtuUrl("https://classroom.btu.edu.ge:8443/ge/student/me/courses"))
    assertNull(BtuRoutes.resolveSafeBtuUrl("https://student@classroom.btu.edu.ge/ge/student/me/courses"))
    assertNull(BtuRoutes.resolveSafeBtuUrl("https://classroom.btu.edu.ge/ge/student/me/%75pdBalance"))
    assertNull(BtuRoutes.resolveSafeBtuUrl("https://classroom.btu.edu.ge/ge/student/me/courses#secret"))
    assertEquals(BtuRoutes.coursesUrl(), BtuRoutes.resolveSafeBtuUrl(BtuRoutes.STUDENT_COURSES))
  }

  @Test
  fun `empty recognized courses table proves authenticated response`() {
    val body = "<table><thead><tr><th>კოდი</th><th>კურსის დასახელება</th><th>ქულა</th></tr></thead><tbody></tbody></table>"
    assertEquals(VerificationStatus.AUTHENTICATED, classify(body))
    assertEquals(VerificationStatus.UNEXPECTED_RESPONSE, classify(body, "application/json"))
    assertFalse(ResponseClassifier.hasAuthenticatedCoursesEvidence("<h1>Course name</h1><a href='/ge/student/me/schedule'>Schedule</a>"))
  }

  @Test
  fun `cookie values are bounded single header values`() {
    assertTrue(SessionCookieValidator.isValid("opaque_cookie-123="))
    listOf("", "secret cookie", "cookie; admin=1", "cookie\r\nOther: value", "a".repeat(4097)).forEach {
      assertFalse(SessionCookieValidator.isValid(it))
    }
  }

  @Test
  fun `statement comments and navigation mentioning login do not expire a valid page`() {
    val body = """
      <div class="col-md-18"><fieldset><legend>განცხადებები</legend>
        <table><thead><tr><th>ნომერი</th><th>კომენტარი</th></tr></thead>
        <tbody><tr><td>SAMPLE-001</td><td>Help: /ge/login/index. Google login instructions
          <a href="/ge/login/oauth/">Google sign-in help</a>
        </td></tr></tbody></table>
      </fieldset></div>
    """.trimIndent()
    assertFalse(ResponseClassifier.looksLikeLoginPage(body))
    assertTrue(fetch(body) is HttpFetchResult.SuccessHtml)
    assertFalse(ResponseClassifier.looksLikeLoginPage("<p>Help: /ge/login/index. Google login instructions.</p>"))
    assertFalse(ResponseClassifier.looksLikeLoginPage("<nav><a href='/ge/login/index'>Login</a></nav><h1>Student portal</h1>"))
    assertFalse(ResponseClassifier.looksLikeLoginPage("<h1>Help</h1><p>For account support, follow these instructions.</p><a href='/ge/login/index'>Login</a>"))
  }

  @Test
  fun `actual BTU login container and password form are detected structurally`() {
    val btuLogin = """
      <div id="loginForm"><h2>ავტორიზაცია</h2>
        <a href="/ge/login/oauth/">Google</a>
      </div>
    """.trimIndent()
    val passwordLogin = "<form action='/session'><input type='email' name='email'><input type='password' name='password'></form>"
    for (body in listOf(btuLogin, passwordLogin, "<a href='/ge/login/index'>Login</a>", "<a href='/ge/login/oauth/'>Sign in with Google</a>")) {
      assertTrue(ResponseClassifier.looksLikeLoginPage(body))
      assertTrue(fetch(body) is HttpFetchResult.SessionExpired)
    }
  }

  @Test
  fun `authoritative HTTP authentication failures still expire sessions`() {
    assertTrue(fetch("<p>Any response</p>", 401) is HttpFetchResult.SessionExpired)
    assertTrue(fetch("", 302, "/ge/login/index") is HttpFetchResult.SessionExpired)
  }

  @Test
  fun `English social login pages expire sessions without treating navigation as login`() {
    val englishLogin = "<div id='loginForm'><h2>Login</h2><a href='/en/login/oauth/'>Login</a></div>"
    assertTrue(ResponseClassifier.looksLikeLoginPage(englishLogin))
    assertTrue(fetch(englishLogin) is HttpFetchResult.SessionExpired)
    assertEquals(VerificationStatus.UNAUTHENTICATED, classify(englishLogin))
    assertTrue(fetch("", 302, "/en/login/index") is HttpFetchResult.SessionExpired)
    assertFalse(ResponseClassifier.looksLikeLoginPage(
      "<nav><a href='/en/login/index'>Login</a></nav><h1>Student portal</h1>"))
    assertFalse(ResponseClassifier.looksLikeLoginPage(
      "<table><thead><tr><th>Course</th></tr></thead><tbody><tr><td><a href='/en/login/oauth/'>Login help</a></td></tr></tbody></table>"))
  }

  private fun fetch(body: String, status: Int = 200, location: String? = null) =
    ResponseClassifier.classifyHtmlFetch(status, location, null, null, "text/html", body, 1)

  private fun classify(body: String, contentType: String = "text/html") =
    ResponseClassifier.classifyCoursesVerification(200, null, null, null, contentType, body, 1).status
}
