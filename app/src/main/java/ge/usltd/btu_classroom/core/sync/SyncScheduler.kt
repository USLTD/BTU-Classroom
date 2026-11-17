package ge.usltd.btu_classroom.core.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import ge.usltd.btu_classroom.core.preferences.UserPreferences
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncScheduler @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  fun update(preferences: UserPreferences) {
    val workManager = WorkManager.getInstance(context)
    if (!preferences.rememberLogin || !preferences.backgroundSync) {
      workManager.cancelUniqueWork(WORK_NAME)
      return
    }

    val request = PeriodicWorkRequestBuilder<CourseSyncWorker>(6, TimeUnit.HOURS)
      .setConstraints(
        Constraints.Builder()
          .setRequiredNetworkType(NetworkType.CONNECTED)
          .setRequiresBatteryNotLow(true)
          .build(),
      )
      .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
      .addTag(WORK_NAME)
      .build()
    workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
  }

  companion object {
    const val WORK_NAME = "btu_selected_courses_sync"
  }
}
