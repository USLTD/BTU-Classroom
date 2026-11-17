package ge.usltd.btu_classroom.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ge.usltd.btu_classroom.R
import ge.usltd.btu_classroom.core.database.entities.CourseWithEnrollment
import ge.usltd.btu_classroom.core.database.entities.SemesterEntity
import ge.usltd.btu_classroom.core.database.entities.StudentEntity
import ge.usltd.btu_classroom.core.database.entities.SyncMetadataEntity
import ge.usltd.btu_classroom.core.portal.PortalCachedPage
import ge.usltd.btu_classroom.core.portal.PortalModule
import ge.usltd.btu_classroom.core.schedule.ScheduleOccurrences
import ge.usltd.btu_classroom.design.components.FreshnessBanner
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
  student: StudentEntity?,
  semesters: List<SemesterEntity>,
  courses: List<CourseWithEnrollment>,
  syncMetadata: SyncMetadataEntity?,
  isSyncing: Boolean,
  isOffline: Boolean,
  onRefresh: () -> Unit,
  onOpenCoursesTab: () -> Unit,
  onOpenCourseDetail: (courseKey: String, courseId: String) -> Unit,
  onOpenScheduleTab: () -> Unit,
  modifier: Modifier = Modifier,
  schedulePage: PortalCachedPage? = null,
  scheduleIsLoading: Boolean = false,
  officialGpa: String? = null,
) {
  val currentTime by produceState(Instant.now()) {
    while (true) {
      value = Instant.now()
      delay(60_000L)
    }
  }
  val nextClass = remember(schedulePage?.content?.schedule, currentTime) {
    schedulePage?.content?.schedule.orEmpty().mapNotNull { ScheduleOccurrences.next(it, currentTime) }
      .minByOrNull { it.start }
  }
  val locale = LocalConfiguration.current.locales[0]
  val activeSemester = semesters.firstOrNull { it.isCurrent } ?: semesters.firstOrNull()
  val displayCourses = if (activeSemester != null) {
    courses.filter { it.course.semesterKey == activeSemester.semesterKey }
  } else {
    courses
  }
  val totalCredits = displayCourses.takeIf { list ->
    list.isNotEmpty() && list.all { it.course.credits != null }
  }?.sumOf { requireNotNull(it.course.credits) }
  val gradedScores = displayCourses.mapNotNull { it.enrollment?.totalScore }
  val averageGraded = gradedScores.takeIf { it.isNotEmpty() }?.average()

  Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            student?.displayName ?: stringResource(R.string.home_greeting_default),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          activeSemester?.let {
            Text(it.title, style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.primary)
          }
          student?.programName?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
      item {
        FreshnessBanner(syncMetadata, isSyncing, isOffline, onRefresh)
      }
      item {
        ElevatedCard(
          onClick = onOpenScheduleTab,
          modifier = Modifier.fillMaxWidth().testTag("home_schedule_preview_card"),
          shape = MaterialTheme.shapes.extraLarge,
          colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
          elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
        ) {
          Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.CalendarMonth, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
              Text(stringResource(R.string.home_schedule_preview_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.weight(1f))
              Icon(Icons.AutoMirrored.Filled.ArrowForward, null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
            }
            if (nextClass != null) {
              Text(stringResource(R.string.home_schedule_pattern),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
              Text(DateTimeFormatter.ofPattern("EEE, d MMM · HH:mm", locale)
                .withZone(ScheduleOccurrences.UNIVERSITY_ZONE).format(nextClass.start) + "–" + nextClass.entry.endTime,
                style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
              Text(nextClass.entry.courseTitle, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
              nextClass.entry.room?.let {
                Text(stringResource(R.string.schedule_room, it), style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onPrimaryContainer)
              }
            } else {
              Text(stringResource(if (schedulePage?.lastSuccessAtMillis != null) R.string.schedule_empty_week
                else R.string.home_schedule_no_cache), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text(stringResource(R.string.home_schedule_pattern_notice), style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onPrimaryContainer)
            schedulePage?.let { cached ->
              FreshnessBanner(SyncMetadataEntity(PortalModule.SCHEDULE.id, cached.lastAttemptAtMillis ?: 0L,
                cached.lastSuccessAtMillis, cached.status, cached.message), scheduleIsLoading, cached.status == "OFFLINE")
            }
          }
        }
      }
      item {
        Surface(
          modifier = Modifier.fillMaxWidth().testTag("home_courses_overview_card"),
          shape = MaterialTheme.shapes.large,
          color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
          Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.home_courses_summary_title), style = MaterialTheme.typography.titleLarge)
            if (displayCourses.isEmpty()) {
              Text(stringResource(R.string.home_no_courses_yet), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
              FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AcademicMetric(stringResource(R.string.home_enrolled_count, displayCourses.size))
                totalCredits?.let { AcademicMetric(stringResource(R.string.home_total_credits, formatNumber(it))) }
                AcademicMetric(stringResource(R.string.home_graded_count, gradedScores.size))
              }
              officialGpa?.takeIf { it.isNotBlank() }?.let {
                Text(stringResource(R.string.home_official_gpa, it), style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.primary)
              }
              averageGraded?.let {
                Text(stringResource(R.string.home_average_score, formatNumber(it)),
                  style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
            FilledTonalButton(onClick = onOpenCoursesTab,
              modifier = Modifier.heightIn(min = 48.dp).testTag("home_view_courses_button")) {
              Text(stringResource(R.string.home_view_all_courses))
              Spacer(Modifier.size(8.dp))
              Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
            }
          }
        }
      }
      if (displayCourses.isNotEmpty()) {
        item {
          Text(stringResource(R.string.home_recent_courses_title), style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface)
        }
        items(displayCourses.take(4), key = { it.course.courseKey }) { item ->
          Surface(
            onClick = { onOpenCourseDetail(item.course.courseKey, item.course.courseId) },
            modifier = Modifier.fillMaxWidth().testTag("home_course_preview_${item.course.courseId}"),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
          ) {
            BoxWithConstraints {
              val stackScore = maxWidth < 420.dp || LocalConfiguration.current.fontScale > 1.25f
              val score = item.enrollment?.totalScore
              val scoreLabel = if (score != null) stringResource(R.string.course_score_value, formatNumber(score))
                else stringResource(R.string.course_score_ungraded)
              Row(Modifier.fillMaxWidth().heightIn(min = 88.dp).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  if (item.course.code.isNotBlank()) {
                    Text(item.course.code, style = MaterialTheme.typography.labelMedium,
                      color = MaterialTheme.colorScheme.primary)
                  }
                  Text(item.course.title, style = MaterialTheme.typography.titleSmall)
                  if (stackScore) Text(scoreLabel, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!stackScore) Text(scoreLabel, style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.primary)
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp),
                  tint = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }
        }
      }
      item {
        Text(stringResource(R.string.about_disclaimer), style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
      }
    }
  }
}

@Composable
private fun AcademicMetric(label: String) {
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
    Icon(Icons.Default.Book, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
    Text(label, style = MaterialTheme.typography.titleSmall)
  }
}

private fun formatNumber(value: Double): String = if (value % 1.0 == 0.0) {
  value.toInt().toString()
} else {
  String.format(Locale.US, "%.1f", value)
}
