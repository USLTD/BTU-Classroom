package ge.usltd.btu_classroom.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ge.usltd.btu_classroom.core.database.dao.CourseDao
import ge.usltd.btu_classroom.core.database.dao.StudentDao
import ge.usltd.btu_classroom.core.database.dao.SyncMetadataDao
import ge.usltd.btu_classroom.core.database.entities.CourseEntity
import ge.usltd.btu_classroom.core.database.entities.EnrollmentEntity
import ge.usltd.btu_classroom.core.database.entities.GradeEntity
import ge.usltd.btu_classroom.core.database.entities.SemesterEntity
import ge.usltd.btu_classroom.core.database.entities.StudentEntity
import ge.usltd.btu_classroom.core.database.entities.SyncMetadataEntity
import ge.usltd.btu_classroom.core.portal.PortalDao
import ge.usltd.btu_classroom.core.portal.PortalSnapshotEntity

@Database(
  entities = [
    StudentEntity::class,
    SemesterEntity::class,
    CourseEntity::class,
    EnrollmentEntity::class,
    GradeEntity::class,
    SyncMetadataEntity::class,
    PortalSnapshotEntity::class,
  ],
  version = 2,
  exportSchema = true,
)
abstract class BtuDatabase : RoomDatabase() {
  abstract fun studentDao(): StudentDao
  abstract fun courseDao(): CourseDao
  abstract fun syncMetadataDao(): SyncMetadataDao
  abstract fun portalDao(): PortalDao

  companion object {
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
          CREATE TABLE IF NOT EXISTS `portal_snapshots` (
            `moduleId` TEXT NOT NULL,
            `payloadJson` TEXT,
            `lastSuccessAtMillis` INTEGER,
            `lastAttemptAtMillis` INTEGER NOT NULL,
            `status` TEXT NOT NULL,
            `message` TEXT,
            PRIMARY KEY(`moduleId`)
          )
        """.trimIndent())
      }
    }

    @Volatile
    private var INSTANCE: BtuDatabase? = null

    fun getInstance(context: Context): BtuDatabase {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: Room.databaseBuilder(
          context.applicationContext,
          BtuDatabase::class.java,
          "btucm_academic.db",
        ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
      }
    }
  }
}
