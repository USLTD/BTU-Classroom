package ge.usltd.btu_classroom

import ge.usltd.btu_classroom.core.parsing.CourseDetailParser
import ge.usltd.btu_classroom.core.parsing.ParseResult
import ge.usltd.btu_classroom.core.parsing.ParsedCourseDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VerifiedCourseDetailParserTest {
  private val parser = CourseDetailParser()

  @Test fun indexExposesVerifiedChildPagesWithoutTreatingNavigationAsResources() {
    val detail = success(parser.parse(fixture("index")))

    assertEquals("BAI - Sample course", detail.title)
    assertEquals("/ge/student/me/course/scores/1001/47", detail.scoresPath)
    assertEquals("/ge/student/me/course/files/1001/47", detail.filesPath)
    assertFalse(detail.hasGradeSnapshot)
    assertTrue(detail.gradeComponents.isEmpty())
    assertEquals(listOf("Sample.pdf"), detail.resources.map { it.title })
    assertEquals("https://classroom.btu.edu.ge/ge/student/me/courseSilabusFile/1001/47",
      detail.resources.single().url)
  }

  @Test fun childPageLinksRequireNumericSameOriginCourseRoutes() {
    val unsafeLinks = listOf(
      "https://example.com/ge/student/me/course/scores/1001/47",
      "https://classroom.btu.edu.ge:444/ge/student/me/course/scores/1001/47",
      "/ge/student/me/course/scores/not-a-course/47",
      "/ge/student/me/course/scores/1001/not-a-semester",
      "/ge/student/me/course/scores/1001/47?submit=yes",
      "/ge/messages/compose/123",
    )
    unsafeLinks.forEach { href ->
      val detail = success(parser.parse("""
        <div class="col-md-18"><h2 class="course-title">Sample course</h2>
        <a href="$href">შეფასებები</a>
        <a href="/ge/student/me/course/files/1001/47">სასწავლო მასალები</a></div>
      """))
      assertNull("Unsafe score link retained: $href", detail.scoresPath)
      assertEquals("/ge/student/me/course/files/1001/47", detail.filesPath)
    }
  }

  @Test fun scoresPreserveCategoryNamesNullableResultsAndExplicitMaximums() {
    val detail = success(parser.parseScores(fixture("scores")))

    assertTrue(detail.hasGradeSnapshot)
    assertEquals(3.0, detail.totalScore)
    assertNull(detail.credits)
    val first = detail.gradeComponents.first { it.categoryName == "პრაქტიკული დავალება 01 (max. 5)" }
    assertEquals(3.0, first.score)
    assertEquals(5.0, first.maxScore)
    val pending = detail.gradeComponents.first { it.categoryName == "პრაქტიკული დავალება 02 (max. 5)" }
    assertNull(pending.score)
    assertEquals(5.0, pending.maxScore)
    val midterm = detail.gradeComponents.first { it.categoryName.startsWith("შუალედური შეფასება 1") }
    assertNull(midterm.score)
    assertEquals(30.0, midterm.maxScore)
    assertTrue(detail.gradeComponents.none { it.categoryName == "სულ" || it.categoryName == "კრედიტი" })
  }

  @Test fun eligibilitySymbolsRemainNotesRatherThanNumericGrades() {
    val detail = success(parser.parseScores(fixture("scores")))
    val eligibility = detail.gradeComponents.first { it.categoryName == "დასკვნით გამოცდაზე გასვლის უფლება" }
    assertNull(eligibility.score)
    assertTrue(eligibility.notes.orEmpty().contains("✘"))
  }

  @Test fun malformedRecognizedScoresRejectTheEntireSnapshot() {
    val malformed = fixture("scores").replace(
      "<tr><td class=\"info\">პრაქტიკული დავალება 02 (max. 5)</td><td><div style=\"padding-left:20px\"><span></span></div></td></tr>",
      "<tr><td class=\"info\">პრაქტიკული დავალება 02 (max. 5)</td></tr>",
    )
    assertTrue(parser.parseScores(malformed) is ParseResult.UnsupportedStructure)
  }

  @Test fun missingScoreTableCannotErasePreviouslyCachedGrades() {
    assertTrue(parser.parseScores(fixture("index")) is ParseResult.UnsupportedStructure)
  }

  @Test fun recognizedEmptyScoreTableIsAnAuthoritativeGradeSnapshot() {
    val detail = success(parser.parseScores("""
      <div class="col-md-18"><h2>Sample course</h2>
      <table><thead><tr><th>შეფასების კრიტერიუმი</th><th>შეფასება</th></tr></thead>
      <tbody></tbody></table></div>
    """))
    assertTrue(detail.hasGradeSnapshot)
    assertTrue(detail.gradeComponents.isEmpty())
    assertNull(detail.totalScore)
  }

  @Test fun filesPageIncludesOnlyVerifiedDownloadResources() {
    val result = parser.parseResourcesPage(fixture("files"))
    assertTrue(result is ParseResult.Success)
    val resources = (result as ParseResult.Success).data
    assertEquals(listOf("Sample notes", "Sample reference"), resources.map { it.title })
    assertTrue(resources.all { it.url.startsWith("https://classroom.btu.edu.ge/uploads/") })
    assertTrue(resources.none { it.url.contains("/lector/") || it.url.contains("/messages/") })
  }

  @Test fun unsafeOnlyDocumentRowsNeverBecomeAnAuthoritativePartialSnapshot() {
    listOf(
      "https://example.com/uploads/71/1001.docx",
      "https://classroom.btu.edu.ge/uploads/71/1001.apk",
      "https://classroom.btu.edu.ge/ge/student/me/updBalance",
      "https://classroom.btu.edu.ge/ge/messages/compose/123",
      "javascript:alert(1)",
      "https://classroom.btu.edu.ge/uploads/not-a-directory/1001.docx",
    ).forEach { href ->
      val unsafeRow = "<tr><td><a href='$href'>Unrecognized resource</a></td><td></td></tr>"
      val html = fixture("files").replace("</tbody>", "$unsafeRow</tbody>")
      assertTrue(parser.parseResourcesPage(html) is ParseResult.UnsupportedStructure)
    }
  }

  @Test fun changedDownloadRouteCannotClearPreviouslyCachedResources() {
    val html = """
      <table id="files"><tbody><tr>
      <td><a href="/ge/student/me/course/download/1001/47">Sample notes</a></td><td></td>
      </tr></tbody></table>
    """.trimIndent()
    assertTrue(parser.parseResourcesPage(html) is ParseResult.UnsupportedStructure)
  }

  @Test fun mixedVerifiedAndChangedDocumentRowsRejectTheWholeSnapshot() {
    val changedRow = """
      <tr><td><a href="/uploads/new-format/sample.pdf">Changed document</a></td><td></td></tr>
    """.trimIndent()
    val html = fixture("files").replace("</tbody>", "$changedRow</tbody>")
    assertTrue(parser.parseResourcesPage(html) is ParseResult.UnsupportedStructure)
  }

  @Test fun aKnownLecturerHeaderWithoutDocumentsIsAnAuthoritativeEmptySnapshot() {
    val html = """
      <table id="files"><tbody><tr class="info"><td colspan="2">
      <a href="/ge/student/me/lector/123">Sample lecturer</a>
      <a href="/ge/messages/compose/123">Message</a>
      </td></tr></tbody></table>
    """.trimIndent()
    val result = parser.parseResourcesPage(html)
    assertTrue(result is ParseResult.Success)
    assertTrue((result as ParseResult.Success).data.isEmpty())
  }

  @Test fun resourcesRequireTheirVerifiedTableInsteadOfGeneralPageAnchors() {
    assertTrue(parser.parseResourcesPage(fixture("index")) is ParseResult.UnsupportedStructure)
  }

  @Test fun loginResponsesExpireBothLinkedPageParsers() {
    val html = "<html><body><a href='/ge/login/index'>Login</a></body></html>"
    assertEquals(ParseResult.SessionExpired, parser.parseScores(html))
    assertEquals(ParseResult.SessionExpired, parser.parseResourcesPage(html))
  }

  private fun fixture(name: String): String =
    requireNotNull(javaClass.getResource("/fixtures/verified-course-$name.html")).readText()

  private fun success(result: ParseResult<ParsedCourseDetail>): ParsedCourseDetail {
    assertTrue("Expected parsed course details, received $result", result is ParseResult.Success)
    return (result as ParseResult.Success).data
  }
}
