package ge.usltd.btu_classroom.core.parsing

import ge.usltd.btu_classroom.core.network.BtuRoutes
import ge.usltd.btu_classroom.core.network.ResponseClassifier
import java.net.URI
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Jsoup contract parser for BTU Classroom's verified Selected Courses page (/ge/student/me/courses).
 * Identifies tables semantically by Georgian/English column headings and course links
 * rather than fragile CSS classes or fixed positional assumptions.
 */
class SelectedCoursesParser : BtuPageParser<SelectedCoursesPageData> {

  private enum class ColumnRole {
    TITLE,
    CODE,
    SCORE,
    MIN_CREDIT,
    CREDITS,
    SEMESTER,
    STATUS,
  }

  override fun parse(html: String): ParseResult<SelectedCoursesPageData> {
    if (html.isBlank()) {
      return ParseResult.UnsupportedStructure("Empty HTML response")
    }

    if (ResponseClassifier.looksLikeLoginPage(html)) {
      return ParseResult.SessionExpired
    }

    val lowerHtml = html.lowercase()
    if (lowerHtml.contains("<title>just a moment...</title>") ||
      (lowerHtml.contains("id=\"challenge-form\"") && lowerHtml.contains("cf_chl_opt"))
    ) {
      return ParseResult.TemporarilyUnavailable
    }

    val doc = Jsoup.parse(html)
    if (isTemporarilyUnavailableNotice(doc.body().text())) {
      return ParseResult.TemporarilyUnavailable
    }
    val tables = doc.select("table")

    val studentSummary = extractStudentSummary(doc)
    val defaultSemesterTitle = extractDefaultSemesterTitle(doc)
    val defaultSemesterKey = ParsingNumberUtils.stableKeySlug(defaultSemesterTitle)

    val parsedCourses = mutableListOf<ParsedCourseItem>()
    val discoveredSemesters = linkedMapOf<String, ParsedSemesterInfo>()
    var recognizedCourseTableCount = 0
    var malformedRowCount = 0

    for ((tableIndex, table) in tables.withIndex()) {
      val headerColumns = resolveTableColumns(table)
      val hasCourseAnchors = table.select("a[href]").any { sanitizeDetailPath(it.attr("href")) != null }

      if (headerColumns.isEmpty() && !hasCourseAnchors) {
        continue
      }
      if (ColumnRole.TITLE !in headerColumns && !hasCourseAnchors) {
        continue
      }
      if (!hasCourseAnchors &&
        (ColumnRole.TITLE !in headerColumns || headerColumns.keys.none { it != ColumnRole.TITLE })
      ) continue

      recognizedCourseTableCount++

      val tableSemesterTitle = extractTableSemesterHeading(table, doc) ?: defaultSemesterTitle
      val tableSemesterKey = ParsingNumberUtils.stableKeySlug(tableSemesterTitle)

      val dataRows = extractDataRows(table)
      for ((rowIdx, row) in dataRows.withIndex()) {
        val cells = row.select("> td, > th")
        if (cells.isEmpty()) continue

        // Skip rows that are purely "no courses" placeholders spanning multiple columns
        val rowText = row.text().trim()
        if (rowText.isBlank()) continue
        if (cells.size == 1 && isEmptyCoursesNotice(rowText)) {
          continue
        }

        val detailAnchor = row.select("a[href]").firstOrNull { sanitizeDetailPath(it.attr("href")) != null }

        val rawHref = detailAnchor?.attr("href")?.trim()
        val detailPath = sanitizeDetailPath(rawHref)

        val titleCellText = headerColumns[ColumnRole.TITLE]?.let { idx ->
          cells.getOrNull(idx)?.let { cell ->
            cell.selectFirst("a")?.text()?.trim()?.ifBlank { null } ?: cell.text().trim()
          }
        }
        val title = titleCellText?.ifBlank { null }
          ?: detailAnchor?.text()?.trim()?.ifBlank { null }
        if (title == null) {
          malformedRowCount++
          continue
        }
        if (headerColumns.values.any { it >= cells.size } ||
          cells.any { it.attr("colspan").toIntOrNull()?.let { span -> span > 1 } == true }
        ) {
          malformedRowCount++
          continue
        }

        val code = headerColumns[ColumnRole.CODE]?.let { index ->
          cells.getOrNull(index)?.let { cell ->
            cell.selectFirst("[data-original-title]")?.attr("data-original-title")?.trim()?.ifBlank { null }
              ?: cell.text().trim()
          }
        }.orEmpty()
        val score = headerColumns[ColumnRole.SCORE]?.let {
          ParsingNumberUtils.parseNullableDecimal(cells.getOrNull(it)?.text())
        }
        val minCredit = headerColumns[ColumnRole.MIN_CREDIT]?.let {
          ParsingNumberUtils.parseNullableDecimal(cells.getOrNull(it)?.text())
        }
        val credits = headerColumns[ColumnRole.CREDITS]?.let {
          ParsingNumberUtils.parseNullableDecimal(cells.getOrNull(it)?.text())
        }
        val rowSemester = headerColumns[ColumnRole.SEMESTER]?.let {
          cells.getOrNull(it)?.text()?.trim()?.ifBlank { null }
        }
        val statusLabel = headerColumns[ColumnRole.STATUS]?.let {
          cells.getOrNull(it)?.text()?.trim()?.ifBlank { null }
        }

        val semesterName = rowSemester ?: tableSemesterTitle
        val semesterKey = if (rowSemester != null) {
          ParsingNumberUtils.stableKeySlug(rowSemester)
        } else {
          tableSemesterKey
        }

        val courseId = extractCourseId(detailPath, code, title, tableIndex, rowIdx)

        if (!discoveredSemesters.containsKey(semesterKey)) {
          discoveredSemesters[semesterKey] = ParsedSemesterInfo(
            semesterKey = semesterKey,
            title = semesterName,
            sortOrder = discoveredSemesters.size,
            isCurrent = semesterKey == defaultSemesterKey || discoveredSemesters.isEmpty(),
          )
        }

        parsedCourses.add(
          ParsedCourseItem(
            courseId = courseId,
            code = code,
            title = title,
            score = score,
            minCredit = minCredit,
            credits = credits,
            semesterKey = semesterKey,
            semesterName = semesterName,
            detailPath = detailPath,
            statusLabel = statusLabel,
          ),
        )
      }
    }

    if (recognizedCourseTableCount > 0) {
      if (malformedRowCount > 0) {
        return ParseResult.UnsupportedStructure("A selected courses table contains incomplete or unrecognized rows")
      }
      if (parsedCourses.isEmpty()) {
        return ParseResult.Empty
      }
      val currentSemesterKey = discoveredSemesters.keys.firstOrNull { it == defaultSemesterKey }
        ?: discoveredSemesters.keys.first()
      return ParseResult.Success(
        SelectedCoursesPageData(
          studentSummary = studentSummary,
          semesters = discoveredSemesters.values.map { it.copy(isCurrent = it.semesterKey == currentSemesterKey) },
          courses = parsedCourses.distinctBy { it.semesterKey to it.courseId },
        ),
      )
    }

    val bodyText = doc.body().text()
    if (isTemporarilyUnavailableNotice(bodyText)) {
      return ParseResult.TemporarilyUnavailable
    }
    if (isEmptyCoursesContainer(doc, bodyText)) {
      return ParseResult.Empty
    }

    return ParseResult.UnsupportedStructure(
      "Could not locate a selected courses table with expected Georgian/English headings.",
    )
  }

