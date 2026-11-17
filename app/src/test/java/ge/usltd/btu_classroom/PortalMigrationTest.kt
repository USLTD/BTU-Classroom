package ge.usltd.btu_classroom

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import ge.usltd.btu_classroom.core.database.BtuDatabase
import ge.usltd.btu_classroom.core.portal.PortalSnapshotEntity
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PortalMigrationTest {

  @Test
  fun `migration adds portal snapshots without losing cached academic data`() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val databaseName = "portal_migration_${System.nanoTime()}.db"
    val schema = JSONObject(versionOneSchema().readText()).getJSONObject("database")
    val helper = FrameworkSQLiteOpenHelperFactory().create(
      SupportSQLiteOpenHelper.Configuration.builder(context)
        .name(databaseName)
        .callback(object : SupportSQLiteOpenHelper.Callback(1) {
          override fun onCreate(db: SupportSQLiteDatabase) {
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
              val entity = entities.getJSONObject(index)
              val table = entity.getString("tableName")
              db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
              val indices = entity.optJSONArray("indices") ?: continue
              for (indexNumber in 0 until indices.length()) {
                db.execSQL(indices.getJSONObject(indexNumber).getString("createSql").replace("\${TABLE_NAME}", table))
              }
            }
            val setupQueries = schema.getJSONArray("setupQueries")
            for (index in 0 until setupQueries.length()) db.execSQL(setupQueries.getString(index))
          }

          override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
            error("The legacy helper must only create version 1")
          }
        })
        .build(),
    )
    var database: BtuDatabase? = null
    try {
      helper.writableDatabase.apply {
        execSQL(
          "INSERT INTO students VALUES (?, ?, ?, ?, ?)",
          arrayOf<Any?>("default_student", "Test Student", "Computer Science", SEMESTER_KEY, 1000L),
        )
        execSQL(
          "INSERT INTO semesters VALUES (?, ?, ?, ?, ?, ?)",
          arrayOf<Any?>(SEMESTER_KEY, "fall2026", "default_student", "Fall 2026", 0, 1),
        )
        execSQL(
          """INSERT INTO courses (
            courseKey, courseId, studentKey, semesterKey, semesterName, code, title,
            credits, minCredit, lecturer, syllabusSummary, detailPath, resourcesJson,
            detailSyncedAtMillis, detailStatus
          ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
          arrayOf<Any?>(
            COURSE_KEY, "501", "default_student", SEMESTER_KEY, "Fall 2026", "CS301", "Algorithms",
            6.0, 51.0, "Test Lecturer", "Graph algorithms", "/ge/student/me/course/index/501", null,
            1000L, "COMPATIBLE",
          ),
        )
        execSQL(
          "INSERT INTO enrollments VALUES (?, ?, ?, ?, ?, ?, ?)",
          arrayOf<Any?>(COURSE_KEY, "default_student", SEMESTER_KEY, "501", 86.5, null, 1000L),
        )
        execSQL(
          "INSERT INTO grades VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
          arrayOf<Any?>("$COURSE_KEY::midterm", COURSE_KEY, "default_student", SEMESTER_KEY, "501", "Midterm", 12.5, 20.0, null, 0),
        )
        execSQL(
          "INSERT INTO sync_metadata VALUES (?, ?, ?, ?, ?)",
          arrayOf<Any?>("selected_courses", 1000L, 1000L, "SUCCESS", null),
        )
        query("PRAGMA user_version").use { cursor ->
          assertTrue(cursor.moveToFirst())
          assertEquals(1, cursor.getInt(0))
        }
      }
      helper.close()

      val migrated = Room.databaseBuilder(context, BtuDatabase::class.java, databaseName)
        .addMigrations(BtuDatabase.MIGRATION_1_2)
        .allowMainThreadQueries()
        .build()
      database = migrated

      val cachedCourse = migrated.courseDao().observeCourseWithEnrollment(COURSE_KEY).first()
      assertEquals("Algorithms", cachedCourse?.course?.title)
      assertEquals("Test Lecturer", cachedCourse?.course?.lecturer)
      assertEquals(1000L, cachedCourse?.course?.detailSyncedAtMillis)
      assertEquals(86.5, cachedCourse?.enrollment?.totalScore)
      val grade = migrated.courseDao().observeGradesForCourse(COURSE_KEY).first().single()
      assertEquals("Midterm", grade.categoryName)
      assertEquals(12.5, grade.score)
      assertEquals(20.0, grade.maxScore)
      assertEquals(1000L, migrated.syncMetadataDao().getModule("selected_courses")?.lastSuccessAtMillis)
      assertEquals("Test Student", migrated.studentDao().observeStudent("default_student").first()?.displayName)
      assertEquals("Fall 2026", migrated.courseDao().observeSemesters("default_student").first().single().title)

      val portalDao = migrated.portalDao()
      assertNull(portalDao.get("weekly_schedule"))
      val snapshot = PortalSnapshotEntity(
        moduleId = "weekly_schedule",
        payloadJson = "{\"classes\":[]}",
        lastSuccessAtMillis = 2000L,
        lastAttemptAtMillis = 2000L,
        status = "SUCCESS",
        message = null,
      )
      portalDao.upsert(snapshot)
      assertEquals(snapshot, portalDao.get("weekly_schedule"))
      assertEquals(snapshot, portalDao.observe("weekly_schedule").first())

      val unavailable = snapshot.copy(payloadJson = null, lastSuccessAtMillis = null, status = "UNAVAILABLE", message = "Not published")
      portalDao.upsert(unavailable)
      assertEquals(unavailable, portalDao.get("weekly_schedule"))
      migrated.openHelper.readableDatabase.query("PRAGMA user_version").use { cursor ->
        assertTrue(cursor.moveToFirst())
        assertEquals(2, cursor.getInt(0))
      }
      portalDao.clearAll()
      assertNull(portalDao.get("weekly_schedule"))
      assertEquals(1, migrated.courseDao().countAllCourses())
    } finally {
      database?.close()
      helper.close()
      context.deleteDatabase(databaseName)
    }
  }

  private fun versionOneSchema(): File {
    val relativePath = "schemas/ge.usltd.btu_classroom.core.database.BtuDatabase/1.json"
    val workingDirectory = File(requireNotNull(System.getProperty("user.dir")))
    return generateSequence(workingDirectory) { it.parentFile }
      .flatMap { sequenceOf(File(it, relativePath), File(it, "app/$relativePath")) }
      .firstOrNull { it.isFile }
      ?: error("The exported version 1 Room schema is required for migration verification")
  }

  private companion object {
    const val SEMESTER_KEY = "default_student::fall2026"
    const val COURSE_KEY = "$SEMESTER_KEY::501"
  }
}
