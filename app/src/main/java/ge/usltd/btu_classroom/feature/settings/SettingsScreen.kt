package ge.usltd.btu_classroom.feature.settings

import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import ge.usltd.btu_classroom.BuildConfig
import ge.usltd.btu_classroom.R
import ge.usltd.btu_classroom.core.preferences.AppLanguage
import ge.usltd.btu_classroom.core.preferences.AppThemeMode
import ge.usltd.btu_classroom.core.preferences.UserPreferences

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
  preferences: UserPreferences,
  onLanguageChanged: (AppLanguage) -> Unit,
  onThemeModeChanged: (AppThemeMode) -> Unit,
  onDynamicColorChanged: (Boolean) -> Unit,
  onRememberLoginChanged: (Boolean) -> Unit,
  onRequestSignOut: () -> Unit,
  onRequestClearLocalData: () -> Unit,
  onNavigateBack: () -> Unit,
  onBackgroundSyncChanged: (Boolean) -> Unit = {},
  onNotificationsEnabledChanged: (Boolean) -> Unit = {},
) {
  val context = LocalContext.current
  var systemNotificationsEnabled by remember(context) {
    mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
  }
  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
    systemNotificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
  }
  val notificationPermission = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission(),
  ) { granted ->
    systemNotificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    onNotificationsEnabledChanged(granted)
  }
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.settings_title)) },
        navigationIcon = {
          IconButton(
            onClick = onNavigateBack,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("settings_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.nav_back),
            )
          }
        },
      )
    },
    containerColor = MaterialTheme.colorScheme.background,
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      LazyColumn(
        modifier = Modifier
          .widthIn(max = 680.dp)
          .fillMaxSize()
          .testTag("settings_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        item {
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
          ) {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Text(
                text = stringResource(R.string.section_preferences),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
              HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

              Text(
                text = stringResource(R.string.setting_language),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
              )
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                FilterChip(
                  selected = preferences.language == AppLanguage.SYSTEM,
                  onClick = { onLanguageChanged(AppLanguage.SYSTEM) },
                  label = { Text(stringResource(R.string.language_system)) },
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("settings_lang_system"),
                )
                FilterChip(
                  selected = preferences.language == AppLanguage.EN,
                  onClick = { onLanguageChanged(AppLanguage.EN) },
                  label = { Text(stringResource(R.string.language_english)) },
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("settings_lang_en"),
                )
                FilterChip(
                  selected = preferences.language == AppLanguage.KA,
                  onClick = { onLanguageChanged(AppLanguage.KA) },
                  label = { Text(stringResource(R.string.language_georgian)) },
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("settings_lang_ka"),
                )
              }

              HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

              Text(
                text = stringResource(R.string.setting_theme),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
              )
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                FilterChip(
                  selected = preferences.themeMode == AppThemeMode.SYSTEM,
                  onClick = { onThemeModeChanged(AppThemeMode.SYSTEM) },
                  label = { Text(stringResource(R.string.theme_system)) },
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("settings_theme_system"),
                )
                FilterChip(
                  selected = preferences.themeMode == AppThemeMode.LIGHT,
                  onClick = { onThemeModeChanged(AppThemeMode.LIGHT) },
                  label = { Text(stringResource(R.string.theme_light)) },
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("settings_theme_light"),
                )
                FilterChip(
                  selected = preferences.themeMode == AppThemeMode.DARK,
                  onClick = { onThemeModeChanged(AppThemeMode.DARK) },
                  label = { Text(stringResource(R.string.theme_dark)) },
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("settings_theme_dark"),
                )
              }

              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val dynamicColorLabel = stringResource(R.string.setting_dynamic_color)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                      text = dynamicColorLabel,
                      style = MaterialTheme.typography.titleSmall,
                      color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                      text = stringResource(R.string.ux_brand_theme_hint),
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  Switch(
                    checked = preferences.dynamicColor,
                    onCheckedChange = onDynamicColorChanged,
                    modifier = Modifier.testTag("settings_dynamic_color_switch")
                      .semantics { contentDescription = dynamicColorLabel },
                  )
                }
              }
            }
          }
        }

        item {
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
          ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
              Text(stringResource(R.string.section_sync), style = MaterialTheme.typography.titleMedium)
              SettingsSwitchRow(
                title = stringResource(R.string.setting_background_sync),
                description = stringResource(R.string.setting_background_sync_desc),
                checked = preferences.backgroundSync,
                enabled = preferences.rememberLogin || preferences.backgroundSync,
                tag = "settings_background_sync_switch",
                onCheckedChange = onBackgroundSyncChanged,
              )
              if (!preferences.rememberLogin) {
                Text(stringResource(R.string.ux_background_requires_login),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
              HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
              SettingsSwitchRow(
                title = stringResource(R.string.setting_notifications),
                description = stringResource(R.string.ux_notification_description),
                checked = preferences.notificationsEnabled,
                enabled = true,
                tag = "settings_notifications_switch",
                onCheckedChange = { enabled ->
                  if (enabled && Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                  } else {
                    onNotificationsEnabledChanged(enabled)
                    if (enabled && !systemNotificationsEnabled) openNotificationSettings(context)
                  }
                },
              )
              if (!systemNotificationsEnabled) {
                Text(stringResource(R.string.ux_notification_blocked),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.testTag("settings_notification_permission_notice"))
                TextButton(onClick = { openNotificationSettings(context) },
                  modifier = Modifier.heightIn(min = 48.dp).testTag("settings_android_notifications_button")) {
                  Text(stringResource(R.string.ux_notification_settings))
                }
              }
            }
          }
        }

        item {
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
          ) {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Text(
                text = stringResource(R.string.section_session_security),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
              HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

              val sessionModeLabel = stringResource(R.string.setting_session_mode)
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                  Text(
                    text = sessionModeLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                  Text(
                    text = if (preferences.rememberLogin) {
                      stringResource(R.string.session_mode_remember)
                    } else {
                      stringResource(R.string.session_mode_memory)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                Switch(
                  checked = preferences.rememberLogin,
                  onCheckedChange = onRememberLoginChanged,
                  modifier = Modifier.testTag("settings_remember_login_switch")
                    .semantics { contentDescription = sessionModeLabel },
                )
              }

              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                OutlinedButton(
                  onClick = onRequestSignOut,
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("settings_sign_out_button"),
                ) {
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(stringResource(R.string.action_sign_out))
                }

                OutlinedButton(
                  onClick = onRequestClearLocalData,
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("settings_clear_data_button"),
                ) {
                  Icon(
                    imageVector = Icons.Filled.DeleteForever,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error,
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = stringResource(R.string.action_clear_local_data),
                    color = MaterialTheme.colorScheme.error,
                  )
                }
              }
            }
          }
        }

        item {
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
          ) {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text(
                text = stringResource(R.string.section_about),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = stringResource(R.string.about_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SettingsSwitchRow(
  title: String,
  description: String,
  checked: Boolean,
  enabled: Boolean,
  tag: String,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
    Column(Modifier.weight(1f).padding(end = 12.dp)) {
      Text(title, style = MaterialTheme.typography.titleSmall)
      Text(description, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Switch(checked, onCheckedChange, enabled = enabled,
      modifier = Modifier.testTag(tag).semantics { contentDescription = title })
  }
}

private fun openNotificationSettings(context: android.content.Context) {
  try {
    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
      putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    })
  } catch (_: ActivityNotFoundException) {
    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
      Uri.parse("package:${context.packageName}")))
  }
}
