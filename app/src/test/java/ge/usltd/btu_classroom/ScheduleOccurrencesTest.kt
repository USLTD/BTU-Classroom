package ge.usltd.btu_classroom

import ge.usltd.btu_classroom.core.schedule.ScheduleEntry
import ge.usltd.btu_classroom.core.schedule.ScheduleOccurrences
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduleOccurrencesTest {
  private val monday = ScheduleEntry(
    id = "fixture-monday-0900",
    dayOfWeek = 1,
    startTime = "09:00",
    endTime = "09:50",
    courseTitle = "Sanitized course",
  )

  @Test
  fun upcomingClassUsesTbilisiInsteadOfTheDeviceZone() {
    val occurrence = requireNotNull(ScheduleOccurrences.next(monday, Instant.parse("2026-11-16T04:59:00Z")))
    assertEquals(Instant.parse("2026-11-16T05:00:00Z"), occurrence.start)
    assertEquals(Instant.parse("2026-11-16T05:50:00Z"), occurrence.end)
  }

  @Test
  fun currentClassRemainsTheNextOccurrenceThroughItsExclusiveEnd() {
    val atStart = requireNotNull(ScheduleOccurrences.next(monday, Instant.parse("2026-11-16T05:00:00Z")))
    val during = requireNotNull(ScheduleOccurrences.next(monday, Instant.parse("2026-11-16T05:30:00Z")))
    assertEquals(atStart, during)
    assertEquals(Instant.parse("2026-11-16T05:00:00Z"), during.start)
  }

  @Test
  fun exactEndAndPastClassesAdvanceToTheFollowingWeek() {
    val atEnd = requireNotNull(ScheduleOccurrences.next(monday, Instant.parse("2026-11-16T05:50:00Z")))
    val followingDay = requireNotNull(ScheduleOccurrences.next(monday, Instant.parse("2026-11-17T08:00:00Z")))
    assertEquals(Instant.parse("2026-11-23T05:00:00Z"), atEnd.start)
    assertEquals(atEnd, followingDay)
  }

  @Test
  fun selectedDateMustMatchTheServerWeekday() {
    assertNull(ScheduleOccurrences.onDate(monday, LocalDate.parse("2026-11-17")))
    val occurrence = requireNotNull(ScheduleOccurrences.onDate(monday, LocalDate.parse("2026-11-16")))
    assertEquals(Instant.parse("2026-11-16T05:00:00Z"), occurrence.start)
  }

  @Test
  fun explicitlyProvidedZoneControlsTheResultWithoutGlobalLocaleOrZoneChanges() {
    val occurrence = requireNotNull(
      ScheduleOccurrences.onDate(monday, LocalDate.parse("2026-11-16"), ZoneId.of("UTC")),
    )
    assertEquals(Instant.parse("2026-11-16T09:00:00Z"), occurrence.start)
    assertEquals("Asia/Tbilisi", ScheduleOccurrences.UNIVERSITY_ZONE.id)
  }

  @Test
  fun invalidTimesOrWeekdaysNeverCreateInventedOccurrences() {
    val date = LocalDate.parse("2026-11-16")
    for (entry in listOf(
      monday.copy(dayOfWeek = 0),
      monday.copy(dayOfWeek = 8),
      monday.copy(startTime = "9:00"),
      monday.copy(startTime = "24:00"),
      monday.copy(startTime = "09:50"),
      monday.copy(endTime = "08:00"),
    )) {
      assertNull(ScheduleOccurrences.onDate(entry, date))
      assertNull(ScheduleOccurrences.next(entry, Instant.parse("2026-11-16T05:00:00Z")))
    }
  }

  @Test
  fun daylightSavingGapsDoNotShiftAReportedClassToAnotherTime() {
    val sunday = monday.copy(dayOfWeek = 7, startTime = "02:30", endTime = "03:30")
    assertNull(ScheduleOccurrences.onDate(sunday, LocalDate.parse("2026-03-29"), ZoneId.of("Europe/Berlin")))
  }
}
