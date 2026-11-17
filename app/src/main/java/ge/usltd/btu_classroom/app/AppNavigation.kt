package ge.usltd.btu_classroom.app

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import ge.usltd.btu_classroom.BuildConfig
import ge.usltd.btu_classroom.R
import ge.usltd.btu_classroom.core.auth.AuthState
import ge.usltd.btu_classroom.core.network.BtuRoutes
import ge.usltd.btu_classroom.core.portal.PortalModule
import ge.usltd.btu_classroom.core.download.CourseDownloadResult
import ge.usltd.btu_classroom.core.sync.ClassReminderResult
import ge.usltd.btu_classroom.core.preferences.LocaleHelper
import ge.usltd.btu_classroom.design.theme.BtucmTheme
import ge.usltd.btu_classroom.feature.auth.GeckoBrowserModal
import ge.usltd.btu_classroom.feature.courses.CourseDetailScreen
import ge.usltd.btu_classroom.feature.courses.CoursesScreen
import ge.usltd.btu_classroom.feature.diagnostics.DiagnosticsScreen
import ge.usltd.btu_classroom.feature.home.HomeScreen
import ge.usltd.btu_classroom.feature.more.MoreScreen
import ge.usltd.btu_classroom.feature.onboarding.OnboardingScreen
import ge.usltd.btu_classroom.feature.schedule.ScheduleScreen
import ge.usltd.btu_classroom.feature.settings.SettingsScreen
import ge.usltd.btu_classroom.feature.portal.NativePortalScreen
import ge.usltd.btu_classroom.feature.schedule.ClassReminderDialog
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data object CoursesRoute
@Serializable data class CourseDetailRoute(val courseKey: String, val courseId: String)
@Serializable data object ScheduleRoute
@Serializable data object MoreRoute
@Serializable data object SettingsRoute
@Serializable data object DiagnosticsRoute
@Serializable data class PortalRoute(val moduleId: String)

private enum class TopTab(
  val route: Any,
  val labelRes: Int,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val tag: String,
) {
  HOME(HomeRoute, R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home, "tab_home"),
  COURSES(CoursesRoute, R.string.nav_courses, Icons.Filled.Book, Icons.Outlined.Book, "tab_courses"),
  SCHEDULE(ScheduleRoute, R.string.nav_schedule, Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth, "tab_schedule"),
  MORE(MoreRoute, R.string.nav_more, Icons.Filled.MoreHoriz, Icons.Outlined.MoreHoriz, "tab_more"),
}

