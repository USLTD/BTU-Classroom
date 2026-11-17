package ge.usltd.btu_classroom.feature.diagnostics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ge.usltd.btu_classroom.R
import ge.usltd.btu_classroom.core.auth.AuthState
import ge.usltd.btu_classroom.core.auth.AuthTelemetryState
import ge.usltd.btu_classroom.core.auth.LogLevel
import ge.usltd.btu_classroom.core.parsing.BtuModuleId
import ge.usltd.btu_classroom.core.parsing.ModuleCompatibilityEntry
import ge.usltd.btu_classroom.core.parsing.ModuleCompatibilityStatus
import ge.usltd.btu_classroom.design.components.BadgeTone
import ge.usltd.btu_classroom.design.components.StatusBadge
import ge.usltd.btu_classroom.design.theme.ConsoleBorder
import ge.usltd.btu_classroom.design.theme.ConsoleSurface
import ge.usltd.btu_classroom.design.theme.ConsoleTextPrimary
import ge.usltd.btu_classroom.design.theme.ConsoleTextTimestamp

/**
 * Debug-only Diagnostics screen exposing PageCompatibilityRegistry health
 * and redacted auth/HTTP telemetry. Never accessible in release builds and never logs raw secrets.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DiagnosticsScreen(
  authState: AuthState,
  telemetry: AuthTelemetryState,
  compatibilityEntries: Map<BtuModuleId, ModuleCompatibilityEntry>,
  onVerifySession: () -> Unit,
  onCheckExpiry: () -> Unit,
  onRefreshCourses: () -> Unit,
  onClearLogs: () -> Unit,
  onNavigateBack: () -> Unit,
) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.diagnostics_title)) },
        navigationIcon = {
          IconButton(
            onClick = onNavigateBack,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("diagnostics_back_button"),
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
          .widthIn(max = 720.dp)
          .fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        // Parser Compatibility Registry Card
        item {
          OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
          ) {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Text(
                text = stringResource(R.string.diagnostics_compat_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
              HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

              BtuModuleId.entries.forEach { module ->
                val entry = compatibilityEntries[module]
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Text(
                      text = "${module.displayTitle} (${module.routePath})",
                      style = MaterialTheme.typography.labelMedium,
                      color = MaterialTheme.colorScheme.onSurface,
                      modifier = Modifier.weight(1f).padding(end = 8.dp),
                    )
                    if (entry != null) {
                      val (tone, icon) = when (entry.status) {
                        ModuleCompatibilityStatus.COMPATIBLE -> BadgeTone.SUCCESS to Icons.Filled.CheckCircle
                        ModuleCompatibilityStatus.NO_DATA_AVAILABLE,
                        ModuleCompatibilityStatus.AWAITING_INSPECTION -> BadgeTone.NEUTRAL to Icons.Filled.HourglassEmpty
                        ModuleCompatibilityStatus.TEMPORARILY_UNAVAILABLE,
                        ModuleCompatibilityStatus.SESSION_EXPIRED -> BadgeTone.WARNING to Icons.Filled.WarningAmber
                        ModuleCompatibilityStatus.UNEXPECTED_HTML,
                        ModuleCompatibilityStatus.PARSER_UPDATE_REQUIRED -> BadgeTone.ERROR to Icons.Filled.ErrorOutline
                      }
                      StatusBadge(label = entry.status.label, icon = icon, tone = tone)
                    }
                  }
                  if (!entry?.detail.isNullOrBlank()) {
                    Text(
                      text = entry!!.detail,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              }
            }
          }
        }

        // Auth & Extension Telemetry Card
        item {
          OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
          ) {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(
                text = stringResource(R.string.diagnostics_auth_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
              HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

              TelemetryRow("AuthState", authState::class.simpleName ?: "Unknown")
              TelemetryRow("GeckoView", telemetry.geckoInitStatus.label)
              TelemetryRow("Extension", telemetry.extensionInstallStatus.label)
              TelemetryRow("OAuth Stage", telemetry.googleLoginStatus.label)
              TelemetryRow("Verification", telemetry.verificationStatus.label)
              TelemetryRow("Last HTTP", telemetry.lastHttpStatusCode?.toString() ?: "None")
              TelemetryRow("Offline Mode", telemetry.isOfflineFallback.toString())

              Spacer(modifier = Modifier.height(4.dp))
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                FilledTonalButton(
                  onClick = onVerifySession,
                  enabled = !telemetry.isBusy,
                  modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                  Icon(Icons.Filled.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(stringResource(R.string.action_verify_session))
                }
                OutlinedButton(
                  onClick = onCheckExpiry,
                  enabled = !telemetry.isBusy,
                  modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                  Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(stringResource(R.string.action_check_expiry))
                }
                OutlinedButton(
                  onClick = onRefreshCourses,
                  enabled = !telemetry.isBusy,
                  modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                  Text(stringResource(R.string.action_refresh))
                }
              }
            }
          }
        }

        // Console Log Viewer
        item {
          Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = ConsoleSurface,
            border = BorderStroke(1.dp, ConsoleBorder),
          ) {
            Column(modifier = Modifier.padding(14.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.Filled.Terminal,
                    contentDescription = null,
                    tint = Color(0xFF93C5FD),
                    modifier = Modifier.size(18.dp),
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = stringResource(R.string.diagnostics_log_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = ConsoleTextPrimary,
                  )
                }
                if (telemetry.logs.isNotEmpty()) {
                  TextButton(
                    onClick = onClearLogs,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                  ) {
                    Icon(
                      imageVector = Icons.Filled.DeleteSweep,
                      contentDescription = null,
                      tint = Color(0xFF94A3B8),
                      modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                      text = stringResource(R.string.action_clear_log),
                      color = Color(0xFF94A3B8),
                      style = MaterialTheme.typography.labelSmall,
                    )
                  }
                }
              }

              Spacer(modifier = Modifier.height(8.dp))

              if (telemetry.logs.isEmpty()) {
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
                    .background(Color(0xFF070C16), RoundedCornerShape(10.dp))
                    .padding(16.dp),
                  contentAlignment = Alignment.Center,
                ) {
                  Text(
                    text = stringResource(R.string.empty_log_message),
                    style = MaterialTheme.typography.labelSmall,
                    color = ConsoleTextTimestamp,
                  )
                }
              } else {
                LazyColumn(
                  modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 280.dp)
                    .background(Color(0xFF070C16), RoundedCornerShape(10.dp))
                    .padding(10.dp),
                  verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  items(telemetry.logs, key = { it.id }) { entry ->
                    val msgColor = when (entry.level) {
                      LogLevel.INFO -> ConsoleTextPrimary
                      LogLevel.SUCCESS -> Color(0xFF34D399)
                      LogLevel.WARNING -> Color(0xFFFBBF24)
                      LogLevel.ERROR -> Color(0xFFF87171)
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                      Text(
                        text = "[${entry.timestamp}] ",
                        style = MaterialTheme.typography.labelSmall,
                        color = ConsoleTextTimestamp,
                      )
                      Text(
                        text = entry.message,
                        style = MaterialTheme.typography.labelSmall,
                        color = msgColor,
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun TelemetryRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = value,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}
