package ge.usltd.btu_classroom.core.parsing

import ge.usltd.btu_classroom.core.network.BtuRoutes
import ge.usltd.btu_classroom.core.network.ResponseClassifier
import java.net.URI
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Jsoup contract parser for BTU Classroom course detail pages (/ge/student/me/course/index/{id}).
 * Preserves actual grading category names, keeps missing scores nullable, and validates external links
 * strictly against the approved BTU origin.
 */
class CourseDetailParser : BtuPageParser<ParsedCourseDetail> {

  override fun parse(html: String): ParseResult<ParsedCourseDetail> = parseDetail(html, requireGrades = false)

  fun parseScores(html: String): ParseResult<ParsedCourseDetail> = parseDetail(html, requireGrades = true)

  private fun parseDetail(html: String, requireGrades: Boolean): ParseResult<ParsedCourseDetail> {
    if (html.isBlank()) {
      return ParseResult.UnsupportedStructure("Empty course detail HTML")
    }
    if (ResponseClassifier.looksLikeLoginPage(html)) {
      return ParseResult.SessionExpired
    }
    val lower = html.lowercase()
    if (lower.contains("<title>just a moment...</title>") ||
      (lower.contains("id=\"challenge-form\"") && lower.contains("cf_chl_opt"))
    ) {
      return ParseResult.TemporarilyUnavailable
    }

    val document = Jsoup.parse(html)
    if (hasUnavailableNotice(document)) {
      return ParseResult.TemporarilyUnavailable
    }
    val doc = document.selectFirst(".col-md-18") ?: document

    val navigation = extractCourseNavigation(doc)
    if (navigation.ambiguous) return ParseResult.UnsupportedStructure("Conflicting course child links")
    val title = extractCourseTitle(doc)
    val kvMetadata = extractKeyValueMetadata(doc)
    val gradeSnapshot = when (val result = extractGradeComponents(doc)) {
      is ParseResult.Success -> result.data
      is ParseResult.UnsupportedStructure -> return result
      else -> return ParseResult.UnsupportedStructure("Could not validate course grade tables")
    }
    if (requireGrades && !gradeSnapshot.present) {
      return ParseResult.UnsupportedStructure("The linked scores page has no recognized grade table")
    }
    val resources = extractSafeResources(doc)
    val syllabus = doc.selectFirst(".course-syllabus, .syllabus-summary, [data-syllabus], .course-description")
      ?.text()?.trim()?.ifBlank { null }

    val hasCourseContext = doc.selectFirst(".course-title, [data-course-title]") != null ||
      kvMetadata.keys.any { key -> key in COURSE_METADATA_KEYS } ||
      gradeSnapshot.present || navigation.scoresPath != null || navigation.filesPath != null

    if (!hasCourseContext) {
      return ParseResult.UnsupportedStructure("Course detail headings, metadata, and grade components not recognized")
    }

    val finalTitle = title ?: kvMetadata.findByKeys("course", "course name", "course title", "კურსის დასახელება")
      ?: if (requireGrades) "" else return ParseResult.UnsupportedStructure("Missing course title in detail markup")

    val code = kvMetadata.findByKeys("კოდი", "course code", "code")
    val lecturer = kvMetadata.findByKeys("ლექტორი", "პედაგოგი", "lecturer", "instructor")
      ?: extractLecturerFromGroupHeading(doc)
    val semester = kvMetadata.findByKeys("სემესტრი", "semester", "term")
    val credits = if (requireGrades) null else ParsingNumberUtils.parseNullableDecimal(kvMetadata.findByKeys("კრედიტი", "კრედიტები", "credits", "credit"))
    val minCredit = if (requireGrades) null else ParsingNumberUtils.parseNullableDecimal(kvMetadata.findByKeys("მინ. კრედიტი", "min credit", "min"))
    val totalScore = gradeSnapshot.totalScore ?: ParsingNumberUtils.parseNullableDecimal(
      kvMetadata.findByKeys("ჯამური ქულა", "ქულა", "სულ", "total score", "total", "score"),
    )

    return ParseResult.Success(
      ParsedCourseDetail(
        title = finalTitle,
        code = code,
        semesterName = semester,
        lecturer = lecturer,
        credits = credits,
        minCredit = minCredit,
        totalScore = totalScore,
        syllabusSummary = syllabus,
        gradeComponents = gradeSnapshot.components,
        resources = resources,
        hasGradeSnapshot = gradeSnapshot.present,
        scoresPath = navigation.scoresPath,
        filesPath = navigation.filesPath,
      ),
    )
  }

