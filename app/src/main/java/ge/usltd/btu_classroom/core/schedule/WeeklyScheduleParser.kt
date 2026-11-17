package ge.usltd.btu_classroom.core.schedule

import ge.usltd.btu_classroom.core.network.ResponseClassifier
import ge.usltd.btu_classroom.core.parsing.BtuPageParser
import ge.usltd.btu_classroom.core.parsing.ParseResult
import java.security.MessageDigest
import java.time.LocalTime
import java.util.Locale
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class WeeklyScheduleParser : BtuPageParser<List<ScheduleEntry>> {
  override fun parse(html: String): ParseResult<List<ScheduleEntry>> {
    if (html.isBlank()) return unsupported("Empty timetable response")
    if (ResponseClassifier.looksLikeLoginPage(html)) return ParseResult.SessionExpired

    val document = Jsoup.parse(html)
    val bodyText = normalize(document.body().text())
    if (bodyText.contains("მიმდინარეობს ტექნიკური") ||
      bodyText.contains("დროებით მიუწვდომელია") ||
      bodyText.contains("temporarily unavailable") ||
      bodyText.contains("under maintenance") ||
      document.title().lowercase(Locale.ROOT).contains("just a moment") ||
      document.selectFirst("#challenge-form") != null
    ) return ParseResult.TemporarilyUnavailable

    val identifiedTables = document.select("table#groups")
    if (identifiedTables.size > 1) return unsupported("Duplicate timetable containers")
    val tables = if (identifiedTables.isNotEmpty()) identifiedTables else {
      document.select("table").filter { table -> rowsOf(table).any { columnsOf(it) != null } }
    }
    if (tables.isEmpty()) return unsupported("Timetable column headings were not found")

    val entries = linkedMapOf<String, ScheduleEntry>()
    var recognizedHeaders = 0
    for (table in tables) {
      var day: Int? = null
      var columns: Map<Column, Int>? = null
      var dayNeedsHeader = false
      var tableHeaders = 0
      for (row in rowsOf(table)) {
        val cells = cellsOf(row)
        if (cells.isEmpty()) continue
        if (cells.all { it.text().isBlank() }) {
          if (cells.size != Column.entries.size || hasMergedCells(cells)) {
            return unsupported("An empty timetable row has an unexpected structure")
          }
          continue
        }

        val rowDay = DAYS[normalize(cells.first().text())]
        if (rowDay != null || row.hasClass("info") || row.selectFirst("h4") != null) {
          if (dayNeedsHeader) return unsupported("A timetable day is missing its column headings")
          if (rowDay == null || cells.size != Column.entries.size ||
            cells.drop(1).any { it.text().isNotBlank() } || hasMergedCells(cells)
          ) return unsupported("Unrecognized timetable day row")
          day = rowDay
          columns = null
          dayNeedsHeader = true
          continue
        }

        val header = columnsOf(row)
        if (header != null) {
          columns = header
          dayNeedsHeader = false
          tableHeaders++
          recognizedHeaders++
          continue
        }

        if (cells.any { columnOf(it.text()) != null }) {
          return unsupported("Incomplete or changed timetable column headings")
        }
        val activeDay = day ?: return unsupported("A timetable entry has no recognized weekday")
        val activeColumns = columns ?: return unsupported("A timetable entry has no column headings")
        if (cells.size != Column.entries.size || hasMergedCells(cells)) {
          return unsupported("A timetable entry has an unexpected number of cells")
        }
        fun value(column: Column): String = cells[activeColumns.getValue(column)].text().trim()
        val range = TIME_RANGE.matchEntire(value(Column.TIME))
          ?: return unsupported("A timetable entry has an invalid time range")
        val start = range.groupValues[1]
        val end = range.groupValues[2]
        if (!LocalTime.parse(start).isBefore(LocalTime.parse(end))) {
          return unsupported("A timetable entry has an unsupported overnight or reversed time range")
        }
        val title = value(Column.COURSE)
        if (title.isBlank()) return unsupported("A timetable entry has no course title")
        val group = value(Column.GROUP).ifBlank { null }
        val entry = ScheduleEntry(
          id = stableId(activeDay, start, title, group),
          dayOfWeek = activeDay,
          startTime = start,
          endTime = end,
          courseTitle = title,
          room = value(Column.ROOM).ifBlank { null },
          group = group,
          lecturers = value(Column.LECTURERS).ifBlank { null },
          notes = value(Column.NOTES).ifBlank { null },
        )
        val previous = entries.putIfAbsent(entry.id, entry)
        if (previous != null && previous != entry) {
          return unsupported("Conflicting timetable entries share the same time and course")
        }
      }
      if (dayNeedsHeader || tableHeaders == 0) {
        return unsupported("Timetable headings were incomplete")
      }
    }
    if (recognizedHeaders == 0) return unsupported("Timetable headings were not found")
    return if (entries.isEmpty()) ParseResult.Empty else ParseResult.Success(entries.values.toList())
  }

  private fun rowsOf(table: Element): List<Element> =
    table.select("tr").filter { it.closest("table") === table }

  private fun cellsOf(row: Element): List<Element> =
    row.children().filter { it.tagName() == "td" || it.tagName() == "th" }

  private fun columnsOf(row: Element): Map<Column, Int>? {
    val cells = cellsOf(row)
    if (cells.size != Column.entries.size || hasMergedCells(cells)) return null
    val roles = cells.map { columnOf(it.text()) ?: return null }
    if (roles.toSet().size != Column.entries.size) return null
    return roles.withIndex().associate { it.value to it.index }
  }

  private fun hasMergedCells(cells: List<Element>): Boolean = cells.any {
    it.attr("colspan").toIntOrNull()?.let { span -> span != 1 } == true ||
      it.attr("rowspan").toIntOrNull()?.let { span -> span != 1 } == true
  }

  private fun columnOf(value: String): Column? = when (normalize(value)) {
    "დრო", "time" -> Column.TIME
    "აუდიტორია", "room", "classroom" -> Column.ROOM
    "კურსის დასახელება", "course name", "course" -> Column.COURSE
    "ჯგუფი", "group" -> Column.GROUP
    "ლექტორები", "lecturers", "lecturer" -> Column.LECTURERS
    "დამატებითი ინფორმაცია", "additional information", "notes" -> Column.NOTES
    else -> null
  }

  private fun stableId(day: Int, start: String, title: String, group: String?): String {
    val source = listOf(day.toString(), start, title, group.orEmpty()).joinToString("\u001f")
    return MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8))
      .take(16).joinToString("") { "%02x".format(it.toInt() and 0xff) }
  }

  private fun normalize(value: String): String = value.replace('\u00a0', ' ')
    .trim().lowercase(Locale.ROOT).replace(WHITESPACE, " ")

  private fun unsupported(reason: String) = ParseResult.UnsupportedStructure(reason)

  private enum class Column { TIME, ROOM, COURSE, GROUP, LECTURERS, NOTES }

  private companion object {
    val WHITESPACE = Regex("\\s+")
    val TIME_RANGE = Regex("((?:[01]\\d|2[0-3]):[0-5]\\d)\\s*[-–—]\\s*((?:[01]\\d|2[0-3]):[0-5]\\d)")
    val DAYS = mapOf(
      "ორშაბათი" to 1, "სამშაბათი" to 2, "ოთხშაბათი" to 3, "ხუთშაბათი" to 4,
      "პარასკევი" to 5, "შაბათი" to 6, "კვირა" to 7,
      "monday" to 1, "tuesday" to 2, "wednesday" to 3, "thursday" to 4,
      "friday" to 5, "saturday" to 6, "sunday" to 7,
    )
  }
}
