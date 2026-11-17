package ge.usltd.btu_classroom.core.portal

import ge.usltd.btu_classroom.core.network.BtuRoutes
import ge.usltd.btu_classroom.core.network.ResponseClassifier
import ge.usltd.btu_classroom.core.parsing.ParseResult
import ge.usltd.btu_classroom.core.schedule.WeeklyScheduleParser
import java.net.URI
import java.time.LocalDate
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class PortalPageParser {
  fun parse(module: PortalModule, html: String): ParseResult<PortalContent> {
    if (html.isBlank()) return unsupported("Empty academic page response")
    if (html.length > MAX_HTML_LENGTH) return unsupported("Academic page exceeded the supported size")
    if (ResponseClassifier.looksLikeLoginPage(html)) return ParseResult.SessionExpired

    val document = Jsoup.parse(html, BtuRoutes.BASE_ORIGIN)
    val content = document.selectFirst(".col-md-18") ?: document.body()
    val text = normalize(content.text()).lowercase(Locale.ROOT)
    val explicitNotices = content.select(".alert,.notice").map { normalize(it.text()).lowercase(Locale.ROOT) }
    val hasDataContainer = content.select("table,#program_wrapper,#calendar").isNotEmpty()
    val isUnavailable = UNAVAILABLE_TEXT.any { marker ->
      explicitNotices.any { it.contains(marker) } || (!hasDataContainer && text.contains(marker))
    }
    if (document.title().lowercase(Locale.ROOT).contains("just a moment") ||
      document.selectFirst("#challenge-form") != null ||
      isUnavailable
    ) return ParseResult.TemporarilyUnavailable

    return when (module) {
      PortalModule.SCHEDULE -> when (val schedule = WeeklyScheduleParser().parse(html)) {
        is ParseResult.Success -> ParseResult.Success(
          PortalContent(title = "ჩემი ცხრილი", schedule = schedule.data),
        )
        ParseResult.Empty -> ParseResult.Empty
        ParseResult.SessionExpired -> ParseResult.SessionExpired
        ParseResult.TemporarilyUnavailable -> ParseResult.TemporarilyUnavailable
        is ParseResult.UnsupportedStructure -> schedule
      }
      PortalModule.EXAMS -> parseExams(document)
      PortalModule.PROGRAM -> parseProgram(document)
      PortalModule.CALENDAR -> parseCalendar(document)
      PortalModule.ACADEMIC_CARD -> parseAcademicCard(document)
      PortalModule.REMAINING -> parseRemaining(document)
      PortalModule.STATEMENTS -> parseStatements(document)
      PortalModule.RATING -> parseRating(document)
    }
  }

  private fun parseExams(document: Document): ParseResult<PortalContent> {
    val tables = document.select("table.exams-table")
    if (tables.size != 1) return unsupported("Examination table was not found or is ambiguous")
    val table = tables.single()
    val headerRows = table.select("thead > tr")
    if (headerRows.size != 1) return unsupported("Examination headings changed")
    val headings = cells(headerRows.single()).map { normalize(it.text()) }
    if (headings != EXAM_HEADINGS || hasMergedCells(cells(headerRows.single()))) {
      return unsupported("Examination headings changed")
    }
    if (table.select("tbody").size != 1) return unsupported("Examination rows were not found")

    val rows = mutableListOf<PortalRow>()
    for (row in table.select("tbody > tr")) {
      val rowCells = cells(row)
      if (rowCells.isEmpty() && row.text().isBlank()) continue
      if (rowCells.size != EXAM_HEADINGS.size || hasMergedCells(rowCells)) {
        return unsupported("Examination row structure changed")
      }
      val values = rowCells.map { normalize(it.text()) }
      if (values[0].isBlank() || values[1].isBlank() || values[2].isBlank()) {
        return unsupported("An examination is missing its course, date, or time")
      }
      rows += PortalRow(values)
    }
    if (rows.isEmpty()) return ParseResult.Empty
    return ParseResult.Success(
      PortalContent(
        title = "საგამოცდო ცხრილი",
        sections = listOf(PortalSection(columns = headings, rows = rows)),
      ),
    )
  }

  private fun parseProgram(document: Document): ParseResult<PortalContent> {
    val wrappers = document.select("fieldset#program_wrapper")
    if (wrappers.size != 1) return unsupported("Read-only program information was not found")
    val fields = linkedMapOf<String, PortalField>()
    for (row in wrappers.single().select(".form-group")) {
      val labels = row.select("label")
      if (labels.size != 1) return unsupported("Program field labels changed")
      val label = normalize(labels.single().text())
      if (label !in PROGRAM_LABELS) continue
      val inputs = row.select("input[type=text]")
      if (inputs.size != 1 || !inputs.single().hasAttr("disabled") || label in fields) {
        return unsupported("Read-only program fields changed")
      }
      fields[label] = PortalField(label, normalize(inputs.single().attr("value")))
    }
    if (fields.keys != PROGRAM_LABELS.toSet()) return unsupported("Required program fields changed")
    if (fields.values.all { it.value.isBlank() }) return ParseResult.Empty
    return ParseResult.Success(
      PortalContent(title = "ჩემი პროგრამები", fields = PROGRAM_LABELS.map { fields.getValue(it) }),
    )
  }

  private fun parseCalendar(document: Document): ParseResult<PortalContent> {
    if (document.select("#calendar").size != 1) return unsupported("Academic calendar was not found")
    val scripts = document.select("script").map(Element::data).filter { script ->
      script.contains("fullCalendar") && CALENDAR_EVENTS_REFERENCE.containsMatchIn(script)
    }
    val arrays = scripts.flatMap { script ->
      EVENTS_ASSIGNMENT.findAll(script).map { match -> jsonArrayLiteral(script, match.range.last + 1) }.toList()
    }
    if (arrays.size != 1 || arrays.single() == null) {
      return unsupported("Academic calendar event data changed")
    }
    val array = try {
      Json.parseToJsonElement(requireNotNull(arrays.single())) as? JsonArray
    } catch (_: Exception) {
      null
    } ?: return unsupported("Academic calendar data is not a valid JSON array")
    if (array.isEmpty()) return ParseResult.Empty

    val events = linkedSetOf<PortalCalendarEvent>()
    for (item in array) {
      val event = item as? JsonObject ?: return unsupported("Academic calendar event structure changed")
      val date = jsonString(event, "start") ?: return unsupported("Academic calendar event date is missing")
      val title = jsonString(event, "title")?.let(::normalize)?.takeIf(String::isNotBlank)
        ?: return unsupported("Academic calendar event title is missing")
      var endDate: String? = null
      if (event.containsKey("end")) {
        endDate = jsonString(event, "end") ?: return unsupported("Academic calendar end date changed")
      }
      val start = parseDate(date) ?: return unsupported("Academic calendar date format changed")
      if (endDate != null) {
        val end = parseDate(endDate) ?: return unsupported("Academic calendar end date format changed")
        if (end.isBefore(start)) return unsupported("Academic calendar date range is invalid")
      }
      events += PortalCalendarEvent(date = date, title = title, endDate = endDate)
    }
    return ParseResult.Success(
      PortalContent(title = "კალენდარი", calendarEvents = events.sortedWith(compareBy({ it.date }, { it.title }))),
    )
  }

  private fun parseRemaining(document: Document): ParseResult<PortalContent> {
    val main = document.selectFirst(".col-md-18") ?: return unsupported("Remaining-course content was not found")
    val copy = main.clone().apply { select("script,style,.promo-ads").remove() }
    return if (copy.select("table").isEmpty() && normalize(copy.text()) == REMAINING_EMPTY_NOTICE) {
      ParseResult.Empty
    } else {
      unsupported("Remaining-course content has not yet been validated")
    }
  }

  private fun parseStatements(document: Document): ParseResult<PortalContent> {
    val main = document.selectFirst(".col-md-18") ?: return unsupported("Statement content was not found")
    val tables = main.select("table").filter { table ->
      table.select("thead > tr").any { cells(it).map { cell -> normalize(cell.text()) } == STATEMENT_HEADINGS }
    }
    if (tables.size != 1) return unsupported("Statement headings changed or are ambiguous")
    val table = tables.single()
    val headers = table.select("thead > tr")
    if (headers.size != 1 || hasMergedCells(cells(headers.single())) || table.select("tbody").size != 1) {
      return unsupported("Statement table structure changed")
    }
    val rows = mutableListOf<PortalRow>()
    for (row in table.select("tbody > tr")) {
      val rowCells = cells(row)
      if (rowCells.isEmpty() && row.text().isBlank()) continue
      if (rowCells.size != STATEMENT_HEADINGS.size || hasMergedCells(rowCells)) {
        return unsupported("Statement row structure changed")
      }
      val values = rowCells.map { normalize(it.text()) }
      if (values.take(3).any(String::isBlank)) return unsupported("A statement is missing required information")
      val links = row.select("a[href]").mapNotNull { anchor ->
        val url = BtuRoutes.resolveSafeBtuUrl(anchor.attr("href")) ?: return@mapNotNull null
        val uri = URI(url)
        if (!STATEMENT_VIEW_PATH.matches(uri.path) || uri.rawQuery != null) return@mapNotNull null
        PortalLink(normalize(anchor.text()).ifBlank { values.first() }, url)
      }.distinct()
      rows += PortalRow(values, links)
    }
    if (rows.isEmpty()) return ParseResult.Empty
    return ParseResult.Success(
      PortalContent(title = "განცხადებები", sections = listOf(PortalSection(columns = STATEMENT_HEADINGS, rows = rows))),
    )
  }

  private fun parseRating(document: Document): ParseResult<PortalContent> {
    val main = document.selectFirst(".col-md-18") ?: return unsupported("Rating content was not found")
    val fieldsets = main.select("fieldset").filter { fieldset ->
      fieldset.children().firstOrNull { it.tagName() == "legend" }?.let { normalize(it.text()) } == "რეიტინგი"
    }
    if (fieldsets.size != 1) return unsupported("Rating heading changed or is ambiguous")
    val body = fieldsets.single().clone().apply { select("legend,script,style").remove() }
    val text = normalize(body.text())
    if (!text.startsWith("$RATING_LABEL:")) return unsupported("Official rating label changed")
    val score = text.removePrefix("$RATING_LABEL:").trim()
    val number = score.replace(',', '.').toDoubleOrNull()
    if (!DECIMAL.matches(score) || number == null || !number.isFinite() || number !in 0.0..100.0) {
      return unsupported("Official rating value is unavailable or changed")
    }
    return ParseResult.Success(PortalContent(title = "რეიტინგი", fields = listOf(PortalField(RATING_LABEL, score))))
  }

  private fun parseAcademicCard(document: Document): ParseResult<PortalContent> {
    val main = document.selectFirst(".col-md-18") ?: return unsupported("Academic record content was not found")
    val tables = main.select("table.table_custom").filter { table ->
      table.select("tbody > tr").any { isTranscriptHeading(cells(it).map { cell -> normalize(cell.text()) }) }
    }
    if (tables.size != 1) return unsupported("Academic transcript headings changed or are ambiguous")
    val table = tables.single()
    val fields = linkedMapOf<String, PortalField>()
    val sections = mutableListOf<PortalSection>()
    var transcriptStarted = false
    var semester: String? = null
    var semesterRows = mutableListOf<PortalRow>()
    fun finishSemester() {
      semester?.let { title ->
        sections += PortalSection(title = title, columns = TRANSCRIPT_HEADINGS, rows = semesterRows.toList())
      }
      semesterRows = mutableListOf()
    }

    for (row in table.select("tbody > tr")) {
      val rowCells = cells(row)
      val values = rowCells.map { normalize(it.text()) }
      if (values.isEmpty() || values.all(String::isBlank)) continue
      if (isTranscriptHeading(values)) {
        if (transcriptStarted || hasMergedCells(rowCells)) return unsupported("Academic transcript headings repeated or changed")
        transcriptStarted = true
        continue
      }
      if (!transcriptStarted) {
        val label = values.first()
        if (label in ACADEMIC_FIELDS) {
          if (values.size < 2 || label in fields) return unsupported("Academic summary fields changed")
          fields[label] = PortalField(label, values[1])
        }
        continue
      }
      if (rowCells.size != 4 || hasMergedCells(rowCells)) return unsupported("Academic transcript row structure changed")
      if (values[0].isBlank() && values[1].isNotBlank() && values[2].isBlank() && values[3].isBlank() &&
        values[1].contains("სემესტრ") && SEMESTER_YEARS.containsMatchIn(values[1])
      ) {
        finishSemester()
        semester = values[1]
        continue
      }
      if (values[0].isBlank() && values[1].isBlank() && values[3].isBlank() &&
        DECIMAL.matches(values[2]) && rowCells[2].selectFirst("b") != null
      ) continue
      if (semester == null || !COURSE_INDEX.matches(values[0]) || values[1].isBlank() ||
        !isAcademicNumber(values[2]) || !isAcademicScore(values[3])
      ) return unsupported("Academic transcript course data changed")
      semesterRows += PortalRow(values)
    }
    finishSemester()
    return ParseResult.Success(
      PortalContent(title = "სასწავლო ბარათი", fields = fields.values.toList(), sections = sections),
    )
  }

  private fun isTranscriptHeading(values: List<String>): Boolean =
    values.size == 4 && values[0].isBlank() && values.drop(1) == TRANSCRIPT_HEADINGS.drop(1)

  private fun isAcademicNumber(value: String): Boolean = value in MISSING_VALUES || DECIMAL.matches(value)

  private fun isAcademicScore(value: String): Boolean = isAcademicNumber(value) || value in GRADE_LETTERS

  private fun jsonArrayLiteral(script: String, assignmentEnd: Int): String? {
    var start = assignmentEnd
    while (start < script.length && script[start].isWhitespace()) start++
    if (script.getOrNull(start) != '[') return null
    var depth = 0
    var nestedDepth = 0
    var inString = false
    var escaped = false
    for (index in start until script.length) {
      val character = script[index]
      if (inString) {
        when {
          escaped -> escaped = false
          character == '\\' -> escaped = true
          character == '"' -> inString = false
        }
        continue
      }
      when (character) {
        '"' -> inString = true
        '{' -> if (++nestedDepth > MAX_JSON_DEPTH) return null
        '}' -> if (--nestedDepth < 0) return null
        '[' -> {
          depth++
          if (depth > MAX_JSON_DEPTH) return null
        }
        ']' -> {
          depth--
          if (depth == 0) {
            if (nestedDepth != 0 || !script.substring(index + 1).trimStart().startsWith(';')) return null
            return script.substring(start, index + 1)
          }
          if (depth < 0) return null
        }
      }
    }
    return null
  }

  private fun jsonString(objectValue: JsonObject, name: String): String? =
    (objectValue[name] as? JsonPrimitive)?.takeIf { it.isString }?.content

  private fun parseDate(value: String): LocalDate? = try {
    LocalDate.parse(value)
  } catch (_: Exception) {
    null
  }

  private fun cells(row: Element): List<Element> = row.children().filter { it.tagName() == "td" || it.tagName() == "th" }

  private fun hasMergedCells(cells: List<Element>): Boolean = cells.any {
    (if (it.hasAttr("colspan")) it.attr("colspan").toIntOrNull() else 1) != 1 ||
      (if (it.hasAttr("rowspan")) it.attr("rowspan").toIntOrNull() else 1) != 1
  }

  private fun normalize(value: String): String = value.replace('\u00a0', ' ').replace(WHITESPACE, " ").trim()

  private fun unsupported(reason: String) = ParseResult.UnsupportedStructure(reason)

  companion object {
    private const val MAX_HTML_LENGTH = 4 * 1024 * 1024
    private const val MAX_JSON_DEPTH = 64
    private val WHITESPACE = Regex("\\s+")
    private val UNAVAILABLE_TEXT = listOf("temporarily unavailable", "under maintenance", "დროებით მიუწვდომელია", "მიმდინარეობს ტექნიკური")
    private val EVENTS_ASSIGNMENT = Regex("\\b(?:var|let|const)\\s+events\\s*=")
    private val CALENDAR_EVENTS_REFERENCE = Regex("\\bevents\\s*:\\s*events\\b")
    private val EXAM_HEADINGS = listOf("საგანი", "დღე", "საათი", "აუდიტორია", "ადგილი", "ფორმატი")
    private val PROGRAM_LABELS = listOf("ფაკულტეტი", "პროგრამა")
    private const val REMAINING_EMPTY_NOTICE = "ინფორმაცია არ მოიძებნა"
    private const val RATING_LABEL = "მიმდინარე სემესტრამდე გავლილი სასწავლო კურსების საშუალო ქულა"
    private val STATEMENT_HEADINGS = listOf("ნომერი", "სათაური", "თარიღი", "პასუხი", "კომენტარი")
    private val STATEMENT_VIEW_PATH = Regex("/ge/student/me/statements/view/\\d+/\\d+/?")
    private val TRANSCRIPT_HEADINGS = listOf("#", "კურსის დასახელება", "კრედიტი", "ქულა")
    private val ACADEMIC_FIELDS = setOf("ძირითადი სპეციალობა", "დამატებითი სპეციალობა", "საშუალო ქულა", "GPA")
    private val DECIMAL = Regex("\\d+(?:[.,]\\d+)?")
    private val COURSE_INDEX = Regex("[1-9]\\d*")
    private val SEMESTER_YEARS = Regex("\\d{4}\\s*[-–]\\s*\\d{4}")
    private val MISSING_VALUES = setOf("", "-", "—", "–", "N/A", "n/a")
    private val GRADE_LETTERS = setOf("A", "B", "C", "D", "E", "FX", "F")
  }
}