  private fun extractCourseTitle(doc: Element): String? {
    val candidates = listOf(
      ".course-title",
      "[data-course-title]",
      ".col-md-18 > fieldset > legend",
      ".col-md-18 legend",
      ".col-md-18 h1",
      ".col-md-18 h2",
      ".page-header h1",
      ".page-title",
      "main h1",
      "main h2",
      "h1",
      "h2",
    )
    for (selector in candidates) {
      val text = doc.selectFirst(selector)?.text()?.trim()?.ifBlank { null }
      if (text != null && !text.equals("BTU Classroom", ignoreCase = true)) {
        return text
      }
    }
    return null
  }

  private fun extractKeyValueMetadata(doc: Element): Map<String, String> {
    val result = linkedMapOf<String, String>()

    // 1. Definition lists <dt>/<dd>
    val dts = doc.select("dl dt")
    for (dt in dts) {
      val dd = dt.nextElementSibling()?.takeIf { it.tagName().equals("dd", ignoreCase = true) } ?: continue
      val k = dt.text().trim().trimEnd(':').lowercase()
      val v = dd.text().trim()
      if (k.isNotBlank() && v.isNotBlank()) {
        result[k] = v
      }
    }

    // 2. Two-column metadata tables (<th>key</th><td>val</td> or <td>key</td><td>val</td>)
    for (table in doc.select("table")) {
      if (isGradeBreakdownTable(table)) continue
      for (row in table.select("tr")) {
        val cells = row.select("> th, > td")
        if (cells.size == 2) {
          val k = cells[0].text().trim().trimEnd(':').lowercase()
          val v = cells[1].text().trim()
          if (k.isNotBlank() && v.isNotBlank() && k.length <= 48) {
            result.putIfAbsent(k, v)
          }
        }
      }
    }

    // 3. Explicit data attributes or labeled spans
    for (item in doc.select("[data-meta-label]")) {
      val k = item.attr("data-meta-label").trim().lowercase()
      val v = item.text().trim()
      if (k.isNotBlank() && v.isNotBlank()) {
        result.putIfAbsent(k, v)
      }
    }

    return result
  }

  private fun isGradeBreakdownTable(table: Element): Boolean {
    if ((sequenceOf(table) + table.parents().asSequence()).any {
        it.hasClass("grades") || it.hasClass("grade-components") || it.hasAttr("data-grades")
      }
    ) return true
    val headerCells = table.select("thead tr").lastOrNull()?.select("> th, > td")
      ?: table.selectFirst("tr")?.select("> th")
      ?: return false
    if (headerCells.size < 2 && table.selectFirst("thead") == null) return false
    val headerText = headerCells.joinToString(" ") { it.text().lowercase() }
    return listOf("კომპონენტ", "component", "შეფასებ", "activity", "category", "ქულ", "score", "grade")
      .any { headerText.contains(it) }
  }

  private data class GradeSnapshot(
    val present: Boolean,
    val components: List<ParsedGradeComponent>,
    val totalScore: Double?,
  )

