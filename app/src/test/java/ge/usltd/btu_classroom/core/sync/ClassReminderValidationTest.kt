package ge.usltd.btu_classroom.core.sync

import ge.usltd.btu_classroom.core.portal.PortalCachedPage
import ge.usltd.btu_classroom.core.portal.PortalContent
import ge.usltd.btu_classroom.core.schedule.ScheduleEntry
import ge.usltd.btu_classroom.core.schedule.ScheduleOccurrences
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassReminderValidationTest {
  private val entry = ScheduleEntry(
    id = "sanitized-class",
    dayOfWeek = 1,
    startTime = "10:00",
    endTime = "11:00",
    courseTitle = "Sanitized course",
    room = "A101",
  )
  private val selectedDate = LocalDate.of(2026, 11, 23)
  private val selectedStart = requireNotNull(ScheduleOccurrences.onDate(entry, selectedDate)).start
  private val beforeClass = selectedStart.minusSeconds(15 * 60)

  @Test
  fun matchingSelectedOccurrenceUsesTbilisiTime() {
    assertEquals(Instant.parse("2026-11-23T06:00:00Z"), selectedStart)
    assertTrue(matches(page(), selectedStart, beforeClass))
  }

  @Test
  fun classThatHasStartedDoesNotNotify() {
    assertFalse(matches(page(), selectedStart, selectedStart))
  }

  @Test
  fun pastClassDoesNotNotify() {
    assertFalse(matches(page(), selectedStart, selectedStart.plusSeconds(30)))
  }

  @Test
  fun missingUnreadableAndUnsynchronizedCachesDoNotNotify() {
    assertFalse(matches(null, selectedStart, beforeClass))
    assertFalse(matches(page().copy(content = null, status = "CACHE_UNREADABLE"), selectedStart, beforeClass))
    assertFalse(matches(page().copy(lastSuccessAtMillis = null), selectedStart, beforeClass))
  }

  @Test
  fun removedOrDifferentPublishedEntryDoesNotNotify() {
    assertFalse(matches(page(emptyList()), selectedStart, beforeClass))
    assertFalse(matches(page(listOf(entry.copy(id = "different-class"))), selectedStart, beforeClass))
  }

  @Test
  fun changedPublishedStartTimeInvalidatesOldReminder() {
    val revisedEntry = entry.copy(startTime = "10:30", endTime = "11:30")

    assertFalse(matches(page(listOf(revisedEntry)), selectedStart, beforeClass))
  }

  @Test
  fun selectedDateWithDifferentWeekdayDoesNotNotify() {
    val wrongWeekdayStart = selectedStart.plusSeconds(24 * 60 * 60)

    assertFalse(matches(page(), wrongWeekdayStart, beforeClass))
  }

  @Test
  fun oldReminderIsNotRolledIntoNextWeeksOccurrence() {
    val nextStart = requireNotNull(ScheduleOccurrences.onDate(entry, selectedDate.plusWeeks(1))).start
    val beforeNextClass = nextStart.minusSeconds(15 * 60)

    assertFalse(matches(page(), selectedStart, beforeNextClass))
    assertTrue(matches(page(), nextStart, beforeNextClass))
  }

  @Test
  fun invalidPublishedTimesDoNotNotify() {
    assertFalse(matches(page(listOf(entry.copy(startTime = "invalid"))), selectedStart, beforeClass))
    assertFalse(matches(page(listOf(entry.copy(endTime = "09:00"))), selectedStart, beforeClass))
  }

  private fun matches(cached: PortalCachedPage?, start: Instant, now: Instant): Boolean =
    ClassReminderValidation.matchesCachedOccurrence(cached, entry.id, start.toEpochMilli(), now)

  private fun page(entries: List<ScheduleEntry> = listOf(entry)): PortalCachedPage = PortalCachedPage(
    content = PortalContent(title = "Weekly schedule", schedule = entries),
    lastSuccessAtMillis = 1_000,
    lastAttemptAtMillis = 1_000,
    status = "SUCCESS",
    message = null,
  )
}
