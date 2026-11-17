package ge.usltd.btu_classroom.core.sync

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import ge.usltd.btu_classroom.core.auth.SessionManager
import ge.usltd.btu_classroom.core.database.CourseRepository
import ge.usltd.btu_classroom.core.notifications.GradeChangeNotifier
import ge.usltd.btu_classroom.core.notifications.ClassReminderNotifier
import ge.usltd.btu_classroom.core.portal.PortalRepository
import ge.usltd.btu_classroom.core.preferences.UserPreferencesStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BtuWorkerFactory @Inject constructor(
  private val preferencesStore: UserPreferencesStore,
  private val sessionManager: SessionManager,
  private val courseRepository: CourseRepository,
  private val gradeChangeNotifier: GradeChangeNotifier,
  private val portalRepository: PortalRepository,
  private val classReminderNotifier: ClassReminderNotifier,
) : WorkerFactory() {
  override fun createWorker(
    appContext: Context,
    workerClassName: String,
    workerParameters: WorkerParameters,
  ): ListenableWorker? = when (workerClassName) {
    CourseSyncWorker::class.java.name -> CourseSyncWorker(
      appContext,
      workerParameters,
      preferencesStore,
      sessionManager,
      courseRepository,
      gradeChangeNotifier,
      portalRepository,
    )
    ClassReminderWorker::class.java.name -> ClassReminderWorker(
      appContext,
      workerParameters,
      preferencesStore,
      portalRepository,
      classReminderNotifier,
    )
    else -> null
  }
}