  private fun extractGradeComponents(doc: Element): ParseResult<GradeSnapshot> {
    val components = mutableListOf<ParsedGradeComponent>()
    var hasGradeTable = false
    var totalScore: Double? = null
    for (table in doc.select("table")) {
      if (!isGradeBreakdownTable(table)) continue
      hasGradeTable = true
      val headerRow = table.select("thead tr").lastOrNull() ?: table.selectFirst("tr")
        ?: return ParseResult.UnsupportedStructure("Course grade table has no column headings")
      val headerCells = headerRow.select("> th, > td")
      if (headerCells.any { it.hasUnsupportedSpan() }) {
        return ParseResult.UnsupportedStructure("Course grade headings contain unsupported merged columns")
      }

      var nameIdx: Int? = null
      var scoreIdx: Int? = null
      var maxIdx: Int? = null
      var noteIdx: Int? = null

      headerCells.forEachIndexed { idx, cell ->
        val h = cell.text().trim().lowercase()
        when {
          nameIdx == null && (h.contains("კომპონენტ") || h.contains("კრიტერიუმ") || h.contains("დასახელებ") || h.contains("აქტივობ") || h.contains("component") || h.contains("category") || h.contains("name") || h.contains("activity")) ->
            nameIdx = idx
          maxIdx == null && (h.contains("მაქს") || h.contains("max") || h.contains("limit")) ->
            maxIdx = idx
          scoreIdx == null && (h.contains("ქულ") || h.contains("შეფასებ") || h.contains("score") || h.contains("earned") || h.contains("grade")) ->
            scoreIdx = idx
          noteIdx == null && (h.contains("შენიშვნ") || h.contains("კომენტარ") || h.contains("note") || h.contains("comment")) ->
            noteIdx = idx
        }
      }

      val requiredNameIdx = nameIdx
        ?: return ParseResult.UnsupportedStructure("Course grade table has no recognized component column")
      val requiredScoreIdx = scoreIdx
        ?: return ParseResult.UnsupportedStructure("Course grade table has no recognized score column")

      val rows = table.select("> tbody > tr, > tfoot > tr, > tr")
      for (row in rows) {
        if (row === headerRow) continue
        val cells = row.select("> td, > th")
        if (cells.size != headerCells.size || cells.any { it.hasUnsupportedSpan() }) {
          return ParseResult.UnsupportedStructure("Course grade table contains incomplete or merged rows")
        }
        val name = cells[requiredNameIdx].text().trim().ifBlank { null }
          ?: return ParseResult.UnsupportedStructure("Course grade table contains an unnamed component")
        val rawScore = cells[requiredScoreIdx].text().trim()
        val earned = parseGradeNumber(rawScore)
        val normalizedName = name.trim().trimEnd(':').lowercase()
        if (normalizedName in TOTAL_LABELS) {
          if (rawScore.isNotBlank() && earned == null && !isMissingScore(rawScore)) {
            return ParseResult.UnsupportedStructure("Course total contains an unsupported score value")
          }
          if (earned != null && totalScore != null && earned != totalScore) {
            return ParseResult.UnsupportedStructure("Course grade tables report conflicting totals")
          }
          if (earned != null) totalScore = earned
          continue
        }
        if (normalizedName in CREDIT_LABELS) continue
        if (earned == null && !isMissingScore(rawScore) && rawScore !in STATUS_VALUES) {
          return ParseResult.UnsupportedStructure("Course grade table contains an unsupported score value")
        }
        val rawMax = maxIdx?.let { cells[it].text().trim() }
        val columnMax = rawMax?.let(::parseGradeNumber)
        if (rawMax != null && columnMax == null && !isMissingScore(rawMax)) {
          return ParseResult.UnsupportedStructure("Course grade table contains an unsupported maximum")
        }
        val max = columnMax ?: MAX_IN_LABEL.find(name)?.groupValues?.get(1)?.let {
          it.replace(',', '.').toDoubleOrNull()?.takeIf(Double::isFinite)
        }
        val explicitNotes = noteIdx?.let { cells[it].text().trim().ifBlank { null } }
        val statusNotes = rawScore.takeIf { it in STATUS_VALUES }
        val notes = listOfNotNull(explicitNotes, statusNotes).joinToString(" · ").ifBlank { null }
        components.add(
          ParsedGradeComponent(
            categoryName = name,
            score = earned,
            maxScore = max,
            notes = notes,
          ),
        )
      }
    }
    return ParseResult.Success(GradeSnapshot(present = hasGradeTable, components = components, totalScore = totalScore))
  }

  private fun Element.hasUnsupportedSpan(): Boolean =
    (hasAttr("colspan") && attr("colspan").toIntOrNull() != 1) ||
      (hasAttr("rowspan") && attr("rowspan").toIntOrNull() != 1)

