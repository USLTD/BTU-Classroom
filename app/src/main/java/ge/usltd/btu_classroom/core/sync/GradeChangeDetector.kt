package ge.usltd.btu_classroom.core.sync

import ge.usltd.btu_classroom.core.database.entities.CourseWithEnrollment

internal object GradeChangeDetector {
  fun hasChangedScores(
    previous: List<CourseWithEnrollment>,
    current: List<CourseWithEnrollment>,
  ): Boolean {
    val previousScores = previous.associate { item ->
      item.course.courseKey to item.enrollment?.totalScore
    }
    return current.any { item ->
      val before = previousScores[item.course.courseKey]
      val after = item.enrollment?.totalScore
      previousScores.containsKey(item.course.courseKey) && after != null && before != after
    }
  }
}