  private fun resolveTableColumns(table: Element): Map<ColumnRole, Int> {
    val headerRows = table.select("thead tr")
    val headerCells = if (headerRows.isNotEmpty()) {
      headerRows.last()!!.select("th, td")
    } else {
      val firstTr = table.selectFirst("tr") ?: return emptyMap()
      val ths = firstTr.select("> th")
      if (ths.isNotEmpty()) ths else return emptyMap()
    }

    val resolved = mutableMapOf<ColumnRole, Int>()
    headerCells.forEachIndexed { index, cell ->
      val text = cell.text().trim().lowercase()
      val role = matchHeaderRole(text)
      if (role != null && role !in resolved) {
        resolved[role] = index
      }
    }
    return resolved
  }

  private fun matchHeaderRole(text: String): ColumnRole? {
    if (text.isBlank()) return null
    return when {
      text.contains("კურსის დასახელება") ||
        text == "დასახელება" ||
        text.contains("საგნის დასახელება") ||
        text.contains("course name") ||
        text.contains("subject") ||
        text == "course" ||
        text == "title" -> ColumnRole.TITLE

      text.contains("მინ. კრედიტ") ||
        text == "მინ. კრედ" ||
        text == "მინ კრედ" ||
        text.contains("მინიმალური კრედიტ") ||
        text.contains("min. credit") ||
        text.contains("min credit") ||
        text == "min" -> ColumnRole.MIN_CREDIT

      text.contains("კრედიტ") ||
        text == "კრედ" || text == "კრედ." ||
        text.contains("credit") ||
        text == "ects" -> ColumnRole.CREDITS

      text.contains("კოდი") ||
        text == "code" ||
        text.contains("course code") -> ColumnRole.CODE

      text.contains("ქულა") ||
        text.contains("შეფასება") ||
        text.contains("score") ||
        text.contains("grade") ||
        text.contains("points") ||
        text == "total" -> ColumnRole.SCORE

      text.contains("სემესტრ") ||
        text.contains("semester") ||
        text.contains("term") -> ColumnRole.SEMESTER

      text.contains("სტატუს") ||
        text.contains("status") -> ColumnRole.STATUS

      else -> null
    }
  }

