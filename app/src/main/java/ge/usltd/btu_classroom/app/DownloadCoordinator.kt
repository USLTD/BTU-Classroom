package ge.usltd.btu_classroom.app

import ge.usltd.btu_classroom.core.database.CachedCourseResourceDto
import ge.usltd.btu_classroom.core.download.CourseDownloadResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class DownloadCoordinator(
  private val scope: CoroutineScope,
  private val readGeneration: suspend () -> Long,
  private val currentSession: () -> String?,
  private val download: suspend (CachedCourseResourceDto) -> CourseDownloadResult,
) {
  private val _pendingResult = MutableStateFlow<CourseDownloadResult?>(null)
  val pendingResult = _pendingResult.asStateFlow()
  private val _loadingUrl = MutableStateFlow<String?>(null)
  val loadingUrl = _loadingUrl.asStateFlow()
  private var job: Job? = null

  fun request(resource: CachedCourseResourceDto) {
    if (job?.isActive == true) return
    _loadingUrl.value = resource.url
    job = scope.launch {
      var identity: Identity? = null
      try {
        identity = Identity(readGeneration(), currentSession())
        val result = download(resource)
        publishIfCurrent(identity, result)
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: Exception) {
        identity?.let { publishIfCurrent(it, CourseDownloadResult.NetworkError) }
      } finally {
        _loadingUrl.value = null
      }
    }
  }

  fun consume() {
    _pendingResult.value = null
  }

  suspend fun cancelAndClear() {
    job?.cancelAndJoin()
    job = null
    _loadingUrl.value = null
    _pendingResult.value = null
  }

  private suspend fun publishIfCurrent(identity: Identity, result: CourseDownloadResult) {
    currentCoroutineContext().ensureActive()
    val generation = try {
      readGeneration()
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (_: Exception) {
      return
    }
    currentCoroutineContext().ensureActive()
    if (generation == identity.generation && currentSession() == identity.session) {
      _pendingResult.value = result
    }
  }

  private data class Identity(val generation: Long, val session: String?)
}