  fun parseResourcesPage(html: String): ParseResult<List<ParsedCourseResource>> {
    if (html.isBlank()) return ParseResult.UnsupportedStructure("Empty course resources HTML")
    if (ResponseClassifier.looksLikeLoginPage(html)) return ParseResult.SessionExpired
    val doc = Jsoup.parse(html)
    if (hasUnavailableNotice(doc) || isChallengePage(html)) return ParseResult.TemporarilyUnavailable
    val tables = (doc.selectFirst(".col-md-18") ?: doc).select("table#files")
    if (tables.size != 1) return ParseResult.UnsupportedStructure("The linked resources page has no unique files table")
    val resources = linkedMapOf<String, ParsedCourseResource>()
    for (row in tables.single().select("> tbody > tr, > tr")) {
      val cells = row.select("> td, > th")
      if (row.hasClass("info") && cells.size == 1 && cells[0].attr("colspan") == "2" &&
        isResourceLecturerHeader(cells[0])
      ) {
        continue
      }
      if (cells.size != 2 || cells.any { it.hasUnsupportedSpan() }) {
        return ParseResult.UnsupportedStructure("Course resources contain an unsupported row")
      }
      val anchors = cells[0].select("a[href]")
      if (anchors.isEmpty() && cells.any { it.text().isNotBlank() }) {
        return ParseResult.UnsupportedStructure("Course resources contain an unrecognized item")
      }
      var hasVerifiedDocument = false
      for (anchor in anchors) {
        val safeUrl = BtuRoutes.resolveSafeBtuUrl(anchor.attr("href")) ?: continue
        if (!isVerifiedDownloadUrl(safeUrl)) continue
        val title = anchor.text().trim().ifBlank { null }
          ?: return ParseResult.UnsupportedStructure("Course resource has no title")
        hasVerifiedDocument = true
        resources.putIfAbsent(safeUrl, ParsedCourseResource(title, safeUrl))
      }
      if (!hasVerifiedDocument && (anchors.isNotEmpty() || cells.any { it.text().isNotBlank() })) {
        return ParseResult.UnsupportedStructure("Course resources contain an unrecognized document link")
      }
    }
    return ParseResult.Success(resources.values.toList())
  }

  private fun isResourceLecturerHeader(cell: Element): Boolean {
    var hasLecturer = false
    val anchors = cell.select("a[href]")
    for (anchor in anchors) {
      val safeUrl = BtuRoutes.resolveSafeBtuUrl(anchor.attr("href")) ?: return false
      val uri = URI(safeUrl)
      if (uri.rawQuery != null) return false
      val path = uri.rawPath ?: return false
      when {
        Regex("/ge/student/me/lector/\\d+/?").matches(path) -> hasLecturer = true
        Regex("/ge/messages/compose/\\d+/?").matches(path) -> Unit
        else -> return false
      }
    }
    return hasLecturer
  }

  private fun extractSafeResources(doc: Element): List<ParsedCourseResource> {
    val explicitContainer = doc.selectFirst(".course-resources, .resources-list, [data-resources]")
    val liveContainer = doc.takeIf { it.hasClass("col-md-18") } ?: doc.selectFirst(".col-md-18")
    val container = explicitContainer ?: liveContainer ?: doc.selectFirst("main") ?: return emptyList()

    val seenUrls = mutableSetOf<String>()
    val resources = mutableListOf<ParsedCourseResource>()
    for (anchor in container.select("a[href]")) {
      if (anchor.parents().any { it.tagName().equals("nav", ignoreCase = true) || it.hasClass("navbar") }) {
        continue
      }
      val rawHref = anchor.attr("href")
      val safeUrl = BtuRoutes.resolveSafeBtuUrl(rawHref) ?: continue
      if (!isVerifiedDownloadUrl(safeUrl, allowLegacyFiles = liveContainer == null)) continue
      val title = anchor.text().trim().ifBlank { null } ?: continue
      if (seenUrls.add(safeUrl)) {
        resources.add(ParsedCourseResource(title = title, url = safeUrl))
      }
    }
    return resources
  }

  private data class CourseNavigation(
    val scoresPath: String? = null,
    val filesPath: String? = null,
    val ambiguous: Boolean = false,
  )

  private fun extractCourseNavigation(doc: Element): CourseNavigation {
    val container = doc.takeIf { it.hasClass("col-md-18") } ?: doc.selectFirst(".col-md-18")
      ?: doc.selectFirst("main") ?: doc
    val children = container.select("a[href]").mapNotNull { anchor ->
      val safeUrl = BtuRoutes.resolveSafeBtuUrl(anchor.attr("href")) ?: return@mapNotNull null
      val uri = URI(safeUrl)
      if (uri.rawQuery != null) return@mapNotNull null
      val path = uri.rawPath ?: return@mapNotNull null
      val match = COURSE_CHILD_PATH.matchEntire(path) ?: return@mapNotNull null
      Triple(match.groupValues[1], path.trimEnd('/'), match.groupValues[2] to match.groupValues[3])
    }.distinct()
    if (children.map { it.third }.distinct().size > 1 ||
      children.groupBy { it.first }.any { (_, paths) -> paths.size > 1 }
    ) return CourseNavigation(ambiguous = true)
    return CourseNavigation(
      scoresPath = children.firstOrNull { it.first == "scores" }?.second,
      filesPath = children.firstOrNull { it.first == "files" }?.second,
    )
  }

