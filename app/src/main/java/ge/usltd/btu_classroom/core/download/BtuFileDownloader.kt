package ge.usltd.btu_classroom.core.download

import android.content.Context
import androidx.core.content.FileProvider
import ge.usltd.btu_classroom.core.auth.SessionCookieValidator
import ge.usltd.btu_classroom.core.auth.SessionManager
import ge.usltd.btu_classroom.core.network.BtuRoutes
import ge.usltd.btu_classroom.core.network.ResponseClassifier
import ge.usltd.btu_classroom.core.preferences.UserPreferencesStore
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** Downloads only verified document routes into the app's private, non-backed-up cache. */
class BtuFileDownloader(
  context: Context,
  private val sessionManager: SessionManager,
  private val preferencesStore: UserPreferencesStore,
  client: OkHttpClient,
  private val baseOrigin: String = BtuRoutes.BASE_ORIGIN,
  private val allowLocalHttpForTesting: Boolean = false,
  private val maxFileBytes: Long = DEFAULT_MAX_FILE_BYTES,
) {
  private val appContext = context.applicationContext
  private val downloadClient = client.newBuilder()
    .followRedirects(false)
    .followSslRedirects(false)
    .cookieJar(CookieJar.NO_COOKIES)
    .build()
  private val transfers = ConcurrentHashMap<Call, Deferred<CourseDownloadResult>>()
  private val cacheMutex = Mutex()
  private val cleanupGeneration = AtomicLong(0L)

  init {
    require(maxFileBytes > 0L)
  }

  suspend fun getCached(url: String, displayName: String): CourseDownloadResult.Success? = withContext(Dispatchers.IO) {
    try {
      val resource = resolveResource(url) ?: return@withContext null
      val academicGeneration = preferencesStore.readAcademicDataGeneration()
      val transferGeneration = cleanupGeneration.get()
      cacheMutex.withLock {
        if (!isCacheCurrent(academicGeneration, transferGeneration)) return@withLock null
        val file = cacheFile(resource, academicGeneration)
        if (!file.isFile || file.length() !in 1L..maxFileBytes ||
          file.canonicalFile.parentFile != downloadDirectory().canonicalFile
        ) return@withLock null
        val prefix = file.inputStream().use { input ->
          val bytes = ByteArray(minOf(PREFIX_BYTES, file.length()).toInt())
          var offset = 0
          while (offset < bytes.size) {
            currentCoroutineContext().ensureActive()
            val read = input.read(bytes, offset, bytes.size - offset)
            if (read == -1) break
            offset += read
          }
          bytes.copyOf(offset)
        }
        if (!resource.accepts(null, prefix) || looksLikeHtml(prefix)) return@withLock null
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.files", file)
        if (!isCacheCurrent(academicGeneration, transferGeneration)) return@withLock null
        CourseDownloadResult.Success(uri, resource.mimeType, safeDisplayName(displayName, resource.extension), fromCache = true)
      }
    } catch (_: IOException) {
      currentCoroutineContext().ensureActive()
      null
    } catch (_: IllegalArgumentException) {
      null
    } catch (_: SecurityException) {
      null
    }
  }

  suspend fun download(url: String, displayName: String): CourseDownloadResult = try {
    downloadInScope(url, displayName)
  } catch (_: IOException) {
    currentCoroutineContext().ensureActive()
    CourseDownloadResult.NetworkError
  }

  private suspend fun downloadInScope(url: String, displayName: String): CourseDownloadResult = coroutineScope {
    val resource = resolveResource(url) ?: return@coroutineScope CourseDownloadResult.InvalidResource
    val cookie = sessionManager.currentSessionValue()
    if (!SessionCookieValidator.isValid(cookie)) return@coroutineScope CourseDownloadResult.SessionExpired
    val sessionCookie = requireNotNull(cookie)
    val academicGeneration = preferencesStore.readAcademicDataGeneration()
    val transferGeneration = cleanupGeneration.get()
    currentCoroutineContext().ensureActive()

    val request = Request.Builder()
      .url(resource.url)
      .get()
      .header("Cookie", "${BtuRoutes.SESSION_COOKIE_NAME}=$sessionCookie")
      .header("Cache-Control", "no-store")
      .build()
    val call = downloadClient.newCall(request)
    val transfer = async(Dispatchers.IO, start = CoroutineStart.LAZY) {
      performDownload(call, resource, displayName, sessionCookie, academicGeneration, transferGeneration)
    }
    transfers[call] = transfer
    try {
      transfer.start()
      transfer.await()
    } finally {
      if (!currentCoroutineContext().isActive) call.cancel()
      transfers.remove(call)
    }
  }

  suspend fun clearDownloads(): Unit = withContext(Dispatchers.IO) {
    cleanupGeneration.incrementAndGet()
    val activeTransfers = transfers.entries.toList()
    activeTransfers.forEach { it.key.cancel() }
    activeTransfers.map { it.value }.joinAll()
    cacheMutex.withLock {
      downloadDirectory().listFiles()?.filter { it.isFile }?.forEach { Files.deleteIfExists(it.toPath()) }
    }
  }

  private suspend fun performDownload(
    call: Call,
    resource: DocumentResource,
    displayName: String,
    cookie: String,
    academicGeneration: Long,
    transferGeneration: Long,
  ): CourseDownloadResult {
    var partialFile: File? = null
    try {
      if (!isCurrent(cookie, academicGeneration, transferGeneration)) return CourseDownloadResult.SessionExpired
      call.execute().use { response ->
        if (!isCurrent(cookie, academicGeneration, transferGeneration)) return CourseDownloadResult.SessionExpired
        classifyStatus(response)?.let { return it }
        val body = response.body ?: return CourseDownloadResult.Unavailable
        if (body.contentLength() > maxFileBytes) return CourseDownloadResult.TooLarge
        val contentType = body.contentType()?.toString()?.substringBefore(';')?.lowercase(Locale.ROOT)
        val peek = body.source().peek()
        peek.request(PREFIX_BYTES)
        val prefix = peek.readByteArray(minOf(PREFIX_BYTES, peek.buffer.size))
        if (contentType == "text/html" || contentType == "application/xhtml+xml" || looksLikeHtml(prefix)) {
          return if (ResponseClassifier.looksLikeLoginPage(prefix.toString(Charsets.UTF_8))) {
            CourseDownloadResult.SessionExpired
          } else {
            CourseDownloadResult.Unavailable
          }
        }
        if (!resource.accepts(contentType, prefix)) return CourseDownloadResult.Unavailable

        val file = cacheMutex.withLock {
          if (!isCurrent(cookie, academicGeneration, transferGeneration)) return CourseDownloadResult.SessionExpired
          val directory = downloadDirectory()
          if (!directory.isDirectory && !directory.mkdirs()) return CourseDownloadResult.NetworkError
          File.createTempFile("btu-", ".part", directory).also { partialFile = it }
        }
        var bytesWritten = 0L
        body.byteStream().use { input ->
          file.outputStream().use { output ->
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            while (true) {
              currentCoroutineContext().ensureActive()
              val count = input.read(buffer)
              if (count == -1) break
              bytesWritten += count
              if (bytesWritten > maxFileBytes) return CourseDownloadResult.TooLarge
              output.write(buffer, 0, count)
            }
          }
        }
        currentCoroutineContext().ensureActive()
        return cacheMutex.withLock {
          if (!isCurrent(cookie, academicGeneration, transferGeneration)) return CourseDownloadResult.SessionExpired
          val destination = cacheFile(resource, academicGeneration)
          moveIntoCache(file, destination)
          partialFile = null
          CourseDownloadResult.Success(
            uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.files", destination),
            mimeType = resource.mimeType,
            displayName = safeDisplayName(displayName, resource.extension),
          )
        }
      }
    } catch (_: IOException) {
      currentCoroutineContext().ensureActive()
      return CourseDownloadResult.NetworkError
    } catch (_: IllegalArgumentException) {
      return CourseDownloadResult.Unavailable
    } catch (_: SecurityException) {
      return CourseDownloadResult.Unavailable
    } finally {
      partialFile?.let { file ->
        try {
          Files.deleteIfExists(file.toPath())
        } catch (cleanupFailure: IOException) {
          currentCoroutineContext().ensureActive()
          throw cleanupFailure
        }
      }
    }
  }

  private suspend fun isCurrent(cookie: String, academicGeneration: Long, transferGeneration: Long): Boolean {
    currentCoroutineContext().ensureActive()
    return sessionManager.currentSessionValue() == cookie && isCacheCurrent(academicGeneration, transferGeneration)
  }

  private suspend fun isCacheCurrent(academicGeneration: Long, transferGeneration: Long): Boolean {
    currentCoroutineContext().ensureActive()
    return cleanupGeneration.get() == transferGeneration &&
      preferencesStore.readAcademicDataGeneration() == academicGeneration
  }

  private fun resolveResource(url: String): DocumentResource? {
    if (!allowLocalHttpForTesting && baseOrigin != BtuRoutes.BASE_ORIGIN) return null
    val rawUri = try { URI(url.trim()) } catch (_: Exception) { return null }
    if (rawUri.rawQuery != null || rawUri.rawFragment != null || rawUri.rawUserInfo != null) return null
    val rawPath = rawUri.rawPath ?: return null
    val extension = when {
      SYLLABUS_PATH.matches(rawPath) -> "pdf"
      UPLOAD_PATH.matches(rawPath) -> rawPath.substringAfterLast('.').lowercase(Locale.ROOT)
      else -> return null
    }
    val resolved = BtuRoutes.resolveSafeBtuUrl(url, baseOrigin, allowLocalHttpForTesting) ?: return null
    return DocumentResource(resolved, extension, requireNotNull(MIME_TYPES[extension]))
  }

  private fun classifyStatus(response: Response): CourseDownloadResult? = when {
    response.code == 401 -> CourseDownloadResult.SessionExpired
    response.code in 300..399 -> {
      val location = response.header("Location")
      val loginPath = try { location?.let { URI(it).path }?.let(BtuRoutes::canonicalLocalePath) } catch (_: Exception) { null }
      if (loginPath == BtuRoutes.LOGIN_INDEX || loginPath == BtuRoutes.LOGIN_OAUTH ||
        loginPath?.startsWith("${BtuRoutes.LOGIN_INDEX}/") == true ||
        loginPath?.startsWith("${BtuRoutes.LOGIN_OAUTH}/") == true
      ) CourseDownloadResult.SessionExpired else CourseDownloadResult.Unavailable
    }
    response.code != 200 -> CourseDownloadResult.Unavailable
    else -> null
  }

  private fun downloadDirectory(): File = File(appContext.cacheDir, "downloads")

  private fun cacheFile(resource: DocumentResource, academicGeneration: Long): File =
    File(downloadDirectory(), "${hash(resource.url)}-$academicGeneration.${resource.extension}")

  private fun moveIntoCache(source: File, destination: File) {
    try {
      Files.move(source.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } catch (_: AtomicMoveNotSupportedException) {
      Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
  }

  private fun hash(url: String): String = MessageDigest.getInstance("SHA-256")
    .digest(url.toByteArray(Charsets.UTF_8)).take(16).joinToString("") { "%02x".format(it) }

  private fun safeDisplayName(displayName: String, extension: String): String = displayName
    .substringAfterLast('/').substringAfterLast('\\')
    .filter { !it.isISOControl() }.trim().take(MAX_DISPLAY_NAME_LENGTH)
    .takeUnless { it.isBlank() || it == "." || it == ".." } ?: "document.$extension"

  private fun looksLikeHtml(prefix: ByteArray): Boolean {
    val text = prefix.toString(Charsets.UTF_8).trimStart().lowercase(Locale.ROOT)
    return text.startsWith("<!doctype html") || text.startsWith("<html") || text.startsWith("<head") ||
      text.startsWith("<body") || text.startsWith("<form")
  }

  private data class DocumentResource(val url: String, val extension: String, val mimeType: String) {
    fun accepts(contentType: String?, prefix: ByteArray): Boolean {
      if (contentType != null && contentType !in BINARY_TYPES && contentType != mimeType &&
        !(extension == "zip" && contentType == "application/x-zip-compressed")
      ) return false
      return when (extension) {
        "pdf" -> prefix.size >= 5 && prefix.copyOfRange(0, 5).contentEquals("%PDF-".toByteArray())
        "docx", "pptx", "xlsx", "zip" -> prefix.size >= 4 && prefix[0] == 0x50.toByte() &&
          prefix[1] == 0x4b.toByte() && prefix[2] == 0x03.toByte() && prefix[3] == 0x04.toByte()
        "txt" -> prefix.isNotEmpty() && prefix.none { it == 0.toByte() }
        else -> false
      }
    }
  }

  companion object {
    const val DEFAULT_MAX_FILE_BYTES = 50L * 1024L * 1024L
    private const val PREFIX_BYTES = 512L
    private const val COPY_BUFFER_BYTES = 64 * 1024
    private const val MAX_DISPLAY_NAME_LENGTH = 160
    private val SYLLABUS_PATH = Regex("^/ge/student/me/courseSilabusFile/[0-9]+/[0-9]+$")
    private val UPLOAD_PATH = Regex("^/uploads/[0-9]+/[0-9]+\\.(pdf|docx|pptx|xlsx|zip|txt)$", RegexOption.IGNORE_CASE)
    private val BINARY_TYPES = setOf("application/octet-stream", "binary/octet-stream", "application/download", "application/x-download")
    private val MIME_TYPES = mapOf(
      "pdf" to "application/pdf",
      "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
      "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
      "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      "zip" to "application/zip",
      "txt" to "text/plain",
    )
  }
}
