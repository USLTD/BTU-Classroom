package ge.usltd.btu_classroom

import ge.usltd.btu_classroom.core.parsing.CourseDetailParser
import ge.usltd.btu_classroom.core.parsing.ParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseDetailParserTest {

  private val parser = CourseDetailParser()

  @Test
  fun `parses course metadata, grade components with nullable scores, and safe BTU resource links`() {
    val html = requireNotNull(javaClass.getResource("/fixtures/course-detail-georgian.html")).readText()

    val result = parser.parse(html)
    assertTrue(result is ParseResult.Success)
    val detail = (result as ParseResult.Success).data

    assertEquals("ალგორითმები და მონაცემთა სტრუქტურები", detail.title)
    assertEquals("CS301", detail.code)
    assertEquals("Test Lecturer", detail.lecturer)
    assertEquals("2026 შემოდგომა", detail.semesterName)
    assertEquals(6.0, detail.credits)
    assertEquals(74.5, detail.totalScore)
    assertEquals(3, detail.gradeComponents.size)
    assertTrue(detail.hasGradeSnapshot)
    assertEquals(26.5, detail.gradeComponents[0].score)
    assertNull(detail.gradeComponents[2].score)
    assertEquals(40.0, detail.gradeComponents[2].maxScore)

    // External non-BTU and javascript links are strictly stripped
    assertEquals(1, detail.resources.size)
    assertEquals("https://classroom.btu.edu.ge/files/syllabus_cs301.pdf", detail.resources[0].url)
  }

  @Test
  fun `returns SessionExpired on login HTML and UnsupportedStructure on unrecognized page`() {
    val loginHtml = "<html><body><a href='/ge/login/index'>Login</a></body></html>"
    assertEquals(ParseResult.SessionExpired, parser.parse(loginHtml))

    val unrecognized = "<html><body><span>hello</span></body></html>"
    assertTrue(parser.parse(unrecognized) is ParseResult.UnsupportedStructure)
  }

  @Test
  fun `does not parse an error page heading as a course title`() {
    assertTrue(parser.parse("<main><h1>Internal server error</h1><p>Please try again later.</p></main>")
      is ParseResult.UnsupportedStructure)
  }

  @Test
  fun `does not expose unrelated portal links as course resources`() {
    val html = """
      <main><h1 class="course-title">Algorithms</h1>
      <a href="/ge/student/me/schedule">Timetable</a>
      <a href="/files/algorithms.pdf">Syllabus</a>
      </main>
    """.trimIndent()
    val detail = (parser.parse(html) as ParseResult.Success).data
    assertEquals(1, detail.resources.size)
    assertEquals("Syllabus", detail.resources.single().title)
    assertNull(detail.totalScore)
  }

  @Test
  fun `grade header rows without thead are not interpreted as components`() {
    val html = """
      <main><h1 class="course-title">Algorithms</h1>
      <table><tr><th>Category</th><th>Score</th><th>Max</th></tr>
      <tr><td>Midterm</td><td>12</td><td>20</td></tr></table></main>
    """.trimIndent()
    val grades = (parser.parse(html) as ParseResult.Success).data.gradeComponents
    assertEquals(1, grades.size)
    assertEquals("Midterm", grades.single().categoryName)
  }

  @Test
  fun `absent grade tables are not authoritative empty snapshots`() {
    val detail = (parser.parse("<h1 class='course-title'>Algorithms</h1>") as ParseResult.Success).data
    assertFalse(detail.hasGradeSnapshot)
    assertTrue(detail.gradeComponents.isEmpty())
  }

  @Test
  fun `a valid empty grade table is an authoritative snapshot`() {
    val detail = (parser.parse(courseDetailWithTable("""
      <table><thead><tr><th>Component</th><th>Score</th></tr></thead><tbody></tbody></table>
    """)) as ParseResult.Success).data
    assertTrue(detail.hasGradeSnapshot)
    assertTrue(detail.gradeComponents.isEmpty())
  }

  @Test
  fun `grade tables missing a required header are unsupported`() {
    val malformedTables = listOf(
      "<table><thead><tr><th>Component</th><th>Max</th></tr></thead><tbody></tbody></table>",
      "<table><thead><tr><th>Label</th><th>Score</th></tr></thead><tbody></tbody></table>",
      "<table class='grades'><thead><tr><th>Label</th><th>Value</th></tr></thead></table>",
      "<div data-grades><table><tr><th>Label</th><th>Value</th></tr></table></div>",
    )
    malformedTables.forEach { table ->
      assertTrue(parser.parse(courseDetailWithTable(table)) is ParseResult.UnsupportedStructure)
    }
  }

  @Test
  fun `short grade rows are unsupported`() {
    val html = courseDetailWithTable("""
      <table><thead><tr><th>Component</th><th>Score</th><th>Max</th></tr></thead>
      <tbody><tr><td>Midterm</td><td>12</td></tr></tbody></table>
    """)
    assertTrue(parser.parse(html) is ParseResult.UnsupportedStructure)
  }

  @Test
  fun `blank grade component names are unsupported`() {
    val html = courseDetailWithTable("""
      <table><thead><tr><th>Component</th><th>Score</th></tr></thead>
      <tbody><tr><td> </td><td>12</td></tr></tbody></table>
    """)
    assertTrue(parser.parse(html) is ParseResult.UnsupportedStructure)
  }

  @Test
  fun `mixed valid and malformed grade rows never produce a partial snapshot`() {
    val html = courseDetailWithTable("""
      <table><thead><tr><th>Component</th><th>Score</th></tr></thead>
      <tbody><tr><td>Midterm</td><td>12</td></tr><tr><td>Final</td></tr></tbody></table>
    """)
    assertTrue(parser.parse(html) is ParseResult.UnsupportedStructure)
  }

  @Test
  fun `merged grade rows are unsupported`() {
    val html = courseDetailWithTable("""
      <table><thead><tr><th>Component</th><th>Score</th></tr></thead>
      <tbody><tr><td colspan='2'>No data from the grade service</td></tr></tbody></table>
    """)
    assertTrue(parser.parse(html) is ParseResult.UnsupportedStructure)
  }

  private fun courseDetailWithTable(table: String): String =
    "<main><h1 class='course-title'>Algorithms</h1>$table</main>"
}
