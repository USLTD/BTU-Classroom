package ge.usltd.btu_classroom.app

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import ge.usltd.btu_classroom.core.auth.SessionManager
import ge.usltd.btu_classroom.core.auth.LocalCleanupException
import ge.usltd.btu_classroom.core.database.CourseRepository
import ge.usltd.btu_classroom.core.database.SyncOutcome
import ge.usltd.btu_classroom.core.parsing.PageCompatibilityRegistry
import ge.usltd.btu_classroom.core.portal.PortalCachedPage
import ge.usltd.btu_classroom.core.portal.PortalModule
import ge.usltd.btu_classroom.core.portal.PortalRepository
import ge.usltd.btu_classroom.core.download.BtuFileDownloader
import ge.usltd.btu_classroom.core.download.CourseDownloadResult
import ge.usltd.btu_classroom.core.schedule.ScheduleEntry
import ge.usltd.btu_classroom.core.sync.ClassReminderScheduler
import ge.usltd.btu_classroom.core.sync.ClassReminderResult
import java.time.LocalDate
import javax.inject.Inject
import ge.usltd.btu_classroom.core.auth.AuthState
import ge.usltd.btu_classroom.core.auth.AuthTelemetryState
import ge.usltd.btu_classroom.core.auth.GeckoAuthManager
import ge.usltd.btu_classroom.core.database.CachedCourseResourceDto
import ge.usltd.btu_classroom.core.database.entities.CourseWithEnrollment
import ge.usltd.btu_classroom.core.database.entities.GradeEntity
import ge.usltd.btu_classroom.core.database.entities.SemesterEntity
import ge.usltd.btu_classroom.core.database.entities.StudentEntity
import ge.usltd.btu_classroom.core.database.entities.SyncMetadataEntity
import ge.usltd.btu_classroom.core.network.VerificationStatus
import ge.usltd.btu_classroom.core.parsing.BtuModuleId
import ge.usltd.btu_classroom.core.parsing.ModuleCompatibilityEntry
import ge.usltd.btu_classroom.core.preferences.AppLanguage
import ge.usltd.btu_classroom.core.preferences.AppThemeMode
import ge.usltd.btu_classroom.core.preferences.UserPreferences
import ge.usltd.btu_classroom.core.preferences.UserPreferencesStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoSession

