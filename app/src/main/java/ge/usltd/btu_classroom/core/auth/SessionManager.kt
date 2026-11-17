package ge.usltd.btu_classroom.core.auth

import ge.usltd.btu_classroom.core.network.BtuHttpClient
import ge.usltd.btu_classroom.core.network.LogoutResult
import ge.usltd.btu_classroom.core.network.VerificationResult
import ge.usltd.btu_classroom.core.network.VerificationStatus
import ge.usltd.btu_classroom.core.preferences.UserPreferencesStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single source of truth for authentication state, active argus_session credentials,
 * Keystore-encrypted optional persistence, and sanitized auth diagnostics.
 * Only verified BtuHttpClient results transition AuthState to Authenticated.
 */
class SessionManager(
  private val httpClient: BtuHttpClient,
  private val preferencesStore: UserPreferencesStore,
) {

  private val _authState = MutableStateFlow<AuthState>(AuthState.Unknown)
  val authState: StateFlow<AuthState> = _authState.asStateFlow()

  private val _telemetry = MutableStateFlow(AuthTelemetryState())
  val telemetry: StateFlow<AuthTelemetryState> = _telemetry.asStateFlow()

  @Volatile
  private var inMemorySessionCookie: String? = null

  @Volatile
  private var lastKnownValidSessionCookie: String? = null

  @Volatile
  private var cachedInitialVerifiedHtml: String? = null

  @Volatile
  private var localCleanupBlocked = false

  private val logCounter = AtomicLong(0L)
  private val verificationMutex = Mutex()
  private val sessionGeneration = AtomicLong(0L)
  private val timeFormatter = ThreadLocal.withInitial {
    SimpleDateFormat("HH:mm:ss", Locale.US)
  }

  fun currentSessionValue(): String? = inMemorySessionCookie

  fun lastKnownValidSessionValue(): String? = lastKnownValidSessionCookie ?: inMemorySessionCookie

  fun consumeCachedInitialVerifiedHtml(): String? {
    val html = cachedInitialVerifiedHtml
    cachedInitialVerifiedHtml = null
    return html
  }

  fun appendLog(message: String, level: LogLevel = LogLevel.INFO) {
    var safeMessage = UrlSanitizer.redactSensitiveTokens(message, inMemorySessionCookie)
    lastKnownValidSessionCookie?.let { prev ->
      safeMessage = UrlSanitizer.redactSensitiveTokens(safeMessage, prev)
    }
    val timestamp = timeFormatter.get()?.format(Date()) ?: "--:--:--"
    val entry = DiagnosticLogEntry(
      id = logCounter.incrementAndGet(),
      timestamp = timestamp,
      message = safeMessage,
      level = level,
    )
    _telemetry.update { current ->
      current.copy(logs = (current.logs + entry).takeLast(200))
    }
  }

  fun clearLogs() {
    _telemetry.update { it.copy(logs = emptyList()) }
  }

  /**
   * Restores Keystore-encrypted session on launch when Remember Login is enabled
   * and verifies validity against BTU Classroom over OkHttp.
   */
  suspend fun restoreAndVerifyOnLaunch(hasCachedAcademicData: Boolean): VerificationResult? = verificationMutex.withLock {
    if (localCleanupBlocked) return@withLock null
    val generation = sessionGeneration.get()
    val prefs = preferencesStore.getCurrentPreferences()
    if (!prefs.rememberLogin) {
      preferencesStore.clearEncryptedSession()
      _authState.value = AuthState.SignedOut
      appendLog("Session-only mode active; waiting for interactive sign-in", LogLevel.INFO)
      return@withLock null
    }

    val restored = preferencesStore.readDecryptedSession()
    if (restored.isNullOrBlank()) {
      _authState.value = AuthState.SignedOut
      appendLog("No saved encrypted session found on device", LogLevel.INFO)
      return@withLock null
    }

    inMemorySessionCookie = restored
    _authState.value = AuthState.Verifying
    _telemetry.update {
      it.copy(
        sessionCaptured = true,
        verificationStatus = VerificationStatus.VERIFYING,
        isBusy = true,
      )
    }
    appendLog("Restored encrypted session; verifying against BTU Classroom...", LogLevel.INFO)

    val result = httpClient.verifySession(restored)
    if (generation != sessionGeneration.get()) return@withLock null
    when (result.status) {
      VerificationStatus.AUTHENTICATED -> {
        lastKnownValidSessionCookie = restored
        cachedInitialVerifiedHtml = result.html
        _authState.value = AuthState.Authenticated
        _telemetry.update {
          it.copy(
            isBusy = false,
            verificationStatus = result.status,
            lastHttpStatusCode = result.httpStatusCode,
            isOfflineFallback = false,
            errorMessage = null,
          )
        }
        appendLog("Saved session verified cleanly", LogLevel.SUCCESS)
      }
      VerificationStatus.NETWORK_ERROR -> {
        if (hasCachedAcademicData) {
          // Offline launch with valid local cache + remembered encrypted session: allow read-only offline access
          lastKnownValidSessionCookie = restored
          _authState.value = AuthState.Authenticated
          _telemetry.update {
            it.copy(
              isBusy = false,
              verificationStatus = VerificationStatus.NETWORK_ERROR,
              lastHttpStatusCode = null,
              isOfflineFallback = true,
              errorMessage = null,
            )
          }
          appendLog("Offline on launch; displaying cached academic data until connectivity returns", LogLevel.WARNING)
        } else {
          _authState.value = AuthState.Error(result.errorMessage ?: "Offline — unable to verify session")
          _telemetry.update {
            it.copy(
              isBusy = false,
              verificationStatus = result.status,
              lastHttpStatusCode = null,
              isOfflineFallback = true,
              errorMessage = result.errorMessage,
            )
          }
          appendLog(result.logSummary, LogLevel.WARNING)
        }
      }
      VerificationStatus.UNAUTHENTICATED -> {
        inMemorySessionCookie = null
        lastKnownValidSessionCookie = null
        preferencesStore.clearEncryptedSession()
        _authState.value = AuthState.Expired
        _telemetry.update {
          it.copy(
            isBusy = false,
            sessionCaptured = false,
            verificationStatus = result.status,
            lastHttpStatusCode = result.httpStatusCode,
            isOfflineFallback = false,
            errorMessage = result.errorMessage,
          )
        }
        appendLog("Saved session no longer valid (${result.logSummary}); cleared stored cookie", LogLevel.WARNING)
      }
      else -> {
        _authState.value = if (hasCachedAcademicData) AuthState.Authenticated else AuthState.Error(result.errorMessage ?: "BTU is temporarily unavailable")
        _telemetry.update {
          it.copy(isBusy = false, verificationStatus = result.status,
            lastHttpStatusCode = result.httpStatusCode, isOfflineFallback = hasCachedAcademicData,
            errorMessage = result.errorMessage)
        }
        appendLog("Session verification deferred; saved credentials retained", LogLevel.WARNING)
      }
    }
    result
  }

  fun beginInteractiveLogin() {
    sessionGeneration.incrementAndGet()
    _authState.value = AuthState.LoggingIn
    _telemetry.update {
      it.copy(
        isReauthPromptVisible = false,
        showExternalBrowserFallback = false,
        errorMessage = null,
        isBrowserModalVisible = true,
      )
    }
  }

  fun setGeckoStatus(status: GeckoInitStatus, errorMessage: String? = null) {
    _telemetry.update {
      it.copy(
        geckoInitStatus = status,
        errorMessage = errorMessage ?: it.errorMessage,
      )
    }
  }

  fun setExtensionStatus(status: ExtensionInstallStatus, errorMessage: String? = null) {
    _telemetry.update {
      it.copy(
        extensionInstallStatus = status,
        errorMessage = errorMessage ?: it.errorMessage,
      )
    }
  }

  fun onAnonymousSessionDetected(sessionValue: String?) {
    if (localCleanupBlocked) return
    if (SessionCookieValidator.isValid(sessionValue) && inMemorySessionCookie.isNullOrBlank()) {
      inMemorySessionCookie = sessionValue
    }
    appendLog("Anonymous BTU session cookie observed", LogLevel.INFO)
  }

  fun onGoogleLoginStatus(
    status: GoogleLoginStatus,
    stageLabel: String? = null,
    errorMessage: String? = null,
    markCallbackDetected: Boolean = false,
    showExternalFallback: Boolean = false,
  ) {
    if (status == GoogleLoginStatus.FAILED || status == GoogleLoginStatus.REJECTED_DISALLOWED_USERAGENT) {
      if (_authState.value !is AuthState.Authenticated) {
        _authState.value = AuthState.Error(errorMessage ?: status.label)
      }
    }
    _telemetry.update { current ->
      current.copy(
        googleLoginStatus = status,
        browserStageLabel = stageLabel ?: status.label,
        btuCallbackObserved = current.btuCallbackObserved || markCallbackDetected,
        showExternalBrowserFallback = current.showExternalBrowserFallback || showExternalFallback,
        errorMessage = errorMessage ?: if (status == GoogleLoginStatus.FAILED ||
          status == GoogleLoginStatus.REJECTED_DISALLOWED_USERAGENT
        ) {
          current.errorMessage
        } else {
          null
        },
      )
    }
  }

  fun onSessionReadyFromBrowser(sessionValue: String) {
    if (localCleanupBlocked) return
    if (!SessionCookieValidator.isValid(sessionValue)) {
      onSessionCookieMissing()
      return
    }
    inMemorySessionCookie = sessionValue
    _telemetry.update {
      it.copy(
        sessionCaptured = true,
        errorMessage = null,
      )
    }
    appendLog("argus_session captured from GeckoView; starting OkHttp verification", LogLevel.INFO)
  }

  fun onSessionCookieMissing() {
    inMemorySessionCookie = null
    val msg = "Authenticated BTU page was reached, but argus_session cookie was missing in GeckoView."
    if (_authState.value !is AuthState.Authenticated) {
      _authState.value = AuthState.Error(msg)
    }
    _telemetry.update {
      it.copy(
        sessionCaptured = false,
        errorMessage = msg,
      )
    }
    appendLog(msg, LogLevel.ERROR)
  }

  suspend fun verifyCurrentSession(onAuthenticatedReleaseBrowser: () -> Unit): VerificationResult = verificationMutex.withLock {
    val generation = sessionGeneration.get()
    val candidate = inMemorySessionCookie
    if (candidate.isNullOrBlank()) {
      val msg = "No active argus_session cookie available to verify."
      _telemetry.update {
        it.copy(
          isBusy = false,
          verificationStatus = VerificationStatus.UNAUTHENTICATED,
          errorMessage = msg,
        )
      }
      if (_authState.value !is AuthState.Authenticated) {
        _authState.value = AuthState.SignedOut
      }
      return@withLock VerificationResult(
        status = VerificationStatus.UNAUTHENTICATED,
        httpStatusCode = null,
        errorMessage = msg,
        logSummary = "Verification skipped: no cookie in memory",
      )
    }

    val wasAuthenticated = _authState.value is AuthState.Authenticated
    _authState.value = AuthState.Verifying
    _telemetry.update {
      it.copy(
        isBusy = true,
        verificationStatus = VerificationStatus.VERIFYING,
      )
    }

    val result = httpClient.verifySession(candidate)
    if (generation != sessionGeneration.get()) return@withLock staleVerificationResult()
    appendLog(
      result.logSummary,
      when (result.status) {
        VerificationStatus.AUTHENTICATED -> LogLevel.SUCCESS
        VerificationStatus.NETWORK_ERROR -> LogLevel.ERROR
        else -> LogLevel.WARNING
      },
    )

    when (result.status) {
      VerificationStatus.AUTHENTICATED -> {
        lastKnownValidSessionCookie = candidate
        cachedInitialVerifiedHtml = result.html
        val prefs = preferencesStore.getCurrentPreferences()
        if (prefs.rememberLogin) {
          preferencesStore.saveEncryptedSession(candidate)
        }
        _authState.value = AuthState.Authenticated
        _telemetry.update {
          it.copy(
            isBusy = false,
            sessionCaptured = true,
            verificationStatus = result.status,
            lastHttpStatusCode = result.httpStatusCode,
            isOfflineFallback = false,
            isReauthPromptVisible = false,
            errorMessage = null,
          )
        }
        onAuthenticatedReleaseBrowser()
      }
      VerificationStatus.UNAUTHENTICATED -> {
        inMemorySessionCookie = null
        lastKnownValidSessionCookie = null
        preferencesStore.clearEncryptedSession()
        _authState.value = if (wasAuthenticated) AuthState.Expired else AuthState.Error(result.errorMessage ?: "Session rejected")
        _telemetry.update {
          it.copy(
            isBusy = false,
            sessionCaptured = false,
            verificationStatus = result.status,
            lastHttpStatusCode = result.httpStatusCode,
            isOfflineFallback = false,
            isReauthPromptVisible = wasAuthenticated,
            errorMessage = result.errorMessage,
          )
        }
      }
      VerificationStatus.NETWORK_ERROR -> {
        if (wasAuthenticated) {
          _authState.value = AuthState.Authenticated
        } else {
          _authState.value = AuthState.Error(result.errorMessage ?: "Network error")
        }
        _telemetry.update {
          it.copy(
            isBusy = false,
            verificationStatus = result.status,
            lastHttpStatusCode = null,
            isOfflineFallback = wasAuthenticated,
            errorMessage = result.errorMessage,
          )
        }
      }
      else -> {
        _authState.value = if (wasAuthenticated) AuthState.Authenticated else AuthState.Error(result.errorMessage ?: result.status.label)
        _telemetry.update {
          it.copy(
            isBusy = false,
            verificationStatus = result.status,
            lastHttpStatusCode = result.httpStatusCode,
            errorMessage = result.errorMessage,
          )
        }
      }
    }

    result
  }

  /**
   * Called by CourseRepository/BtuHttpClient when any authenticated page request receives
   * an authoritative server-side expiration (302 login / 401 / login HTML).
   */
  suspend fun handleServerSessionExpired(reason: String) {
    sessionGeneration.incrementAndGet()
    inMemorySessionCookie = null
    lastKnownValidSessionCookie = null
    cachedInitialVerifiedHtml = null
    preferencesStore.clearEncryptedSession()
    _authState.value = AuthState.Expired
    _telemetry.update {
      it.copy(
        sessionCaptured = false,
        verificationStatus = VerificationStatus.UNAUTHENTICATED,
        isReauthPromptVisible = true,
        isOfflineFallback = false,
        errorMessage = reason,
      )
    }
    appendLog("Server session expired: $reason", LogLevel.WARNING)
  }

  suspend fun verifyExpirationOrRecover(reason: String): Unit = verificationMutex.withLock {
    val generation = sessionGeneration.get()
    val candidate = lastKnownValidSessionValue()
    if (candidate.isNullOrBlank()) {
      handleServerSessionExpired(reason)
      return@withLock
    }
    val result = httpClient.verifySession(candidate)
    if (generation != sessionGeneration.get()) return@withLock
    if (result.status == VerificationStatus.AUTHENTICATED) {
      inMemorySessionCookie = candidate
      if (preferencesStore.getCurrentPreferences().rememberLogin) {
        preferencesStore.saveEncryptedSession(candidate)
      }
      _authState.value = AuthState.Authenticated
      _telemetry.update {
        it.copy(
          sessionCaptured = true,
          verificationStatus = VerificationStatus.AUTHENTICATED,
          isOfflineFallback = false,
          errorMessage = null,
        )
      }
      appendLog("Cookie probe confirmed session still active on BTU server", LogLevel.INFO)
    } else if (result.status != VerificationStatus.UNAUTHENTICATED) {
      _telemetry.update { it.copy(isOfflineFallback = true) }
      appendLog("Session expiry check deferred while offline", LogLevel.WARNING)
    } else {
      handleServerSessionExpired(reason)
    }
  }

  fun dismissReauthPrompt() {
    _telemetry.update { it.copy(isReauthPromptVisible = false) }
  }

  fun setBrowserModalVisible(visible: Boolean) {
    if (!visible && _authState.value is AuthState.LoggingIn && inMemorySessionCookie.isNullOrBlank()) {
      _authState.value = AuthState.SignedOut
    }
    _telemetry.update { it.copy(isBrowserModalVisible = visible) }
  }

  fun setActiveBrowserSession(active: Boolean) {
    _telemetry.update {
      it.copy(
        hasActiveBrowserSession = active,
        browserCanGoBack = if (active) it.browserCanGoBack else false,
        browserLoadProgress = if (active) it.browserLoadProgress else 0,
      )
    }
  }

  fun setBrowserNavigationUi(canGoBack: Boolean? = null, progress: Int? = null) {
    _telemetry.update {
      it.copy(
        browserCanGoBack = canGoBack ?: it.browserCanGoBack,
        browserLoadProgress = progress ?: it.browserLoadProgress,
      )
    }
  }

  fun markOfflineFallback(offline: Boolean) {
    _telemetry.update { it.copy(isOfflineFallback = offline) }
  }

  suspend fun signOutRemoteAndClearLocal(
    clearAcademicCache: suspend () -> Unit,
    resetBrowserStorage: (() -> Unit) -> Unit,
  ): LogoutResult {
    sessionGeneration.incrementAndGet()
    val currentCookie = inMemorySessionCookie
    _telemetry.update { it.copy(isBusy = true) }
    appendLog("Invoking remote BTU logout (/ge/login/logout)...", LogLevel.INFO)

    try {
      val logoutResult = httpClient.logout(currentCookie)
      appendLog(logoutResult.detail, if (logoutResult.success) LogLevel.INFO else LogLevel.WARNING)
      return logoutResult
    } finally {
      clearLocalCredentialsAndCache(clearAcademicCache, resetBrowserStorage)
    }
  }

  suspend fun clearLocalCredentialsAndCache(
    clearAcademicCache: suspend () -> Unit,
    resetBrowserStorage: (() -> Unit) -> Unit,
  ): Unit = withContext(NonCancellable) {
    localCleanupBlocked = true
    sessionGeneration.incrementAndGet()
    _telemetry.update { it.copy(isBusy = true) }
    verificationMutex.withLock {
      inMemorySessionCookie = null
      lastKnownValidSessionCookie = null
      cachedInitialVerifiedHtml = null
      var localCleanupFailed = false
      try {
        preferencesStore.clearSessionAndIdentity()
      } catch (_: Exception) {
        localCleanupFailed = true
      }
      try {
        clearAcademicCache()
      } catch (_: Exception) {
        localCleanupFailed = true
      }
      val browserResetCompleted = withTimeoutOrNull(12_000L) {
        suspendCancellableCoroutine<Boolean> { continuation ->
          try {
            resetBrowserStorage {
              if (continuation.isActive) continuation.resume(true)
            }
          } catch (_: Exception) {
            if (continuation.isActive) continuation.resume(false)
          }
        }
      } ?: false
      localCleanupBlocked = localCleanupFailed
      _authState.value = AuthState.SignedOut
      _telemetry.update { current ->
        AuthTelemetryState(
          geckoInitStatus = current.geckoInitStatus,
          extensionInstallStatus = current.extensionInstallStatus,
          logs = current.logs,
          errorMessage = when {
            localCleanupFailed -> LocalCleanupException.MESSAGE
            !browserResetCompleted -> "Sign-in browser reset could not be confirmed. Please retry sign-in."
            else -> current.errorMessage.takeIf { current.geckoInitStatus == GeckoInitStatus.FAILED }
          },
        )
      }
      when {
        localCleanupFailed -> appendLog(LocalCleanupException.MESSAGE, LogLevel.ERROR)
        !browserResetCompleted || telemetry.value.geckoInitStatus == GeckoInitStatus.FAILED ->
          appendLog("Local credentials and academic records removed; browser reset could not be confirmed", LogLevel.WARNING)
        else -> appendLog("Local credentials, academic records and browser data removed", LogLevel.INFO)
      }
      if (localCleanupFailed) throw LocalCleanupException()
    }
  }

  private fun staleVerificationResult() = VerificationResult(
    VerificationStatus.UNAUTHENTICATED, null, "Session changed during verification", "Stale verification discarded",
  )
}
