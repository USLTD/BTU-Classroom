package ge.usltd.btu_classroom.core.portal

import ge.usltd.btu_classroom.core.schedule.ScheduleEntry
import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
enum class PortalModule(val path: String) {
  SCHEDULE("/ge/student/me/schedule"),
  EXAMS("/ge/exams/list"),
  PROGRAM("/ge/student/me/program"),
  CALENDAR("/ge/student/me/calendar"),
  ACADEMIC_CARD("/ge/student/card"),
  REMAINING("/ge/training/list"),
  STATEMENTS("/ge/student/me/statements"),
  RATING("/ge/student/rating");

  val id: String get() = name.lowercase(Locale.ROOT)
}

@Serializable
data class PortalField(val label: String, val value: String)

@Serializable
data class PortalLink(val title: String, val url: String)

@Serializable
data class PortalRow(
  val values: List<String>,
  val links: List<PortalLink> = emptyList(),
)

@Serializable
data class PortalSection(
  val title: String? = null,
  val columns: List<String> = emptyList(),
  val rows: List<PortalRow> = emptyList(),
)

@Serializable
data class PortalCalendarEvent(
  val date: String,
  val title: String,
  val endDate: String? = null,
)

@Serializable
data class PortalContent(
  val title: String,
  val sections: List<PortalSection> = emptyList(),
  val fields: List<PortalField> = emptyList(),
  val schedule: List<ScheduleEntry> = emptyList(),
  val calendarEvents: List<PortalCalendarEvent> = emptyList(),
  val notices: List<String> = emptyList(),
)

data class PortalCachedPage(
  val content: PortalContent?,
  val lastSuccessAtMillis: Long?,
  val lastAttemptAtMillis: Long?,
  val status: String,
  val message: String?,
)
