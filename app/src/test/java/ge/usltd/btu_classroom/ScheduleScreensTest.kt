package ge.usltd.btu_classroom

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.provider.CalendarContract
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import ge.usltd.btu_classroom.core.database.SyncOutcome
import ge.usltd.btu_classroom.core.portal.PortalCachedPage
import ge.usltd.btu_classroom.core.portal.PortalContent
import ge.usltd.btu_classroom.core.preferences.AppLanguage
import ge.usltd.btu_classroom.core.preferences.AppThemeMode
import ge.usltd.btu_classroom.core.preferences.LocaleHelper
import ge.usltd.btu_classroom.core.schedule.ScheduleEntry
import ge.usltd.btu_classroom.design.theme.BtucmTheme
import ge.usltd.btu_classroom.feature.schedule.ScheduleScreen
import ge.usltd.btu_classroom.feature.schedule.scheduleCalendarIntent
import java.io.File
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScheduleScreensTest {
  @get:Rule val compose = createComposeRule()

  @Test fun dayViewShowsOnlyTheSelectedUniversityDay() {
    compose.setContent {
      ScheduleTestTheme {
        ScheduleScreen(onOpenPortalSchedule = {}, page = cachedPage, initialDate = monday)
      }
    }

    scrollTo(classTag(mondayClass, monday))
    compose.onNodeWithText(mondayClass.courseTitle).assertIsDisplayed()
    compose.onNodeWithText(mondayClass.startTime, substring = true).assertIsDisplayed()
    compose.onNodeWithText(mondayClass.endTime, substring = true).assertIsDisplayed()
    compose.onNodeWithText(mondayClass.room!!, substring = true).assertIsDisplayed()
    compose.onNodeWithText(mondayClass.lecturers!!, substring = true).assertIsDisplayed()
    compose.onNodeWithText(tuesdayClass.courseTitle).assertDoesNotExist()
    capture("schedule-day-en-light")

    scrollTo("schedule_next")
    compose.onNodeWithTag("schedule_next").performClick()
    scrollTo(classTag(tuesdayClass, monday.plusDays(1)))
    compose.onNodeWithText(tuesdayClass.courseTitle).assertIsDisplayed()
    compose.onNodeWithText(mondayClass.courseTitle).assertDoesNotExist()
    capture("schedule-day-next")

    scrollTo("schedule_previous")
    compose.onNodeWithTag("schedule_previous").performClick()
    scrollTo(classTag(mondayClass, monday))
    compose.onNodeWithText(mondayClass.courseTitle).assertIsDisplayed()
  }

  @Test fun weekViewGroupsRealEntriesAndMovesByWholeWeeks() {
    compose.setContent {
      ScheduleTestTheme {
        ScheduleScreen(onOpenPortalSchedule = {}, page = cachedPage, initialDate = monday)
      }
    }

    compose.onNodeWithTag("schedule_mode_week").performClick()
    scrollTo(classTag(mondayClass, monday))
    compose.onNodeWithTag(classTag(mondayClass, monday)).assertIsDisplayed()
    scrollTo(classTag(tuesdayClass, monday.plusDays(1)))
    compose.onNodeWithTag(classTag(tuesdayClass, monday.plusDays(1))).assertIsDisplayed()
    capture("schedule-week-en-light")

    scrollTo("schedule_next")
    compose.onNodeWithTag("schedule_next").performClick()
    scrollTo(classTag(mondayClass, monday.plusWeeks(1)))
    compose.onNodeWithTag(classTag(mondayClass, monday.plusWeeks(1))).assertIsDisplayed()
    compose.onNodeWithTag(classTag(mondayClass, monday)).assertDoesNotExist()
    capture("schedule-week-next")
  }

  @Test fun reminderAndCalendarActionsUseTheSelectedActualOccurrence() {
    var reminder: Pair<ScheduleEntry, LocalDate>? = null
    var exported: Pair<ScheduleEntry, LocalDate>? = null
    compose.setContent {
      ScheduleTestTheme {
        ScheduleScreen(
          onOpenPortalSchedule = {},
          page = cachedPage,
          initialDate = monday,
          onRequestReminder = { entry, date -> reminder = entry to date },
          onExportCalendar = { entry, date -> exported = entry to date },
        )
      }
    }

    val reminderTag = "schedule_reminder_${mondayClass.id}_$monday"
    scrollTo(reminderTag)
    compose.onNodeWithTag(reminderTag).performClick()
    assertEquals(mondayClass to monday, reminder)

    val calendarTag = "schedule_calendar_${mondayClass.id}_$monday"
    scrollTo(calendarTag)
    compose.onNodeWithTag(calendarTag).performClick()
    assertEquals(mondayClass to monday, exported)
    capture("schedule-occurrence-actions")
  }

  @Test fun refreshFailurePreservesCachedUniversityClasses() {
    compose.setContent {
      ScheduleTestTheme {
        ScheduleScreen(
          onOpenPortalSchedule = {},
          page = cachedPage.copy(status = "OFFLINE"),
          initialDate = monday,
          syncOutcome = SyncOutcome.OfflineOrFailed("No connection", isOffline = true),
        )
      }
    }

    scrollTo(classTag(mondayClass, monday))
    compose.onNodeWithText(mondayClass.courseTitle).assertIsDisplayed()
    compose.onNodeWithText(mondayClass.notes!!, substring = true).assertIsDisplayed()
    capture("schedule-cached-offline")
  }

  @Test fun refreshingKeepsCachedClassesAndPreventsDuplicateRefreshes() {
    compose.setContent {
      ScheduleTestTheme {
        ScheduleScreen(onOpenPortalSchedule = {}, page = cachedPage,
          isLoading = true, initialDate = monday)
      }
    }

    compose.onNodeWithTag("schedule_refresh").assertIsNotEnabled()
    scrollTo(classTag(mondayClass, monday))
    compose.onNodeWithText(mondayClass.courseTitle).assertIsDisplayed()
    capture("schedule-refreshing")
  }

  @Test fun unsynchronizedScheduleDoesNotClaimThereAreNoClasses() {
    compose.setContent {
      ScheduleTestTheme {
        ScheduleScreen(onOpenPortalSchedule = {}, initialDate = monday)
      }
    }

    compose.onNodeWithText("Not synchronized yet", substring = true).assertIsDisplayed()
    compose.onNodeWithText("No classes on this day").assertDoesNotExist()
    capture("schedule-unsynchronized")
  }

  @Test fun confirmedEmptySnapshotHasItsOwnEmptyDayState() {
    compose.setContent {
      ScheduleTestTheme {
        ScheduleScreen(onOpenPortalSchedule = {}, initialDate = monday,
          page = cachedPage.copy(content = PortalContent(title = "ჩემი ცხრილი"), status = "EMPTY"))
      }
    }

    compose.onNodeWithText("No classes on this day").assertIsDisplayed()
    compose.onNodeWithText("Not synchronized yet", substring = true).assertDoesNotExist()
    capture("schedule-confirmed-empty")
  }

  @Test fun calendarIntentUsesTheActualOccurrenceInTbilisi() {
    val intent = requireNotNull(scheduleCalendarIntent(mondayClass, monday))
    assertEquals(Intent.ACTION_INSERT, intent.action)
    assertEquals(CalendarContract.Events.CONTENT_URI, intent.data)
    assertEquals(mondayClass.courseTitle, intent.getStringExtra(CalendarContract.Events.TITLE))
    assertEquals(mondayClass.room, intent.getStringExtra(CalendarContract.Events.EVENT_LOCATION))
    assertEquals("Asia/Tbilisi", intent.getStringExtra(CalendarContract.Events.EVENT_TIMEZONE))
    assertEquals(Instant.parse("2026-11-16T05:30:00Z").toEpochMilli(),
      intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, -1L))
    assertEquals(Instant.parse("2026-11-16T07:00:00Z").toEpochMilli(),
      intent.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, -1L))
  }

  @Test fun calendarIntentRejectsAnUnmatchedDayOrInvalidUniversityTime() {
    assertNull(scheduleCalendarIntent(mondayClass, monday.plusDays(1)))
    assertNull(scheduleCalendarIntent(mondayClass.copy(startTime = "29:30"), monday))
    assertNull(scheduleCalendarIntent(mondayClass.copy(endTime = "09:00"), monday))
  }

  @Test fun officialPortalAndExamFallbackKeepTheirConfirmedRoutes() {
    var target = ""
    compose.setContent {
      ScheduleTestTheme {
        ScheduleScreen(onOpenPortalSchedule = { target = it }, initialDate = monday)
      }
    }

    scrollTo("schedule_official_portal")
    compose.onNodeWithTag("schedule_official_portal").performClick()
    assertEquals("https://classroom.btu.edu.ge/ge/student/me/schedule", target)
    scrollTo("schedule_exams")
    compose.onNodeWithTag("schedule_exams").performClick()
    assertEquals("https://classroom.btu.edu.ge/ge/exams/list", target)
  }

  @Test fun nativeExamNavigationTakesPrecedenceOverBrowserFallback() {
    var examOpened = false
    var portalOpened = false
    compose.setContent {
      ScheduleTestTheme {
        ScheduleScreen(onOpenPortalSchedule = { portalOpened = true }, initialDate = monday,
          onOpenExams = { examOpened = true })
      }
    }

    scrollTo("schedule_exams")
    compose.onNodeWithTag("schedule_exams").performClick()
    assertEquals(true, examOpened)
    assertEquals(false, portalOpened)
  }

  @Test fun georgianDarkModeKeepsOriginalUniversityText() {
    compose.setContent {
      ScheduleTestTheme(AppLanguage.KA, AppThemeMode.DARK) {
        ScheduleScreen(onOpenPortalSchedule = {}, page = cachedPage, initialDate = monday)
      }
    }

    scrollTo(classTag(mondayClass, monday))
    compose.onNodeWithText(mondayClass.courseTitle).assertIsDisplayed()
    capture("schedule-day-ka-dark")
  }

  private fun scrollTo(tag: String) {
    compose.onNodeWithTag("schedule_content").performScrollToNode(hasTestTag(tag))
  }

  private fun capture(name: String) {
    compose.waitForIdle()
    val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
    val output = File("build/outputs/qa/$name.png")
    output.parentFile?.mkdirs()
    output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
  }

  companion object {
    private val monday = LocalDate.of(2026, 11, 16)
    private val mondayClass = ScheduleEntry(
      id = "monday-lecture",
      dayOfWeek = 1,
      startTime = "09:30",
      endTime = "11:00",
      courseTitle = "მონაცემთა სტრუქტურები",
      room = "Auditorium 204",
      group = "CS-A",
      lecturers = "Fixture lecturer",
      notes = "Lecture",
    )
    private val tuesdayClass = ScheduleEntry(
      id = "tuesday-seminar",
      dayOfWeek = 2,
      startTime = "13:00",
      endTime = "14:30",
      courseTitle = "Mobile application development",
    )
    private val snapshotTime = Instant.parse("2026-11-16T06:00:00Z").toEpochMilli()
    private val cachedPage = PortalCachedPage(
      content = PortalContent(title = "ჩემი ცხრილი", schedule = listOf(mondayClass, tuesdayClass)),
      lastSuccessAtMillis = snapshotTime,
      lastAttemptAtMillis = snapshotTime,
      status = "SUCCESS",
      message = null,
    )

    private fun classTag(entry: ScheduleEntry, date: LocalDate) = "schedule_class_${entry.id}_$date"
  }
}

@Composable
private fun ScheduleTestTheme(
  language: AppLanguage = AppLanguage.EN,
  theme: AppThemeMode = AppThemeMode.LIGHT,
  content: @Composable () -> Unit,
) {
  val baseContext = LocalContext.current
  val localized = remember(baseContext, language) {
    LocaleHelper.createLocalizedContext(baseContext, language)
  }
  CompositionLocalProvider(
    LocalContext provides localized,
    LocalConfiguration provides localized.resources.configuration,
  ) {
    BtucmTheme(themeMode = theme) {
      Surface(color = MaterialTheme.colorScheme.background, content = content)
    }
  }
}
