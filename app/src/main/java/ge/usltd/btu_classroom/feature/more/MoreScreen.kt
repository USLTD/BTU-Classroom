package ge.usltd.btu_classroom.feature.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ge.usltd.btu_classroom.BuildConfig
import ge.usltd.btu_classroom.R
import ge.usltd.btu_classroom.core.network.BtuRoutes
import ge.usltd.btu_classroom.core.portal.PortalModule

@Composable
fun MoreScreen(
  onOpenSettings: () -> Unit,
  onOpenDiagnostics: () -> Unit,
  onRequestSignOut: () -> Unit,
  onRequestClearLocalData: () -> Unit,
  modifier: Modifier = Modifier,
  onOpenPortal: ((String) -> Unit)? = null,
  onOpenModule: ((PortalModule) -> Unit)? = null,
) {
  val uriHandler = LocalUriHandler.current
  val locale = LocalConfiguration.current.locales[0]
  val openPortal: (String) -> Unit = onOpenPortal ?: { uriHandler.openUri(it) }
  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    LazyColumn(
      modifier = Modifier
        .widthIn(max = 680.dp)
        .fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
      item(key = "heading") {
        Column {
          Text(
            text = stringResource(R.string.nav_more),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 24.dp).semantics { heading() },
          )
          MoreSectionHeading(stringResource(R.string.section_preferences))
        }
      }
      item(key = "settings") {
        MoreActionRow(
          icon = Icons.Filled.Settings,
          title = stringResource(R.string.settings_title),
          subtitle = "${stringResource(R.string.setting_language)} · ${stringResource(R.string.setting_theme)}",
          tag = "more_settings_card",
          onClick = onOpenSettings,
        )
      }

      item(key = "academic_heading") {
        Column(
          modifier = Modifier.padding(top = 24.dp, bottom = 12.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = stringResource(R.string.academic_portal_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
          )
          Text(
            text = stringResource(R.string.academic_portal_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      items(portalDestinations, key = { it.path }) { destination ->
        val isNative = destination.module != null && onOpenModule != null
        MoreActionRow(
          icon = destination.icon,
          title = stringResource(destination.label),
          subtitle = if (isNative) null else stringResource(destination.subtitle),
          tag = "more_portal_${destination.tag}",
          position = when (destination) {
            portalDestinations.first() -> RowPosition.FIRST
            portalDestinations.last() -> RowPosition.LAST
            else -> RowPosition.MIDDLE
          },
          onClick = {
            val module = destination.module
            if (module != null && onOpenModule != null) onOpenModule(module)
            else BtuRoutes.forLocale(destination.path, locale)?.let(openPortal)
          },
        )
      }

      item(key = "session_heading") {
        MoreSectionHeading(
          stringResource(R.string.section_session_security),
          modifier = Modifier.padding(top = 24.dp),
        )
      }
      item(key = "sign_out") {
        MoreActionRow(
          icon = Icons.AutoMirrored.Filled.Logout,
          title = stringResource(R.string.action_sign_out),
          subtitle = null,
          tag = "more_sign_out_card",
          position = RowPosition.FIRST,
          onClick = onRequestSignOut,
        )
      }

      item(key = "clear_data") {
        MoreActionRow(
          icon = Icons.Filled.DeleteForever,
          title = stringResource(R.string.action_clear_local_data),
          subtitle = null,
          tint = MaterialTheme.colorScheme.error,
          tag = "more_clear_data_card",
          position = RowPosition.LAST,
          onClick = onRequestClearLocalData,
        )
      }

      if (BuildConfig.DEBUG) {
        item(key = "diagnostics") {
          Column {
            Spacer(Modifier.size(24.dp))
            MoreActionRow(
              icon = Icons.Filled.BugReport,
              title = stringResource(R.string.diagnostics_title),
              subtitle = stringResource(R.string.diagnostics_subtitle),
              tag = "more_diagnostics_card",
              onClick = onOpenDiagnostics,
            )
          }
        }
      }

      item(key = "about") {
        Column(
          modifier = Modifier.padding(top = 24.dp, start = 4.dp, end = 4.dp, bottom = 8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Filled.Info,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
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

private data class PortalDestination(
  val label: Int,
  val icon: ImageVector,
  val path: String,
  val tag: String,
  val subtitle: Int = R.string.action_open_portal,
  val module: PortalModule? = null,
)

private val portalDestinations = listOf(
  PortalDestination(R.string.academic_program, Icons.Filled.School, "/ge/student/me/program", "program", module = PortalModule.PROGRAM),
  PortalDestination(R.string.academic_calendar, Icons.Filled.CalendarMonth, "/ge/student/me/calendar", "calendar", module = PortalModule.CALENDAR),
  PortalDestination(R.string.academic_exams, Icons.Filled.EventNote, BtuRoutes.STUDENT_EXAMS, "exams", module = PortalModule.EXAMS),
  PortalDestination(R.string.academic_record, Icons.Filled.AccountBalance, "/ge/student/card", "record", module = PortalModule.ACADEMIC_CARD),
  PortalDestination(R.string.academic_remaining_courses, Icons.Filled.MenuBook, "/ge/training/list", "remaining", module = PortalModule.REMAINING),
  PortalDestination(R.string.academic_rating, Icons.Filled.StarOutline, "/ge/student/rating", "rating", module = PortalModule.RATING),
  PortalDestination(R.string.academic_statements, Icons.Filled.Description, "/ge/student/me/statements", "statements", module = PortalModule.STATEMENTS),
  PortalDestination(R.string.academic_registration, Icons.Filled.HowToReg, "/ge/", "registration",
    R.string.academic_portal_continue),
)

@Composable
private fun MoreSectionHeading(title: String, modifier: Modifier = Modifier) {
  Text(
    text = title,
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = modifier.padding(start = 4.dp, bottom = 12.dp).semantics { heading() },
  )
}

private enum class RowPosition { SINGLE, FIRST, MIDDLE, LAST }

@Composable
private fun MoreActionRow(
  icon: ImageVector,
  title: String,
  subtitle: String?,
  tag: String,
  tint: Color = MaterialTheme.colorScheme.primary,
  position: RowPosition = RowPosition.SINGLE,
  onClick: () -> Unit,
) {
  val shape = MaterialTheme.shapes.large
  val groupedShape = when (position) {
    RowPosition.SINGLE -> shape
    RowPosition.FIRST -> shape.copy(bottomStart = CornerSize(4.dp), bottomEnd = CornerSize(4.dp))
    RowPosition.MIDDLE -> MaterialTheme.shapes.extraSmall
    RowPosition.LAST -> shape.copy(topStart = CornerSize(4.dp), topEnd = CornerSize(4.dp))
  }
  Surface(
    onClick = onClick,
    modifier = Modifier
      .fillMaxWidth()
      .padding(bottom = if (position == RowPosition.FIRST || position == RowPosition.MIDDLE) 2.dp else 0.dp)
      .testTag(tag),
    shape = groupedShape,
    color = MaterialTheme.colorScheme.surfaceContainerLow,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 72.dp)
        .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        modifier = Modifier.weight(1f).padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = tint,
          modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column {
          Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          if (!subtitle.isNullOrBlank()) {
            Text(
              text = subtitle,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
      Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
