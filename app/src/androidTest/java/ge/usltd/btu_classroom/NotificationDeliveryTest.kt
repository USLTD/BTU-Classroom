package ge.usltd.btu_classroom

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import ge.usltd.btu_classroom.core.auth.SessionManager
import ge.usltd.btu_classroom.core.database.BtuDatabase
import ge.usltd.btu_classroom.core.database.CourseRepository
import ge.usltd.btu_classroom.core.network.BtuHttpClient
import ge.usltd.btu_classroom.core.notifications.ClassReminderNotifier
import ge.usltd.btu_classroom.core.notifications.GradeChangeNotifier
import ge.usltd.btu_classroom.core.parsing.PageCompatibilityRegistry
import ge.usltd.btu_classroom.core.portal.PortalContent
import ge.usltd.btu_classroom.core.portal.PortalModule
import ge.usltd.btu_classroom.core.portal.PortalRepository
import ge.usltd.btu_classroom.core.portal.PortalSnapshotEntity
import ge.usltd.btu_classroom.core.preferences.AppLanguage
import ge.usltd.btu_classroom.core.preferences.UserPreferencesStore
import ge.usltd.btu_classroom.core.schedule.ScheduleEntry
import ge.usltd.btu_classroom.core.schedule.ScheduleOccurrences
import ge.usltd.btu_classroom.core.security.SessionCipher
import ge.usltd.btu_classroom.core.sync.BtuWorkerFactory
import ge.usltd.btu_classroom.core.sync.ClassReminderResult
import ge.usltd.btu_classroom.core.sync.ClassReminderScheduler
import ge.usltd.btu_classroom.core.sync.ClassReminderWorker
import java.io.File
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/** Run after revoking POST_NOTIFICATIONS on the disposable test install, before instrumentation starts. */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class NotificationDeliveryTest {
  private val instrumentation = InstrumentationRegistry.getInstrumentation()
  private lateinit var context: Context
  private lateinit var notifications: NotificationManager
  private lateinit var database: BtuDatabase
  private lateinit var preferences: UserPreferencesStore
  private lateinit var scope: CoroutineScope
  private lateinit var preferencesFile: File
  private lateinit var factory: BtuWorkerFactory
  private lateinit var entry: ScheduleEntry
  private lateinit var date: LocalDate
  private var startAtMillis = 0L

  @Before
  fun setUp() = runBlocking {
    context = instrumentation.targetContext
    notifications = context.getSystemService(NotificationManager::class.java)
    notifications.cancelAll()
    // The test install remains signed out; launching the tap target cannot restore a portal session.
    val application = context.applicationContext as BTUClassroomApplication
    application.preferencesStore.setBackgroundSync(false)
    application.preferencesStore.setRememberLogin(false)
    database = Room.inMemoryDatabaseBuilder(context, BtuDatabase::class.java).build()
    scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    preferencesFile = File(context.cacheDir, "notification_fixture_${System.nanoTime()}.preferences_pb")
    preferences = UserPreferencesStore(
      PreferenceDataStoreFactory.create(scope = scope, produceFile = { preferencesFile }),
      object : SessionCipher {
        override fun encrypt(plaintext: String): String = plaintext
        override fun decrypt(encodedBlob: String): String = encodedBlob
      },
    )
    preferences.setNotificationsEnabled(true)
    preferences.setLanguage(AppLanguage.EN)
    val noNetworkClient = BtuHttpClient(client = OkHttpClient.Builder().addInterceptor {
      throw AssertionError("Notification delivery tests must not access the network")
    }.build())
    val sessions = SessionManager(noNetworkClient, preferences)
    val portal = PortalRepository(database, noNetworkClient, sessions, preferences)
    val courses = CourseRepository(database, noNetworkClient, sessions, preferences, PageCompatibilityRegistry())
    factory = BtuWorkerFactory(
      preferences, sessions, courses, GradeChangeNotifier(context), portal, ClassReminderNotifier(context),
    )
    date = LocalDate.now(ScheduleOccurrences.UNIVERSITY_ZONE).plusDays(1)
    entry = ScheduleEntry("notification_fixture", date.dayOfWeek.value, "10:00", "11:00", "Private fixture course")
    startAtMillis = requireNotNull(ScheduleOccurrences.onDate(entry, date)).start.toEpochMilli()
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
  fun a_deniedPermissionPreventsAcademicAlertsAndReminderDelivery() = runBlocking {
    if (Build.VERSION.SDK_INT >= 33) {
      assertEquals("Revoke notification permission before starting this test class", PackageManager.PERMISSION_DENIED,
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
      assertFalse(notifications.areNotificationsEnabled())
      GradeChangeNotifier(context).notifyScoresChanged(AppLanguage.EN)
      GradeChangeNotifier(context).notifyAcademicInformationChanged(AppLanguage.EN)
      assertEquals(ListenableWorker.Result.success(), reminderWorker().doWork())
      assertTrue(notifications.activeNotifications.isEmpty())
      assertEquals(ClassReminderResult.PERMISSION_REQUIRED, ClassReminderScheduler(context, preferences).schedule(entry, date))
    }
  }

  @Test
  fun b_academicNotificationPostsBrandedChannelContentAndWorkingTapIntent() {
    grantNotificationPermission()
    ActivityScenario.launch(MainActivity::class.java).use {
      val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
      try {
        GradeChangeNotifier(context).notifyScoresChanged(AppLanguage.EN)
        val posted = awaitNotification { it.id == 1001 && it.tag == null }
        val notification = posted.notification
        assertEquals("btu_grade_updates", notification.channelId)
        assertEquals(context.getString(R.string.notification_grade_title), notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(context.getString(R.string.notification_grade_body), notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(R.drawable.ic_notification_btu, requireNotNull(notification.smallIcon).resId)
        assertNotNull(notifications.getNotificationChannel(notification.channelId))
        val tapIntent = requireNotNull(notification.contentIntent)
        assertEquals(context.packageName, tapIntent.creatorPackage)
        assertTrue(tapIntent.isActivity)
        if (Build.VERSION.SDK_INT >= 31) assertTrue(tapIntent.isImmutable)
        tapIntent.send()
        val opened = monitor.waitForActivityWithTimeout(5_000)
          ?: throw AssertionError("Notification tap must open BTU Classroom")
        assertEquals(MainActivity::class.java.name, opened.javaClass.name)
        instrumentation.runOnMainSync { opened.finish() }
      } finally {
        instrumentation.removeMonitor(monitor)
      }
    }
  }

  @Test
  fun c_productionWorkerFactoryDeliversLocalReminderWithStableIdAndPrivateContent() = runBlocking {
    grantNotificationPermission()
    assertEquals(ListenableWorker.Result.success(), reminderWorker().doWork())
    val expectedId = ClassReminderScheduler.uniqueWorkName(entry.id, startAtMillis).hashCode()
    val posted = awaitNotification { it.id == expectedId && it.tag == "btu_class_reminder" }
    val notification = posted.notification
    assertEquals("btu_class_reminders", notification.channelId)
    assertEquals(context.getString(R.string.schedule_reminder_title), notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
    assertEquals(context.getString(R.string.schedule_reminder_body), notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
    assertFalse(notification.extras.toString().contains(entry.courseTitle))
    assertEquals(R.drawable.ic_notification_btu, requireNotNull(notification.smallIcon).resId)
    assertEquals(context.packageName, requireNotNull(notification.contentIntent).creatorPackage)
    assertEquals(ListenableWorker.Result.success(), reminderWorker().doWork())
    assertEquals(1, notifications.activeNotifications.count { it.tag == "btu_class_reminder" })
  }

  @Test
  fun d_accountWipeInvalidatesPreviouslyPreparedReminder() = runBlocking {
    grantNotificationPermission()
    val pending = reminderWorker()
    preferences.clearSessionAndIdentity()
    assertEquals(ListenableWorker.Result.success(), pending.doWork())
    assertTrue(notifications.activeNotifications.isEmpty())
  }

  @Test
  fun e_changedCachedStartAndDisabledConsentSuppressReminder() = runBlocking {
    grantNotificationPermission()
    val pending = reminderWorker()
    cache(entry.copy(startTime = "10:30"))
    assertEquals(ListenableWorker.Result.success(), pending.doWork())
    assertTrue(notifications.activeNotifications.isEmpty())
    cache(entry)
    preferences.setNotificationsEnabled(false)
    assertEquals(ListenableWorker.Result.success(), reminderWorker().doWork())
    assertTrue(notifications.activeNotifications.isEmpty())
  }

  @Test
  fun f_schedulerPersistsOfflineReminderAndCancellationRemovesPendingWork() = runBlocking {
    grantNotificationPermission()
    val scheduler = ClassReminderScheduler(context, preferences)
    val manager = WorkManager.getInstance(context)
    val name = ClassReminderScheduler.uniqueWorkName(entry.id, startAtMillis)
    try {
      assertEquals(ClassReminderResult.SCHEDULED, scheduler.schedule(entry, date))
      val work = manager.getWorkInfosForUniqueWork(name).get(5, TimeUnit.SECONDS).single { !it.state.isFinished }
      assertEquals(WorkInfo.State.ENQUEUED, work.state)
      assertEquals(androidx.work.NetworkType.NOT_REQUIRED, work.constraints.requiredNetworkType)
      scheduler.cancelAll()
      awaitCondition { manager.getWorkInfosForUniqueWork(name).get(5, TimeUnit.SECONDS).all { it.state.isFinished } }
      assertTrue(notifications.activeNotifications.isEmpty())
    } finally {
      manager.cancelUniqueWork(name).result.get(5, TimeUnit.SECONDS)
    }
  }

  private suspend fun reminderWorker(): ClassReminderWorker =
    TestListenableWorkerBuilder<ClassReminderWorker>(context)
      .setInputData(workDataOf(
        "academic_generation" to preferences.readAcademicDataGeneration(),
        "schedule_entry_id" to entry.id,
        "class_start_at" to startAtMillis,
      ))
      .setWorkerFactory(factory)
      .build()

  private suspend fun cache(value: ScheduleEntry) {
    database.portalDao().upsert(PortalSnapshotEntity(
      PortalModule.SCHEDULE.id,
      Json.encodeToString(PortalContent("Fixture timetable", schedule = listOf(value))),
      System.currentTimeMillis(),
      System.currentTimeMillis(),
      "SUCCESS",
      null,
    ))
  }

  private fun grantNotificationPermission() {
    if (Build.VERSION.SDK_INT >= 33) {
      instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
      awaitCondition { context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED }
    }
    assertTrue(notifications.areNotificationsEnabled())
  }

  private fun awaitNotification(predicate: (android.service.notification.StatusBarNotification) -> Boolean): android.service.notification.StatusBarNotification {
    awaitCondition { notifications.activeNotifications.any(predicate) }
    return notifications.activeNotifications.first(predicate)
  }

  private fun awaitCondition(condition: () -> Boolean) {
    val deadline = android.os.SystemClock.elapsedRealtime() + 5_000
    while (!condition() && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(50)
    assertTrue("Expected local notification/work state within five seconds", condition())
  }
}
