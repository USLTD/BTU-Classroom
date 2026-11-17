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
class GradeChangeNotifier @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  fun notifyScoresChanged(language: AppLanguage) = publish(
    language,
    R.string.notification_grade_title,
    R.string.notification_grade_body,
  )

  fun notifyAcademicInformationChanged(language: AppLanguage) = publish(
    language,
    R.string.notification_academic_title,
    R.string.notification_academic_body,
  )

  private fun publish(language: AppLanguage, titleResource: Int, bodyResource: Int) {
    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
      ) != PackageManager.PERMISSION_GRANTED
    ) return

    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    if (!manager.areNotificationsEnabled()) return
    val localizedContext = LocaleHelper.createLocalizedContext(context, language)
    manager.createNotificationChannel(
      NotificationChannel(
        CHANNEL_ID,
        localizedContext.getString(R.string.notification_grade_channel_name),
        NotificationManager.IMPORTANCE_DEFAULT,
      ).apply {
        description = localizedContext.getString(R.string.notification_grade_channel_description)
      },
    )
    if (manager.getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE) return
    val openApp = PendingIntent.getActivity(
      context,
      0,
      Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val title = localizedContext.getString(titleResource)
    val body = localizedContext.getString(bodyResource)
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_notification_btu)
      .setContentTitle(title)
      .setContentText(body)
      .setStyle(NotificationCompat.BigTextStyle().bigText(body))
      .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
      .setContentIntent(openApp)
      .setAutoCancel(true)
      .setCategory(NotificationCompat.CATEGORY_STATUS)
      .build()
    try {
      manager.notify(NOTIFICATION_ID, notification)
    } catch (_: SecurityException) {
      // Notification permission can be revoked after the check above.
    }
  }

  companion object {
    private const val CHANNEL_ID = "btu_grade_updates"
    private const val NOTIFICATION_ID = 1001
  }
}
