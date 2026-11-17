package ge.usltd.btu_classroom.core.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ge.usltd.btu_classroom.core.auth.AuthState
import ge.usltd.btu_classroom.core.auth.SessionManager
import ge.usltd.btu_classroom.core.database.CourseRepository
import ge.usltd.btu_classroom.core.database.SyncOutcome
import ge.usltd.btu_classroom.core.network.VerificationStatus
import ge.usltd.btu_classroom.core.notifications.GradeChangeNotifier
import ge.usltd.btu_classroom.core.portal.PortalModule
import ge.usltd.btu_classroom.core.portal.PortalRepository
import ge.usltd.btu_classroom.core.preferences.UserPreferencesStore
import java.io.IOException
import kotlinx.coroutines.flow.first

class CourseSyncWorker(
  context: Context,
  parameters: WorkerParameters,
  private val preferencesStore: UserPreferencesStore,
  private val sessionManager: SessionManager,
  private val courseRepository: CourseRepository,
  private val gradeChangeNotifier: GradeChangeNotifier,
  private val portalRepository: PortalRepository,
) : CoroutineWorker(context, parameters) {
  override suspend fun doWork(): Result {
    try {
      val preferences = preferencesStore.getCurrentPreferences()
      if (!preferences.rememberLogin || !preferences.backgroundSync) return Result.success()
      if (sessionManager.telemetry.value.isBusy ||
        sessionManager.authState.value is AuthState.LoggingIn ||
        sessionManager.authState.value is AuthState.Verifying
      ) return Result.success()

      val generation = preferencesStore.readAcademicDataGeneration()
      val previous = courseRepository.observeCourses().first()
      val eligiblePages = PortalModule.entries.mapNotNull { module ->
        portalRepository.observe(module).first()
          ?.takeIf { it.lastSuccessAtMillis != null }
          ?.let { module to it }
      }
      if (sessionManager.currentSessionValue().isNullOrBlank()) {
        val verification = sessionManager.restoreAndVerifyOnLaunch(previous.isNotEmpty() || eligiblePages.isNotEmpty())
        when (verification?.status) {
          VerificationStatus.AUTHENTICATED -> Unit
          VerificationStatus.NETWORK_ERROR -> return retryTransientFailure()
          else -> return Result.success()
        }
      }

      var transientFailure = false
      var gradesChanged = false
      when (val outcome = courseRepository.refreshSelectedCourses()) {
        SyncOutcome.Success -> {
          val current = courseRepository.observeCourses().first()
          gradesChanged = GradeChangeDetector.hasChangedScores(previous, current)
        }
        SyncOutcome.SessionExpired -> return Result.success()
        SyncOutcome.Empty, is SyncOutcome.UnsupportedStructure -> Unit
        is SyncOutcome.OfflineOrFailed -> transientFailure = outcome.isOffline
      }

      var portalChanged = false
      for ((module, before) in eligiblePages) {
        val latestPreferences = preferencesStore.getCurrentPreferences()
        if (!latestPreferences.rememberLogin || !latestPreferences.backgroundSync ||
          preferencesStore.readAcademicDataGeneration() != generation
        ) return Result.success()
        when (val outcome = portalRepository.refresh(module)) {
          SyncOutcome.Success, SyncOutcome.Empty -> {
            val after = portalRepository.observe(module).first()
            portalChanged = portalChanged || PortalChangeDetector.hasChangedContent(before, after)
          }
          SyncOutcome.SessionExpired -> return Result.success()
          is SyncOutcome.OfflineOrFailed -> transientFailure = transientFailure || outcome.isOffline
          is SyncOutcome.UnsupportedStructure -> Unit
        }
      }

      val latestPreferences = preferencesStore.getCurrentPreferences()
      if (latestPreferences.rememberLogin && latestPreferences.backgroundSync &&
        latestPreferences.notificationsEnabled && preferencesStore.readAcademicDataGeneration() == generation
      ) {
        when {
          portalChanged -> gradeChangeNotifier.notifyAcademicInformationChanged(latestPreferences.language)
          gradesChanged -> gradeChangeNotifier.notifyScoresChanged(latestPreferences.language)
        }
      }
      return if (transientFailure) retryTransientFailure() else Result.success()
    } catch (_: IOException) {
      return retryTransientFailure()
    }
  }

  private fun retryTransientFailure(): Result =
    if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()

  companion object {
    private const val MAX_RETRIES = 3
  }
}
