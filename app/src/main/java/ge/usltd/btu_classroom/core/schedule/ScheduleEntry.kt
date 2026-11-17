package ge.usltd.btu_classroom.core.schedule

import kotlinx.serialization.Serializable

@Serializable
data class ScheduleEntry(
  val id: String,
  val dayOfWeek: Int,
  val startTime: String,
  val endTime: String,
  val courseTitle: String,
  val room: String? = null,
  val group: String? = null,
  val lecturers: String? = null,
  val notes: String? = null,
)
