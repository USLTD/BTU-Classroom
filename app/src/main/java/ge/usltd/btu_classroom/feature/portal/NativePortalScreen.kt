package ge.usltd.btu_classroom.feature.portal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ge.usltd.btu_classroom.R
import ge.usltd.btu_classroom.core.database.SyncOutcome
import ge.usltd.btu_classroom.core.database.entities.SyncMetadataEntity
import ge.usltd.btu_classroom.core.network.BtuRoutes
import ge.usltd.btu_classroom.core.portal.PortalCachedPage
import ge.usltd.btu_classroom.core.portal.PortalCalendarEvent
import ge.usltd.btu_classroom.core.portal.PortalField
import ge.usltd.btu_classroom.core.portal.PortalModule
import ge.usltd.btu_classroom.core.portal.PortalRow
import ge.usltd.btu_classroom.core.portal.PortalSection
import ge.usltd.btu_classroom.design.components.EmptyStateCard
import ge.usltd.btu_classroom.design.components.FreshnessBanner
import java.net.URI
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativePortalScreen(
  module: PortalModule,
  page: PortalCachedPage?,
  isLoading: Boolean,
  syncOutcome: SyncOutcome?,
  onRefresh: () -> Unit,
  onOpenOfficial: () -> Unit,
  onOpenSafeUrl: (String) -> Unit,
  onNavigateBack: () -> Unit,
) {
  var query by rememberSaveable(module.id) { mutableStateOf("") }
  val content = page?.content
  val hasSearchableContent = content?.sections?.any { it.rows.isNotEmpty() } == true ||
    content?.calendarEvents?.isNotEmpty() == true
  val normalizedQuery = query.trim()
  val sections = content?.sections.orEmpty().map { section ->
    section.copy(rows = section.rows.filter { row -> row.values.any { it.contains(normalizedQuery, ignoreCase = true) } })
  }
  val events = content?.calendarEvents.orEmpty().filter {
    it.title.contains(normalizedQuery, ignoreCase = true) || it.date.contains(normalizedQuery)
  }
  val failedNotice = failureMessage(syncOutcome, page?.status)
  val isOffline = (syncOutcome as? SyncOutcome.OfflineOrFailed)?.isOffline == true || page?.status == "OFFLINE"
  val metadata = page?.let {
    SyncMetadataEntity(module.id, it.lastAttemptAtMillis ?: it.lastSuccessAtMillis ?: 0L,
      it.lastSuccessAtMillis, it.status, it.message)
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(portalModuleTitle(module))) },
        navigationIcon = {
          IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("portal_back")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.nav_back))
          }
        },
        actions = {
          IconButton(onClick = onRefresh, enabled = !isLoading, modifier = Modifier.testTag("portal_refresh")) {
            Icon(Icons.Default.Refresh, stringResource(R.string.action_refresh))
          }
          IconButton(onClick = onOpenOfficial, modifier = Modifier.testTag("portal_official")) {
            Icon(Icons.Default.OpenInBrowser, stringResource(R.string.action_open_portal))
          }
        },
      )
    },
    containerColor = MaterialTheme.colorScheme.background,
  ) { padding ->
    PullToRefreshBox(isRefreshing = isLoading, onRefresh = onRefresh,
      modifier = Modifier.fillMaxSize().padding(padding)) {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
          modifier = Modifier.widthIn(max = 760.dp).fillMaxSize().testTag("portal_list"),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          item {
            FreshnessBanner(metadata, isLoading, isOffline, onRefresh)
          }
          if (!isLoading && failedNotice != null) {
            item { PortalNotice(stringResource(failedNotice), "portal_sync_notice") }
          }
          if (module == PortalModule.EXAMS) {
            item { PortalNotice(stringResource(R.string.portal_exam_year_notice), "portal_exam_year_notice") }
          }
          if (module == PortalModule.PROGRAM) {
            item { PortalNotice(stringResource(R.string.portal_program_read_only), "portal_program_read_only") }
          }
          if (module == PortalModule.ACADEMIC_CARD || module == PortalModule.RATING) {
            item { PortalNotice(stringResource(R.string.portal_official_values), "portal_official_values") }
          }
          if (module == PortalModule.CALENDAR && content?.calendarEvents?.isNotEmpty() == true) {
            val years = content.calendarEvents.mapNotNull { parseDate(it.date)?.year }.distinct().sorted()
            val currentYear = LocalDate.now(TBILISI).year
            if (years.isNotEmpty() && years.all { it < currentYear }) {
              item {
                PortalNotice(stringResource(R.string.portal_calendar_archive, years.joinToString(", ")),
                  "portal_calendar_archive_notice")
              }
            }
            if (currentYear !in years) {
              item {
                PortalNotice(stringResource(R.string.portal_calendar_no_current, currentYear),
                  "portal_calendar_no_current_notice")
              }
            }
          }
          content?.notices.orEmpty().forEachIndexed { index, notice ->
            item(key = "notice-$index") { PortalNotice(portalText(notice), "portal_server_notice_$index") }
          }
          content?.fields.orEmpty().forEachIndexed { index, field ->
            item(key = "field-$index") { PortalFieldCard(field, index) }
          }
          if (hasSearchableContent) {
            item {
              OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().testTag("portal_search"),
                label = { Text(stringResource(R.string.portal_search)) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                  if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                      Icon(Icons.Default.Close, stringResource(R.string.action_clear_search))
                    }
                  }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
              )
            }
          }
          sections.forEachIndexed { sectionIndex, section ->
            if (section.rows.isNotEmpty()) {
              section.title?.takeIf(String::isNotBlank)?.let { title ->
                item(key = "section-$sectionIndex") {
                  Text(portalText(title), style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground)
                }
              }
              items(section.rows.size, key = { "row-$sectionIndex-$it" }) { rowIndex ->
                PortalRowCard(module, section, section.rows[rowIndex], sectionIndex, rowIndex, onOpenSafeUrl)
              }
            }
          }
          items(events, key = { "${it.date}-${it.endDate}-${it.title}" }) { event -> CalendarEventCard(event) }
          val noContent = content == null || (content.fields.isEmpty() &&
            content.sections.all { it.rows.isEmpty() } && content.calendarEvents.isEmpty() && content.notices.isEmpty())
          if (!isLoading && noContent) {
            val synchronized = page?.lastSuccessAtMillis != null && content != null
            item {
              EmptyStateCard(Icons.Default.Info,
                stringResource(if (synchronized) R.string.portal_empty_title else R.string.portal_not_synced_title),
                stringResource(if (synchronized) R.string.portal_empty_body else R.string.portal_not_synced_body),
                stringResource(R.string.action_refresh), onRefresh)
            }
          } else if (normalizedQuery.isNotEmpty() && sections.all { it.rows.isEmpty() } && events.isEmpty()) {
            item {
              EmptyStateCard(Icons.Default.Search, stringResource(R.string.portal_no_search_results),
                stringResource(R.string.courses_search_empty_body), stringResource(R.string.action_clear_search), { query = "" })
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PortalFieldCard(field: PortalField, index: Int) {
  PortalCard(Modifier.testTag("portal_field_$index")) {
    Text(portalLabel(field.label, index), style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(field.value.ifBlank { "—" }, style = MaterialTheme.typography.titleLarge,
      color = MaterialTheme.colorScheme.onSurface)
  }
}

@Composable
private fun PortalRowCard(
  module: PortalModule,
  section: PortalSection,
  row: PortalRow,
  sectionIndex: Int,
  rowIndex: Int,
  onOpenSafeUrl: (String) -> Unit,
) {
  val titleIndex = if (module == PortalModule.ACADEMIC_CARD || module == PortalModule.STATEMENTS) 1 else 0
  PortalCard(Modifier.testTag("portal_row_${sectionIndex}_$rowIndex")) {
    row.values.getOrNull(titleIndex)?.takeIf(String::isNotBlank)?.let { title ->
      Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
    row.values.forEachIndexed { index, value ->
      if (index != titleIndex) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
          Text(portalLabel(section.columns.getOrElse(index) { "" }, index),
            modifier = Modifier.weight(0.42f), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
          Text(value.ifBlank { "—" }, modifier = Modifier.weight(0.58f),
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface)
        }
      }
    }
    if (module == PortalModule.STATEMENTS) {
      row.links.mapNotNull { link ->
        BtuRoutes.resolveSafeBtuUrl(link.url)?.takeIf { url ->
          val uri = URI(url)
          STATEMENT_VIEW_PATH.matches(uri.path) && uri.rawQuery == null
        }
      }.distinct().forEachIndexed { linkIndex, safeUrl ->
        TextButton(onClick = { onOpenSafeUrl(safeUrl) },
          modifier = Modifier.testTag("portal_statement_${sectionIndex}_${rowIndex}_$linkIndex")) {
          Text(stringResource(R.string.portal_view_statement))
        }
      }
    }
  }
}

@Composable
private fun CalendarEventCard(event: PortalCalendarEvent) {
  val locale = LocalConfiguration.current.locales[0]
  val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
  val start = parseDate(event.date)?.format(formatter) ?: event.date
  val end = event.endDate?.let { parseDate(it)?.format(formatter) ?: it }
  PortalCard {
    Text(if (end == null || end == start) start else "$start – $end",
      style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    Text(event.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
  }
}

@Composable
private fun PortalNotice(text: String, tag: String) {
  Surface(Modifier.fillMaxWidth().testTag(tag), shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.secondaryContainer) {
    Text(text, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSecondaryContainer)
  }
}

@Composable
private fun PortalCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceContainerLow) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
  }
}

private fun failureMessage(outcome: SyncOutcome?, status: String?): Int? = when {
  outcome is SyncOutcome.UnsupportedStructure || status in setOf("UNSUPPORTED_STRUCTURE", "UNEXPECTED_RESPONSE", "CACHE_UNREADABLE") ->
    R.string.portal_refresh_unsupported
  outcome is SyncOutcome.SessionExpired || status == "SESSION_EXPIRED" -> R.string.portal_refresh_expired
  outcome is SyncOutcome.OfflineOrFailed && outcome.isOffline || status == "OFFLINE" -> R.string.portal_refresh_offline
  outcome is SyncOutcome.OfflineOrFailed || status == "TEMPORARILY_UNAVAILABLE" -> R.string.portal_refresh_unavailable
  else -> null
}

private fun parseDate(value: String): LocalDate? = try {
  LocalDate.parse(value)
} catch (_: java.time.format.DateTimeParseException) {
  null
}

private val TBILISI: ZoneId = ZoneId.of("Asia/Tbilisi")
private val STATEMENT_VIEW_PATH = Regex("/ge/student/me/statements/view/\\d+/\\d+/?")
