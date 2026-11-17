package ge.usltd.btu_classroom

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import ge.usltd.btu_classroom.core.database.SyncOutcome
import ge.usltd.btu_classroom.core.portal.PortalCachedPage
import ge.usltd.btu_classroom.core.portal.PortalCalendarEvent
import ge.usltd.btu_classroom.core.portal.PortalContent
import ge.usltd.btu_classroom.core.portal.PortalField
import ge.usltd.btu_classroom.core.portal.PortalLink
import ge.usltd.btu_classroom.core.portal.PortalModule
import ge.usltd.btu_classroom.core.portal.PortalRow
import ge.usltd.btu_classroom.core.portal.PortalSection
import ge.usltd.btu_classroom.core.preferences.AppLanguage
import ge.usltd.btu_classroom.core.preferences.AppThemeMode
import ge.usltd.btu_classroom.core.preferences.LocaleHelper
import ge.usltd.btu_classroom.design.theme.BtucmTheme
import ge.usltd.btu_classroom.feature.portal.NativePortalScreen
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PortalScreensTest {
  @get:Rule val compose = createComposeRule()

  @Test fun examinationsRetainRawMonthAndDayAndExplainMissingYear() {
    render(PortalModule.EXAMS, PortalContent(
      title = "საგამოცდო ცხრილი",
      sections = listOf(PortalSection(columns = listOf("საგანი", "დღე", "საათი", "აუდიტორია", "ადგილი", "ფორმატი"),
        rows = listOf(PortalRow(listOf("Algorithms", "11 იანვარი", "10:00", "A204", "12", "Written"))))),
    ))
    compose.onNodeWithTag("portal_exam_year_notice").assertIsDisplayed()
    compose.onNodeWithText("11 იანვარი").performScrollTo().assertIsDisplayed()
    compose.onNodeWithText("Algorithms").assertIsDisplayed()
    compose.onNodeWithText("Add to calendar").assertDoesNotExist()
    compose.onNodeWithText("Set reminder").assertDoesNotExist()
    capture("portal-exams-en-light")
  }

  @Test fun programPresentsOfficialFacultyAndDegreeWithoutEditableFields() {
    render(PortalModule.PROGRAM, PortalContent(
      title = "ჩემი პროგრამები",
      fields = listOf(PortalField("ფაკულტეტი", "Business and Technology"),
        PortalField("პროგრამა", "Information Technology")),
    ))
    compose.onNodeWithTag("portal_program_read_only").assertIsDisplayed()
    compose.onNodeWithText("Business and Technology").assertIsDisplayed()
    compose.onNodeWithText("Information Technology").assertIsDisplayed()
    assertEquals(0, compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
    capture("portal-program-en-light")
  }

  @Test fun calendarKeepsArchivedYearsAndDoesNotInventCurrentEvents() {
    render(PortalModule.CALENDAR, PortalContent(
      title = "კალენდარი",
      calendarEvents = listOf(PortalCalendarEvent("2017-12-25", "Winter holiday"),
        PortalCalendarEvent("2018-01-07", "Christmas holiday")),
    ))
    compose.onNodeWithTag("portal_calendar_archive_notice").assertIsDisplayed()
    compose.onNodeWithTag("portal_calendar_no_current_notice").assertIsDisplayed()
    compose.onNodeWithText("Winter holiday").performScrollTo().assertIsDisplayed()
    capture("portal-calendar-en-light")
    compose.onNodeWithText("Christmas holiday").performScrollTo().assertIsDisplayed()
    compose.onNodeWithText("2026-12-25").assertDoesNotExist()
  }

  @Test fun academicRecordShowsOfficialGpaAndFiltersSemesterRows() {
    render(PortalModule.ACADEMIC_CARD, academicRecord)
    compose.onNodeWithText("3.40").assertIsDisplayed()
    capture("portal-academic-card-en-light")
    compose.onNodeWithTag("portal_search").performTextInput("Algorithms")
    compose.onNodeWithTag("portal_row_0_0").performScrollTo().assertIsDisplayed()
    compose.onNodeWithText("Databases").assertDoesNotExist()
    capture("portal-academic-card-en-search")
  }

  @Test fun remainingCoursesDisplayVerifiedEmptyInformation() {
    render(PortalModule.REMAINING, PortalContent(title = "Courses to take"), status = "EMPTY")
    compose.onNodeWithText("The university returned no information for this page.").assertIsDisplayed()
    compose.onNodeWithText("No courses have been completed").assertDoesNotExist()
    capture("portal-remaining-en-light")
  }

  @Test fun statementsExposeOnlyReadOnlySafeViewLinks() {
    var openedUrl = ""
    render(PortalModule.STATEMENTS, PortalContent(
      title = "განცხადებები",
      sections = listOf(PortalSection(columns = listOf("ნომერი", "სათაური", "თარიღი", "პასუხი", "კომენტარი"),
        rows = listOf(PortalRow(listOf("17", "Certificate request", "2026-10-02", "Completed", "Available"),
          links = listOf(PortalLink("View response", "https://classroom.btu.edu.ge/ge/student/me/statements/view/17/4"),
            PortalLink("Unsafe destination", "https://example.com/private")))))),
    ), onOpenSafeUrl = { openedUrl = it })
    compose.onNodeWithText("Certificate request").assertIsDisplayed()
    compose.onNodeWithText("Unsafe destination").assertDoesNotExist()
    compose.onNodeWithTag("portal_statement_0_0_0").performScrollTo().performClick()
    assertEquals("https://classroom.btu.edu.ge/ge/student/me/statements/view/17/4", openedUrl)
    capture("portal-statements-en-light")
  }

  @Test fun ratingDisplaysUniversityReportedAverageWithoutRecalculation() {
    render(PortalModule.RATING, PortalContent(
      title = "რეიტინგი",
      fields = listOf(PortalField("მიმდინარე სემესტრამდე გავლილი სასწავლო კურსების საშუალო ქულა", "88.75")),
    ))
    compose.onNodeWithText("88.75").assertIsDisplayed()
    compose.onNodeWithText("Estimated GPA").assertDoesNotExist()
    capture("portal-rating-en-light")
  }

  @Test fun unsuccessfulRefreshKeepsPriorTranscriptRowsVisible() {
    render(PortalModule.ACADEMIC_CARD, academicRecord, status = "UNSUPPORTED_STRUCTURE",
      outcome = SyncOutcome.UnsupportedStructure("Changed table"))
    compose.onNodeWithTag("portal_sync_notice").assertIsDisplayed()
    capture("portal-academic-card-en-failed-refresh")
    compose.onNodeWithText("Algorithms").performScrollTo().assertIsDisplayed()
    compose.onNodeWithText("95").assertIsDisplayed()
    capture("portal-academic-card-en-cached-rows")
  }

  @Test
  @Config(qualifiers = "w800dp-h1000dp-mdpi")
  fun academicRecordReflowsInGeorgianDarkTabletLayout() {
    render(PortalModule.ACADEMIC_CARD, academicRecord,
      language = AppLanguage.KA, theme = AppThemeMode.DARK)
    compose.onNodeWithText("3.40").assertIsDisplayed()
    compose.onNodeWithText("Algorithms").performScrollTo().assertIsDisplayed()
    capture("portal-academic-card-ka-dark-tablet")
  }

  private fun render(
    module: PortalModule,
    content: PortalContent,
    status: String = "SUCCESS",
    outcome: SyncOutcome? = null,
    language: AppLanguage = AppLanguage.EN,
    theme: AppThemeMode = AppThemeMode.LIGHT,
    onOpenSafeUrl: (String) -> Unit = {},
  ) {
    val cached = PortalCachedPage(content, updatedAt, updatedAt, status, null)
    compose.setContent {
      PortalTestTheme(language, theme) {
        NativePortalScreen(module = module, page = cached, isLoading = false,
          syncOutcome = outcome, onRefresh = {}, onOpenOfficial = {},
          onOpenSafeUrl = onOpenSafeUrl, onNavigateBack = {})
      }
    }
  }

  private fun capture(name: String) {
    compose.waitForIdle()
    val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
    val output = File("build/outputs/qa/$name.png")
    output.parentFile?.mkdirs()
    output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
  }

  companion object {
    private val updatedAt = System.currentTimeMillis() - 180_000L
    private val academicRecord = PortalContent(
      title = "სასწავლო ბარათი",
      fields = listOf(PortalField("GPA", "3.40"), PortalField("საშუალო ქულა", "90.50")),
      sections = listOf(PortalSection(title = "პირველი სემესტრი 2025-2026",
        columns = listOf("#", "კურსის დასახელება", "კრედიტი", "ქულა"),
        rows = listOf(PortalRow(listOf("1", "Algorithms", "6", "95")),
          PortalRow(listOf("2", "Databases", "6", "86"))))),
    )
  }
}

@Composable
private fun PortalTestTheme(language: AppLanguage, theme: AppThemeMode, content: @Composable () -> Unit) {
  val baseContext = LocalContext.current
  val localized = remember(baseContext, language) { LocaleHelper.createLocalizedContext(baseContext, language) }
  CompositionLocalProvider(LocalContext provides localized,
    LocalConfiguration provides localized.resources.configuration) {
    BtucmTheme(themeMode = theme) {
      Surface(color = MaterialTheme.colorScheme.background, content = content)
    }
  }
}
