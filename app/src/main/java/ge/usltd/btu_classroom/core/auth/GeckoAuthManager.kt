package ge.usltd.btu_classroom.core.auth

import android.content.Context
import android.os.Handler
import android.os.Looper
import ge.usltd.btu_classroom.core.network.BtuRoutes
import ge.usltd.btu_classroom.core.preferences.AppLanguage
import ge.usltd.btu_classroom.core.preferences.LocaleHelper
import java.net.URI
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.StorageController
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebRequestError

/**
 * Coordinates on-demand GeckoRuntime & GeckoSession lifecycle strictly for authentication.
 * Releases the browser session immediately once OkHttp verifies the captured argus_session.
 */
class GeckoAuthManager(
  context: Context,
  private val sessionManager: SessionManager,
  private val onAuthenticatedSessionReady: (String) -> Unit,
  private val onSessionExpired: (String) -> Unit,
) {

  private val appContext = context.applicationContext

  private val _activeSession = MutableStateFlow<GeckoSession?>(null)
  val activeSession: StateFlow<GeckoSession?> = _activeSession.asStateFlow()

  private var installedExtension: WebExtension? = null
  private var lastLoggedPathKey: String? = null
  private var loginRequested = false

  private val messageHandler = GeckoExtensionMessageHandler(
    object : GeckoExtensionMessageHandler.Listener {
      override fun onSessionDetected(sessionValue: String?, expirationDateEpochSeconds: Long?) {
        if (_activeSession.value == null) return
        sessionManager.onAnonymousSessionDetected(sessionValue)
      }

      override fun onSessionReady(sessionValue: String, expirationDateEpochSeconds: Long?) {
        if (_activeSession.value == null) return
        sessionManager.onSessionReadyFromBrowser(sessionValue)
        onAuthenticatedSessionReady(sessionValue)
      }

      override fun onSessionCookieMissing() {
        if (_activeSession.value == null) return
        sessionManager.onSessionCookieMissing()
      }

      override fun onSessionExpired(reason: String, expirationDateEpochSeconds: Long?) {
        if (_activeSession.value == null) return
        sessionManager.appendLog("WebExtension observed argus_session expiry ($reason)", LogLevel.WARNING)
        onSessionExpired(reason)
      }

      override fun onBtuCallbackObserved(hasError: Boolean) {
        if (!sessionManager.telemetry.value.btuCallbackObserved) {
          sessionManager.onGoogleLoginStatus(
            status = GoogleLoginStatus.BTU_CALLBACK_OBSERVED,
            stageLabel = "BTU OAuth Callback",
            markCallbackDetected = true,
          )
          sessionManager.appendLog(
            if (hasError) "BTU OAuth callback returned error flag" else "BTU OAuth callback observed",
            if (hasError) LogLevel.WARNING else LogLevel.INFO,
          )
        }
      }

      override fun onAuthenticatedPageReached() {
        if (sessionManager.telemetry.value.googleLoginStatus != GoogleLoginStatus.AUTHENTICATED_PAGE_REACHED) {
          sessionManager.onGoogleLoginStatus(
            status = GoogleLoginStatus.AUTHENTICATED_PAGE_REACHED,
            stageLabel = "BTU Student Portal",
          )
          sessionManager.appendLog("BTU student route reached; querying session cookie", LogLevel.INFO)
        }
      }
    },
  )

  fun startLogin(language: AppLanguage = AppLanguage.SYSTEM) {
    if (loginRequested) return
    val loginLocale = LocaleHelper.resolveLocale(language, appContext.resources.configuration)
    loginRequested = true
    sessionManager.beginInteractiveLogin()
    lastLoggedPathKey = null
    ensureRuntimeAndExtension { runtime, ok ->
      if (!ok || runtime == null) {
        loginRequested = false
        return@ensureRuntimeAndExtension
      }
      messageHandler.pauseSessionObservation()
      clearBrowserStorage { cleared ->
        loginRequested = false
        if (cleared) {
          messageHandler.resetExtensionState()
          openSessionAndNavigate(runtime, loginLocale)
        } else {
          sessionManager.onGoogleLoginStatus(
            GoogleLoginStatus.FAILED,
            errorMessage = "Sign-in browser data could not be cleared. Please retry sign-in.",
            showExternalFallback = true,
          )
          sessionManager.setBrowserModalVisible(false)
        }
      }
    }
  }

  fun checkSessionExpiration() {
    if (_activeSession.value == null) return
    ensureRuntimeAndExtension { _, ok ->
      if (ok) {
        messageHandler.checkSessionExpiration()
      }
    }
  }

  private fun ensureRuntimeAndExtension(onReady: (GeckoRuntime?, Boolean) -> Unit) {
    val existingRuntime = sharedRuntime
    val existingExt = installedExtension
    if (existingRuntime != null && existingExt != null) {
      messageHandler.attachToExtension(existingExt)
      sessionManager.setGeckoStatus(GeckoInitStatus.READY)
      sessionManager.setExtensionStatus(ExtensionInstallStatus.INSTALLED)
      onReady(existingRuntime, true)
      return
    }

    if (existingRuntime == null) {
      sessionManager.setGeckoStatus(GeckoInitStatus.INITIALIZING)
    }
    sessionManager.setExtensionStatus(ExtensionInstallStatus.INSTALLING)

    val runtime = try {
      val wasNull = sharedRuntime == null
      getOrCreateRuntime(appContext).also {
        sessionManager.setGeckoStatus(GeckoInitStatus.READY)
        if (wasNull) {
          sessionManager.appendLog("GeckoRuntime initialized", LogLevel.INFO)
        }
      }
    } catch (t: Throwable) {
      val msg = "Failed to initialize GeckoRuntime: ${t.message ?: t.javaClass.simpleName}"
      sessionManager.setGeckoStatus(GeckoInitStatus.FAILED, msg)
      sessionManager.appendLog(msg, LogLevel.ERROR)
      onReady(null, false)
      return
    }

    runtime.webExtensionController
      .ensureBuiltIn(EXTENSION_LOCATION, EXTENSION_ID)
      .accept(
        { extension ->
          if (extension == null) {
            val msg = "WebExtension installation returned null extension."
            sessionManager.setExtensionStatus(ExtensionInstallStatus.FAILED, msg)
            sessionManager.appendLog(msg, LogLevel.ERROR)
            onReady(runtime, false)
          } else {
            val wasNew = installedExtension == null
            installedExtension = extension
            messageHandler.attachToExtension(extension)
            sessionManager.setExtensionStatus(ExtensionInstallStatus.INSTALLED)
            if (wasNew) {
              sessionManager.appendLog("Bundled session bridge installed", LogLevel.SUCCESS)
            }
            onReady(runtime, true)
          }
        },
        { throwable ->
          val msg = "WebExtension install failed: ${throwable?.message ?: "unknown error"}"
          sessionManager.setExtensionStatus(ExtensionInstallStatus.FAILED, msg)
          sessionManager.appendLog(msg, LogLevel.ERROR)
          onReady(runtime, false)
        },
      )
  }

  private fun openSessionAndNavigate(runtime: GeckoRuntime, locale: Locale) {
    releaseCurrentSessionInternal(logRelease = false)

    val sessionSettings = GeckoSessionSettings.Builder()
      .usePrivateMode(false)
      .build()

    val session = GeckoSession(sessionSettings).apply {
      navigationDelegate = createNavigationDelegate()
      progressDelegate = createProgressDelegate()
    }

    installedExtension?.let { messageHandler.attachToExtension(it) }

    session.open(runtime)
    _activeSession.value = session
    sessionManager.setActiveBrowserSession(true)
    sessionManager.onGoogleLoginStatus(
      status = GoogleLoginStatus.IDLE,
      stageLabel = "Loading BTU Login...",
    )
    sessionManager.appendLog("Opening BTU Classroom login page", LogLevel.INFO)
    session.loadUri(BtuRoutes.loginUrl(locale))
  }

  private fun createNavigationDelegate(): GeckoSession.NavigationDelegate {
    return object : GeckoSession.NavigationDelegate {
      override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
        sessionManager.setBrowserNavigationUi(canGoBack = canGoBack)
      }

      override fun onLoadRequest(
        session: GeckoSession,
        request: GeckoSession.NavigationDelegate.LoadRequest,
      ): GeckoResult<AllowOrDeny> {
        val scheme = runCatching { URI(request.uri).scheme }.getOrNull()
        if (scheme != "https" && request.uri != "about:blank") {
          return GeckoResult.fromValue(AllowOrDeny.DENY)
        }
        inspectNavigationUri(request.uri, isCommitted = false)
        return GeckoResult.fromValue(AllowOrDeny.ALLOW)
      }

      override fun onLocationChange(
        session: GeckoSession,
        url: String?,
        perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
        hasUserGesture: Boolean,
      ) {
        inspectNavigationUri(url, isCommitted = true)
      }

      override fun onLoadError(
        session: GeckoSession,
        uri: String?,
        error: WebRequestError,
      ): GeckoResult<String>? {
        val parsed = UrlSanitizer.inspect(uri)
        val targetHost = parsed?.host?.ifBlank { "unknown" } ?: "unknown"
        val message = "Navigation failed on $targetHost (code=${error.code}, category=${error.category})"
        sessionManager.onGoogleLoginStatus(
          status = GoogleLoginStatus.FAILED,
          stageLabel = "Network Error",
          errorMessage = message,
        )
        sessionManager.appendLog(message, LogLevel.ERROR)
        return null
      }
    }
  }

  private fun createProgressDelegate(): GeckoSession.ProgressDelegate {
    return object : GeckoSession.ProgressDelegate {
      override fun onProgressChange(session: GeckoSession, progress: Int) {
        sessionManager.setBrowserNavigationUi(progress = progress)
      }

      override fun onPageStop(session: GeckoSession, success: Boolean) {
        sessionManager.setBrowserNavigationUi(progress = if (success) 100 else 0)
        val telemetry = sessionManager.telemetry.value
        if (success && telemetry.googleLoginStatus == GoogleLoginStatus.AUTHENTICATED_PAGE_REACHED &&
          !telemetry.sessionCaptured
        ) {
          messageHandler.queryBtuSession(expectAuthenticated = true)
        } else if (success && telemetry.googleLoginStatus == GoogleLoginStatus.BTU_LOGIN_LOADED) {
          messageHandler.queryBtuSession(expectAuthenticated = false)
        }
      }
    }
  }

  private fun inspectNavigationUri(rawUrl: String?, isCommitted: Boolean) {
    val parsed = UrlSanitizer.inspect(rawUrl) ?: return
    val host = parsed.host
    val path = BtuRoutes.canonicalLocalePath(parsed.path)

    if (parsed.hasDisallowedUserAgent || parsed.oauthErrorCode == "disallowed_useragent") {
      val key = "rejected:$host"
      if (lastLoggedPathKey != key) {
        lastLoggedPathKey = key
        val rejectionMsg = "Google rejected the embedded browser (disallowed_useragent). You can open BTU Classroom in your external browser below."
        sessionManager.onGoogleLoginStatus(
          status = GoogleLoginStatus.REJECTED_DISALLOWED_USERAGENT,
          stageLabel = "Google Rejected (disallowed_useragent)",
          errorMessage = rejectionMsg,
          showExternalFallback = true,
        )
        sessionManager.appendLog("Google rejected embedded browser (disallowed_useragent)", LogLevel.ERROR)
      }
      return
    }

    if (parsed.oauthErrorCode != null && (host.endsWith("google.com") || host == BtuRoutes.APPROVED_HOST)) {
      val key = "oauth_err:${parsed.oauthErrorCode}"
      if (lastLoggedPathKey != key) {
        lastLoggedPathKey = key
        val errMsg = "OAuth reported error: ${parsed.oauthErrorCode}"
        sessionManager.onGoogleLoginStatus(
          status = GoogleLoginStatus.FAILED,
          stageLabel = "OAuth Error (${parsed.oauthErrorCode})",
          errorMessage = errMsg,
        )
        sessionManager.appendLog(errMsg, LogLevel.ERROR)
      }
      return
    }

    if (host == BtuRoutes.APPROVED_HOST) {
      when {
        path.startsWith(BtuRoutes.LOGIN_OAUTH) -> {
          val key = "btu_callback"
          if (lastLoggedPathKey != key) {
            lastLoggedPathKey = key
            sessionManager.onGoogleLoginStatus(
              status = GoogleLoginStatus.BTU_CALLBACK_OBSERVED,
              stageLabel = "BTU OAuth Callback",
              markCallbackDetected = true,
            )
            sessionManager.appendLog("BTU callback observed", LogLevel.INFO)
          }
        }
        path.startsWith("/ge/student/") -> {
          val key = "btu_authenticated"
          if (lastLoggedPathKey != key) {
            lastLoggedPathKey = key
            sessionManager.onGoogleLoginStatus(
              status = GoogleLoginStatus.AUTHENTICATED_PAGE_REACHED,
              stageLabel = "Verifying Portal...",
            )
            sessionManager.appendLog("Navigated to BTU student route", LogLevel.INFO)
          }
          if (isCommitted) {
            messageHandler.queryBtuSession(expectAuthenticated = true)
          }
        }
        path.startsWith(BtuRoutes.LOGIN_INDEX) || path == "/" -> {
          if (isCommitted) {
            val key = "btu_login_page"
            if (lastLoggedPathKey != key) {
              lastLoggedPathKey = key
              sessionManager.onGoogleLoginStatus(
                status = GoogleLoginStatus.BTU_LOGIN_LOADED,
                stageLabel = "BTU Login Page",
              )
              sessionManager.appendLog("BTU login page loaded", LogLevel.INFO)
            }
          }
        }
      }
      return
    }

    if (host == "accounts.google.com" || host.endsWith(".accounts.google.com")) {
      if (isCommitted) {
        val key = "google_login_loaded"
        if (lastLoggedPathKey != key) {
          lastLoggedPathKey = key
          sessionManager.onGoogleLoginStatus(
            status = GoogleLoginStatus.GOOGLE_LOGIN_PAGE_LOADED,
            stageLabel = "Google Sign-In",
          )
          sessionManager.appendLog("Google sign-in page loaded", LogLevel.INFO)
        }
      } else {
        val key = "google_oauth_started"
        if (lastLoggedPathKey != key && lastLoggedPathKey != "google_login_loaded") {
          lastLoggedPathKey = key
          sessionManager.onGoogleLoginStatus(
            status = GoogleLoginStatus.OAUTH_IN_PROGRESS,
            stageLabel = "Google OAuth...",
          )
          sessionManager.appendLog("Google OAuth flow started", LogLevel.INFO)
        }
      }
    }
  }

  fun goBackInBrowser(): Boolean {
    val session = _activeSession.value ?: return false
    return if (sessionManager.telemetry.value.browserCanGoBack) {
      session.goBack()
      true
    } else {
      false
    }
  }

  fun releaseLoginSession() {
    messageHandler.pauseSessionObservation()
    val hadSession = _activeSession.value != null
    releaseCurrentSessionInternal(logRelease = hadSession)
    sessionManager.setBrowserModalVisible(false)
    clearBrowserStorage {}
  }

  fun resetBrowserSession(onComplete: () -> Unit = {}) {
    messageHandler.pauseSessionObservation()
    releaseCurrentSessionInternal(logRelease = false)
    sessionManager.setBrowserModalVisible(false)
    lastLoggedPathKey = null
    clearBrowserStorage { onComplete() }
  }

  private fun clearBrowserStorage(onComplete: (Boolean) -> Unit) {
    if (storageResetTimedOut) {
      onComplete(false)
      return
    }
    val runtime = sharedRuntime
    if (runtime == null) {
      onComplete(true)
      return
    }
    storageResetCallbacks.add(onComplete)
    if (storageResetInProgress) return
    storageResetInProgress = true
    val generation = ++storageResetGeneration
    var completed = false
    lateinit var timeout: Runnable
    fun finish(success: Boolean) {
      if (completed || generation != storageResetGeneration) return
      completed = true
      storageResetHandler.removeCallbacks(timeout)
      storageResetInProgress = false
      if (success) {
        sessionManager.appendLog("Dedicated sign-in browser cookies and storage cleared", LogLevel.INFO)
      } else {
        val message = "Sign-in browser data could not be cleared. Please retry sign-in."
        sessionManager.setGeckoStatus(GeckoInitStatus.FAILED, message)
        sessionManager.appendLog(message, LogLevel.WARNING)
      }
      val callbacks = storageResetCallbacks.toList()
      storageResetCallbacks.clear()
      callbacks.forEach { it(success) }
    }
    timeout = Runnable {
      if (generation != storageResetGeneration) return@Runnable
      storageResetTimedOut = true
      finish(false)
    }
    storageResetHandler.postDelayed(timeout, STORAGE_RESET_TIMEOUT_MILLIS)
    try {
      runtime.storageController.clearData(StorageController.ClearFlags.ALL).accept(
        {
          if (generation == storageResetGeneration) {
            storageResetTimedOut = false
            finish(true)
          }
        },
        {
          if (generation == storageResetGeneration) {
            storageResetTimedOut = false
            finish(false)
          }
        },
      )
    } catch (_: Exception) {
      finish(false)
    }
  }

  private fun releaseCurrentSessionInternal(logRelease: Boolean) {
    val session = _activeSession.value
    if (session != null) {
      _activeSession.value = null
      sessionManager.setActiveBrowserSession(false)
      try {
        session.navigationDelegate = null
        session.progressDelegate = null
        session.close()
      } catch (_: Throwable) {
      }
      if (logRelease) {
        sessionManager.appendLog("GeckoView login session released", LogLevel.INFO)
      }
    } else {
      sessionManager.setActiveBrowserSession(false)
    }
  }

  companion object {
    private const val EXTENSION_LOCATION = "resource://android/assets/btucm_ext/"
    private const val EXTENSION_ID = "btucm-auth@btucm.local"
    private const val STORAGE_RESET_TIMEOUT_MILLIS = 10_000L
    private val storageResetHandler = Handler(Looper.getMainLooper())
    private val storageResetCallbacks = mutableListOf<(Boolean) -> Unit>()
    private var storageResetInProgress = false
    private var storageResetTimedOut = false
    private var storageResetGeneration = 0L

    @Volatile
    private var sharedRuntime: GeckoRuntime? = null

    fun getOrCreateRuntime(context: Context): GeckoRuntime {
      return sharedRuntime ?: synchronized(this) {
        sharedRuntime ?: run {
          val settings = GeckoRuntimeSettings.Builder()
            .javaScriptEnabled(true)
            .build()
          GeckoRuntime.create(context.applicationContext, settings).also {
            sharedRuntime = it
          }
        }
      }
    }
  }
}
