package ge.usltd.btu_classroom.core.database.entities

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "students")
data class StudentEntity(
  @PrimaryKey val studentKey: String,
  val displayName: String?,
  val programName: String?,
  val activeSemesterKey: String?,
  val updatedAtMillis: Long,
)

@Entity(
  tableName = "semesters",
  indices = [Index(value = ["studentKey"])],
)
data class SemesterEntity(
  @PrimaryKey val semesterKey: String,
  val rawSemesterKey: String,
  val studentKey: String,
  val title: String,
  val sortOrder: Int,
  val isCurrent: Boolean,
)

@Entity(
  tableName = "courses",
  indices = [Index(value = ["studentKey", "semesterKey"])],
)
data class CourseEntity(
  @PrimaryKey val courseKey: String,
  val courseId: String,
  val studentKey: String,
  val semesterKey: String,
  val semesterName: String,
  val code: String,
  val title: String,
  val credits: Double?,
  val minCredit: Double?,
  val lecturer: String?,
  val syllabusSummary: String?,
  val detailPath: String?,
  val resourcesJson: String?,
  val detailSyncedAtMillis: Long?,
  val detailStatus: String?,
)

@Entity(
  tableName = "enrollments",
  indices = [Index(value = ["studentKey", "semesterKey"])],
)
data class EnrollmentEntity(
  @PrimaryKey val enrollmentKey: String,
  val studentKey: String,
  val semesterKey: String,
  val courseId: String,
  val totalScore: Double?,
  val statusLabel: String?,
  val updatedAtMillis: Long,
)

@Entity(
  tableName = "grades",
  indices = [Index(value = ["courseKey"])],
)
data class GradeEntity(
  @PrimaryKey val gradeKey: String,
  val courseKey: String,
  val studentKey: String,
  val semesterKey: String,
  val courseId: String,
  val categoryName: String,
  val score: Double?,
  val maxScore: Double?,
  val notes: String?,
  val sortOrder: Int,
)

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
  @PrimaryKey val moduleId: String,
  val lastAttemptAtMillis: Long,
  val lastSuccessAtMillis: Long?,
  val outcomeStatus: String,
  val message: String?,
)

data class CourseWithEnrollment(
  @Embedded val course: CourseEntity,
  @Relation(
    parentColumn = "courseKey",
    entityColumn = "enrollmentKey",
  )
  val enrollment: EnrollmentEntity?,
)
