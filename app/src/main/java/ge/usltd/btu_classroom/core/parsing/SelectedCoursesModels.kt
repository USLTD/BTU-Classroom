package ge.usltd.btu_classroom.core.parsing

data class ParsedStudentSummary(
  val displayName: String?,
  val programName: String?,
)

data class ParsedSemesterInfo(
  val semesterKey: String,
  val title: String,
  val sortOrder: Int,
  val isCurrent: Boolean,
)

data class ParsedCourseItem(
  val courseId: String,
  val code: String,
  val title: String,
  val score: Double?,
  val minCredit: Double?,
  val credits: Double?,
  val semesterKey: String,
  val semesterName: String,
  val detailPath: String?,
  val statusLabel: String?,
)

data class SelectedCoursesPageData(
  val studentSummary: ParsedStudentSummary?,
  val semesters: List<ParsedSemesterInfo>,
  val courses: List<ParsedCourseItem>,
)

internal object ParsingNumberUtils {
  private val DECIMAL_REGEX = Regex("""-?\d+(?:[.,]\d+)?""")

  fun parseNullableDecimal(raw: String?): Double? {
    val cleaned = raw?.trim().orEmpty()
    if (cleaned.isEmpty()) return null
    val lower = cleaned.lowercase()
    if (lower == "-" || lower == "—" || lower == "–" || lower == "n/a" || lower == "none" ||
      lower.contains("შეუფასებ") || lower.contains("ungraded")
    ) {
      return null
    }
    val match = DECIMAL_REGEX.find(cleaned) ?: return null
    return match.value.replace(',', '.').toDoubleOrNull()
  }

  fun stableKeySlug(input: String): String {
    val normalized = input.trim().lowercase()
      .replace(Regex("""[^\p{L}\p{N}]+"""), "_")
      .trim('_')
    return normalized.ifEmpty { "default" }
  }
}
