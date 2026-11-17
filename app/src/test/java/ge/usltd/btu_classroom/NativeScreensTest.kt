package ge.usltd.btu_classroom

import android.app.Application
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import ge.usltd.btu_classroom.core.auth.AuthState
import ge.usltd.btu_classroom.core.auth.AuthTelemetryState
import ge.usltd.btu_classroom.core.database.SyncOutcome
import ge.usltd.btu_classroom.core.database.CachedCourseResourceDto
import ge.usltd.btu_classroom.core.portal.PortalCachedPage
import ge.usltd.btu_classroom.core.portal.PortalContent
import ge.usltd.btu_classroom.core.portal.PortalModule
import ge.usltd.btu_classroom.core.schedule.ScheduleEntry
import ge.usltd.btu_classroom.core.database.entities.CourseEntity
import ge.usltd.btu_classroom.core.database.entities.CourseWithEnrollment
import ge.usltd.btu_classroom.core.database.entities.EnrollmentEntity
import ge.usltd.btu_classroom.core.database.entities.GradeEntity
import ge.usltd.btu_classroom.core.database.entities.SemesterEntity
import ge.usltd.btu_classroom.core.database.entities.SyncMetadataEntity
import ge.usltd.btu_classroom.core.preferences.AppLanguage
import ge.usltd.btu_classroom.core.preferences.AppThemeMode
import ge.usltd.btu_classroom.core.preferences.LocaleHelper
import ge.usltd.btu_classroom.core.preferences.UserPreferences
import ge.usltd.btu_classroom.design.components.BadgeTone
import ge.usltd.btu_classroom.design.components.EmptyStateCard
import ge.usltd.btu_classroom.design.components.FreshnessBanner
import ge.usltd.btu_classroom.design.components.StatusBadge
import ge.usltd.btu_classroom.design.theme.BtucmTheme
import ge.usltd.btu_classroom.feature.courses.CourseDetailScreen
import ge.usltd.btu_classroom.feature.courses.CoursesScreen
import ge.usltd.btu_classroom.feature.diagnostics.DiagnosticsScreen
import ge.usltd.btu_classroom.feature.home.HomeScreen
import ge.usltd.btu_classroom.feature.more.MoreScreen
import ge.usltd.btu_classroom.feature.onboarding.OnboardingScreen
import ge.usltd.btu_classroom.feature.schedule.ScheduleScreen
import ge.usltd.btu_classroom.feature.schedule.ClassReminderDialog
import ge.usltd.btu_classroom.feature.settings.SettingsScreen
import java.io.File
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NativeScreensTest {
  @get:Rule val compose = createComposeRule()

  @Test fun onboardingSupportsExplorationAndRuntimeLanguageChanges() {
    var explored = false
    compose.setContent {
      var preferences by remember { mutableStateOf(UserPreferences(language = AppLanguage.EN)) }
      LocalizedTheme(preferences) {
        OnboardingScreen(AuthState.SignedOut, AuthTelemetryState(), preferences,
          onSignInClick = {}, onResumeBrowserClick = {}, onOpenExternalBrowser = {},
          onRememberLoginChanged = { preferences = preferences.copy(rememberLogin = it) },
          onLanguageChanged = { preferences = preferences.copy(language = it) },
          onExploreApp = { explored = true })
      }
    }
    compose.onNodeWithTag("sign_in_button").assertIsDisplayed()
    capture("onboarding-en-light")
    compose.onNodeWithTag("explore_app_button").performScrollTo().performClick()
    assertEquals(true, explored)
    compose.onNodeWithTag("lang_ka_chip").performScrollTo().performClick()
    compose.onNodeWithText("შენი საუნივერსიტეტო დღე ერთ სივრცეში.").assertIsDisplayed()
    capture("onboarding-ka-light")
  }

  @Test fun selectedCoursesSupportSearchAndNullableGrades() {
    compose.setContent {
      LocalizedTheme {
        CoursesScreen(listOf(semester), listOf(graded, ungraded), metadata, null,
          false, true, {}, { _, _ -> })
      }
    }
    compose.onNodeWithText("92 pts").assertIsDisplayed()
    compose.onNodeWithText("Ungraded (—)").assertIsDisplayed()
    capture("courses-en-offline")
    compose.onNodeWithTag("courses_search").performTextInput("CS301")
    compose.onNodeWithTag("course_card_501").assertIsDisplayed()
    compose.onNodeWithTag("course_card_502").assertDoesNotExist()
    capture("courses-en-search")
  }

  @Test fun unsynchronizedCoursesDoNotClaimUniversityReturnedEmptyData() {
    compose.setContent {
      LocalizedTheme {
        CoursesScreen(emptyList(), emptyList(), null, null, false, false, {}, { _, _ -> })
      }
    }
    compose.onNodeWithText("Your courses belong here").assertIsDisplayed()
    compose.onNodeWithText("No courses listed").assertDoesNotExist()
    capture("courses-en-unsynchronized")
  }

  @Test fun verifiedEmptyCoursesRemainAnExplicitServerResult() {
    compose.setContent {
      LocalizedTheme {
        CoursesScreen(emptyList(), emptyList(), metadata.copy(outcomeStatus = "EMPTY"),
          null, false, false, {}, { _, _ -> })
      }
    }
    compose.onNodeWithText("No courses listed").assertIsDisplayed()
    capture("courses-en-empty")
  }

  @Test fun courseDetailsDisplayOfficialGradeCategories() {
    compose.setContent {
      LocalizedTheme {
        CourseDetailScreen(graded, listOf(GradeEntity("g1", "k1", "default_student",
          semester.semesterKey, "501", "Midterm", 24.0, 30.0, null, 0)),
          emptyList(), false, false, {}, {}, {})
      }
    }
    compose.onNodeWithText("Algorithms and data structures").assertIsDisplayed()
    capture("course-detail-en-light")
    compose.onNodeWithText("Midterm").performScrollTo().assertIsDisplayed()
    capture("course-detail-en-grades")
  }

  @Test
  @Config(qualifiers = "w360dp-h800dp-mdpi")
  fun georgianCourseDetailsKeepLongMetadataReadableWithLargeText() {
    val semesterName = "2026–2027 სასწავლო წლის შემოდგომის სემესტრი — საინფორმაციო ტექნოლოგიები"
    val lecturer = "პროფესორი ალექსანდრე კონსტანტინეპოლელი — კომპიუტერული მეცნიერებების დეპარტამენტი"
    val course = graded.copy(course = graded.course.copy(
      title = "ალგორითმები და მონაცემთა სტრუქტურები",
      semesterName = semesterName,
      lecturer = lecturer,
    ))
    var navigatedBack = false
    compose.setContent {
      LocalizedTheme(UserPreferences(language = AppLanguage.KA), fontScale = 2f) {
        CourseDetailScreen(course, emptyList(), emptyList(), false, false, {}, {},
          onNavigateBack = { navigatedBack = true })
      }
    }
    compose.onNodeWithText(course.course.title).assertIsDisplayed()
    capture("course-detail-ka-large-font-header")
    assertFullTextReadableAfterScroll(semesterName)
    capture("course-detail-ka-large-font-semester")
    assertFullTextReadableAfterScroll(lecturer)
    capture("course-detail-ka-large-font-metadata")
    compose.onNodeWithTag("course_detail_back_button").assertIsDisplayed().performClick()
    assertEquals(true, navigatedBack)
  }

  @Test fun homePresentsOnlySourceBasedSummary() {
    compose.setContent {
      LocalizedTheme {
        HomeScreen(null, listOf(semester), listOf(graded, ungraded), metadata, false,
          false, {}, {}, { _, _ -> }, {})
      }
    }
    compose.onNodeWithText("2 courses").assertIsDisplayed()
    compose.onNodeWithText("Local average: 92").assertIsDisplayed()
    capture("home-en-light")
  }

  @Test fun failedDetailRefreshKeepsGradesAndExplainsTheirSavedTimestamp() {
    compose.setContent {
      LocalizedTheme {
        CourseDetailScreen(
          courseWithEnrollment = graded.copy(course = graded.course.copy(detailSyncedAtMillis = fixtureUpdatedAt)),
          grades = listOf(GradeEntity("g1", "k1", "default_student", semester.semesterKey,
            "501", "Midterm", 24.0, 30.0, null, 0)),
          resources = emptyList(), isLoading = false, isOffline = false,
          onSyncDetail = {}, onOpenSafeUrl = {}, onNavigateBack = {},
          syncOutcome = SyncOutcome.UnsupportedStructure("Unrecognized grade table"),
        )
      }
    }
    compose.onNodeWithTag("course_detail_refresh_notice").assertIsDisplayed()
    compose.onNodeWithText("The university page changed. Details could not be refreshed.").assertIsDisplayed()
    compose.onNodeWithText("Showing saved details from", substring = true).assertIsDisplayed()
    capture("course-detail-en-failed-refresh")
    compose.onNodeWithText("Midterm").performScrollTo().assertIsDisplayed()
    compose.onNodeWithText("24 / 30").assertIsDisplayed()
    capture("course-detail-en-retained-grades")
  }

  @Test fun timetableAndExamsOpenTheirConfirmedOfficialPages() {
    var target = ""
    compose.setContent {
      LocalizedTheme { ScheduleScreen(onOpenPortalSchedule = { target = it }) }
    }
    compose.onNodeWithText("Examinations").assertIsDisplayed()
    capture("schedule-en-light")
    compose.onNodeWithTag("schedule_exams").performScrollTo().performClick()
    assertEquals("https://classroom.btu.edu.ge/ge/exams/list", target)
  }

  @Test fun moreProvidesAcademicLinksAndSettings() {
    var settingsOpened = false
    var portalTarget = ""
    var nativeModule: PortalModule? = null
    compose.setContent {
      LocalizedTheme {
        MoreScreen({ settingsOpened = true }, {}, {}, {}, onOpenPortal = { portalTarget = it },
          onOpenModule = { nativeModule = it })
      }
    }
    compose.onNodeWithTag("more_settings_card").performClick()
    assertEquals(true, settingsOpened)
    compose.onNodeWithTag("more_portal_program").performClick()
    assertEquals(PortalModule.PROGRAM, nativeModule)
    assertEquals("", portalTarget)
    capture("more-en-light")
    compose.onNodeWithText("Statements").performScrollTo().assertIsDisplayed()
    capture("more-en-academic-links")
    compose.onNodeWithTag("more_portal_registration").performScrollTo().performClick()
    assertEquals("https://classroom.btu.edu.ge/en/", portalTarget)
    capture("more-en-registration")
  }

  @Test fun homeUsesTheSavedWeeklyPatternAndLabelsOfficialGpa() {
    val schedule = PortalCachedPage(PortalContent("Weekly timetable", schedule = listOf(
      ScheduleEntry("weekly-seminar", 2, "11:00", "12:00", "Verified weekly seminar", "A204"))),
      fixtureUpdatedAt, fixtureUpdatedAt, "SUCCESS", null)
    compose.setContent {
      LocalizedTheme {
        HomeScreen(null, listOf(semester), listOf(graded), metadata, false, false, {}, {},
          { _, _ -> }, {}, schedulePage = schedule, officialGpa = "3.47")
      }
    }
    compose.onNodeWithText("Official GPA: 3.47").assertIsDisplayed()
    compose.onNodeWithText("Verified weekly seminar").performScrollTo().assertIsDisplayed()
    compose.onNodeWithText("Weekly pattern only.", substring = true).assertIsDisplayed()
    capture("home-en-weekly-pattern")
  }

  @Test fun resourcesDownloadOnlyAfterAnExplicitTapAndShowLoading() {
    val resource = CachedCourseResourceDto("Sample syllabus PDF", "https://classroom.btu.edu.ge/uploads/fixture.pdf")
    var requestedUrl: String? = null
    compose.setContent {
      var downloading by remember { mutableStateOf<String?>(null) }
      LocalizedTheme {
        CourseDetailScreen(graded, emptyList(), listOf(resource), false, false, {}, {}, {},
          onDownloadResource = { requestedUrl = it.url; downloading = it.url },
          downloadingResourceUrl = downloading)
      }
    }
    assertEquals(null, requestedUrl)
    compose.onNodeWithTag("course_resource_0").performScrollTo().performClick()
    assertEquals(resource.url, requestedUrl)
    capture("course-detail-en-file-loading")
  }

  @Test fun classReminderRequiresConfirmationOfTheSelectedDate() {
    var confirmed = false
    compose.setContent {
      LocalizedTheme {
        ClassReminderDialog(ScheduleEntry("class", 2, "11:00", "12:00", "Sample seminar"),
          LocalDate.of(2026, 11, 24), onConfirm = { confirmed = true }, onDismiss = {})
      }
    }
    assertEquals(false, confirmed)
    compose.onNodeWithText("15 minutes", substring = true).assertIsDisplayed()
    capture("schedule-en-reminder-confirmation", "class_reminder_dialog")
    compose.onNodeWithTag("class_reminder_confirm").performClick()
    assertEquals(true, confirmed)
  }

  @Test fun settingsApplyDarkAppearanceAndGeorgianWithoutRestart() {
    compose.setContent {
      var preferences by remember { mutableStateOf(UserPreferences(language = AppLanguage.EN,
        themeMode = AppThemeMode.LIGHT)) }
      LocalizedTheme(preferences) {
        SettingsScreen(preferences,
          onLanguageChanged = { preferences = preferences.copy(language = it) },
          onThemeModeChanged = { preferences = preferences.copy(themeMode = it) },
          onDynamicColorChanged = { preferences = preferences.copy(dynamicColor = it) },
          onRememberLoginChanged = { preferences = preferences.copy(rememberLogin = it) },
          onRequestSignOut = {}, onRequestClearLocalData = {}, onNavigateBack = {})
      }
    }
    capture("settings-en-light")
    compose.onNodeWithTag("settings_theme_dark").performScrollTo().performClick()
    capture("settings-en-dark")
    compose.onNodeWithTag("settings_lang_ka").performScrollTo().performClick()
    capture("settings-ka-dark")
  }

  @Test
  @Config(qualifiers = "w360dp-h800dp-mdpi")
  fun georgianSettingsKeepEndActionsAndBackNavigationReachableWithLargeText() {
    var signOutRequested = false
    var clearDataRequested = false
    var navigatedBack = false
    compose.setContent {
      val preferences = UserPreferences(language = AppLanguage.KA)
      LocalizedTheme(preferences, fontScale = 2f) {
        SettingsScreen(preferences, onLanguageChanged = {}, onThemeModeChanged = {},
          onDynamicColorChanged = {}, onRememberLoginChanged = {},
          onRequestSignOut = { signOutRequested = true },
          onRequestClearLocalData = { clearDataRequested = true },
          onNavigateBack = { navigatedBack = true })
      }
    }
    capture("settings-ka-large-font-header")
    compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_sign_out_button"))
    compose.onNodeWithTag("settings_sign_out_button").assertIsDisplayed().performClick()
    assertEquals(true, signOutRequested)
    compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_clear_data_button"))
    compose.onNodeWithTag("settings_clear_data_button").assertIsDisplayed().performClick()
    assertEquals(true, clearDataRequested)
    capture("settings-ka-large-font-actions")
    compose.onNodeWithTag("settings_back_button").assertIsDisplayed().performClick()
    assertEquals(true, navigatedBack)
  }

  @Test fun reusablePrimitivesRenderEachStatusWithText() {
    compose.setContent {
      LocalizedTheme {
        Column(Modifier.padding(16.dp)) {
          BadgeTone.entries.forEach { tone -> StatusBadge(tone.name, Icons.Default.Info, tone) }
          EmptyStateCard(Icons.Default.Info, "No saved information", "Connect your account to begin.")
        }
      }
    }
    capture("primitives-en-light")
  }

  @Test fun oldLastSyncUsesAStableAbsoluteTbilisiTime() {
    val oldTimestamp = Instant.parse("2026-01-15T08:30:00Z").toEpochMilli()
    compose.setContent {
      LocalizedTheme {
        FreshnessBanner(metadata.copy(lastSuccessAtMillis = oldTimestamp), false, false)
      }
    }
    compose.onNodeWithText("12:30 (Tbilisi)", substring = true).assertIsDisplayed()
    compose.onNodeWithText("Updated just now").assertDoesNotExist()
    capture("freshness-en-absolute-time")
  }

  @Test fun debugDiagnosticsRenderWithoutPrivateSessionValues() {
    compose.setContent {
      LocalizedTheme {
        DiagnosticsScreen(AuthState.SignedOut, AuthTelemetryState(), emptyMap(), {}, {}, {}, {}, {})
      }
    }
    capture("diagnostics-en-light")
  }

  @Test
  @Config(qualifiers = "w800dp-h1000dp-mdpi")
  fun coursesReflowWithinReadableTabletWidth() {
    compose.setContent {
      LocalizedTheme {
        CoursesScreen(listOf(semester), listOf(graded, ungraded), metadata, null,
          false, false, {}, { _, _ -> })
      }
    }
    compose.onNodeWithText("92 pts").assertIsDisplayed()
    capture("courses-en-tablet")
  }

  private fun assertFullTextReadableAfterScroll(text: String) {
    val node = compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
      .assertWidthIsAtLeast(280.dp)
    val layouts = mutableListOf<TextLayoutResult>()
    node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
    assertEquals(1, layouts.size)
    assertEquals(text, layouts.single().layoutInput.text.text)
    assertFalse("Full metadata value must fit its text layout", layouts.single().hasVisualOverflow)
  }

  private fun capture(name: String, tag: String? = null) {
    compose.waitForIdle()
    val node = if (tag == null) compose.onRoot() else compose.onNodeWithTag(tag)
    val bitmap = node.captureToImage().asAndroidBitmap()
    val output = File("build/outputs/qa/$name.png")
    output.parentFile.mkdirs()
    output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
  }

  companion object {
    private val semester = SemesterEntity("default_student::2026_fall", "2026_fall", "default_student",
      "Autumn 2026", 0, true)
    private val fixtureUpdatedAt = System.currentTimeMillis() - 180_000L
    private val metadata = SyncMetadataEntity("selected_courses", fixtureUpdatedAt, fixtureUpdatedAt, "SUCCESS", null)
    private val graded = CourseWithEnrollment(
      CourseEntity("k1", "501", "default_student", semester.semesterKey, semester.title,
        "CS301", "Algorithms and data structures", 6.0, 51.0, "Sample lecturer", null,
        "/ge/student/me/course/index/501", null, null, null),
      EnrollmentEntity("k1", "default_student", semester.semesterKey, "501", 92.0, null, 1L),
    )
    private val ungraded = graded.copy(course = graded.course.copy(courseKey = "k2", courseId = "502",
      code = "CS302", title = "მობილური აპლიკაციების დეველოპმენტი"),
      enrollment = graded.enrollment?.copy(enrollmentKey = "k2", courseId = "502", totalScore = null))
  }
}

@Composable
private fun LocalizedTheme(
  preferences: UserPreferences = UserPreferences(language = AppLanguage.EN, themeMode = AppThemeMode.LIGHT),
  fontScale: Float? = null,
  content: @Composable () -> Unit,
) {
  val baseContext = LocalContext.current
  val baseDensity = LocalDensity.current
  val localized = remember(baseContext, preferences.language) {
    LocaleHelper.createLocalizedContext(baseContext, preferences.language)
  }
  val configuration = remember(localized, fontScale) {
    Configuration(localized.resources.configuration).also {
      if (fontScale != null) it.fontScale = fontScale
    }
  }
  CompositionLocalProvider(LocalContext provides localized,
    LocalConfiguration provides configuration,
    LocalDensity provides Density(baseDensity.density, fontScale ?: baseDensity.fontScale)) {
    BtucmTheme(themeMode = preferences.themeMode) {
      Surface(color = MaterialTheme.colorScheme.background, content = content)
    }
  }
}
