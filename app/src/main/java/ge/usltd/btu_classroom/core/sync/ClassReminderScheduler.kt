package ge.usltd.btu_classroom.core.sync

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import ge.usltd.btu_classroom.core.notifications.ClassReminderNotifier
import ge.usltd.btu_classroom.core.notifications.ReminderNotificationAccess
import ge.usltd.btu_classroom.core.preferences.UserPreferencesStore
import ge.usltd.btu_classroom.core.schedule.ScheduleEntry
import ge.usltd.btu_classroom.core.schedule.ScheduleOccurrences
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class ClassReminderResult { SCHEDULED, PERMISSION_REQUIRED, NOTIFICATIONS_DISABLED, TOO_LATE, INVALID_DATE }

@Singleton
class ClassReminderScheduler internal constructor(
  private val context: Context,
  private val preferencesStore: UserPreferencesStore,
  private val clock: Clock,
) {
  @Inject constructor(
    @ApplicationContext context: Context,
    preferencesStore: UserPreferencesStore,
  ) : this(context, preferencesStore, Clock.systemUTC())

  suspend fun schedule(
    entry: ScheduleEntry,
    date: LocalDate,
    leadMinutes: Long = 15,
  ): ClassReminderResult {
    if (entry.id.isBlank() || leadMinutes !in 0..MAX_LEAD_MINUTES) return ClassReminderResult.INVALID_DATE
    val occurrence = ScheduleOccurrences.onDate(entry, date) ?: return ClassReminderResult.INVALID_DATE
    val reminderAt = occurrence.start.minus(Duration.ofMinutes(leadMinutes))
    val now = clock.instant()
    if (!now.isBefore(reminderAt)) return ClassReminderResult.TOO_LATE
    val preferences = preferencesStore.getCurrentPreferences()
    if (!preferences.notificationsEnabled) return ClassReminderResult.NOTIFICATIONS_DISABLED
    when (ClassReminderNotifier.availability(context, preferences.language)) {
      ReminderNotificationAccess.PERMISSION_REQUIRED -> return ClassReminderResult.PERMISSION_REQUIRED
      ReminderNotificationAccess.DISABLED -> return ClassReminderResult.NOTIFICATIONS_DISABLED
      ReminderNotificationAccess.ALLOWED -> Unit
    }

    val generation = preferencesStore.readAcademicDataGeneration()
    val work = OneTimeWorkRequestBuilder<ClassReminderWorker>()
      .setInitialDelay(Duration.between(now, reminderAt).toMillis(), TimeUnit.MILLISECONDS)
      .setInputData(workDataOf(
        ClassReminderWorker.KEY_GENERATION to generation,
        ClassReminderWorker.KEY_ENTRY_ID to entry.id,
        ClassReminderWorker.KEY_START_AT to occurrence.start.toEpochMilli(),
      ))
      .addTag(WORK_TAG)
      .build()
    WorkManager.getInstance(context).enqueueUniqueWork(
      uniqueWorkName(entry.id, occurrence.start.toEpochMilli()),
      ExistingWorkPolicy.REPLACE,
      work,
    ).await()
    if (preferencesStore.readAcademicDataGeneration() != generation) {
      WorkManager.getInstance(context).cancelWorkById(work.id).await()
      return ClassReminderResult.INVALID_DATE
    }
    return ClassReminderResult.SCHEDULED
  }

  fun cancelAll() {
    WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
    ClassReminderNotifier.clearDelivered(context)
  }

  companion object {
    const val WORK_TAG = "classreminders"
    private const val MAX_LEAD_MINUTES = 7 * 24 * 60L

    internal fun uniqueWorkName(entryId: String, startAtMillis: Long): String {
      val digest = MessageDigest.getInstance("SHA-256").digest(entryId.toByteArray(Charsets.UTF_8))
        .take(12).joinToString("") { "%02x".format(it.toInt() and 0xff) }
      return "${WORK_TAG}_${digest}_$startAtMillis"
    }
  }
}
