package ge.usltd.btu_classroom.core.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ge.usltd.btu_classroom.core.notifications.ClassReminderNotifier
import ge.usltd.btu_classroom.core.portal.PortalCachedPage
import ge.usltd.btu_classroom.core.portal.PortalModule
import ge.usltd.btu_classroom.core.portal.PortalRepository
import ge.usltd.btu_classroom.core.preferences.UserPreferencesStore
import ge.usltd.btu_classroom.core.schedule.ScheduleOccurrences
import java.io.IOException
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.flow.first

class ClassReminderWorker(
  context: Context,
  parameters: WorkerParameters,
  private val preferencesStore: UserPreferencesStore,
  private val portalRepository: PortalRepository,
  private val notifier: ClassReminderNotifier,
  private val clock: Clock = Clock.systemUTC(),
) : CoroutineWorker(context, parameters) {
  override suspend fun doWork(): Result {
    try {
      val generation = inputData.getLong(KEY_GENERATION, -1)
      val entryId = inputData.getString(KEY_ENTRY_ID) ?: return Result.success()
      val startAtMillis = inputData.getLong(KEY_START_AT, -1)
      val preferences = preferencesStore.getCurrentPreferences()
      if (!preferences.notificationsEnabled || generation < 0 ||
        generation != preferencesStore.readAcademicDataGeneration()
      ) return Result.success()
      val cached = portalRepository.observe(PortalModule.SCHEDULE).first()
      if (!ClassReminderValidation.matchesCachedOccurrence(cached, entryId, startAtMillis, clock.instant())) {
        return Result.success()
      }
      if (preferencesStore.readAcademicDataGeneration() == generation &&
        preferencesStore.getCurrentPreferences().notificationsEnabled
      ) {
        notifier.notifyClassStarting(preferences.language, ClassReminderScheduler.uniqueWorkName(entryId, startAtMillis).hashCode())
      }
    } catch (_: IOException) {
      // A local cache/read failure cannot justify sending an unverified reminder.
    }
    return Result.success()
  }

  companion object {
    internal const val KEY_GENERATION = "academic_generation"
    internal const val KEY_ENTRY_ID = "schedule_entry_id"
    internal const val KEY_START_AT = "class_start_at"
  }
}

internal object ClassReminderValidation {
  fun matchesCachedOccurrence(
    cached: PortalCachedPage?,
    entryId: String,
    startAtMillis: Long,
    now: Instant,
  ): Boolean {
    if (startAtMillis < 0 || cached?.lastSuccessAtMillis == null) return false
    val start = Instant.ofEpochMilli(startAtMillis)
    if (!now.isBefore(start)) return false
    val date = start.atZone(ScheduleOccurrences.UNIVERSITY_ZONE).toLocalDate()
    return cached.content?.schedule.orEmpty().any { entry ->
      entry.id == entryId && ScheduleOccurrences.onDate(entry, date)?.start == start
    }
  }
}
