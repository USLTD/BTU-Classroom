package ge.usltd.btu_classroom.core.parsing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface BtuPageParser<T> {
  fun parse(html: String): ParseResult<T>
}

sealed interface ParseResult<out T> {
  data class Success<T>(val data: T) : ParseResult<T>
  data object Empty : ParseResult<Nothing>
  data object TemporarilyUnavailable : ParseResult<Nothing>
  data object SessionExpired : ParseResult<Nothing>
  data class UnsupportedStructure(val reason: String) : ParseResult<Nothing>
}

enum class BtuModuleId(val id: String, val displayTitle: String, val routePath: String) {
  SELECTED_COURSES("selected_courses", "Selected Courses", "/ge/student/me/courses"),
  COURSE_DETAILS("course_details", "Course Details", "/ge/student/me/course/index/{id}"),
  WEEKLY_SCHEDULE("weekly_schedule", "Weekly Schedule", "/ge/student/me/schedule"),
  EXAMINATIONS("examinations", "Examinations", "/ge/exams/list"),
}

enum class ModuleCompatibilityStatus(val label: String) {
  COMPATIBLE("Compatible"),
  NO_DATA_AVAILABLE("No Data Available"),
  TEMPORARILY_UNAVAILABLE("Temporarily Unavailable"),
  SESSION_EXPIRED("Session Expired"),
  UNEXPECTED_HTML("Unexpected HTML"),
  PARSER_UPDATE_REQUIRED("Parser Update Required"),
  AWAITING_INSPECTION("Awaiting HTML Inspection"),
}

data class ModuleCompatibilityEntry(
  val module: BtuModuleId,
  val status: ModuleCompatibilityStatus,
  val detail: String,
  val updatedAtMillis: Long? = null,
)

/**
 * Tracks parser and endpoint compatibility per BTU module so one changed page
 * never brings down unaffected cached modules.
 */
class PageCompatibilityRegistry {

  private val _entries = MutableStateFlow(initialEntries())
  val entries: StateFlow<Map<BtuModuleId, ModuleCompatibilityEntry>> = _entries.asStateFlow()

  fun recordResult(
    module: BtuModuleId,
    parseResult: ParseResult<*>,
    timestampMillis: Long = System.currentTimeMillis(),
  ) {
    val (status, detail) = when (parseResult) {
      is ParseResult.Success -> ModuleCompatibilityStatus.COMPATIBLE to "Verified structure parsed cleanly"
      ParseResult.Empty -> ModuleCompatibilityStatus.NO_DATA_AVAILABLE to "Valid structure returned 0 rows"
      ParseResult.TemporarilyUnavailable -> ModuleCompatibilityStatus.TEMPORARILY_UNAVAILABLE to "Portal reported module restriction or maintenance"
      ParseResult.SessionExpired -> ModuleCompatibilityStatus.SESSION_EXPIRED to "Server required fresh authentication"
      is ParseResult.UnsupportedStructure -> ModuleCompatibilityStatus.PARSER_UPDATE_REQUIRED to parseResult.reason
    }
    update(module, status, detail, timestampMillis)
  }

  fun update(
    module: BtuModuleId,
    status: ModuleCompatibilityStatus,
    detail: String,
    timestampMillis: Long = System.currentTimeMillis(),
  ) {
    _entries.update { current ->
      current + (
        module to ModuleCompatibilityEntry(
          module = module,
          status = status,
          detail = detail,
          updatedAtMillis = timestampMillis,
        )
      )
    }
  }

  fun resetAll() {
    _entries.value = initialEntries()
  }

  private fun initialEntries(): Map<BtuModuleId, ModuleCompatibilityEntry> = mapOf(
    BtuModuleId.SELECTED_COURSES to ModuleCompatibilityEntry(
      module = BtuModuleId.SELECTED_COURSES,
      status = ModuleCompatibilityStatus.NO_DATA_AVAILABLE,
      detail = "Not fetched yet",
    ),
    BtuModuleId.COURSE_DETAILS to ModuleCompatibilityEntry(
      module = BtuModuleId.COURSE_DETAILS,
      status = ModuleCompatibilityStatus.NO_DATA_AVAILABLE,
      detail = "Not fetched yet",
    ),
    BtuModuleId.WEEKLY_SCHEDULE to ModuleCompatibilityEntry(
      module = BtuModuleId.WEEKLY_SCHEDULE,
      status = ModuleCompatibilityStatus.AWAITING_INSPECTION,
      detail = "Scheduled for Phase 3 inspection",
    ),
    BtuModuleId.EXAMINATIONS to ModuleCompatibilityEntry(
      module = BtuModuleId.EXAMINATIONS,
      status = ModuleCompatibilityStatus.AWAITING_INSPECTION,
      detail = "Scheduled for Phase 3 inspection",
    ),
  )
}
