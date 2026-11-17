package ge.usltd.btu_classroom.app

import android.app.Application
import android.net.Uri
import ge.usltd.btu_classroom.core.database.CachedCourseResourceDto
import ge.usltd.btu_classroom.core.download.CourseDownloadResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DownloadCoordinatorTest {
  @Test fun cleanupWaitsForTheOuterTaskAndRejectsItsLateSuccess() = runTest {
    val release = CompletableDeferred<Unit>()
    val coordinator = DownloadCoordinator(this, { 1L }, { "test-session" }) {
      withContext(NonCancellable) { release.await() }
      success()
    }
    coordinator.request(resource)
    runCurrent()
    val cleanup = launch { coordinator.cancelAndClear() }
    runCurrent()
    assertFalse(cleanup.isCompleted)
    release.complete(Unit)
    cleanup.join()
    assertNull(coordinator.pendingResult.value)
    assertNull(coordinator.loadingUrl.value)
  }

  @Test fun aGenerationChangeSuppressesCompletedFilesBeforeTheyReachTheUi() = runTest {
    var generation = 1L
    val release = CompletableDeferred<Unit>()
    val coordinator = DownloadCoordinator(this, { generation }, { "test-session" }) {
      release.await()
      success()
    }
    coordinator.request(resource)
    runCurrent()
    generation = 2L
    release.complete(Unit)
    advanceUntilIdle()
    assertNull(coordinator.pendingResult.value)
  }

  @Test fun aSessionChangeSuppressesThePreviousAccountsCompletedFile() = runTest {
    var session = "test-account-a"
    val release = CompletableDeferred<Unit>()
    val coordinator = DownloadCoordinator(this, { 1L }, { session }) {
      release.await()
      success()
    }
    coordinator.request(resource)
    runCurrent()
    session = "test-account-b"
    release.complete(Unit)
    advanceUntilIdle()
    assertNull(coordinator.pendingResult.value)
  }

  @Test fun aCurrentCompletedFileRemainsAvailableUntilConsumed() = runTest {
    val expected = success()
    val coordinator = DownloadCoordinator(this, { 1L }, { "test-session" }) { expected }
    coordinator.request(resource)
    advanceUntilIdle()
    assertEquals(expected, coordinator.pendingResult.value)
    coordinator.consume()
    assertNull(coordinator.pendingResult.value)
  }

  private fun success() = CourseDownloadResult.Success(
    Uri.parse("content://test.files/fixture.pdf"), "application/pdf", "Fixture syllabus",
  )

  companion object {
    private val resource = CachedCourseResourceDto("Fixture syllabus", "https://classroom.btu.edu.ge/ge/syllabus/pdf/1")
  }
}
