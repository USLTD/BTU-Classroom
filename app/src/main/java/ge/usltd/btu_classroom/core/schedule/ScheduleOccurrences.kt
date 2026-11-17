package ge.usltd.btu_classroom.core.schedule

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class ScheduleOccurrence(
  val entry: ScheduleEntry,
  val start: Instant,
  val end: Instant,
)

object ScheduleOccurrences {
  val UNIVERSITY_ZONE: ZoneId = ZoneId.of("Asia/Tbilisi")

  fun onDate(
    entry: ScheduleEntry,
    date: LocalDate,
    zoneId: ZoneId = UNIVERSITY_ZONE,
  ): ScheduleOccurrence? {
    if (entry.dayOfWeek !in 1..7 || date.dayOfWeek.value != entry.dayOfWeek) return null
    val startTime = parseTime(entry.startTime) ?: return null
    val endTime = parseTime(entry.endTime) ?: return null
    if (!startTime.isBefore(endTime)) return null

    val start = date.atTime(startTime).atZone(zoneId)
    val end = date.atTime(endTime).atZone(zoneId)
    // A daylight-saving gap must not silently move a university-provided time.
    if (start.toLocalTime() != startTime || end.toLocalTime() != endTime || !start.isBefore(end)) return null
    return ScheduleOccurrence(entry, start.toInstant(), end.toInstant())
  }

  fun next(
    entry: ScheduleEntry,
    from: Instant,
    zoneId: ZoneId = UNIVERSITY_ZONE,
  ): ScheduleOccurrence? {
    if (entry.dayOfWeek !in 1..7) return null
    val today = from.atZone(zoneId).toLocalDate()
    val daysUntilClass = (entry.dayOfWeek - today.dayOfWeek.value + 7) % 7
    val date = today.plusDays(daysUntilClass.toLong())
    val candidate = onDate(entry, date, zoneId) ?: return null
    return if (from.isBefore(candidate.end)) candidate else onDate(entry, date.plusWeeks(1), zoneId)
  }

  private fun parseTime(value: String): LocalTime? {
    if (!TIME_FORMAT.matches(value)) return null
    return try {
      LocalTime.parse(value)
    } catch (_: DateTimeException) {
      null
    }
  }

  private val TIME_FORMAT = Regex("\\d{2}:\\d{2}")
}
