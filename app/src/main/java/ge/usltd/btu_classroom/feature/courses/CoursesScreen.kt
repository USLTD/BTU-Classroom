package ge.usltd.btu_classroom.feature.courses

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ge.usltd.btu_classroom.R
import ge.usltd.btu_classroom.core.database.entities.CourseWithEnrollment
import ge.usltd.btu_classroom.core.database.entities.SemesterEntity
import ge.usltd.btu_classroom.core.database.entities.SyncMetadataEntity
import ge.usltd.btu_classroom.core.parsing.ModuleCompatibilityEntry
import ge.usltd.btu_classroom.core.parsing.ModuleCompatibilityStatus
import ge.usltd.btu_classroom.design.components.BadgeTone
import ge.usltd.btu_classroom.design.components.EmptyStateCard
import ge.usltd.btu_classroom.design.components.FreshnessBanner
import ge.usltd.btu_classroom.design.components.StatusBadge
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CoursesScreen(
  semesters: List<SemesterEntity>,
  courses: List<CourseWithEnrollment>,
  syncMetadata: SyncMetadataEntity?,
  compatibility: ModuleCompatibilityEntry?,
  isSyncing: Boolean,
  isOffline: Boolean,
  onRefresh: () -> Unit,
  onCourseClick: (courseKey: String, courseId: String) -> Unit,
  modifier: Modifier = Modifier,
) {
  var selectedSemesterKey by rememberSaveable { mutableStateOf<String?>(null) }
  var searchQuery by rememberSaveable { mutableStateOf("") }
  LaunchedEffect(semesters) {
    if (semesters.none { it.semesterKey == selectedSemesterKey }) selectedSemesterKey = null
  }

  val semesterCourses = if (selectedSemesterKey == null) {
    courses
  } else {
    courses.filter { it.course.semesterKey == selectedSemesterKey }
  }
  val filteredCourses = semesterCourses.filter {
    it.course.title.contains(searchQuery.trim(), ignoreCase = true) ||
      it.course.code.contains(searchQuery.trim(), ignoreCase = true)
  }

  PullToRefreshBox(
    isRefreshing = isSyncing,
    onRefresh = onRefresh,
    modifier = modifier.fillMaxSize(),
  ) {
    Box(
      modifier = Modifier.fillMaxSize(),
      contentAlignment = Alignment.TopCenter,
    ) {
      LazyColumn(
        modifier = Modifier
          .widthIn(max = 720.dp)
          .fillMaxSize()
          .testTag("courses_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        item {
          FreshnessBanner(
            syncMetadata = syncMetadata,
            isSyncing = isSyncing,
            isOffline = isOffline,
            onRefresh = onRefresh,
          )
        }

        item {
          OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().testTag("courses_search"),
            singleLine = true,
            label = { Text(stringResource(R.string.courses_search_label)) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
              if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { searchQuery = "" }) {
                  Icon(Icons.Default.Close, stringResource(R.string.action_clear_search))
                }
              }
            },
            shape = MaterialTheme.shapes.large,
          )
        }

        // Compatibility notice when parser/module reports structural mismatch or temporary lock
        val showCompatNotice = compatibility != null &&
          compatibility.status in setOf(
            ModuleCompatibilityStatus.TEMPORARILY_UNAVAILABLE,
            ModuleCompatibilityStatus.PARSER_UPDATE_REQUIRED,
            ModuleCompatibilityStatus.UNEXPECTED_HTML,
          )

        item {
          AnimatedVisibility(visible = showCompatNotice) {
            EmptyStateCard(
              icon = Icons.Filled.WarningAmber,
              title = if (compatibility?.status == ModuleCompatibilityStatus.TEMPORARILY_UNAVAILABLE) {
                stringResource(R.string.courses_unavailable_title)
              } else {
                stringResource(R.string.courses_unsupported_title)
              },
              message = if (compatibility?.status == ModuleCompatibilityStatus.TEMPORARILY_UNAVAILABLE) {
                stringResource(R.string.courses_unavailable_body)
              } else {
                stringResource(R.string.courses_unsupported_body)
              },
              actionLabel = stringResource(R.string.action_retry),
              onAction = onRefresh,
            )
          }
        }

        // Semester selector chips when multiple semesters exist
        if (semesters.size > 1) {
          item {
            LazyRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              item {
                FilterChip(
                  selected = selectedSemesterKey == null,
                  onClick = { selectedSemesterKey = null },
                  label = { Text(stringResource(R.string.courses_filter_all)) },
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("semester_chip_all"),
                )
              }
              items(semesters, key = { it.semesterKey }) { semester ->
                FilterChip(
                  selected = selectedSemesterKey == semester.semesterKey,
                  onClick = { selectedSemesterKey = semester.semesterKey },
                  label = { Text(semester.title) },
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("semester_chip_${semester.rawSemesterKey}"),
                )
              }
            }
          }
        }

        if (filteredCourses.isEmpty() && !showCompatNotice) {
          item {
            EmptyStateCard(
              icon = Icons.Filled.Book,
              title = stringResource(if (searchQuery.isNotEmpty()) R.string.courses_search_empty_title
                else if (syncMetadata?.lastSuccessAtMillis == null) R.string.courses_not_synced_title
                else R.string.courses_empty_title),
              message = stringResource(if (searchQuery.isNotEmpty()) R.string.courses_search_empty_body
                else if (syncMetadata?.lastSuccessAtMillis == null) R.string.courses_not_synced_body
                else R.string.courses_empty_body),
              actionLabel = stringResource(if (searchQuery.isNotEmpty()) R.string.action_clear_search else R.string.action_refresh),
              onAction = { if (searchQuery.isNotEmpty()) searchQuery = "" else onRefresh() },
            )
          }
        } else {
          items(filteredCourses, key = { it.course.courseKey }) { item ->
            CourseCard(
              item = item,
              showSemesterLabel = semesters.size > 1 && selectedSemesterKey == null,
              onClick = { onCourseClick(item.course.courseKey, item.course.courseId) },
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CourseCard(
  item: CourseWithEnrollment,
  showSemesterLabel: Boolean,
  onClick: () -> Unit,
) {
  val course = item.course
  val score = item.enrollment?.totalScore

  Card(
    onClick = onClick,
    modifier = Modifier
      .fillMaxWidth()
      .testTag("course_card_${course.courseId}"),
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
      ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
          if (course.code.isNotBlank()) {
            Surface(
              shape = MaterialTheme.shapes.small,
              color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
              Text(
                text = course.code,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
              )
            }
            Spacer(modifier = Modifier.height(6.dp))
          }
          Text(
            text = course.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          if (showSemesterLabel && course.semesterName.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = course.semesterName,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        Icon(
          imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(22.dp),
        )
      }

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        if (score != null) {
          StatusBadge(
            label = stringResource(R.string.course_score_value, formatNumber(score)),
            icon = Icons.Filled.CheckCircle,
            tone = BadgeTone.PRIMARY,
          )
        } else {
          StatusBadge(
            label = stringResource(R.string.course_score_ungraded),
            icon = Icons.Filled.HourglassEmpty,
            tone = BadgeTone.NEUTRAL,
          )
        }

        course.credits?.let { cr ->
          StatusBadge(
            label = stringResource(R.string.course_credits_label, formatNumber(cr)),
            icon = Icons.Filled.School,
            tone = BadgeTone.PRIMARY,
          )
        }

        course.minCredit?.let { minCr ->
          StatusBadge(
            label = stringResource(R.string.course_min_credit_label, formatNumber(minCr)),
            icon = Icons.Filled.Book,
            tone = BadgeTone.NEUTRAL,
          )
        }
      }
    }
  }
}

private fun formatNumber(value: Double): String {
  return if (value % 1.0 == 0.0) {
    value.toInt().toString()
  } else {
    String.format(Locale.US, "%.1f", value)
  }
}
