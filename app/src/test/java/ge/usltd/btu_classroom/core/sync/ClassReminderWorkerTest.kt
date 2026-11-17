package ge.usltd.btu_classroom.core.sync

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import ge.usltd.btu_classroom.R
import ge.usltd.btu_classroom.core.auth.SessionManager
import ge.usltd.btu_classroom.core.database.BtuDatabase
import ge.usltd.btu_classroom.core.network.BtuHttpClient
import ge.usltd.btu_classroom.core.notifications.ClassReminderNotifier
import ge.usltd.btu_classroom.core.portal.PortalContent
import ge.usltd.btu_classroom.core.portal.PortalModule
import ge.usltd.btu_classroom.core.portal.PortalRepository
import ge.usltd.btu_classroom.core.portal.PortalSnapshotEntity
import ge.usltd.btu_classroom.core.preferences.UserPreferencesStore
import ge.usltd.btu_classroom.core.schedule.ScheduleEntry
import ge.usltd.btu_classroom.core.schedule.ScheduleOccurrences
import ge.usltd.btu_classroom.core.security.SessionCipher
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
class ClassReminderWorkerTest {
  private lateinit var context: Context
  private lateinit var database: BtuDatabase
  private lateinit var preferences: UserPreferencesStore
  private lateinit var scope: CoroutineScope
  private lateinit var preferencesFile: File
  private lateinit var repository: PortalRepository
  private lateinit var notifications: NotificationManager
  private val entry = ScheduleEntry("fixture_entry", 1, "10:00", "11:00", "Private course title")
  private val occurrence = requireNotNull(ScheduleOccurrences.onDate(entry, LocalDate.of(2026, 11, 23)))
  private val reminderTime = Instant.parse("2026-11-23T05:45:00Z")

  @Before
  fun setUp() = runBlocking {
    context = ApplicationProvider.getApplicationContext()
    shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    database = Room.inMemoryDatabaseBuilder(context, BtuDatabase::class.java).allowMainThreadQueries().build()
    scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    preferencesFile = File(context.filesDir, "reminder_worker_${System.nanoTime()}.preferences_pb")
    preferences = UserPreferencesStore(
      PreferenceDataStoreFactory.create(scope = scope, produceFile = { preferencesFile }),
      object : SessionCipher {
        override fun encrypt(plaintext: String): String = "fixture:$plaintext"
        override fun decrypt(encodedBlob: String): String = encodedBlob.removePrefix("fixture:")
      },
    )
    preferences.setNotificationsEnabled(true)
    val httpClient = BtuHttpClient()
    repository = PortalRepository(database, httpClient, SessionManager(httpClient, preferences), preferences)
    notifications = context.getSystemService(NotificationManager::class.java)
    cache(entry)
  }

  @After
  fun tearDown() {
    notifications.cancelAll()
    database.close()
    scope.cancel()
    preferencesFile.delete()
  }

  @Test
  fun matchingFutureOccurrencePostsGenericNotificationWithoutStudentData() = runBlocking {
    assertEquals(ListenableWorker.Result.success(), worker().doWork())
    val notification = notifications.activeNotifications.single().notification
    val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
    val body = notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
    assertEquals(context.getString(R.string.schedule_reminder_title), title)
    assertEquals(context.getString(R.string.schedule_reminder_body), body)
    assertFalse(title.contains(entry.courseTitle))
    assertFalse(body.contains(entry.courseTitle))
  }

  @Test
  fun previousAccountGenerationCannotSendReminderEvenIfOldCacheSurvives() = runBlocking {
    val scheduled = worker()
    preferences.clearSessionAndIdentity()
    assertEquals(ListenableWorker.Result.success(), scheduled.doWork())
    assertTrue(notifications.activeNotifications.isEmpty())
  }

  @Test
  fun changedPublishedStartTimeInvalidatesPendingReminder() = runBlocking {
    val scheduled = worker()
    cache(entry.copy(startTime = "10:30"))
    assertEquals(ListenableWorker.Result.success(), scheduled.doWork())
    assertTrue(notifications.activeNotifications.isEmpty())
  }

  @Test
  fun removedCachedScheduleCannotSendReminder() = runBlocking {
    val scheduled = worker()
    database.portalDao().clearAll()
    assertEquals(ListenableWorker.Result.success(), scheduled.doWork())
    assertTrue(notifications.activeNotifications.isEmpty())
  }

  @Test
  fun delayedWorkCannotSendAfterClassHasStarted() = runBlocking {
    assertEquals(ListenableWorker.Result.success(), worker(now = occurrence.start).doWork())
    assertTrue(notifications.activeNotifications.isEmpty())
  }

  @Test
  fun disabledPreferenceOrRevokedPermissionSuppressesReminder() = runBlocking {
    preferences.setNotificationsEnabled(false)
    worker().doWork()
    assertTrue(notifications.activeNotifications.isEmpty())
    preferences.setNotificationsEnabled(true)
    shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
    worker().doWork()
    assertTrue(notifications.activeNotifications.isEmpty())
  }

  private suspend fun cache(entry: ScheduleEntry) {
    database.portalDao().upsert(PortalSnapshotEntity(
      moduleId = PortalModule.SCHEDULE.id,
      payloadJson = Json.encodeToString(PortalContent("Weekly schedule", schedule = listOf(entry))),
      lastSuccessAtMillis = reminderTime.toEpochMilli(),
      lastAttemptAtMillis = reminderTime.toEpochMilli(),
      status = "SUCCESS",
      message = null,
    ))
  }

  private suspend fun worker(now: Instant = reminderTime): ClassReminderWorker =
    TestListenableWorkerBuilder<ClassReminderWorker>(context)
      .setInputData(workDataOf(
        ClassReminderWorker.KEY_GENERATION to preferences.readAcademicDataGeneration(),
        ClassReminderWorker.KEY_ENTRY_ID to entry.id,
        ClassReminderWorker.KEY_START_AT to occurrence.start.toEpochMilli(),
      ))
      .setWorkerFactory(object : WorkerFactory() {
        override fun createWorker(
          appContext: Context,
          workerClassName: String,
          workerParameters: WorkerParameters,
        ): ListenableWorker = ClassReminderWorker(
          appContext,
          workerParameters,
          preferences,
          repository,
          ClassReminderNotifier(context),
          Clock.fixed(now, ZoneOffset.UTC),
        )
      })
      .build()
}
