package ge.usltd.btu_classroom

import ge.usltd.btu_classroom.core.parsing.ParseResult
import ge.usltd.btu_classroom.core.parsing.SelectedCoursesParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VerifiedSelectedCoursesParserTest {
  private val parser = SelectedCoursesParser()

  @Test fun liveAbbreviatedHeadersTooltipCodesAndNavigationSemesterArePreserved() {
    val html = requireNotNull(javaClass.getResource("/fixtures/verified-course-selected.html")).readText()
    val result = parser.parse(html)
    assertTrue(result is ParseResult.Success)
    val data = (result as ParseResult.Success).data
    val course = data.courses.single()
    assertEquals("SAMPLE16 SAMPLE22 / 0 / 0", course.code)
    assertEquals(0.0, course.minCredit)
    assertEquals(6.0, course.credits)
    assertNull(course.score)
    assertEquals("2026-2027 წლის შემოდგომის სემესტრი", course.semesterName)
    assertEquals("Sample Student", data.studentSummary?.displayName)
    assertEquals("/ge/student/me/course/index/1001", course.detailPath)
  }

  @Test fun unobservedCourseIdentifiersAndQueryActionsNeverBecomeDetailRoutes() {
    listOf("not-a-course", "1001?submit=yes").forEach { suffix ->
      val html = """
        <table><thead><tr><th>კოდი</th><th>კურსის დასახელება</th><th>კრედ</th></tr></thead>
        <tbody><tr><td>SAMPLE</td><td><a href="/ge/student/me/course/index/$suffix">Sample course</a></td><td>6</td></tr></tbody></table>
      """.trimIndent()
      val result = parser.parse(html) as ParseResult.Success
      assertNull(result.data.courses.single().detailPath)
      assertEquals("sample", result.data.courses.single().courseId)
    }
  }

  @Test fun externalProfileLinksCannotSupplyTheStudentIdentity() {
    val html = """
      <a href="https://other.example/ge/profile/index">Unrelated name</a>
      <table><thead><tr><th>კოდი</th><th>კურსის დასახელება</th><th>კრედ</th></tr></thead>
      <tbody><tr><td>SAMPLE</td><td>Sample course</td><td>6</td></tr></tbody></table>
    """.trimIndent()
    val result = parser.parse(html) as ParseResult.Success
    assertNull(result.data.studentSummary)
  }
}