@Composable
fun BtucmAppRoot(viewModel: MainViewModel) {
  val authState by viewModel.authState.collectAsStateWithLifecycle()
  val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
  val geckoSession by viewModel.activeGeckoSession.collectAsStateWithLifecycle()
  val preferences by viewModel.preferences.collectAsStateWithLifecycle()
  val stayOfflineOverride by viewModel.stayOfflineOverride.collectAsStateWithLifecycle()
  val exploring by viewModel.exploring.collectAsStateWithLifecycle()
  val courses by viewModel.courses.collectAsStateWithLifecycle()
  val syncMetadata by viewModel.coursesSyncMetadata.collectAsStateWithLifecycle()
  val snackbarNotice by viewModel.snackbarNotice.collectAsStateWithLifecycle()
  val reminderRequest by viewModel.pendingClassReminder.collectAsStateWithLifecycle()
  val reminderResult by viewModel.classReminderResult.collectAsStateWithLifecycle()
  val downloadedFile by viewModel.pendingFileDownload.collectAsStateWithLifecycle()
  val localCleanupFailed by viewModel.localCleanupFailed.collectAsStateWithLifecycle()

  val baseContext = LocalContext.current
  val localizedContext = remember(baseContext, preferences.language) {
    LocaleHelper.createLocalizedContext(baseContext, preferences.language)
  }
  val portalLocale = localizedContext.resources.configuration.locales[0]
  val reminderPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    if (granted) viewModel.confirmClassReminder() else {
      viewModel.dismissClassReminder()
      Toast.makeText(localizedContext, localizedContext.getString(R.string.reminder_permission_required), Toast.LENGTH_LONG).show()
    }
  }

  CompositionLocalProvider(
    LocalContext provides localizedContext,
    LocalConfiguration provides localizedContext.resources.configuration,
  ) {
    BtucmTheme(
      themeMode = preferences.themeMode,
      dynamicColor = preferences.dynamicColor,
    ) {
      LaunchedEffect(localCleanupFailed) {
        if (localCleanupFailed) {
          Toast.makeText(localizedContext, localizedContext.getString(R.string.local_cleanup_failed), Toast.LENGTH_LONG).show()
          viewModel.consumeLocalCleanupFailure()
        }
      }
      reminderRequest?.let { request ->
        ClassReminderDialog(request.entry, request.date,
          onConfirm = {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(baseContext,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
              reminderPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else viewModel.confirmClassReminder()
          }, onDismiss = viewModel::dismissClassReminder)
      }
      LaunchedEffect(reminderResult) {
        reminderResult?.let { result ->
          val message = when (result) {
            ClassReminderResult.SCHEDULED -> R.string.reminder_scheduled
            ClassReminderResult.PERMISSION_REQUIRED -> R.string.reminder_permission_required
            ClassReminderResult.NOTIFICATIONS_DISABLED -> R.string.reminder_notifications_disabled
            ClassReminderResult.TOO_LATE -> R.string.reminder_too_late
            ClassReminderResult.INVALID_DATE -> R.string.reminder_invalid_date
          }
          Toast.makeText(localizedContext, localizedContext.getString(message), Toast.LENGTH_LONG).show()
          viewModel.consumeClassReminderResult()
        }
      }
      LaunchedEffect(downloadedFile) {
        downloadedFile?.let { result ->
          val error = when (result) {
            is CourseDownloadResult.Success -> {
              try {
                localizedContext.startActivity(Intent(Intent.ACTION_VIEW).apply {
                  setDataAndType(result.uri, result.mimeType)
                  addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                null
              } catch (_: ActivityNotFoundException) {
                R.string.download_no_viewer
              }
            }
            CourseDownloadResult.SessionExpired -> R.string.download_session_expired
            CourseDownloadResult.InvalidResource -> R.string.download_invalid
            CourseDownloadResult.TooLarge -> R.string.download_too_large
            CourseDownloadResult.Unavailable -> R.string.download_unavailable
            CourseDownloadResult.NetworkError -> R.string.download_network_error
          }
          if (error != null) Toast.makeText(localizedContext, localizedContext.getString(error), Toast.LENGTH_LONG).show()
          viewModel.consumeFileDownload()
        }
      }
      val showMainShell = authState is AuthState.Authenticated || exploring ||
        ((stayOfflineOverride || telemetry.isOfflineFallback) &&
          (courses.isNotEmpty() || syncMetadata?.lastSuccessAtMillis != null))

      if (showMainShell) {
        AuthenticatedShell(
          viewModel = viewModel,
          snackbarNotice = snackbarNotice,
          onOpenExternalUrl = { url ->
            BtuRoutes.forLocale(url, portalLocale)?.let { openUrlExternally(baseContext, it) }
          },
        )
      } else {
        OnboardingScreen(
          authState = authState,
          telemetry = telemetry,
          preferences = preferences,
          onSignInClick = viewModel::startLogin,
          onResumeBrowserClick = viewModel::openBrowserModal,
          onOpenExternalBrowser = { openUrlExternally(baseContext, BtuRoutes.loginUrl(portalLocale)) },
          onRememberLoginChanged = viewModel::setRememberLogin,
          onLanguageChanged = viewModel::setLanguage,
          onExploreApp = viewModel::exploreApp,
        )
        val snackbarHost = remember { SnackbarHostState() }
        LaunchedEffect(snackbarNotice) {
          if (!snackbarNotice.isNullOrBlank()) {
            snackbarHost.showSnackbar(snackbarNotice!!)
            viewModel.consumeSnackbarNotice()
          }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
          SnackbarHost(snackbarHost)
        }
      }

      if (telemetry.isBrowserModalVisible) {
        GeckoBrowserModal(
          telemetry = telemetry,
          geckoSession = geckoSession,
          onBackPressed = { viewModel.handleBrowserBack() },
          onCloseModal = viewModel::closeBrowserModal,
          onOpenExternalBrowser = { openUrlExternally(baseContext, BtuRoutes.loginUrl(portalLocale)) },
        )
      }

      if (telemetry.isReauthPromptVisible) {
        AlertDialog(
          onDismissRequest = viewModel::dismissReauthPromptAndStayOffline,
          modifier = Modifier.testTag("reauth_dialog"),
          title = { Text(stringResource(R.string.reauth_dialog_title)) },
          text = { Text(stringResource(R.string.reauth_dialog_message)) },
          confirmButton = {
            Button(
              onClick = viewModel::reauthenticateFromPrompt,
              shapes = ButtonDefaults.shapes(),
              modifier = Modifier.testTag("reauth_confirm_button"),
            ) {
              Text(stringResource(R.string.reauth_dialog_confirm))
            }
          },
          dismissButton = {
            TextButton(
              onClick = viewModel::dismissReauthPromptAndStayOffline,
              modifier = Modifier.testTag("reauth_dismiss_button"),
            ) {
              Text(stringResource(R.string.reauth_dialog_dismiss))
            }
          },
        )
      }

      val isSignOutDialogVisible by viewModel.isSignOutDialogVisible.collectAsStateWithLifecycle()
      if (isSignOutDialogVisible) {
        val unconfirmedRemoteMsg = stringResource(R.string.remote_logout_unconfirmed)
        AlertDialog(
          onDismissRequest = viewModel::dismissSignOutDialog,
          modifier = Modifier.testTag("logout_confirm_dialog"),
          title = { Text(stringResource(R.string.logout_dialog_title)) },
          text = { Text(stringResource(R.string.logout_dialog_message)) },
          confirmButton = {
            Button(
              onClick = { viewModel.confirmSignOut(unconfirmedRemoteMsg) },
              shapes = ButtonDefaults.shapes(),
              modifier = Modifier.testTag("logout_confirm_button"),
            ) {
              Text(stringResource(R.string.logout_dialog_confirm))
            }
          },
          dismissButton = {
            TextButton(
              onClick = viewModel::dismissSignOutDialog,
              modifier = Modifier.testTag("logout_cancel_button"),
            ) {
              Text(stringResource(R.string.dialog_cancel))
            }
          },
        )
      }

      val isClearDataDialogVisible by viewModel.isClearDataDialogVisible.collectAsStateWithLifecycle()
      if (isClearDataDialogVisible) {
        AlertDialog(
          onDismissRequest = viewModel::dismissClearDataDialog,
          modifier = Modifier.testTag("clear_data_confirm_dialog"),
          title = { Text(stringResource(R.string.clear_data_dialog_title)) },
          text = { Text(stringResource(R.string.clear_data_dialog_message)) },
          confirmButton = {
            Button(
              onClick = viewModel::confirmClearLocalData,
              shapes = ButtonDefaults.shapes(),
              modifier = Modifier.testTag("clear_data_confirm_button"),
            ) {
              Text(stringResource(R.string.clear_data_dialog_confirm))
            }
          },
          dismissButton = {
            TextButton(
              onClick = viewModel::dismissClearDataDialog,
              modifier = Modifier.testTag("clear_data_cancel_button"),
            ) {
              Text(stringResource(R.string.dialog_cancel))
            }
          },
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedShell(
  viewModel: MainViewModel,
  snackbarNotice: String?,
  onOpenExternalUrl: (String) -> Unit,
) {
  val navController = rememberNavController()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentDestination = navBackStackEntry?.destination
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(snackbarNotice) {
    if (!snackbarNotice.isNullOrBlank()) {
      snackbarHostState.showSnackbar(snackbarNotice)
      viewModel.consumeSnackbarNotice()
    }
  }

  val isTopLevelRoute = currentDestination?.let { dest ->
    dest.hasRoute(HomeRoute::class) ||
      dest.hasRoute(CoursesRoute::class) ||
      dest.hasRoute(ScheduleRoute::class) ||
      dest.hasRoute(MoreRoute::class)
  } ?: true

  val topBarTitleRes = when {
    currentDestination?.hasRoute(CoursesRoute::class) == true -> R.string.courses_title
    else -> R.string.app_full_name
  }

  val authState by viewModel.authState.collectAsStateWithLifecycle()
  val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
  val preferences by viewModel.preferences.collectAsStateWithLifecycle()
  val student by viewModel.student.collectAsStateWithLifecycle()
  val semesters by viewModel.semesters.collectAsStateWithLifecycle()
  val courses by viewModel.courses.collectAsStateWithLifecycle()
  val syncMetadata by viewModel.coursesSyncMetadata.collectAsStateWithLifecycle()
  val compatibilityEntries by viewModel.compatibilityEntries.collectAsStateWithLifecycle()
  val isSyncingCourses by viewModel.isSyncingCourses.collectAsStateWithLifecycle()
  val syncingDetailKeys by viewModel.syncingDetailKeys.collectAsStateWithLifecycle()
  val detailSyncOutcomes by viewModel.detailSyncOutcomes.collectAsStateWithLifecycle()
  val syncingPortalModules by viewModel.syncingPortalModules.collectAsStateWithLifecycle()
  val portalSyncOutcomes by viewModel.portalSyncOutcomes.collectAsStateWithLifecycle()
  val schedulePage by viewModel.observePortal(PortalModule.SCHEDULE).collectAsStateWithLifecycle()
  val academicCard by viewModel.observePortal(PortalModule.ACADEMIC_CARD).collectAsStateWithLifecycle()
  val downloadingResourceUrl by viewModel.downloadingResourceUrl.collectAsStateWithLifecycle()

  fun navigateTopTab(route: Any) {
    navController.navigate(route) {
      popUpTo(navController.graph.findStartDestination().id) {
        saveState = true
      }
      launchSingleTop = true
      restoreState = true
    }
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useNavigationRail = maxWidth >= 600.dp

    Scaffold(
      contentWindowInsets = WindowInsets.safeDrawing,
      snackbarHost = { SnackbarHost(snackbarHostState) },
      topBar = {
        if (isTopLevelRoute) {
          TopAppBar(
            title = {
              Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(painterResource(R.drawable.btu_logo), stringResource(R.string.ux_btu_logo),
                  Modifier.size(36.dp))
                Text(stringResource(topBarTitleRes), style = MaterialTheme.typography.titleLarge)
              }
            },
            colors = TopAppBarDefaults.topAppBarColors(
              containerColor = MaterialTheme.colorScheme.background,
              scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            actions = {
              if (authState !is AuthState.Authenticated) {
                TextButton(onClick = viewModel::startLogin, enabled = !telemetry.isBusy) {
                  Text(stringResource(R.string.action_sign_in_short))
                }
              }
            },
          )
        }
      },
      bottomBar = {
        if (isTopLevelRoute && !useNavigationRail) {
          NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp) {
            TopTab.entries.forEach { tab ->
              val selected = currentDestination?.hasRoute(tab.route::class) == true
              NavigationBarItem(
                selected = selected,
                onClick = { navigateTopTab(tab.route) },
                icon = {
                  Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = null,
                  )
                },
                label = { Text(stringResource(tab.labelRes)) },
                modifier = Modifier.testTag(tab.tag),
              )
            }
          }
        }
      },
      containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
      ) {
        if (isTopLevelRoute && useNavigationRail) {
          NavigationRail(modifier = Modifier.fillMaxHeight(),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            header = {
              Image(painterResource(R.drawable.btu_logo), stringResource(R.string.ux_btu_logo),
                Modifier.padding(vertical = 16.dp).size(48.dp))
            }) {
            TopTab.entries.forEach { tab ->
              val selected = currentDestination?.hasRoute(tab.route::class) == true
              NavigationRailItem(
                selected = selected,
                onClick = { navigateTopTab(tab.route) },
                icon = {
                  Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = null,
                  )
                },
                label = { Text(stringResource(tab.labelRes)) },
                modifier = Modifier.testTag(tab.tag),
              )
            }
          }
        }

        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
          NavHost(
            navController = navController,
            startDestination = HomeRoute,
          ) {
            composable<HomeRoute> {
              LaunchedEffect(authState) {
                if (authState is AuthState.Authenticated) {
                  viewModel.refreshPortal(PortalModule.SCHEDULE)
                  viewModel.refreshPortal(PortalModule.ACADEMIC_CARD)
                }
              }
              HomeScreen(
                student = student,
                semesters = semesters,
                courses = courses,
                syncMetadata = syncMetadata,
                isSyncing = isSyncingCourses,
                isOffline = telemetry.isOfflineFallback,
                onRefresh = {
                  if (authState is AuthState.Authenticated) {
                    viewModel.refreshCourses()
                    viewModel.refreshPortal(PortalModule.SCHEDULE)
                    viewModel.refreshPortal(PortalModule.ACADEMIC_CARD)
                  } else viewModel.startLogin()
                },
                onOpenCoursesTab = { navigateTopTab(CoursesRoute) },
                onOpenCourseDetail = { key, id ->
                  navController.navigate(CourseDetailRoute(courseKey = key, courseId = id))
                },
                onOpenScheduleTab = { navigateTopTab(ScheduleRoute) },
                schedulePage = schedulePage,
                scheduleIsLoading = PortalModule.SCHEDULE in syncingPortalModules,
                officialGpa = academicCard?.content?.fields?.firstOrNull {
                  it.label.trim().equals("GPA", ignoreCase = true)
                }?.value,
              )
            }

            composable<CoursesRoute> {
              CoursesScreen(
                semesters = semesters,
                courses = courses,
                syncMetadata = syncMetadata,
                compatibility = compatibilityEntries[ge.usltd.btu_classroom.core.parsing.BtuModuleId.SELECTED_COURSES],
                isSyncing = isSyncingCourses,
                isOffline = telemetry.isOfflineFallback,
                onRefresh = {
                  if (authState is AuthState.Authenticated) viewModel.refreshCourses() else viewModel.startLogin()
                },
                onCourseClick = { key, id ->
                  navController.navigate(CourseDetailRoute(courseKey = key, courseId = id))
                },
              )
            }

            composable<CourseDetailRoute> { backStackEntry ->
              val args = backStackEntry.toRoute<CourseDetailRoute>()
              val courseFlow = remember(args.courseKey) { viewModel.observeCourseDetail(args.courseKey) }
              val gradesFlow = remember(args.courseKey) { viewModel.observeCourseGrades(args.courseKey) }
              val courseWithEnrollment by courseFlow.collectAsStateWithLifecycle(initialValue = null)
              val grades by gradesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
              val resources = remember(courseWithEnrollment?.course?.resourcesJson) {
                viewModel.decodeResources(courseWithEnrollment?.course?.resourcesJson)
              }

              CourseDetailScreen(
                courseWithEnrollment = courseWithEnrollment,
                grades = grades,
                resources = resources,
                isLoading = args.courseKey in syncingDetailKeys,
                isOffline = telemetry.isOfflineFallback,
                onSyncDetail = {
                  viewModel.refreshCourseDetail(
                    courseKey = args.courseKey,
                    courseId = args.courseId,
                    detailPath = courseWithEnrollment?.course?.detailPath,
                  )
                },
                onOpenSafeUrl = { url ->
                  BtuRoutes.resolveSafeBtuUrl(url)?.let(onOpenExternalUrl)
                },
                onNavigateBack = { navController.popBackStack() },
                syncOutcome = detailSyncOutcomes[args.courseKey],
                onDownloadResource = viewModel::downloadResource,
                downloadingResourceUrl = downloadingResourceUrl,
              )
            }

            composable<ScheduleRoute> {
              LaunchedEffect(authState) {
                if (authState is AuthState.Authenticated) viewModel.refreshPortal(PortalModule.SCHEDULE)
              }
              ScheduleScreen(
                onOpenPortalSchedule = { url ->
                  BtuRoutes.resolveSafeBtuUrl(url)?.let(onOpenExternalUrl)
                },
                page = schedulePage,
                isLoading = PortalModule.SCHEDULE in syncingPortalModules,
                syncOutcome = portalSyncOutcomes[PortalModule.SCHEDULE],
                onRefresh = {
                  if (authState is AuthState.Authenticated) viewModel.refreshPortal(PortalModule.SCHEDULE)
                  else viewModel.startLogin()
                },
                onOpenExams = { navController.navigate(PortalRoute(PortalModule.EXAMS.id)) },
                onRequestReminder = viewModel::requestClassReminder,
              )
            }

            composable<MoreRoute> {
              MoreScreen(
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onOpenDiagnostics = {
                  if (BuildConfig.DEBUG) {
                    navController.navigate(DiagnosticsRoute)
                  }
                },
                onRequestSignOut = viewModel::requestSignOut,
                onRequestClearLocalData = viewModel::requestClearLocalData,
                onOpenPortal = { url ->
                  BtuRoutes.resolveSafeBtuUrl(url)?.let(onOpenExternalUrl)
                },
                onOpenModule = { module -> navController.navigate(PortalRoute(module.id)) },
              )
            }

            composable<PortalRoute> { backStackEntry ->
              val args = backStackEntry.toRoute<PortalRoute>()
              val module = PortalModule.entries.firstOrNull { it.id == args.moduleId }
              if (module == null) {
                Text(stringResource(R.string.portal_route_unavailable))
              } else {
                val page by viewModel.observePortal(module).collectAsStateWithLifecycle()
                LaunchedEffect(module, authState) {
                  if (authState is AuthState.Authenticated) viewModel.refreshPortal(module)
                }
                NativePortalScreen(
                  module = module,
                  page = page,
                  isLoading = module in syncingPortalModules,
                  syncOutcome = portalSyncOutcomes[module],
                  onRefresh = {
                    if (authState is AuthState.Authenticated) viewModel.refreshPortal(module)
                    else viewModel.startLogin()
                  },
                  onOpenOfficial = { onOpenExternalUrl(BtuRoutes.BASE_ORIGIN + module.path) },
                  onOpenSafeUrl = { url -> BtuRoutes.resolveSafeBtuUrl(url)?.let(onOpenExternalUrl) },
                  onNavigateBack = { navController.popBackStack() },
                )
              }
            }

            composable<SettingsRoute> {
              SettingsScreen(
                preferences = preferences,
                onLanguageChanged = viewModel::setLanguage,
                onThemeModeChanged = viewModel::setThemeMode,
                onDynamicColorChanged = viewModel::setDynamicColor,
                onRememberLoginChanged = viewModel::setRememberLogin,
                onRequestSignOut = viewModel::requestSignOut,
                onRequestClearLocalData = viewModel::requestClearLocalData,
                onNavigateBack = { navController.popBackStack() },
                onBackgroundSyncChanged = viewModel::setBackgroundSync,
                onNotificationsEnabledChanged = viewModel::setNotificationsEnabled,
              )
            }

            if (BuildConfig.DEBUG) {
              composable<DiagnosticsRoute> {
                DiagnosticsScreen(
                  authState = authState,
                  telemetry = telemetry,
                  compatibilityEntries = compatibilityEntries,
                  onVerifySession = viewModel::verifySession,
                  onCheckExpiry = viewModel::checkSessionExpiration,
                  onRefreshCourses = viewModel::refreshCourses,
                  onClearLogs = viewModel::clearLogs,
                  onNavigateBack = { navController.popBackStack() },
                )
              }
            }
          }
        }
      }
    }
  }
}

private fun openUrlExternally(context: Context, url: String) {
  try {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
  } catch (_: ActivityNotFoundException) {
    Toast.makeText(context, context.getString(R.string.browser_unavailable), Toast.LENGTH_LONG).show()
  }
}