  private fun extractLecturerFromGroupHeading(doc: Element): String? =
    doc.select(".col-md-18 h3").firstOrNull { it.text().trim().startsWith("ჯგუფი") }
      ?.text()?.substringAfter(" - ", "")?.trim()?.ifBlank { null }

  private fun isVerifiedDownloadUrl(url: String, allowLegacyFiles: Boolean = false): Boolean {
    val uri = URI(url)
    if (uri.rawQuery != null) return false
    val path = uri.rawPath ?: return false
    return UPLOAD_PATH.matches(path) || SYLLABUS_FILE_PATH.matches(path) ||
      (allowLegacyFiles && LEGACY_FILE_PATH.matches(path))
  }

  private fun parseGradeNumber(raw: String): Double? =
    raw.trim().takeIf { SCORE_NUMBER.matches(it) }?.replace(',', '.')?.toDoubleOrNull()?.takeIf(Double::isFinite)

  private fun isMissingScore(raw: String): Boolean = raw.isBlank() ||
    raw.lowercase() in setOf("-", "—", "–", "n/a", "none", "ungraded", "pending", "not graded") ||
    raw.contains("შეუფასებ", ignoreCase = true)

  private fun hasUnavailableNotice(doc: Document): Boolean {
    val container = doc.selectFirst(".col-md-18") ?: doc.body()
    val notices = container.select(".alert, .notice").map { it.text() }
    val candidates = if (container.selectFirst("table, .course-title, [data-course-title], a[href*='/course/'], a[href*='/courseSilabusFile/']") == null) {
      notices + container.text()
    } else notices
    return candidates.any { text ->
      text.contains("დროებით მიუწვდომელია", ignoreCase = true) ||
        text.contains("temporarily unavailable", ignoreCase = true) ||
        text.contains("under maintenance", ignoreCase = true)
    }
  }

  private fun isChallengePage(html: String): Boolean {
    val lower = html.lowercase()
    return lower.contains("<title>just a moment...</title>") ||
      (lower.contains("id=\"challenge-form\"") && lower.contains("cf_chl_opt"))
  }

  private fun Map<String, String>.findByKeys(vararg candidates: String): String? {
    for (candidate in candidates) {
      this[candidate.lowercase()]?.let { return it }
    }
    for ((k, v) in this) {
      if (candidates.any { c -> k.contains(c, ignoreCase = true) }) {
        return v
      }
    }
    return null
  }

  private companion object {
    val COURSE_CHILD_PATH = Regex("/ge/student/me/course/(scores|files)/(\\d+)/(\\d+)/?")
    val UPLOAD_PATH = Regex("/uploads/(?:\\d+/)+[^/]+\\.(pdf|docx?|xlsx?|pptx?|zip|txt)", RegexOption.IGNORE_CASE)
    val SYLLABUS_FILE_PATH = Regex("/ge/student/me/courseSilabusFile/\\d+/\\d+/?")
    val LEGACY_FILE_PATH = Regex("/files/[^/]+\\.(pdf|docx?|xlsx?|pptx?|zip|txt)", RegexOption.IGNORE_CASE)
    val SCORE_NUMBER = Regex("[+-]?\\d+(?:[.,]\\d+)?")
    val MAX_IN_LABEL = Regex("(?:max\\.?|მაქს\\.?)[\\s.:]*(\\d+(?:[.,]\\d+)?)", RegexOption.IGNORE_CASE)
    val TOTAL_LABELS = setOf("სულ", "ჯამური ქულა", "total", "total score")
    val CREDIT_LABELS = setOf("კრედიტი", "კრედიტები", "credit", "credits")
    val STATUS_VALUES = setOf("✘", "✗", "✓", "✔", "×", "დიახ", "არა", "Yes", "No", "yes", "no")
    val COURSE_METADATA_KEYS = setOf(
      "course", "course name", "course title", "კურსის დასახელება", "კოდი", "course code",
      "ლექტორი", "პედაგოგი", "lecturer", "instructor", "კრედიტი", "კრედიტები", "credits",
    )
  }
}