  private fun extractDataRows(table: Element): List<Element> {
    val tbodyRows = table.select("> tbody > tr")
    if (tbodyRows.isNotEmpty()) {
      return tbodyRows.filter { row ->
        row.select("> td").isNotEmpty()
      }
    }
    val allRows = table.select("tr")
    return allRows.drop(1).filter { it.select("> td").isNotEmpty() }
  }

  private fun isCourseDetailHref(href: String?): Boolean {
    if (href.isNullOrBlank()) return false
    val lower = href.lowercase()
    return lower.contains("/ge/student/me/course/index/") ||
      lower.contains("/ge/student/course/") ||
      lower.contains("/ge/student/me/courses/") ||
      lower.contains("/course/index/")
  }

  private fun sanitizeDetailPath(rawHref: String?): String? {
    if (rawHref.isNullOrBlank()) return null
    val safeUrl = BtuRoutes.resolveSafeBtuUrl(rawHref) ?: return null
    return try {
      val uri = URI(safeUrl)
      if (!isCourseDetailHref(uri.rawPath)) return null
      val path = uri.rawPath ?: return null
      if (!Regex("/ge/student/me/course/index/\\d+/?").matches(path)) return null
      if (uri.rawQuery != null) return null
      path
    } catch (_: Exception) {
      null
    }
  }

  private fun extractCourseId(
    rawHref: String?,
    code: String,
    title: String,
    tableIndex: Int,
    rowIndex: Int,
  ): String {
    if (!rawHref.isNullOrBlank()) {
      val pathOnly = rawHref.substringBefore('?').substringBefore('#').trimEnd('/')
      val lastSegment = pathOnly.substringAfterLast('/').trim()
      if (lastSegment.isNotBlank() && lastSegment != "courses" && lastSegment != "index") {
        return lastSegment
      }
    }
    if (code.isNotBlank()) {
      return ParsingNumberUtils.stableKeySlug(code)
    }
    return "${ParsingNumberUtils.stableKeySlug(title)}_${tableIndex}_$rowIndex"
  }

