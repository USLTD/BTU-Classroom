package ge.usltd.btu_classroom.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import ge.usltd.btu_classroom.core.database.entities.CourseEntity
import ge.usltd.btu_classroom.core.database.entities.CourseWithEnrollment
import ge.usltd.btu_classroom.core.database.entities.EnrollmentEntity
import ge.usltd.btu_classroom.core.database.entities.GradeEntity
import ge.usltd.btu_classroom.core.database.entities.SemesterEntity
import ge.usltd.btu_classroom.core.database.entities.StudentEntity
import ge.usltd.btu_classroom.core.database.entities.SyncMetadataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {
  @Query("SELECT * FROM students WHERE studentKey = :studentKey LIMIT 1")
  fun observeStudent(studentKey: String): Flow<StudentEntity?>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertStudent(student: StudentEntity)

  @Query("DELETE FROM students")
  suspend fun deleteAllStudents()
}

@Dao
interface CourseDao {
  @Query("SELECT * FROM semesters WHERE studentKey = :studentKey ORDER BY sortOrder ASC")
  fun observeSemesters(studentKey: String): Flow<List<SemesterEntity>>

  @Transaction
  @Query("SELECT * FROM courses WHERE studentKey = :studentKey ORDER BY semesterKey ASC, title ASC")
  fun observeCoursesWithEnrollment(studentKey: String): Flow<List<CourseWithEnrollment>>

  @Transaction
  @Query("SELECT * FROM courses WHERE courseKey = :courseKey LIMIT 1")
  fun observeCourseWithEnrollment(courseKey: String): Flow<CourseWithEnrollment?>

  @Query("SELECT * FROM grades WHERE courseKey = :courseKey ORDER BY sortOrder ASC")
  fun observeGradesForCourse(courseKey: String): Flow<List<GradeEntity>>

  @Query("SELECT * FROM courses WHERE studentKey = :studentKey")
  suspend fun getCoursesSnapshot(studentKey: String): List<CourseEntity>

  @Query("SELECT COUNT(*) FROM courses")
  suspend fun countAllCourses(): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSemesters(semesters: List<SemesterEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertCourses(courses: List<CourseEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertEnrollments(enrollments: List<EnrollmentEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertGrades(grades: List<GradeEntity>)

  @Query("DELETE FROM semesters WHERE studentKey = :studentKey")
  suspend fun deleteSemestersForStudent(studentKey: String)

  @Query("DELETE FROM courses WHERE studentKey = :studentKey")
  suspend fun deleteCoursesForStudent(studentKey: String)

  @Query("DELETE FROM enrollments WHERE studentKey = :studentKey")
  suspend fun deleteEnrollmentsForStudent(studentKey: String)

  @Query("DELETE FROM grades WHERE courseKey = :courseKey")
  suspend fun deleteGradesForCourse(courseKey: String)

  @Query("DELETE FROM grades WHERE studentKey = :studentKey")
  suspend fun deleteGradesForStudent(studentKey: String)

  @Query("DELETE FROM grades WHERE studentKey = :studentKey AND courseKey NOT IN (:courseKeys)")
  suspend fun deleteRemovedCourseGrades(studentKey: String, courseKeys: List<String>)

  @Query("DELETE FROM grades")
  suspend fun deleteAllGrades()

  @Query("DELETE FROM enrollments")
  suspend fun deleteAllEnrollments()

  @Query("DELETE FROM courses")
  suspend fun deleteAllCourses()

  @Query("DELETE FROM semesters")
  suspend fun deleteAllSemesters()

  @Transaction
  suspend fun replaceSelectedCoursesSnapshot(
    studentKey: String,
    semesters: List<SemesterEntity>,
    courses: List<CourseEntity>,
    enrollments: List<EnrollmentEntity>,
  ) {
    val existingByKey = getCoursesSnapshot(studentKey).associateBy { it.courseKey }
    val mergedCourses = courses.map { incoming ->
      val prev = existingByKey[incoming.courseKey]
      if (prev != null) {
        incoming.copy(
          credits = incoming.credits ?: prev.credits,
          minCredit = incoming.minCredit ?: prev.minCredit,
          lecturer = incoming.lecturer ?: prev.lecturer,
          detailPath = incoming.detailPath ?: prev.detailPath,
          syllabusSummary = incoming.syllabusSummary ?: prev.syllabusSummary,
          resourcesJson = incoming.resourcesJson ?: prev.resourcesJson,
          detailSyncedAtMillis = incoming.detailSyncedAtMillis ?: prev.detailSyncedAtMillis,
          detailStatus = incoming.detailStatus ?: prev.detailStatus,
        )
      } else {
        incoming
      }
    }

    if (courses.isEmpty()) {
      deleteGradesForStudent(studentKey)
    } else {
      deleteRemovedCourseGrades(studentKey, courses.map { it.courseKey })
    }
    deleteEnrollmentsForStudent(studentKey)
    deleteCoursesForStudent(studentKey)
    deleteSemestersForStudent(studentKey)

    if (semesters.isNotEmpty()) {
      insertSemesters(semesters)
    }
    if (mergedCourses.isNotEmpty()) {
      insertCourses(mergedCourses)
    }
    if (enrollments.isNotEmpty()) {
      insertEnrollments(enrollments)
    }
  }

  @Transaction
  suspend fun updateCourseDetailAndGrades(
    course: CourseEntity,
    enrollment: EnrollmentEntity?,
    grades: List<GradeEntity>,
    replaceGrades: Boolean = true,
  ) {
    insertCourses(listOf(course))
    if (enrollment != null) {
      insertEnrollments(listOf(enrollment))
    }
    if (replaceGrades) {
      deleteGradesForCourse(course.courseKey)
      if (grades.isNotEmpty()) {
        insertGrades(grades)
      }
    }
  }

  @Transaction
  suspend fun deleteAllAcademicData() {
    deleteAllGrades()
    deleteAllEnrollments()
    deleteAllCourses()
    deleteAllSemesters()
  }
}

@Dao
interface SyncMetadataDao {
  @Query("SELECT * FROM sync_metadata")
  fun observeAll(): Flow<List<SyncMetadataEntity>>

  @Query("SELECT * FROM sync_metadata WHERE moduleId = :moduleId LIMIT 1")
  fun observeModule(moduleId: String): Flow<SyncMetadataEntity?>

  @Query("SELECT * FROM sync_metadata WHERE moduleId = :moduleId LIMIT 1")
  suspend fun getModule(moduleId: String): SyncMetadataEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(metadata: SyncMetadataEntity)

  @Query("DELETE FROM sync_metadata")
  suspend fun clearAll()
}