@HiltViewModel
class MainViewModel @Inject constructor(
  private val sessionManager: SessionManager,
  private val preferencesStore: UserPreferencesStore,
  private val courseRepository: CourseRepository,
  private val compatibilityRegistry: PageCompatibilityRegistry,
  private val portalRepository: PortalRepository,
  private val fileDownloader: BtuFileDownloader,
  private val reminderScheduler: ClassReminderScheduler,
  private val savedStateHandle: SavedStateHandle,
  @ApplicationContext context: Context,
) : ViewModel() {

  private val geckoManager = GeckoAuthManager(
    context = context,
    sessionManager = sessionManager,
    onAuthenticatedSessionReady = { verifyCapturedSessionAndSync() },
    onSessionExpired = { reason -> handleCookieExpirationEvent(reason) },
  )

  val authState: StateFlow<AuthState> = sessionManager.authState
  val telemetry: StateFlow<AuthTelemetryState> = sessionManager.telemetry
  val activeGeckoSession: StateFlow<GeckoSession?> = geckoManager.activeSession
  val exploring: StateFlow<Boolean> = savedStateHandle.getStateFlow("exploring", false)
  private var verifyingSession = false
  private var startingLogin = false

  val preferences: StateFlow<UserPreferences> = preferencesStore.preferencesFlow.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000L),
    initialValue = UserPreferences(),
  )

  val student: StateFlow<StudentEntity?> = courseRepository
    .observeStudent(UserPreferencesStore.DEFAULT_STUDENT_KEY)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)

  val semesters: StateFlow<List<SemesterEntity>> = courseRepository
    .observeSemesters(UserPreferencesStore.DEFAULT_STUDENT_KEY)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

  val courses: StateFlow<List<CourseWithEnrollment>> = courseRepository
    .observeCourses(UserPreferencesStore.DEFAULT_STUDENT_KEY)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

  val coursesSyncMetadata: StateFlow<SyncMetadataEntity?> = courseRepository
    .observeCoursesSyncMetadata()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)

  val compatibilityEntries: StateFlow<Map<BtuModuleId, ModuleCompatibilityEntry>> =
    compatibilityRegistry.entries

  private val _isSyncingCourses = MutableStateFlow(false)
  val isSyncingCourses: StateFlow<Boolean> = _isSyncingCourses.asStateFlow()

  private val _isSyncingDetail = MutableStateFlow(false)
  val isSyncingDetail: StateFlow<Boolean> = _isSyncingDetail.asStateFlow()
  private val _syncingDetailKeys = MutableStateFlow<Set<String>>(emptySet())
  val syncingDetailKeys: StateFlow<Set<String>> = _syncingDetailKeys.asStateFlow()
  private val _detailSyncOutcomes = MutableStateFlow<Map<String, SyncOutcome>>(emptyMap())
  val detailSyncOutcomes: StateFlow<Map<String, SyncOutcome>> = _detailSyncOutcomes.asStateFlow()
  private val portalPages = PortalModule.entries.associateWith { module ->
    portalRepository.observe(module).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)
  }
  private val _syncingPortalModules = MutableStateFlow<Set<PortalModule>>(emptySet())
  val syncingPortalModules: StateFlow<Set<PortalModule>> = _syncingPortalModules.asStateFlow()
  private val _portalSyncOutcomes = MutableStateFlow<Map<PortalModule, SyncOutcome>>(emptyMap())
  val portalSyncOutcomes: StateFlow<Map<PortalModule, SyncOutcome>> = _portalSyncOutcomes.asStateFlow()
  private val _pendingClassReminder = MutableStateFlow<ClassReminderRequest?>(null)
  val pendingClassReminder: StateFlow<ClassReminderRequest?> = _pendingClassReminder.asStateFlow()
  private val _classReminderResult = MutableStateFlow<ClassReminderResult?>(null)
  val classReminderResult: StateFlow<ClassReminderResult?> = _classReminderResult.asStateFlow()
  private val downloadCoordinator = DownloadCoordinator(viewModelScope,
    readGeneration = preferencesStore::readAcademicDataGeneration,
    currentSession = sessionManager::currentSessionValue,
    download = { resource ->
      fileDownloader.getCached(resource.url, resource.title)
        ?: fileDownloader.download(resource.url, resource.title)
    })
  val pendingFileDownload: StateFlow<CourseDownloadResult?> = downloadCoordinator.pendingResult
  val downloadingResourceUrl: StateFlow<String?> = downloadCoordinator.loadingUrl
  private val _localCleanupFailed = MutableStateFlow(false)
  val localCleanupFailed: StateFlow<Boolean> = _localCleanupFailed.asStateFlow()

  private val _isSignOutDialogVisible = MutableStateFlow(false)
  val isSignOutDialogVisible: StateFlow<Boolean> = _isSignOutDialogVisible.asStateFlow()

  private val _isClearDataDialogVisible = MutableStateFlow(false)
  val isClearDataDialogVisible: StateFlow<Boolean> = _isClearDataDialogVisible.asStateFlow()

  private val _stayOfflineOverride = MutableStateFlow(false)
  val stayOfflineOverride: StateFlow<Boolean> = _stayOfflineOverride.asStateFlow()

  private val _snackbarNotice = MutableStateFlow<String?>(null)
  val snackbarNotice: StateFlow<String?> = _snackbarNotice.asStateFlow()

  init {
    viewModelScope.launch {
      val hasCache = courseRepository.hasCachedCourses()
      val result = sessionManager.restoreAndVerifyOnLaunch(hasCachedAcademicData = hasCache)
      if (result?.status == VerificationStatus.AUTHENTICATED) {
        val preFetchedHtml = sessionManager.consumeCachedInitialVerifiedHtml()
        syncCourses(preFetchedHtml)
      }
    }
  }

  fun startLogin() {
    if (telemetry.value.isBusy || startingLogin) return
    if (activeGeckoSession.value != null) {
      openBrowserModal()
      return
    }
    startFreshLogin()
  }

  private fun startFreshLogin() {
    startingLogin = true
    _stayOfflineOverride.value = false
    savedStateHandle["exploring"] = false
    viewModelScope.launch {
      try {
        sessionManager.clearLocalCredentialsAndCache(
          clearAcademicCache = { clearAcademicData() },
          resetBrowserStorage = { done -> geckoManager.resetBrowserSession(done) },
        )
        geckoManager.startLogin(preferences.value.language)
      } catch (_: LocalCleanupException) {
        _localCleanupFailed.value = true
      } finally {
        startingLogin = false
      }
    }
  }

  fun exploreApp() {
    savedStateHandle["exploring"] = true
  }

  fun openBrowserModal() {
    if (activeGeckoSession.value != null) {
      sessionManager.setBrowserModalVisible(true)
    }
  }

  fun closeBrowserModal() {
    sessionManager.setBrowserModalVisible(false)
  }

  fun handleBrowserBack(): Boolean {
    return if (geckoManager.goBackInBrowser()) {
      true
    } else {
      sessionManager.setBrowserModalVisible(false)
      true
    }
  }

  fun verifySession() {
    verifyCapturedSessionAndSync()
  }

  private fun verifyCapturedSessionAndSync() {
    if (verifyingSession) return
    verifyingSession = true
    viewModelScope.launch {
      try {
        val result = sessionManager.verifyCurrentSession(
          onAuthenticatedReleaseBrowser = { geckoManager.releaseLoginSession() },
        )
        if (result.status == VerificationStatus.AUTHENTICATED) {
          _stayOfflineOverride.value = false
          val preFetchedHtml = sessionManager.consumeCachedInitialVerifiedHtml()
          syncCourses(preFetchedHtml)
        }
      } finally {
        verifyingSession = false
      }
    }
  }

  private fun handleCookieExpirationEvent(reason: String) {
    viewModelScope.launch {
      sessionManager.verifyExpirationOrRecover(reason)
    }
  }

  fun checkSessionExpiration() {
    geckoManager.checkSessionExpiration()
    viewModelScope.launch {
      if (!sessionManager.currentSessionValue().isNullOrBlank()) {
        sessionManager.verifyCurrentSession(
          onAuthenticatedReleaseBrowser = { geckoManager.releaseLoginSession() },
        )
      }
    }
  }

  fun refreshCourses() {
    if (_isSyncingCourses.value) return
    _isSyncingCourses.value = true
    viewModelScope.launch {
      try {
        courseRepository.refreshSelectedCourses()
      } finally {
        _isSyncingCourses.value = false
      }
    }
  }

  private suspend fun syncCourses(html: String? = null) {
    _isSyncingCourses.value = true
    try {
      courseRepository.refreshSelectedCourses(html)
    } finally {
      _isSyncingCourses.value = false
    }
  }

  fun observeCourseDetail(courseKey: String): Flow<CourseWithEnrollment?> =
    courseRepository.observeCourseDetail(courseKey)

  fun observeCourseGrades(courseKey: String): Flow<List<GradeEntity>> =
    courseRepository.observeCourseGrades(courseKey)

  fun decodeResources(resourcesJson: String?): List<CachedCourseResourceDto> =
    courseRepository.decodeResources(resourcesJson)

  fun observePortal(module: PortalModule): StateFlow<PortalCachedPage?> = portalPages.getValue(module)

  fun refreshPortal(module: PortalModule) {
    if (module in _syncingPortalModules.value) return
    _syncingPortalModules.update { it + module }
    viewModelScope.launch {
      try {
        val outcome = portalRepository.refresh(module)
        _portalSyncOutcomes.update { it + (module to outcome) }
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: Exception) {
        _portalSyncOutcomes.update {
          it + (module to SyncOutcome.OfflineOrFailed("Portal refresh failed", isOffline = false))
        }
      } finally {
        _syncingPortalModules.update { it - module }
      }
    }
  }

  fun requestClassReminder(entry: ScheduleEntry, date: LocalDate) {
    _pendingClassReminder.value = ClassReminderRequest(entry, date)
  }

  fun dismissClassReminder() {
    _pendingClassReminder.value = null
  }

  fun confirmClassReminder() {
    val request = _pendingClassReminder.value ?: return
    _pendingClassReminder.value = null
    viewModelScope.launch {
      preferencesStore.setNotificationsEnabled(true)
      _classReminderResult.value = reminderScheduler.schedule(request.entry, request.date)
    }
  }

  fun consumeClassReminderResult() {
    _classReminderResult.value = null
  }

  fun downloadResource(resource: CachedCourseResourceDto) {
    if (telemetry.value.isBusy || startingLogin) return
    downloadCoordinator.request(resource)
  }

  fun consumeFileDownload() {
    downloadCoordinator.consume()
  }

  fun consumeLocalCleanupFailure() {
    _localCleanupFailed.value = false
  }

  private suspend fun clearAcademicData() {
    downloadCoordinator.cancelAndClear()
    _pendingClassReminder.value = null
    _classReminderResult.value = null
    _detailSyncOutcomes.value = emptyMap()
    _portalSyncOutcomes.value = emptyMap()
    try {
      courseRepository.clearAllAcademicData()
    } finally {
      try {
        fileDownloader.clearDownloads()
      } finally {
        reminderScheduler.cancelAll()
      }
    }
  }

  fun refreshCourseDetail(courseKey: String, courseId: String, detailPath: String?) {
    if (courseKey in _syncingDetailKeys.value) return
    _syncingDetailKeys.update { it + courseKey }
    _isSyncingDetail.value = true
    viewModelScope.launch {
      try {
        val outcome = courseRepository.refreshCourseDetail(courseKey, courseId, detailPath)
        _detailSyncOutcomes.update { it + (courseKey to outcome) }
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: Exception) {
        _detailSyncOutcomes.update {
          it + (courseKey to SyncOutcome.OfflineOrFailed("Course detail refresh failed", isOffline = false))
        }
      } finally {
        _syncingDetailKeys.update { it - courseKey }
        _isSyncingDetail.value = _syncingDetailKeys.value.isNotEmpty()
      }
    }
  }

  fun dismissReauthPromptAndStayOffline() {
    sessionManager.dismissReauthPrompt()
    if (courses.value.isNotEmpty() || coursesSyncMetadata.value?.lastSuccessAtMillis != null) {
      _stayOfflineOverride.value = true
    }
  }

  fun reauthenticateFromPrompt() {
    if (telemetry.value.isBusy || startingLogin) return
    sessionManager.dismissReauthPrompt()
    startFreshLogin()
  }

  fun setRememberLogin(remember: Boolean) {
    viewModelScope.launch {
      preferencesStore.setRememberLogin(remember)
      if (remember) {
        sessionManager.currentSessionValue()?.let { activeCookie ->
          preferencesStore.saveEncryptedSession(activeCookie)
        }
      }
    }
  }

  fun setLanguage(language: AppLanguage) {
    viewModelScope.launch {
      preferencesStore.setLanguage(language)
    }
  }

  fun setThemeMode(themeMode: AppThemeMode) {
    viewModelScope.launch {
      preferencesStore.setThemeMode(themeMode)
    }
  }

  fun setDynamicColor(enabled: Boolean) {
    viewModelScope.launch {
      preferencesStore.setDynamicColor(enabled)
    }
  }

  fun setBackgroundSync(enabled: Boolean) {
    viewModelScope.launch { preferencesStore.setBackgroundSync(enabled) }
  }

  fun setNotificationsEnabled(enabled: Boolean) {
    viewModelScope.launch { preferencesStore.setNotificationsEnabled(enabled) }
  }

  fun requestSignOut() {
    _isSignOutDialogVisible.value = true
  }

  fun dismissSignOutDialog() {
    _isSignOutDialogVisible.value = false
  }

  fun confirmSignOut(unconfirmedRemoteMessage: String) {
    _isSignOutDialogVisible.value = false
    _stayOfflineOverride.value = false
    savedStateHandle["exploring"] = false
    viewModelScope.launch {
      try {
        val logoutResult = sessionManager.signOutRemoteAndClearLocal(
          clearAcademicCache = { clearAcademicData() },
          resetBrowserStorage = { done -> geckoManager.resetBrowserSession(done) },
        )
        if (!logoutResult.success) {
          _snackbarNotice.value = unconfirmedRemoteMessage
        }
      } catch (_: LocalCleanupException) {
        _localCleanupFailed.value = true
      }
    }
  }

  fun requestClearLocalData() {
    _isClearDataDialogVisible.value = true
  }

  fun dismissClearDataDialog() {
    _isClearDataDialogVisible.value = false
  }

  fun confirmClearLocalData() {
    _isClearDataDialogVisible.value = false
    _stayOfflineOverride.value = false
    savedStateHandle["exploring"] = false
    viewModelScope.launch {
      try {
        sessionManager.clearLocalCredentialsAndCache(
          clearAcademicCache = { clearAcademicData() },
          resetBrowserStorage = { done -> geckoManager.resetBrowserSession(done) },
        )
      } catch (_: LocalCleanupException) {
        _localCleanupFailed.value = true
      }
    }
  }

  fun consumeSnackbarNotice() {
    _snackbarNotice.value = null
  }

  fun clearLogs() {
    sessionManager.clearLogs()
  }

  override fun onCleared() {
    geckoManager.releaseLoginSession()
  }
}

data class ClassReminderRequest(val entry: ScheduleEntry, val date: LocalDate)
