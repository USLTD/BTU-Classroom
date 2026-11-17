package ge.usltd.btu_classroom.core.sync

import ge.usltd.btu_classroom.core.database.entities.CourseEntity
import ge.usltd.btu_classroom.core.database.entities.CourseWithEnrollment
import ge.usltd.btu_classroom.core.database.entities.EnrollmentEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GradeChangeDetectorTest {
  @Test
  fun firstSynchronizationDoesNotNotify() {
    assertFalse(GradeChangeDetector.hasChangedScores(emptyList(), listOf(course("math", 80.0))))
  }

  @Test
  fun changedUniversityReportedScoreNotifies() {
    assertTrue(
      GradeChangeDetector.hasChangedScores(listOf(course("math", 80.0)), listOf(course("math", 85.0))),
    )
  }

  @Test
  fun unchangedScoresAndReorderedCoursesDoNotNotify() {
    assertFalse(
      GradeChangeDetector.hasChangedScores(
        listOf(course("math", 80.0), course("english", 90.0)),
        listOf(course("english", 90.0), course("math", 80.0)),
      ),
    )
  }

  @Test
  fun firstPublishedScoreOnKnownCourseNotifiesIncludingAnActualZero() {
    assertTrue(
      GradeChangeDetector.hasChangedScores(listOf(course("math", null)), listOf(course("math", 0.0))),
    )
    assertTrue(
      GradeChangeDetector.hasChangedScores(listOf(course("math", null)), listOf(course("math", 85.0))),
    )
  }

  @Test
  fun unavailableScoreDoesNotNotifyOrBecomeZero() {
    assertFalse(
      GradeChangeDetector.hasChangedScores(listOf(course("math", 0.0)), listOf(course("math", null))),
    )
    assertFalse(
      GradeChangeDetector.hasChangedScores(listOf(course("math", 85.0)), listOf(course("math", null))),
    )
    assertFalse(
      GradeChangeDetector.hasChangedScores(listOf(course("math", null)), listOf(course("math", null))),
    )
  }

  @Test
  fun newlySelectedCourseDoesNotNotifyAsAGradeChange() {
    assertFalse(
      GradeChangeDetector.hasChangedScores(
        listOf(course("math", 85.0)),
        listOf(course("math", 85.0), course("english", 90.0)),
      ),
    )
  }

  @Test
  fun changedActualZeroScoreNotifies() {
    assertTrue(
      GradeChangeDetector.hasChangedScores(listOf(course("math", 0.0)), listOf(course("math", 5.0))),
    )
  }

  @Test
  fun newSemesterAndRemovedCoursesDoNotNotify() {
    assertFalse(
      GradeChangeDetector.hasChangedScores(listOf(course("fall::math", 80.0)), listOf(course("spring::math", 85.0))),
    )
    assertFalse(GradeChangeDetector.hasChangedScores(listOf(course("math", 80.0)), emptyList()))
  }

  private fun course(key: String, score: Double?) = CourseWithEnrollment(
    course = CourseEntity(
      courseKey = key,
      courseId = key,
      studentKey = "test_student",
      semesterKey = "test_semester",
      semesterName = "Test semester",
      code = key,
      title = "Sanitized course",
      credits = 5.0,
      minCredit = null,
      lecturer = null,
      syllabusSummary = null,
      detailPath = null,
      resourcesJson = null,
      detailSyncedAtMillis = null,
      detailStatus = null,
    ),
    enrollment = EnrollmentEntity(
      enrollmentKey = key,
      studentKey = "test_student",
      semesterKey = "test_semester",
      courseId = key,
      totalScore = score,
      statusLabel = null,
      updatedAtMillis = 0,
    ),
  )
}
