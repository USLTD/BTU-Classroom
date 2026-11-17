package ge.usltd.btu_classroom

import ge.usltd.btu_classroom.core.parsing.ParseResult
import ge.usltd.btu_classroom.core.portal.PortalContent
import ge.usltd.btu_classroom.core.portal.PortalModule
import ge.usltd.btu_classroom.core.portal.PortalPageParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortalPageParserTest {
  private val parser = PortalPageParser()

  @Test fun `exam rows retain university date labels without fabricating a year`() {
    val content = success(PortalModule.EXAMS, fixture("portal-exams-georgian.html"))
    val section = content.sections.single()
    assertEquals(listOf("საგანი", "დღე", "საათი", "აუდიტორია", "ადგილი", "ფორმატი"), section.columns)
    assertEquals(2, section.rows.size)
    assertEquals("ნოემბერი 09", section.rows.first().values[1])
    assertEquals("19:35", section.rows.first().values[2])
    assertEquals("B --205", section.rows.last().values[3])
    assertEquals("-", section.rows.last().values[4])
    assertFalse(section.rows.any { row -> row.values.any { it.contains("2026") } })
  }

  @Test fun `a short exam row rejects the whole snapshot rather than returning partial data`() {
    val html = fixture("portal-exams-georgian.html").replace("<td>A-411</td>", "")
    assertTrue(parser.parse(PortalModule.EXAMS, html) is ParseResult.UnsupportedStructure)
  }

  @Test fun `unexpected exam columns require parser review`() {
    val html = fixture("portal-exams-georgian.html").replace("<td>ფორმატი</td>", "<td>Unknown column</td>")
    assertTrue(parser.parse(PortalModule.EXAMS, html) is ParseResult.UnsupportedStructure)
  }

  @Test fun `valid empty examination table is distinguished from missing structure`() {
    val html = fixture("portal-exams-georgian.html").replace(Regex("<tbody>[\\s\\S]*?</tbody>"), "<tbody></tbody>")
    val result = parser.parse(PortalModule.EXAMS, html)
    assertTrue(result == ParseResult.Empty || result is ParseResult.Success && result.data.sections.all { it.rows.isEmpty() })
    assertTrue(parser.parse(PortalModule.EXAMS, "<html><body>No table supplied</body></html>") is ParseResult.UnsupportedStructure)
  }

  @Test fun `program summary includes only disabled current program values`() {
    val content = success(PortalModule.PROGRAM, fixture("portal-program-georgian.html"))
    assertEquals(listOf("ფაკულტეტი", "პროგრამა"), content.fields.map { it.label })
    assertEquals(listOf("Sample faculty", "Sample degree"), content.fields.map { it.value })
    val serializedSummary = content.toString()
    assertFalse(serializedSummary.contains("fixture-hidden-value"))
    assertFalse(serializedSummary.contains("Unselected sample faculty"))
    assertFalse(serializedSummary.contains("შენახვა"))
  }

  @Test fun `editable program form is not mistaken for current confirmed academic information`() {
    val html = fixture("portal-program-georgian.html").replace(" disabled=\"disabled\"", "")
    assertTrue(parser.parse(PortalModule.PROGRAM, html) is ParseResult.UnsupportedStructure)
  }

  @Test fun `missing labels in current program form reject an ambiguous snapshot`() {
    val html = fixture("portal-program-georgian.html").replace(
      "<label class=\"col-sm-4 control-label\">პროგრამა</label>", "",
    )
    assertTrue(parser.parse(PortalModule.PROGRAM, html) is ParseResult.UnsupportedStructure)
  }

  @Test fun `calendar preserves historical dates and safely reads brackets and escaped quotes`() {
    val content = success(PortalModule.CALENDAR, fixture("portal-calendar-historical.html"))
    assertEquals(listOf("2017-01-01", "2018-04-06"), content.calendarEvents.map { it.date })
    assertEquals("2018-04-09", content.calendarEvents.last().endDate)
    assertEquals("Sample [holiday]", content.calendarEvents.first().title)
    assertEquals("Sample \"break\"", content.calendarEvents.last().title)
  }

  @Test fun `invalid published calendar dates reject the whole snapshot`() {
    val html = fixture("portal-calendar-historical.html").replace("2018-04-06", "2018-13-42")
    assertTrue(parser.parse(PortalModule.CALENDAR, html) is ParseResult.UnsupportedStructure)
  }

  @Test fun `calendar JavaScript expressions are never treated as JSON events`() {
    val html = """
      <div id="calendar"></div><script>
      var events = [{start: '2018-01-01', title: window.secretValue}];
      ${'$'}('#calendar').fullCalendar({events: events});
      </script>
    """.trimIndent()
    assertTrue(parser.parse(PortalModule.CALENDAR, html) is ParseResult.UnsupportedStructure)
  }

  @Test fun `calendar container without event data is unsupported rather than empty`() {
    assertTrue(parser.parse(PortalModule.CALENDAR, "<div id='calendar'></div>") is ParseResult.UnsupportedStructure)
  }

  @Test fun `known calendar with an empty published event array is a valid empty result`() {
    val html = """
      <div id="calendar"></div><script>var events = [];
      ${'$'}('#calendar').fullCalendar({events: events});</script>
    """.trimIndent()
    val result = parser.parse(PortalModule.CALENDAR, html)
    assertTrue(result == ParseResult.Empty || result is ParseResult.Success && result.data.calendarEvents.isEmpty())
  }

  @Test fun `portal login and maintenance responses are classified before academic structures`() {
    val login = "<html><body><a href='/ge/login/index'>Login</a></body></html>"
    val unavailable = "<html><body><div class='alert'>დროებით მიუწვდომელია</div></body></html>"
    for (module in listOf(PortalModule.EXAMS, PortalModule.PROGRAM, PortalModule.CALENDAR)) {
      assertEquals(ParseResult.SessionExpired, parser.parse(module, login))
      assertEquals(ParseResult.TemporarilyUnavailable, parser.parse(module, unavailable))
    }
  }

  @Test fun `remaining courses require the observed no-data notice inside the academic container`() {
    assertEquals(ParseResult.Empty, parser.parse(PortalModule.REMAINING,
      "<div class='col-md-18'>ინფორმაცია არ მოიძებნა</div>"))
    assertTrue(parser.parse(PortalModule.REMAINING,
      "<div class='col-md-18'>A different page response</div>") is ParseResult.UnsupportedStructure)
    assertTrue(parser.parse(PortalModule.REMAINING,
      "<aside>ინფორმაცია არ მოიძებნა</aside><div class='col-md-18'>Unknown content</div>") is ParseResult.UnsupportedStructure)
  }

  @Test fun `statements retain published columns and the confirmed read-only detail link`() {
    val content = success(PortalModule.STATEMENTS, fixture("portal-statements-georgian.html"))
    val section = content.sections.single()
    assertEquals(listOf("ნომერი", "სათაური", "თარიღი", "პასუხი", "კომენტარი"), section.columns)
    val row = section.rows.single()
    assertEquals("SAMPLE-001/26", row.values.first())
    assertEquals("2026-11-02 14:31", row.values[2])
    assertEquals("Sample response text", row.values.last())
    assertEquals("https://classroom.btu.edu.ge/ge/student/me/statements/view/1001/47", row.links.single().url)
    assertFalse(content.toString().contains("statements/add"))
  }

  @Test fun `statement links exclude external destinations and mutation paths`() {
    val html = fixture("portal-statements-georgian.html").replace("SAMPLE-001/26</a>", """
      SAMPLE-001/26</a><a href="https://example.org/document">External</a>
      <a href="/ge/student/me/statements/delete/1001">Delete</a>
      <a href="/ge/student/me/statements/add">Add</a>
    """.trimIndent())
    val row = success(PortalModule.STATEMENTS, html).sections.single().rows.single()
    assertEquals(listOf("https://classroom.btu.edu.ge/ge/student/me/statements/view/1001/47"), row.links.map { it.url })
  }

  @Test fun `malformed statements cannot replace a complete cached snapshot`() {
    val html = fixture("portal-statements-georgian.html").replace("<td>დადებითი</td>", "")
    assertTrue(parser.parse(PortalModule.STATEMENTS, html) is ParseResult.UnsupportedStructure)
  }

  @Test fun `published statement comments are not mistaken for page maintenance notices`() {
    val comment = "An earlier reply mentioned under maintenance."
    val html = fixture("portal-statements-georgian.html").replace("Sample response text", comment)
    val content = success(PortalModule.STATEMENTS, html)
    assertEquals(comment, content.sections.single().rows.single().values.last())
  }

  @Test fun `rating preserves the university reported average without relabeling it as GPA`() {
    val content = success(PortalModule.RATING, fixture("portal-rating-georgian.html"))
    assertEquals("72.25", content.fields.single().value)
    assertFalse(content.fields.single().label.contains("GPA", ignoreCase = true))
    assertTrue(content.sections.isEmpty())
    assertTrue(parser.parse(PortalModule.RATING, "<div class='col-md-18'><legend>რეიტინგი</legend></div>") is ParseResult.UnsupportedStructure)
  }

  @Test fun `academic card preserves semester rows and nullable grade markers`() {
    val content = success(PortalModule.ACADEMIC_CARD, fixture("portal-academic-card-georgian.html"))
    assertEquals(2, content.sections.size)
    assertEquals("2025-2026 წლის შემოდგომის სემესტრი", content.sections.first().title)
    assertEquals("2025-2026 წლის გაზაფხულის სემესტრი", content.sections.last().title)
    assertEquals(listOf("#", "კურსის დასახელება", "კრედიტი", "ქულა"), content.sections.first().columns)
    assertEquals(2, content.sections.first().rows.size)
    assertEquals(listOf("2", "Another sample course", "6", "-"), content.sections.first().rows.last().values)
    assertEquals(listOf("1", "Later sample course", "6", "81"), content.sections.last().rows.single().values)
    assertFalse(content.sections.flatMap { it.rows }.any { it.values.contains("48") })
  }

  @Test fun `academic card stores only approved academic fields and excludes profile identifiers`() {
    val content = success(PortalModule.ACADEMIC_CARD, fixture("portal-academic-card-georgian.html"))
    assertEquals(setOf("ძირითადი სპეციალობა", "დამატებითი სპეციალობა", "საშუალო ქულა", "GPA"), content.fields.map { it.label }.toSet())
    assertEquals("Sample degree", content.fields.single { it.label == "ძირითადი სპეციალობა" }.value)
    val academicSnapshot = content.toString()
    assertFalse(academicSnapshot.contains("Sample student"))
    assertFalse(academicSnapshot.contains("FIXTURE-ID-DO-NOT-SAVE"))
    assertFalse(academicSnapshot.contains("01.01.2000"))
    assertFalse(academicSnapshot.contains("პირადი №"))
    assertFalse(academicSnapshot.contains("დაბადების თარიღი"))
  }

  @Test fun `malformed academic transcript rows reject the whole record`() {
    val html = fixture("portal-academic-card-georgian.html").replace("<td>Later sample course</td><td>6</td><td>81</td>",
      "<td>Later sample course</td><td>6</td>")
    assertTrue(parser.parse(PortalModule.ACADEMIC_CARD, html) is ParseResult.UnsupportedStructure)
  }

  private fun success(module: PortalModule, html: String): PortalContent {
    val result = parser.parse(module, html)
    assertTrue("Expected validated $module content but received $result", result is ParseResult.Success)
    return (result as ParseResult.Success).data
  }

  private fun fixture(name: String): String =
    requireNotNull(javaClass.getResource("/fixtures/$name")).readText()
}
