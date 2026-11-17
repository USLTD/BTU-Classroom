package ge.usltd.btu_classroom

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import ge.usltd.btu_classroom.core.network.BtuRoutes
import ge.usltd.btu_classroom.core.portal.PortalTerminology
import ge.usltd.btu_classroom.core.preferences.AppLanguage
import ge.usltd.btu_classroom.core.preferences.LocaleHelper
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BilingualPortalTest {
  @Test fun browserLanguageFollowsEnglishOrGeorgianWithoutChangingCanonicalFetchRoutes() {
    assertEquals("https://classroom.btu.edu.ge/en/login/index", BtuRoutes.loginUrl(Locale.ENGLISH))
    assertEquals("https://classroom.btu.edu.ge/ge/login/index", BtuRoutes.loginUrl(Locale.forLanguageTag("ka-GE")))
    assertEquals("https://classroom.btu.edu.ge/en/student/me/schedule", BtuRoutes.scheduleUrl(Locale.ENGLISH))
    assertEquals("https://classroom.btu.edu.ge/ge/student/me/courses", BtuRoutes.coursesUrl())
    assertEquals("/ge/student/me/courses", BtuRoutes.STUDENT_COURSES)
    assertEquals("/ge/login/oauth/", BtuRoutes.canonicalLocalePath("/en/login/oauth/"))
    assertEquals("/ge/student/me/courses", BtuRoutes.canonicalLocalePath("/en/student/me/courses"))
    assertEquals("/fr/student/me/courses", BtuRoutes.canonicalLocalePath("/fr/student/me/courses"))
  }

  @Test fun languageLinksKeepEncodedQueryAndDocumentPathsIntact() {
    assertEquals(
      "https://classroom.btu.edu.ge/en/student/me/statements/view/17/4?name=a%2Fb%20c",
      BtuRoutes.forLocale("/ge/student/me/statements/view/17/4?name=a%2Fb%20c", Locale.ENGLISH),
    )
    assertEquals(
      "https://classroom.btu.edu.ge/uploads/12/34.docx",
      BtuRoutes.forLocale("/uploads/12/34.docx", Locale.ENGLISH),
    )
    assertEquals(
      "https://classroom.btu.edu.ge/ge/student/me/program",
      BtuRoutes.forLocale("/en/student/me/program", Locale.forLanguageTag("ka")),
    )
  }

  @Test fun bothPortalLanguagesEnforceOriginAndMutationGuards() {
    for (language in listOf("ge", "en")) {
      assertNull(BtuRoutes.resolveSafeBtuUrl("/$language/student/me/updBalance"))
      assertNull(BtuRoutes.resolveSafeBtuUrl("/$language/student/me/%75pdBalance"))
      assertNull(BtuRoutes.forLocale("/$language/student/me/updBalance", Locale.ENGLISH))
    }
    assertNull(BtuRoutes.forLocale("https://example.com/ge/student/me/courses", Locale.ENGLISH))
    assertNull(BtuRoutes.forLocale("https://classroom.btu.edu.ge:8443/en/login/index", Locale.ENGLISH))
    assertNull(BtuRoutes.forLocale("javascript:alert(1)", Locale.ENGLISH))
  }

  @Test fun knownCachedLabelsRenderInEitherLanguageAndUnknownSourceTextRemainsExact() {
    val english = context(AppLanguage.EN)
    val georgian = context(AppLanguage.KA)
    assertEquals("Faculty", PortalTerminology.localizeInterfaceText("ფაკულტეტი:", english))
    assertEquals("ფაკულტეტი", PortalTerminology.localizeInterfaceText("Faculty", georgian))
    assertEquals("Course", PortalTerminology.localizeInterfaceText("\u00a0კურსის   დასახელება\u00a0", english))
    assertEquals("GPA", PortalTerminology.localizeInterfaceText("GPA", georgian))
    val officialSource = "  სპეციალური კურსი — ლექტორის ჩანაწერი  "
    assertEquals(officialSource, PortalTerminology.localizeInterfaceText(officialSource, english))
    assertNull(PortalTerminology.labelResource(officialSource))
    assertEquals("72.25", PortalTerminology.localizeInterfaceText("72.25", georgian))
  }

  @Test fun recognizedSemesterHeadingsRetainTheirPublishedAcademicYears() {
    assertEquals("2025-2026 autumn semester", PortalTerminology.localizeInterfaceText(
      "2025-2026 წლის შემოდგომის სემესტრი", context(AppLanguage.EN)))
    assertEquals("2025–2026 წლის გაზაფხულის სემესტრი", PortalTerminology.localizeInterfaceText(
      "2025–2026 spring semester", context(AppLanguage.KA)))
    assertEquals("Monday", PortalTerminology.localizeInterfaceText("ორშაბათი", context(AppLanguage.EN)))
    val unsupported = "2025-2026 წლის სპეციალური სემესტრი"
    assertEquals(unsupported, PortalTerminology.localizeInterfaceText(unsupported, context(AppLanguage.EN)))
  }

  private fun context(language: AppLanguage): Context = LocaleHelper.createLocalizedContext(
    ApplicationProvider.getApplicationContext(), language)
}