  private fun extractDefaultSemesterTitle(doc: Document): String {
    val selectedOption = doc.selectFirst(
      "select[name*=semester] option[selected], select#semester option[selected], .semester-selector .active",
    )?.text()?.trim()?.ifBlank { null }
    if (selectedOption != null) return selectedOption

    val semesterHeading = doc.select("h1, h2, h3, .page-title, .card-title")
      .firstOrNull { el ->
        val t = el.text().lowercase()
        (t.contains("სემესტრ") || t.contains("semester")) && el.text().trim().length in 4..64
      }?.text()?.trim()
    if (!semesterHeading.isNullOrBlank()) return semesterHeading

    val navigationSemester = doc.select(".btn-group > .dropdown-toggle, .navbar .dropdown-toggle, button, a")
      .firstNotNullOfOrNull { element -> GEORGIAN_SEMESTER.find(element.text())?.value }
    if (navigationSemester != null) return navigationSemester

    return DEFAULT_SEMESTER_TITLE
  }

  private fun extractTableSemesterHeading(table: Element, doc: Document): String? {
    val caption = table.selectFirst("caption")?.text()?.trim()?.ifBlank { null }
    if (caption != null) return caption

    var prev = table.previousElementSibling()
    var steps = 0
    while (prev != null && steps < 3) {
      val tag = prev.tagName().lowercase()
      if (tag in setOf("h1", "h2", "h3", "h4", "h5")) {
        val text = prev.text().trim()
        if (text.isNotBlank() && text.length <= 80) return text
      }
      prev = prev.previousElementSibling()
      steps++
    }

    val parentHeader = table.parent()?.previousElementSibling()
    if (parentHeader != null && parentHeader.tagName().lowercase() in setOf("h2", "h3", "h4", "div")) {
      val text = parentHeader.text().trim()
      if (text.isNotBlank() && text.length <= 64 &&
        (text.contains("სემესტრ", ignoreCase = true) || text.contains("semester", ignoreCase = true))
      ) {
        return text
      }
    }
    return null
  }

  private fun extractStudentSummary(doc: Document): ParsedStudentSummary? {
    val name = doc.selectFirst(
      "[data-student-name], .student-name, .user-profile-name, .navbar .user-name",
    )?.text()?.trim()?.ifBlank { null } ?: doc.select("a[href]").firstOrNull { anchor ->
      val safeUrl = BtuRoutes.resolveSafeBtuUrl(anchor.attr("href")) ?: return@firstOrNull false
      val uri = URI(safeUrl)
      uri.rawPath == "/ge/profile/index" && uri.rawQuery == null
    }?.text()?.trim()?.ifBlank { null }

    val program = doc.selectFirst(
      "[data-program-name], .student-program, .program-name",
    )?.text()?.trim()?.ifBlank { null }

    if (name == null && program == null) return null
    return ParsedStudentSummary(displayName = name, programName = program)
  }

  private fun isEmptyCoursesNotice(text: String): Boolean {
    val lower = text.lowercase()
    return lower.contains("ჩანაწერი არ მოიძებნა") ||
      lower.contains("კურსები არ მოიძებნა") ||
      lower.contains("არჩეული კურსები არ") ||
      lower.contains("no courses") ||
      lower.contains("no records found") ||
      lower.contains("empty")
  }

  private fun isEmptyCoursesContainer(doc: Document, bodyText: String): Boolean {
    val hasCoursesContext = bodyText.contains("არჩეული კურს", ignoreCase = true) ||
      bodyText.contains("selected courses", ignoreCase = true) ||
      doc.selectFirst("[data-module=selected-courses], .courses-empty, .empty-courses") != null
    return hasCoursesContext && isEmptyCoursesNotice(bodyText)
  }

  private fun isTemporarilyUnavailableNotice(bodyText: String): Boolean {
    val lower = bodyText.lowercase()
    return lower.contains("მიმდინარეობს ტექნიკური") ||
      lower.contains("დროებით მიუწვდომელია") ||
      lower.contains("სემესტრი დახურულია") ||
      lower.contains("temporarily unavailable") ||
      lower.contains("under maintenance")
  }

  companion object {
    const val DEFAULT_SEMESTER_TITLE = "Current Semester"
    private val GEORGIAN_SEMESTER = Regex("\\d{4}\\s*[-–]\\s*\\d{4}\\s+წლის\\s+(?:შემოდგომის|გაზაფხულის|ზაფხულის)\\s+სემესტრი")
  }
}
