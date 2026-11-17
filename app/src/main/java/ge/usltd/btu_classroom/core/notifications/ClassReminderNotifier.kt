package ge.usltd.btu_classroom.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import ge.usltd.btu_classroom.MainActivity
import ge.usltd.btu_classroom.R
import ge.usltd.btu_classroom.core.preferences.AppLanguage
import ge.usltd.btu_classroom.core.preferences.LocaleHelper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClassReminderNotifier @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  fun notifyClassStarting(language: AppLanguage, notificationId: Int) {
    if (availability(context, language) != ReminderNotificationAccess.ALLOWED) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    val localizedContext = LocaleHelper.createLocalizedContext(context, language)
    val openApp = PendingIntent.getActivity(
      context,
      0,
      Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val body = localizedContext.getString(R.string.schedule_reminder_body)
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_notification_btu)
      .setContentTitle(localizedContext.getString(R.string.schedule_reminder_title))
      .setContentText(body)
      .setStyle(NotificationCompat.BigTextStyle().bigText(body))
      .setContentIntent(openApp)
      .setAutoCancel(true)
      .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
      .setCategory(NotificationCompat.CATEGORY_REMINDER)
      .build()
    try {
      manager.notify(NOTIFICATION_TAG, notificationId, notification)
    } catch (_: SecurityException) {
      // A revoked notification permission must not fail background work.
    }
  }

  companion object {
    private const val CHANNEL_ID = "btu_class_reminders"
    private const val NOTIFICATION_TAG = "btu_class_reminder"

    internal fun availability(context: Context, language: AppLanguage): ReminderNotificationAccess {
      if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
          context,
          Manifest.permission.POST_NOTIFICATIONS,
        ) != PackageManager.PERMISSION_GRANTED
      ) return ReminderNotificationAccess.PERMISSION_REQUIRED
      val manager = context.getSystemService(NotificationManager::class.java)
        ?: return ReminderNotificationAccess.DISABLED
      if (!manager.areNotificationsEnabled()) return ReminderNotificationAccess.DISABLED
      val localizedContext = LocaleHelper.createLocalizedContext(context, language)
      manager.createNotificationChannel(NotificationChannel(
        CHANNEL_ID,
        localizedContext.getString(R.string.schedule_reminder_channel_name),
        NotificationManager.IMPORTANCE_DEFAULT,
      ).apply {
        description = localizedContext.getString(R.string.schedule_reminder_channel_description)
      })
      return if (manager.getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE) {
        ReminderNotificationAccess.DISABLED
      } else ReminderNotificationAccess.ALLOWED
    }

    internal fun clearDelivered(context: Context) {
      val manager = context.getSystemService(NotificationManager::class.java) ?: return
      manager.activeNotifications
        .filter { it.tag == NOTIFICATION_TAG }
        .forEach { manager.cancel(NOTIFICATION_TAG, it.id) }
    }
  }
}

internal enum class ReminderNotificationAccess { ALLOWED, PERMISSION_REQUIRED, DISABLED }
