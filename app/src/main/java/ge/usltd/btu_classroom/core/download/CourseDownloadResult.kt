package ge.usltd.btu_classroom.core.download

import android.net.Uri

sealed interface CourseDownloadResult {
  data class Success(
    val uri: Uri,
    val mimeType: String,
    val displayName: String,
    val fromCache: Boolean = false,
  ) : CourseDownloadResult

  data object SessionExpired : CourseDownloadResult
  data object InvalidResource : CourseDownloadResult
  data object TooLarge : CourseDownloadResult
  data object Unavailable : CourseDownloadResult
  data object NetworkError : CourseDownloadResult
}
