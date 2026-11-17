package ge.usltd.btu_classroom.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ge.usltd.btu_classroom.R
import ge.usltd.btu_classroom.core.auth.AuthState
import ge.usltd.btu_classroom.core.auth.AuthTelemetryState
import ge.usltd.btu_classroom.core.preferences.AppLanguage
import ge.usltd.btu_classroom.core.preferences.UserPreferences
import ge.usltd.btu_classroom.design.text.authErrorResource

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnboardingScreen(
  authState: AuthState,
  telemetry: AuthTelemetryState,
  preferences: UserPreferences,
  onSignInClick: () -> Unit,
  onResumeBrowserClick: () -> Unit,
  onOpenExternalBrowser: () -> Unit,
  onRememberLoginChanged: (Boolean) -> Unit,
  onLanguageChanged: (AppLanguage) -> Unit,
  onExploreApp: () -> Unit = {},
) {
  val restoring = authState is AuthState.Unknown || authState is AuthState.Verifying
  val busy = restoring || telemetry.isBusy
  Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
      Column(
        Modifier.widthIn(max = 560.dp).fillMaxSize()
          .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          Image(painterResource(R.drawable.btu_logo), stringResource(R.string.ux_btu_logo), Modifier.size(60.dp))
          Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.app_full_name), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.unofficial_badge), style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          listOf(
            AppLanguage.SYSTEM to stringResource(R.string.language_system),
            AppLanguage.EN to stringResource(R.string.language_english),
            AppLanguage.KA to stringResource(R.string.language_georgian),
          ).forEach { (language, label) ->
            FilterChip(
              selected = preferences.language == language,
              onClick = { onLanguageChanged(language) },
              label = { Text(label) },
              modifier = Modifier.heightIn(min = 48.dp).testTag("lang_${language.code}_chip"),
            )
          }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(stringResource(R.string.ux_entry_title), style = MaterialTheme.typography.headlineMedium)
          Text(stringResource(R.string.ux_entry_body), style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (restoring) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LoadingIndicator(Modifier.size(32.dp))
            Text(stringResource(if (authState is AuthState.Verifying) R.string.auth_verifying_session
              else R.string.auth_restoring_session), style = MaterialTheme.typography.bodyMedium)
          }
        }
        val error = (authState as? AuthState.Error)?.reason ?: telemetry.errorMessage
        if (!error.isNullOrBlank()) {
          Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small,
            modifier = Modifier.testTag("onboarding_error_banner")) {
            Text(stringResource(authErrorResource(error, telemetry)), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer,
              style = MaterialTheme.typography.bodyMedium)
          }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(
            onClick = onSignInClick, enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("sign_in_button"),
            shapes = ButtonDefaults.shapes(),
          ) {
            Icon(Icons.AutoMirrored.Filled.Login, null, Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.ux_continue_btu))
          }
          if (telemetry.hasActiveBrowserSession && !telemetry.isBrowserModalVisible) {
            OutlinedButton(onClick = onResumeBrowserClick, modifier = Modifier.fillMaxWidth().testTag("resume_browser_button")) {
              Text(stringResource(R.string.action_resume_browser))
            }
          }
          TextButton(onClick = onExploreApp, enabled = !busy,
            modifier = Modifier.fillMaxWidth().testTag("explore_app_button")) {
            Text(stringResource(R.string.action_explore_app))
          }
          if (telemetry.showExternalBrowserFallback) {
            Text(stringResource(R.string.auth_external_browser_hint), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onOpenExternalBrowser,
              modifier = Modifier.fillMaxWidth().testTag("external_browser_fallback_button")) {
              Text(stringResource(R.string.action_open_external_browser))
            }
          }
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
          Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Icon(Icons.Default.Lock, null, Modifier.size(20.dp), MaterialTheme.colorScheme.primary)
              Text(stringResource(R.string.ux_entry_privacy), style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
              Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(stringResource(R.string.remember_login_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(if (preferences.rememberLogin) R.string.remember_login_subtitle_on
                  else R.string.remember_login_subtitle_off), style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
              val rememberLabel = stringResource(R.string.remember_login_title)
              Switch(preferences.rememberLogin, onRememberLoginChanged, enabled = !busy,
                modifier = Modifier.testTag("remember_login_switch")
                  .semantics { contentDescription = rememberLabel })
            }
          }
        }
        Text(stringResource(R.string.onboarding_disclaimer), style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}
