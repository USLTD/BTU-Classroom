package ge.usltd.btu_classroom.core.sync

import android.Manifest
import android.app.Application
import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import ge.usltd.btu_classroom.core.preferences.UserPreferencesStore
import ge.usltd.btu_classroom.core.schedule.ScheduleEntry
import ge.usltd.btu_classroom.core.schedule.ScheduleOccurrences
import ge.usltd.btu_classroom.core.security.SessionCipher
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ClassReminderSchedulerTest {
  private lateinit var context: Context
  private lateinit var preferences: UserPreferencesStore
  private lateinit var scope: CoroutineScope
  private lateinit var preferencesFile: File
  private lateinit var scheduler: ClassReminderScheduler
  private lateinit var workManager: WorkManager
  private val date = LocalDate.of(2026, 11, 23)
  private val now = Instant.parse("2026-11-23T05:00:00Z")
  private val entry = ScheduleEntry("fixture_entry", 1, "10:00", "11:00", "Sanitized course")

  @Before
  fun setUp() = runBlocking {
    context = ApplicationProvider.getApplicationContext()
    if (Build.VERSION.SDK_INT >= 33) {
      shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }
    scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    preferencesFile = File(context.filesDir, "reminders_${System.nanoTime()}.preferences_pb")
    preferences = UserPreferencesStore(
      PreferenceDataStoreFactory.create(scope = scope, produceFile = { preferencesFile }),
      object : SessionCipher {
        override fun encrypt(plaintext: String): String = "fixture:$plaintext"
        override fun decrypt(encodedBlob: String): String = encodedBlob.removePrefix("fixture:")
      },
    )
    preferences.setNotificationsEnabled(true)
    WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder()
      .setExecutor(SynchronousExecutor())
      .setTaskExecutor(SynchronousExecutor())
      .build())
    workManager = WorkManager.getInstance(context)
    scheduler = ClassReminderScheduler(context, preferences, Clock.fixed(now, ZoneOffset.UTC))
  }

  @After
  fun tearDown() {
    workManager.cancelAllWork().result.get(5, TimeUnit.SECONDS)
    WorkManagerTestInitHelper.closeWorkDatabase()
    scope.cancel()
    preferencesFile.delete()
  }

  @Test
  fun selectedOccurrenceUsesTbilisiTimeAndNoNetworkConstraint() = runBlocking {
    assertEquals(ClassReminderResult.SCHEDULED, scheduler.schedule(entry, date))
    val work = workManager.getWorkInfosByTag(ClassReminderScheduler.WORK_TAG).get(5, TimeUnit.SECONDS).single()
    assertEquals(45 * 60 * 1000L, work.initialDelayMillis)
    assertEquals(NetworkType.NOT_REQUIRED, work.constraints.requiredNetworkType)
    assertEquals(WorkInfo.State.ENQUEUED, work.state)
  }

  @Test
  fun schedulingSameOccurrenceReplacesInsteadOfDuplicating() = runBlocking {
    assertEquals(ClassReminderResult.SCHEDULED, scheduler.schedule(entry, date))
    assertEquals(ClassReminderResult.SCHEDULED, scheduler.schedule(entry, date, leadMinutes = 30))
    val name = ClassReminderScheduler.uniqueWorkName(
      entry.id,
      requireNotNull(ScheduleOccurrences.onDate(entry, date)).start.toEpochMilli(),
    )
    val active = workManager.getWorkInfosForUniqueWork(name).get(5, TimeUnit.SECONDS)
      .filter { !it.state.isFinished }
    assertEquals(1, active.size)
    assertEquals(30 * 60 * 1000L, active.single().initialDelayMillis)
  }

  @Test
  fun localWipeCancellationCancelsPendingReminders() = runBlocking {
    scheduler.schedule(entry, date)
    scheduler.cancelAll()
    val works = workManager.getWorkInfosByTag(ClassReminderScheduler.WORK_TAG).get(5, TimeUnit.SECONDS)
    assertTrue(works.isNotEmpty())
    assertTrue(works.all { it.state == WorkInfo.State.CANCELLED })
  }

  @Test
  fun disabledPreferenceDoesNotCreateWork() = runBlocking {
    preferences.setNotificationsEnabled(false)
    assertEquals(ClassReminderResult.NOTIFICATIONS_DISABLED, scheduler.schedule(entry, date))
    assertTrue(workManager.getWorkInfosByTag(ClassReminderScheduler.WORK_TAG).get().isEmpty())
  }

  @Test
  fun deniedNotificationPermissionDoesNotCreateWork() = runBlocking {
    shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
    assertEquals(ClassReminderResult.PERMISSION_REQUIRED, scheduler.schedule(entry, date))
    assertTrue(workManager.getWorkInfosByTag(ClassReminderScheduler.WORK_TAG).get().isEmpty())
  }

  @Test
  fun mismatchedWeekdayAndInvalidTimeAreRejected() = runBlocking {
    assertEquals(ClassReminderResult.INVALID_DATE, scheduler.schedule(entry, date.plusDays(1)))
    assertEquals(ClassReminderResult.INVALID_DATE, scheduler.schedule(entry.copy(startTime = "25:00"), date))
    assertEquals(ClassReminderResult.INVALID_DATE, scheduler.schedule(entry, date, -1))
    assertTrue(workManager.getWorkInfosByTag(ClassReminderScheduler.WORK_TAG).get().isEmpty())
  }

  @Test
  fun missedReminderTimeDoesNotRollForwardToAnotherWeek() = runBlocking {
    val lateScheduler = ClassReminderScheduler(context, preferences, Clock.fixed(
      Instant.parse("2026-11-23T05:50:00Z"), ZoneOffset.UTC,
    ))
    assertEquals(ClassReminderResult.TOO_LATE, lateScheduler.schedule(entry, date))
    assertTrue(workManager.getWorkInfosByTag(ClassReminderScheduler.WORK_TAG).get().isEmpty())
  }

  @Test
  fun explicitNextWeekIsAnIndependentOccurrence() = runBlocking {
    assertEquals(ClassReminderResult.SCHEDULED, scheduler.schedule(entry, date))
    assertEquals(ClassReminderResult.SCHEDULED, scheduler.schedule(entry, date.plusWeeks(1)))
    val works = workManager.getWorkInfosByTag(ClassReminderScheduler.WORK_TAG).get(5, TimeUnit.SECONDS)
    assertEquals(2, works.count { !it.state.isFinished })
    assertFalse(works.any { it.constraints.requiresCharging() })
  }
}
