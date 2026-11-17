package ge.usltd.btu_classroom

import ge.usltd.btu_classroom.core.parsing.ParseResult
import ge.usltd.btu_classroom.core.schedule.WeeklyScheduleParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyScheduleParserTest {
  private val parser = WeeklyScheduleParser()

  @Test fun parsesTheInspectedGeorgianWeekdayAndRepeatedHeaderStructure() {
    val entries = (parser.parse(fixture()) as ParseResult.Success).data
    assertEquals(3, entries.size)
    assertEquals(listOf(1, 1, 4), entries.map { it.dayOfWeek })
    assertEquals("09:00", entries[0].startTime)
    assertEquals("09:50", entries[0].endTime)
    assertEquals("BAI - Sample course", entries[0].courseTitle)
    assertEquals("A101", entries[0].room)
    assertEquals("ჯგუფი 1.1", entries[0].group)
    assertEquals("Sample lecturer", entries[0].lecturers)
    assertNull(entries[0].notes)
    assertEquals("Sample note", entries[1].notes)
    assertEquals("Sample lecturer A, Sample lecturer B", entries[2].lecturers)
  }

  @Test fun nullableFieldsRemainMissingAndRoomChangesKeepTheEntryIdentity() {
    val original = (parser.parse(fixture()) as ParseResult.Success).data.first()
    val changed = fixture().replace("<td>A101</td>", "<td></td>")
      .replace("<td>Sample lecturer</td>", "<td></td>")
    val updated = (parser.parse(changed) as ParseResult.Success).data.first()
    assertEquals(original.id, updated.id)
    assertNull(updated.room)
    assertNull(updated.lecturers)
  }

  @Test fun validHeadersWithNoClassesAreEmptyButAnUnidentifiedBlankPageIsUnsupported() {
    assertEquals(ParseResult.Empty, parser.parse(table(HEADER)))
    assertTrue(parser.parse("<h3>ჩემი ცხრილი</h3><table id='groups'><tbody></tbody></table>")
      is ParseResult.UnsupportedStructure)
    assertTrue(parser.parse("<html><body>Nothing was returned.</body></html>")
      is ParseResult.UnsupportedStructure)
  }

  @Test fun rejectsPartialSnapshotsInsteadOfDiscardingAMalformedClass() {
    val changed = fixture().replace("<td>10:00 - 10:50</td>", "<td>invalid</td>")
    assertTrue(parser.parse(changed) is ParseResult.UnsupportedStructure)
    val shortRow = fixture().replace("<td>Sample note</td>", "")
    assertTrue(parser.parse(shortRow) is ParseResult.UnsupportedStructure)
  }

  @Test fun rejectsChangedHeaderColumnsAndMergedEmptyPlaceholders() {
    assertTrue(parser.parse(fixture().replace("დამატებითი ინფორმაცია", "Unknown column"))
      is ParseResult.UnsupportedStructure)
    assertTrue(parser.parse(table("$HEADER<tr><td colspan='6'></td></tr>"))
      is ParseResult.UnsupportedStructure)
  }

  @Test fun rejectsUnknownWeekdaysAndRowsWithoutWeekdayContext() {
    assertTrue(parser.parse(fixture().replace("ორშაბათი", "Unknown day"))
      is ParseResult.UnsupportedStructure)
    assertTrue(parser.parse(table("$HEADER$ROW")) is ParseResult.UnsupportedStructure)
  }

  @Test fun rejectsImpossibleReversedAndUndocumentedOvernightTimes() {
    for (invalid in listOf("25:00 - 25:50", "09:60 - 10:50", "10:00 - 09:50", "23:00 - 01:00")) {
      assertTrue(invalid, parser.parse(fixture().replace("09:00 - 09:50", invalid))
        is ParseResult.UnsupportedStructure)
    }
  }

  @Test fun distinguishesExpiredSessionsAndTemporaryPortalFailures() {
    assertEquals(ParseResult.SessionExpired,
      parser.parse("<html><body><a href='/ge/login/oauth/'>Sign in with Google</a></body></html>"))
    assertEquals(ParseResult.TemporarilyUnavailable,
      parser.parse("<html><body>მიმდინარეობს ტექნიკური სამუშაოები</body></html>"))
    assertEquals(ParseResult.TemporarilyUnavailable,
      parser.parse("<html><head><title>Just a moment...</title></head><body></body></html>"))
  }

  @Test fun collapsesIdenticalDuplicatesAndRejectsConflictingDuplicateEntries() {
    val duplicate = table("$DAY$HEADER$ROW$ROW")
    assertEquals(1, (parser.parse(duplicate) as ParseResult.Success).data.size)
    val conflicting = table("$DAY$HEADER$ROW${ROW.replace("A101", "B202")}")
    assertTrue(parser.parse(conflicting) is ParseResult.UnsupportedStructure)
  }

  @Test fun requiresRepeatedHeadersAfterEachObservedDaySection() {
    assertTrue(parser.parse(table("$DAY$HEADER$ROW$DAY")) is ParseResult.UnsupportedStructure)
    assertTrue(parser.parse(table("$DAY$ROW")) is ParseResult.UnsupportedStructure)
  }

  private fun fixture() = requireNotNull(
    javaClass.getResource("/fixtures/weekly-schedule-verified-sanitized.html"),
  ).readText()

  private fun table(content: String) = "<table id='groups'><tbody>$content</tbody></table>"

  private companion object {
    const val DAY = "<tr class='info'><td><h4>ორშაბათი</h4></td><td></td><td></td><td></td><td></td><td></td></tr>"
    const val HEADER = "<tr><td><b>დრო</b></td><td><b>აუდიტორია</b></td><td><b>კურსის დასახელება</b></td><td><b>ჯგუფი</b></td><td><b>ლექტორები</b></td><td><b>დამატებითი ინფორმაცია</b></td></tr>"
    const val ROW = "<tr class='tip'><td>09:00 - 09:50</td><td>A101</td><td>Sample course</td><td>ჯგუფი 1.1</td><td>Sample lecturer</td><td></td></tr>"
  }
}
