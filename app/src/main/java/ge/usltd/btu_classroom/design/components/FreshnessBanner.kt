package ge.usltd.btu_classroom.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ge.usltd.btu_classroom.R
import ge.usltd.btu_classroom.core.database.entities.SyncMetadataEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FreshnessBanner(
  syncMetadata: SyncMetadataEntity?,
  isSyncing: Boolean,
  isOffline: Boolean,
  onRefresh: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  val hasCache = syncMetadata?.lastSuccessAtMillis != null
  val failed = syncMetadata != null && syncMetadata.outcomeStatus !in setOf("SUCCESS", "EMPTY")
  val locale = LocalConfiguration.current.locales[0]
  val absoluteFreshness = syncMetadata?.lastSuccessAtMillis?.let { timestamp ->
    val saved = Instant.ofEpochMilli(timestamp).atZone(ZoneId.of("Asia/Tbilisi"))
    val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(saved)
    val time = DateTimeFormatter.ofPattern("HH:mm", locale).format(saved)
    stringResource(R.string.freshness_updated_at, "$date $time")
  }
  val text = when {
    isSyncing -> stringResource(R.string.status_syncing)
    isOffline && hasCache -> "${stringResource(R.string.freshness_offline_cached)}\n$absoluteFreshness"
    isOffline -> stringResource(R.string.freshness_offline_no_cache)
    failed && syncMetadata?.lastSuccessAtMillis != null -> stringResource(
      R.string.freshness_failed_with_cache, absoluteFreshness.orEmpty())
    failed -> stringResource(R.string.freshness_refresh_failed)
    absoluteFreshness != null -> absoluteFreshness
    else -> stringResource(R.string.freshness_never_synced)
  }

  val containerColor = if (isOffline) {
    MaterialTheme.colorScheme.secondaryContainer
  } else {
    MaterialTheme.colorScheme.surfaceContainer
  }
  val contentColor = if (isOffline) {
    MaterialTheme.colorScheme.onSecondaryContainer
  } else {
    MaterialTheme.colorScheme.onSurfaceVariant
  }

  Surface(
    modifier = modifier
      .fillMaxWidth()
      .testTag("freshness_banner"),
    shape = MaterialTheme.shapes.medium,
    color = containerColor,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 12.dp, end = if (onRefresh != null) 4.dp else 12.dp, top = 6.dp, bottom = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (isSyncing) {
          LoadingIndicator(
            modifier = Modifier.size(24.dp),
            color = MaterialTheme.colorScheme.primary,
          )
        } else {
          Icon(
            imageVector = when {
              isOffline -> Icons.Filled.CloudOff
              failed -> Icons.Filled.WarningAmber
              syncMetadata?.lastSuccessAtMillis != null -> Icons.Filled.CloudDone
              else -> Icons.Filled.Sync
            },
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(16.dp),
          )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = text,
          style = MaterialTheme.typography.labelSmall,
          color = contentColor,
        )
      }

      if (onRefresh != null) {
        IconButton(
          onClick = onRefresh,
          enabled = !isSyncing,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("freshness_refresh_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.Refresh,
            contentDescription = stringResource(R.string.action_refresh),
            tint = MaterialTheme.colorScheme.primary,
          )
        }
      }
    }
  }
}

