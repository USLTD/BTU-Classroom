package ge.usltd.btu_classroom.feature.schedule

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddAlarm
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ge.usltd.btu_classroom.R
import ge.usltd.btu_classroom.core.database.SyncOutcome
import ge.usltd.btu_classroom.core.database.entities.SyncMetadataEntity
import ge.usltd.btu_classroom.core.network.BtuRoutes
import ge.usltd.btu_classroom.core.portal.PortalCachedPage
import ge.usltd.btu_classroom.core.schedule.ScheduleEntry
import ge.usltd.btu_classroom.core.schedule.ScheduleOccurrences
import ge.usltd.btu_classroom.design.components.EmptyStateCard
import ge.usltd.btu_classroom.design.components.FreshnessBanner
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.TemporalAdjusters

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
  onOpenPortalSchedule: (String) -> Unit,
  modifier: Modifier = Modifier,
  page: PortalCachedPage? = null,
  isLoading: Boolean = false,
  syncOutcome: SyncOutcome? = null,
  onRefresh: () -> Unit = {},
  onRequestReminder: (ScheduleEntry, LocalDate) -> Unit = { _, _ -> },
  onOpenExams: (() -> Unit)? = null,
  initialDate: LocalDate? = null,
  onExportCalendar: ((ScheduleEntry, LocalDate) -> Unit)? = null,
) {
  var selectedEpochDay by rememberSaveable(initialDate?.toEpochDay()) {
    mutableStateOf((initialDate ?: LocalDate.now(ScheduleOccurrences.UNIVERSITY_ZONE)).toEpochDay())
  }
  var showWeek by rememberSaveable { mutableStateOf(false) }
  val selectedDate = LocalDate.ofEpochDay(selectedEpochDay)
  val firstDate = if (showWeek) selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) else selectedDate
  val dates = if (showWeek) List(7) { firstDate.plusDays(it.toLong()) } else listOf(selectedDate)
  val locale = LocalConfiguration.current.locales[0]
  val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
  val dayFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
  val entries = page?.content?.schedule.orEmpty()
  val hasSnapshot = page?.content != null
  val context = LocalContext.current
  val noCalendarMessage = stringResource(R.string.schedule_calendar_unavailable)
  val failure = scheduleFailureMessage(page, syncOutcome)
  val offline = (syncOutcome as? SyncOutcome.OfflineOrFailed)?.isOffline == true || page?.status == "OFFLINE"
  val metadata = page?.let {
    SyncMetadataEntity("schedule", it.lastAttemptAtMillis ?: 0L, it.lastSuccessAtMillis, it.status, null)
  }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 720.dp).fillMaxSize().testTag("schedule_content"),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item(key = "header") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.nav_schedule), style = MaterialTheme.typography.headlineMedium,
              color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
            IconButton(onClick = onRefresh, enabled = !isLoading,
              modifier = Modifier.testTag("schedule_refresh")) {
              Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh))
            }
          }
          Text(stringResource(R.string.schedule_tbilisi), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
          FreshnessBanner(metadata, isLoading, offline)
          if (failure != null) {
            Text(stringResource(failure), style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("schedule_refresh_notice"))
          }
        }
      }

      item(key = "date_controls") {
        Surface(shape = MaterialTheme.shapes.extraLarge,
          color = MaterialTheme.colorScheme.surfaceContainerLow) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
              SegmentedButton(selected = !showWeek, onClick = { showWeek = false },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                modifier = Modifier.heightIn(min = 48.dp).testTag("schedule_mode_day")) {
                Text(stringResource(R.string.schedule_mode_day))
              }
              SegmentedButton(selected = showWeek, onClick = { showWeek = true },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                modifier = Modifier.heightIn(min = 48.dp).testTag("schedule_mode_week")) {
                Text(stringResource(R.string.schedule_mode_week))
              }
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
              IconButton(onClick = { selectedEpochDay -= if (showWeek) 7 else 1 },
                modifier = Modifier.testTag("schedule_previous")) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                  contentDescription = stringResource(R.string.schedule_previous_period))
              }
              Text(
                text = if (showWeek) "${firstDate.format(dateFormat)} – ${dates.last().format(dateFormat)}"
                  else selectedDate.format(dayFormat),
                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).testTag("schedule_selected_date"),
              )
              IconButton(onClick = { selectedEpochDay += if (showWeek) 7 else 1 },
                modifier = Modifier.testTag("schedule_next")) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight,
                  contentDescription = stringResource(R.string.schedule_next_period))
              }
            }
            TextButton(onClick = {
              selectedEpochDay = LocalDate.now(ScheduleOccurrences.UNIVERSITY_ZONE).toEpochDay()
            }, modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp).testTag("schedule_today")) {
              Text(stringResource(R.string.schedule_today))
            }
          }
        }
      }

      if (!hasSnapshot) {
        item(key = "unsynchronized") {
          EmptyStateCard(Icons.Default.CalendarMonth, stringResource(R.string.schedule_placeholder_title),
            stringResource(R.string.schedule_sync_to_begin))
        }
      } else if (entries.isEmpty()) {
        item(key = "empty_timetable") {
          EmptyStateCard(Icons.Default.CalendarMonth,
            stringResource(if (showWeek) R.string.schedule_empty_week else R.string.schedule_no_classes),
            stringResource(R.string.schedule_tbilisi))
        }
      } else {
        dates.forEach { date ->
          val dayEntries = entries.filter { ScheduleOccurrences.onDate(it, date) != null }
            .sortedWith(compareBy(ScheduleEntry::startTime, ScheduleEntry::courseTitle))
          if (showWeek) {
            item(key = "day_$date") {
              Text(date.format(dayFormat), style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 8.dp).semantics { heading() }.testTag("schedule_day_$date"))
            }
          }
          if (dayEntries.isEmpty()) {
            item(key = "empty_$date") {
              Text(stringResource(R.string.schedule_no_classes), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp).testTag("schedule_empty_$date"))
            }
          } else {
            items(dayEntries, key = { "class_${it.id}_$date" }) { entry ->
              ScheduleClassCard(entry = entry, date = date,
                onRequestReminder = { onRequestReminder(entry, date) },
                onExportCalendar = {
                  if (onExportCalendar != null) {
                    onExportCalendar(entry, date)
                  } else {
                    scheduleCalendarIntent(entry, date)?.let { intent ->
                      try {
                        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                      } catch (_: ActivityNotFoundException) {
                        Toast.makeText(context, noCalendarMessage, Toast.LENGTH_LONG).show()
                      } catch (_: SecurityException) {
                        Toast.makeText(context, noCalendarMessage, Toast.LENGTH_LONG).show()
                      }
                    }
                  }
                })
            }
          }
        }
      }

      item(key = "supporting_actions") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = { onOpenPortalSchedule(BtuRoutes.scheduleUrl()) },
            modifier = Modifier.fillMaxWidth().testTag("schedule_official_portal")) {
            Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.schedule_placeholder_action))
          }
          TextButton(onClick = {
            if (onOpenExams != null) onOpenExams()
            else onOpenPortalSchedule(BtuRoutes.BASE_ORIGIN + BtuRoutes.STUDENT_EXAMS)
          }, modifier = Modifier.fillMaxWidth().testTag("schedule_exams")) {
            Icon(Icons.Default.EventNote, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.academic_exams))
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleClassCard(
  entry: ScheduleEntry,
  date: LocalDate,
  onRequestReminder: () -> Unit,
  onExportCalendar: () -> Unit,
) {
  Surface(modifier = Modifier.fillMaxWidth().testTag("schedule_class_${entry.id}_$date"),
    shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer)
          Text("${entry.startTime} – ${entry.endTime}", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
      }
      Text(entry.courseTitle, style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.semantics { heading() })
      FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        entry.room?.takeIf(String::isNotBlank)?.let { room ->
          ScheduleMetadata(Icons.Default.LocationOn, stringResource(R.string.schedule_room, room))
        }
        entry.group?.takeIf(String::isNotBlank)?.let { group ->
          Text(stringResource(R.string.schedule_group, group),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
      entry.lecturers?.takeIf(String::isNotBlank)?.let { lecturer ->
        ScheduleMetadata(Icons.Default.PersonOutline, lecturer)
      }
      entry.notes?.takeIf(String::isNotBlank)?.let { notes ->
        Text(notes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FilledTonalButton(onClick = onRequestReminder,
          modifier = Modifier.heightIn(min = 48.dp).testTag("schedule_reminder_${entry.id}_$date")) {
          Icon(Icons.Default.AddAlarm, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text(stringResource(R.string.schedule_reminder))
        }
        TextButton(onClick = onExportCalendar,
          modifier = Modifier.heightIn(min = 48.dp).testTag("schedule_calendar_${entry.id}_$date")) {
          Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text(stringResource(R.string.schedule_add_calendar))
        }
      }
    }
  }
}

@Composable
private fun ScheduleMetadata(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(16.dp))
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

private fun scheduleFailureMessage(page: PortalCachedPage?, outcome: SyncOutcome?): Int? = when (outcome) {
  SyncOutcome.SessionExpired -> R.string.schedule_refresh_expired
  is SyncOutcome.UnsupportedStructure -> R.string.schedule_refresh_unsupported
  is SyncOutcome.OfflineOrFailed -> if (outcome.isOffline) R.string.schedule_refresh_offline
    else R.string.schedule_refresh_unavailable
  SyncOutcome.Success, SyncOutcome.Empty -> null
  null -> when (page?.status) {
    "SESSION_EXPIRED" -> R.string.schedule_refresh_expired
    "UNSUPPORTED_STRUCTURE", "UNEXPECTED_RESPONSE", "CACHE_UNREADABLE" -> R.string.schedule_refresh_unsupported
    "OFFLINE" -> R.string.schedule_refresh_offline
    "TEMPORARILY_UNAVAILABLE" -> R.string.schedule_refresh_unavailable
    else -> null
  }
}

internal fun scheduleCalendarIntent(entry: ScheduleEntry, date: LocalDate): Intent? {
  val occurrence = ScheduleOccurrences.onDate(entry, date) ?: return null
  return Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).apply {
    putExtra(CalendarContract.Events.TITLE, entry.courseTitle)
    putExtra(CalendarContract.Events.EVENT_TIMEZONE, ScheduleOccurrences.UNIVERSITY_ZONE.id)
    putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, occurrence.start.toEpochMilli())
    putExtra(CalendarContract.EXTRA_EVENT_END_TIME, occurrence.end.toEpochMilli())
    putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false)
    entry.room?.takeIf(String::isNotBlank)?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
    entry.notes?.takeIf(String::isNotBlank)?.let { putExtra(CalendarContract.Events.DESCRIPTION, it) }
  }
}
