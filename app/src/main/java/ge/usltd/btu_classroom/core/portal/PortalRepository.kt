package ge.usltd.btu_classroom.core.portal

import androidx.room.withTransaction
import ge.usltd.btu_classroom.core.auth.SessionManager
import ge.usltd.btu_classroom.core.database.BtuDatabase
import ge.usltd.btu_classroom.core.database.SyncOutcome
import ge.usltd.btu_classroom.core.network.BtuHttpClient
import ge.usltd.btu_classroom.core.network.HttpFetchResult
import ge.usltd.btu_classroom.core.parsing.ParseResult
import ge.usltd.btu_classroom.core.preferences.UserPreferencesStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PortalRepository(
  private val database: BtuDatabase,
  private val httpClient: BtuHttpClient,
  private val sessionManager: SessionManager,
  private val preferencesStore: UserPreferencesStore,
  private val parser: PortalPageParser = PortalPageParser(),
) {
  private val dao = database.portalDao()
  private val syncMutex = Mutex()
  private val json = Json { ignoreUnknownKeys = true }

  fun observe(module: PortalModule): Flow<PortalCachedPage?> = dao.observe(module.id).map { snapshot ->
    snapshot?.let(::decodeSnapshot)
  }

  suspend fun refresh(module: PortalModule): SyncOutcome = syncMutex.withLock {
    try {
      refreshInternal(module)
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (_: Exception) {
      SyncOutcome.OfflineOrFailed("This page could not be synchronized. Please try again.", isOffline = false)
    }
  }

  suspend fun clearAll() {
    syncMutex.withLock { dao.clearAll() }
  }

  private suspend fun refreshInternal(module: PortalModule): SyncOutcome {
    val session = sessionManager.currentSessionValue()?.takeIf { it.isNotBlank() }
      ?: return SyncOutcome.SessionExpired
    val generation = preferencesStore.readAcademicDataGeneration()
    val attemptedAt = System.currentTimeMillis()
    val html = when (val fetched = httpClient.fetchHtml(module.path, session)) {
      is HttpFetchResult.SuccessHtml -> fetched.html
      is HttpFetchResult.SessionExpired -> {
        recordFailure(module, session, generation, attemptedAt, "SESSION_EXPIRED", "Sign in again to update this page.")
        if (isCurrentAcademicSession(session, generation)) {
          sessionManager.handleServerSessionExpired("The portal page requires authentication")
        }
        return SyncOutcome.SessionExpired
      }
      is HttpFetchResult.NetworkError -> {
        return failed(module, session, generation, attemptedAt, "OFFLINE", "Saved information is available while offline.", true)
      }
      is HttpFetchResult.CloudflareBlocked, is HttpFetchResult.TemporarilyUnavailable -> {
        return failed(module, session, generation, attemptedAt, "TEMPORARILY_UNAVAILABLE", "The university has temporarily made this page unavailable.")
      }
      is HttpFetchResult.UnexpectedResponse -> {
        return failed(module, session, generation, attemptedAt, "UNEXPECTED_RESPONSE", "The university returned an unexpected page. Saved information is retained.")
      }
    }
    if (!isCurrentAcademicSession(session, generation)) return SyncOutcome.SessionExpired

    return when (val parsed = withContext(Dispatchers.Default) { parser.parse(module, html) }) {
      is ParseResult.Success -> {
        if (storeContent(module, session, generation, attemptedAt, parsed.data, "SUCCESS")) SyncOutcome.Success
        else SyncOutcome.SessionExpired
      }
      ParseResult.Empty -> {
        if (storeContent(module, session, generation, attemptedAt, PortalContent(title = moduleTitle(module)), "EMPTY")) SyncOutcome.Empty
        else SyncOutcome.SessionExpired
      }
      ParseResult.SessionExpired -> {
        recordFailure(module, session, generation, attemptedAt, "SESSION_EXPIRED", "Sign in again to update this page.")
        if (isCurrentAcademicSession(session, generation)) {
          sessionManager.handleServerSessionExpired("The portal returned its sign-in page")
        }
        SyncOutcome.SessionExpired
      }
      ParseResult.TemporarilyUnavailable -> failed(
        module, session, generation, attemptedAt, "TEMPORARILY_UNAVAILABLE", "The university has temporarily made this page unavailable.",
      )
      is ParseResult.UnsupportedStructure -> {
        if (recordFailure(module, session, generation, attemptedAt, "UNSUPPORTED_STRUCTURE", "This page has changed. Saved information is retained.")) {
          SyncOutcome.UnsupportedStructure(parsed.reason)
        } else SyncOutcome.SessionExpired
      }
    }
  }

  private suspend fun storeContent(
    module: PortalModule,
    expectedSession: String,
    expectedGeneration: Long,
    attemptedAt: Long,
    content: PortalContent,
    status: String,
  ): Boolean {
    val payload = json.encodeToString(content)
    return database.withTransaction {
      // Check after acquiring the database transaction so logout's clear cannot be undone.
      if (!isCurrentAcademicSession(expectedSession, expectedGeneration)) return@withTransaction false
      dao.upsert(PortalSnapshotEntity(
        moduleId = module.id,
        payloadJson = payload,
        lastSuccessAtMillis = System.currentTimeMillis(),
        lastAttemptAtMillis = attemptedAt,
        status = status,
        message = null,
      ))
      true
    }
  }

  private suspend fun failed(
    module: PortalModule,
    session: String,
    generation: Long,
    attemptedAt: Long,
    status: String,
    message: String,
    offline: Boolean = false,
  ): SyncOutcome = if (recordFailure(module, session, generation, attemptedAt, status, message)) {
    SyncOutcome.OfflineOrFailed(message, isOffline = offline)
  } else SyncOutcome.SessionExpired

  private suspend fun recordFailure(
    module: PortalModule,
    expectedSession: String,
    expectedGeneration: Long,
    attemptedAt: Long,
    status: String,
    message: String,
  ): Boolean = database.withTransaction {
    if (!isCurrentAcademicSession(expectedSession, expectedGeneration)) return@withTransaction false
    val previous = dao.get(module.id)
    dao.upsert(PortalSnapshotEntity(
      moduleId = module.id,
      payloadJson = previous?.payloadJson,
      lastSuccessAtMillis = previous?.lastSuccessAtMillis,
      lastAttemptAtMillis = attemptedAt,
      status = status,
      message = message,
    ))
    true
  }

  private suspend fun isCurrentAcademicSession(session: String, generation: Long): Boolean =
    sessionManager.currentSessionValue() == session &&
      preferencesStore.readAcademicDataGeneration() == generation && sessionManager.currentSessionValue() == session

  private fun decodeSnapshot(snapshot: PortalSnapshotEntity): PortalCachedPage {
    val content = try {
      snapshot.payloadJson?.let { json.decodeFromString<PortalContent>(it) }
    } catch (_: SerializationException) {
      null
    } catch (_: IllegalArgumentException) {
      null
    }
    val unreadable = snapshot.payloadJson != null && content == null
    return PortalCachedPage(
      content = content,
      lastSuccessAtMillis = snapshot.lastSuccessAtMillis,
      lastAttemptAtMillis = snapshot.lastAttemptAtMillis,
      status = if (unreadable) "CACHE_UNREADABLE" else snapshot.status,
      message = if (unreadable) "Saved information could not be read. Refresh this page." else snapshot.message,
    )
  }

  private fun moduleTitle(module: PortalModule): String = when (module) {
    PortalModule.SCHEDULE -> "Weekly schedule"
    PortalModule.EXAMS -> "Examinations"
    PortalModule.PROGRAM -> "Academic program"
    PortalModule.CALENDAR -> "Academic calendar"
    PortalModule.ACADEMIC_CARD -> "Academic card"
    PortalModule.REMAINING -> "Courses to take"
    PortalModule.STATEMENTS -> "Statements"
    PortalModule.RATING -> "Rating"
  }
}
