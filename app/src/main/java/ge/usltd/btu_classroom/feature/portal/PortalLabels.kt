package ge.usltd.btu_classroom.feature.portal

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import ge.usltd.btu_classroom.R
import ge.usltd.btu_classroom.core.portal.PortalModule
import ge.usltd.btu_classroom.core.portal.PortalTerminology

fun portalModuleTitle(module: PortalModule): Int = when (module) {
  PortalModule.SCHEDULE -> R.string.nav_schedule
  PortalModule.EXAMS -> R.string.academic_exams
  PortalModule.PROGRAM -> R.string.academic_program
  PortalModule.CALENDAR -> R.string.academic_calendar
  PortalModule.ACADEMIC_CARD -> R.string.academic_record
  PortalModule.REMAINING -> R.string.academic_remaining_courses
  PortalModule.STATEMENTS -> R.string.academic_statements
  PortalModule.RATING -> R.string.academic_rating
}

@Composable
internal fun portalLabel(source: String, fallbackIndex: Int): String {
  val label = source.trim().removeSuffix(":").trim()
  val resource = PortalTerminology.labelResource(source)
  return when {
    resource != null -> stringResource(resource)
    label.isNotEmpty() -> label
    else -> stringResource(R.string.portal_field_item, fallbackIndex + 1)
  }
}

@Composable
internal fun portalText(source: String): String = PortalTerminology.localizeInterfaceText(source, LocalContext.current)
