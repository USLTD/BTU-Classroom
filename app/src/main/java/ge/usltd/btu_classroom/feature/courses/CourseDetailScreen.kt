package ge.usltd.btu_classroom.feature.courses

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Card
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ge.usltd.btu_classroom.R
import ge.usltd.btu_classroom.core.database.CachedCourseResourceDto
import ge.usltd.btu_classroom.core.database.SyncOutcome
import ge.usltd.btu_classroom.core.database.entities.CourseWithEnrollment
import ge.usltd.btu_classroom.core.database.entities.GradeEntity
import ge.usltd.btu_classroom.design.components.BadgeTone
import ge.usltd.btu_classroom.design.components.EmptyStateCard
import ge.usltd.btu_classroom.design.components.StatusBadge
import java.util.Locale
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailScreen(
  courseWithEnrollment: CourseWithEnrollment?,
  grades: List<GradeEntity>,
  resources: List<CachedCourseResourceDto>,
  isLoading: Boolean,
  isOffline: Boolean,
  onSyncDetail: () -> Unit,
  onOpenSafeUrl: (String) -> Unit,
  onNavigateBack: () -> Unit,
  syncOutcome: SyncOutcome? = null,
  onDownloadResource: ((CachedCourseResourceDto) -> Unit)? = null,
  downloadingResourceUrl: String? = null,
) {
  LaunchedEffect(courseWithEnrollment?.course?.courseKey) {
    if (courseWithEnrollment != null) onSyncDetail()
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = stringResource(R.string.course_detail_title),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        },
        navigationIcon = {
          IconButton(
            onClick = onNavigateBack,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("course_detail_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.nav_back),
            )
          }
        },
        actions = {
          IconButton(
            onClick = onSyncDetail,
            enabled = !isLoading,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("course_detail_refresh_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.Refresh,
              contentDescription = stringResource(R.string.action_refresh),
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
      Column(
        modifier = Modifier
          .widthIn(max = 720.dp)
          .fillMaxSize(),
      ) {
        if (isLoading) {
          LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        val course = courseWithEnrollment?.course
        if (course == null) {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .padding(16.dp),
            contentAlignment = Alignment.Center,
          ) {
            EmptyStateCard(
              icon = Icons.Filled.Info,
              title = stringResource(R.string.course_detail_title),
              message = stringResource(R.string.course_detail_unavailable),
              actionLabel = stringResource(R.string.action_retry),
              onAction = onSyncDetail,
            )
          }
        } else {
          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            if (!isLoading && (syncOutcome != null && syncOutcome != SyncOutcome.Success ||
                course.detailStatus == "UNSUPPORTED_STRUCTURE")) {
              item {
                DetailRefreshNotice(syncOutcome, course.detailSyncedAtMillis)
              }
            }
            if (isOffline) {
              item {
                Surface(
                  shape = RoundedCornerShape(10.dp),
                  color = MaterialTheme.colorScheme.secondaryContainer,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Text(
                    text = stringResource(R.string.course_detail_offline_notice),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                  )
                }
              }
            }

            // Title & score header
            item {
              Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
              ) {
                Column(
                  modifier = Modifier.padding(18.dp),
                  verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  if (course.code.isNotBlank()) {
                    Text(
                      text = course.code,
                      style = MaterialTheme.typography.labelMedium,
                      color = MaterialTheme.colorScheme.primary,
                    )
                  }
                  Text(
                    text = course.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                  )

                  val totalScore = courseWithEnrollment.enrollment?.totalScore
                  if (totalScore != null) {
                    StatusBadge(
                      label = "${stringResource(R.string.course_detail_score)}: ${formatNumber(totalScore)}",
                      icon = Icons.Filled.CheckCircle,
                      tone = BadgeTone.SUCCESS,
                    )
                  } else {
                    StatusBadge(
                      label = stringResource(R.string.course_score_ungraded),
                      icon = Icons.Filled.HourglassEmpty,
                      tone = BadgeTone.NEUTRAL,
                    )
                  }
                }
              }
            }

            item {
              Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
              ) {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Text(
                    text = stringResource(R.string.course_detail_overview),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                  HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                  if (course.code.isNotBlank()) {
                    DetailMetaRow(stringResource(R.string.course_detail_code), course.code)
                  }
                  if (course.semesterName.isNotBlank()) {
                    DetailMetaRow(stringResource(R.string.course_detail_semester), course.semesterName)
                  }
                  course.lecturer?.let { lec ->
                    DetailMetaRow(stringResource(R.string.course_detail_lecturer), lec)
                  }
                  course.credits?.let { cr ->
                    DetailMetaRow(stringResource(R.string.course_detail_credits), formatNumber(cr))
                  }
                  if (!course.syllabusSummary.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                      text = course.syllabusSummary,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              }
            }

            if (grades.isNotEmpty()) {
              item {
                Text(
                  text = stringResource(R.string.course_detail_grades_title),
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.onBackground,
                )
              }
              items(grades, key = { it.gradeKey }) { grade ->
                Card(
                  modifier = Modifier.fillMaxWidth(),
                  shape = MaterialTheme.shapes.medium,
                  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                      Text(
                        text = grade.categoryName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                      )
                      if (!grade.notes.isNullOrBlank()) {
                        Text(
                          text = grade.notes,
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                      }
                    }
                    val scoreText = when {
                      grade.score != null && grade.maxScore != null ->
                        "${formatNumber(grade.score)} / ${formatNumber(grade.maxScore)}"
                      grade.score != null -> formatNumber(grade.score)
                      grade.maxScore != null -> "— / ${formatNumber(grade.maxScore)}"
                      else -> "—"
                    }
                    Text(
                      text = scoreText,
                      style = MaterialTheme.typography.labelLarge,
                      fontWeight = FontWeight.SemiBold,
                      color = if (grade.score != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              }
            }

            if (resources.isNotEmpty()) {
              item {
                Text(
                  text = stringResource(R.string.course_detail_resources_title),
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.onBackground,
                )
              }
              items(resources, key = { it.url }) { res ->
                Card(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = downloadingResourceUrl == null) {
                      if (onDownloadResource != null) onDownloadResource(res) else onOpenSafeUrl(res.url)
                    }
                    .testTag("course_resource_${resources.indexOf(res)}"),
                  shape = MaterialTheme.shapes.medium,
                  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Text(
                      text = res.title,
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (downloadingResourceUrl == res.url) {
                      CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                      Icon(if (onDownloadResource != null) Icons.Filled.Download else Icons.Filled.OpenInBrowser,
                        contentDescription = stringResource(if (onDownloadResource != null)
                          R.string.action_download_resource else R.string.action_open_portal),
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
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
private fun DetailRefreshNotice(outcome: SyncOutcome?, savedAtMillis: Long?) {
  val message = when (outcome) {
    is SyncOutcome.OfflineOrFailed -> if (outcome.isOffline) R.string.course_detail_refresh_offline
      else R.string.course_detail_refresh_unavailable
    SyncOutcome.SessionExpired -> R.string.course_detail_refresh_expired
    SyncOutcome.Empty -> R.string.course_detail_refresh_empty
    else -> R.string.course_detail_refresh_unsupported
  }
  val locale = LocalConfiguration.current.locales[0]
  Surface(
    modifier = Modifier.fillMaxWidth().testTag("course_detail_refresh_notice"),
    color = MaterialTheme.colorScheme.secondaryContainer,
    shape = MaterialTheme.shapes.medium,
  ) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(stringResource(message), style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer)
      val cacheMessage = if (savedAtMillis != null) {
        val timestamp = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
          .withLocale(locale).withZone(ZoneId.of("Asia/Tbilisi")).format(Instant.ofEpochMilli(savedAtMillis))
        stringResource(R.string.course_detail_saved_at, timestamp)
      } else {
        stringResource(R.string.course_detail_summary_only)
      }
      Text(cacheMessage, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
  }
}

@Composable
private fun DetailMetaRow(label: String, value: String) {
  BoxWithConstraints(Modifier.fillMaxWidth()) {
    if (maxWidth < 360.dp || LocalConfiguration.current.fontScale > 1.3f) {
      Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
      }
    } else {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top) {
        Text(label, modifier = Modifier.weight(0.38f), style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, modifier = Modifier.weight(0.62f), style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
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
