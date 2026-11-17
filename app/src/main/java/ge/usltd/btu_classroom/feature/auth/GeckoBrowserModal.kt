package ge.usltd.btu_classroom.feature.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ge.usltd.btu_classroom.R
import ge.usltd.btu_classroom.core.auth.AuthTelemetryState
import ge.usltd.btu_classroom.core.auth.GoogleLoginStatus
import ge.usltd.btu_classroom.core.network.VerificationStatus
import ge.usltd.btu_classroom.design.theme.LocalIsDarkTheme
import ge.usltd.btu_classroom.design.theme.StatusError
import ge.usltd.btu_classroom.design.theme.StatusErrorBgDark
import ge.usltd.btu_classroom.design.theme.StatusErrorBgLight
import ge.usltd.btu_classroom.design.text.authBrowserStageResource
import ge.usltd.btu_classroom.design.text.authErrorResource
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

@Composable
fun GeckoBrowserModal(
  telemetry: AuthTelemetryState,
  geckoSession: GeckoSession?,
  onBackPressed: () -> Unit,
  onCloseModal: () -> Unit,
  onOpenExternalBrowser: () -> Unit,
) {
  val isDark = LocalIsDarkTheme.current
  val errBg = if (isDark) StatusErrorBgDark else StatusErrorBgLight
  val errFg = if (isDark) Color(0xFFFCA5A5) else StatusError

  Dialog(
    onDismissRequest = onCloseModal,
    properties = DialogProperties(
      usePlatformDefaultWidth = false,
      decorFitsSystemWindows = false,
    ),
  ) {
    BackHandler {
      onBackPressed()
    }

    Surface(
      modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.surface)
        .windowInsetsPadding(WindowInsets.safeDrawing),
      color = MaterialTheme.colorScheme.surface,
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Filled.Lock,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = stringResource(R.string.browser_modal_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = stringResource(authBrowserStageResource(telemetry)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }

          IconButton(
            onClick = onCloseModal,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("close_browser_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.Close,
              contentDescription = stringResource(R.string.action_close_browser),
              tint = MaterialTheme.colorScheme.onSurface,
            )
          }
        }

        if (telemetry.browserLoadProgress in 1..99) {
          LinearProgressIndicator(
            progress = { telemetry.browserLoadProgress / 100f },
            modifier = Modifier.fillMaxWidth(),
          )
        }

        val hasFailureBanner =
          telemetry.errorMessage != null &&
            (telemetry.googleLoginStatus == GoogleLoginStatus.REJECTED_DISALLOWED_USERAGENT ||
              telemetry.googleLoginStatus == GoogleLoginStatus.FAILED ||
              telemetry.verificationStatus in setOf(
                VerificationStatus.UNAUTHENTICATED,
                VerificationStatus.CLOUDFLARE_BLOCKED,
                VerificationStatus.NETWORK_ERROR,
                VerificationStatus.UNEXPECTED_RESPONSE,
              ))

        AnimatedVisibility(visible = hasFailureBanner) {
          Surface(
            modifier = Modifier
              .fillMaxWidth()
              .padding(8.dp),
            color = errBg,
            shape = RoundedCornerShape(10.dp),
          ) {
            Column(modifier = Modifier.padding(12.dp)) {
              Row(verticalAlignment = Alignment.Top) {
                Icon(
                  imageVector = Icons.Filled.ErrorOutline,
                  contentDescription = null,
                  tint = errFg,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = stringResource(authErrorResource(telemetry.errorMessage.orEmpty(), telemetry)),
                  style = MaterialTheme.typography.bodySmall,
                  color = errFg,
                )
              }
              if (telemetry.showExternalBrowserFallback) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                  onClick = onOpenExternalBrowser,
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("modal_external_browser_button"),
                ) {
                  Icon(
                    imageVector = Icons.Filled.OpenInBrowser,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(stringResource(R.string.action_open_external_browser))
                }
              }
            }
          }
        }

        Box(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .testTag("geckoview_container"),
        ) {
          if (geckoSession != null) {
            AndroidView(
              modifier = Modifier.fillMaxSize(),
              factory = { context ->
                GeckoView(context).apply {
                  setSession(geckoSession)
                }
              },
              update = { view ->
                if (view.session !== geckoSession) {
                  view.setSession(geckoSession)
                }
              },
              onRelease = { view ->
                view.releaseSession()
              },
            )
          } else {
            Box(
              modifier = Modifier.fillMaxSize(),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = stringResource(R.string.browser_initializing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }
}
