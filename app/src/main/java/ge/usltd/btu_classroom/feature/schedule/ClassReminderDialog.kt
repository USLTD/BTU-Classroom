package ge.usltd.btu_classroom.feature.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAlarm
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ge.usltd.btu_classroom.R
import ge.usltd.btu_classroom.core.schedule.ScheduleEntry
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun ClassReminderDialog(
  entry: ScheduleEntry,
  date: LocalDate,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  val locale = LocalConfiguration.current.locales[0]
  val formattedDate = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))
  AlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.testTag("class_reminder_dialog"),
    icon = { Icon(Icons.Default.AddAlarm, contentDescription = null) },
    title = { Text(stringResource(R.string.reminder_confirm_title)) },
    text = {
      Column(modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(entry.courseTitle, style = MaterialTheme.typography.titleMedium)
        Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
          color = MaterialTheme.colorScheme.primaryContainer) {
          Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(formattedDate, style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(entry.startTime, style = MaterialTheme.typography.titleLarge,
              color = MaterialTheme.colorScheme.onPrimaryContainer)
          }
        }
        Text(stringResource(R.string.reminder_confirm_body))
      }
    },
    confirmButton = {
      Button(onClick = onConfirm, modifier = Modifier.testTag("class_reminder_confirm")) {
        Text(stringResource(R.string.schedule_reminder))
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
  )
}
