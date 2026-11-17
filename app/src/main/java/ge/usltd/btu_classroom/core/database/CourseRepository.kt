package ge.usltd.btu_classroom.core.database

import androidx.room.withTransaction
import ge.usltd.btu_classroom.core.auth.LogLevel
import ge.usltd.btu_classroom.core.auth.SessionManager
import ge.usltd.btu_classroom.core.database.entities.CourseEntity
import ge.usltd.btu_classroom.core.database.entities.CourseWithEnrollment
import ge.usltd.btu_classroom.core.database.entities.EnrollmentEntity
import ge.usltd.btu_classroom.core.database.entities.GradeEntity
import ge.usltd.btu_classroom.core.database.entities.SemesterEntity
import ge.usltd.btu_classroom.core.database.entities.StudentEntity
import ge.usltd.btu_classroom.core.database.entities.SyncMetadataEntity
import ge.usltd.btu_classroom.core.network.BtuHttpClient
import ge.usltd.btu_classroom.core.network.BtuRoutes
import ge.usltd.btu_classroom.core.network.HttpFetchResult
import ge.usltd.btu_classroom.core.parsing.BtuModuleId
import ge.usltd.btu_classroom.core.parsing.CourseDetailParser
import ge.usltd.btu_classroom.core.parsing.ModuleCompatibilityStatus
import ge.usltd.btu_classroom.core.parsing.PageCompatibilityRegistry
import ge.usltd.btu_classroom.core.parsing.ParseResult
import ge.usltd.btu_classroom.core.parsing.ParsedCourseDetail
import ge.usltd.btu_classroom.core.parsing.ParsedCourseResource
import ge.usltd.btu_classroom.core.parsing.SelectedCoursesPageData
import ge.usltd.btu_classroom.core.parsing.SelectedCoursesParser
import ge.usltd.btu_classroom.core.preferences.UserPreferencesStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URI

sealed interface SyncOutcome {
  data object Success : SyncOutcome
  data object Empty : SyncOutcome
  data object SessionExpired : SyncOutcome
  data class OfflineOrFailed(val message: String, val isOffline: Boolean) : SyncOutcome
  data class UnsupportedStructure(val reason: String) : SyncOutcome
}

@Serializable
data class CachedCourseResourceDto(
  val title: String,
  val url: String,
)

class CourseRepository(
  private val database: BtuDatabase,
  private val httpClient: BtuHttpClient,
  private val sessionManager: SessionManager,
  private val preferencesStore: UserPreferencesStore,
  private val compatibilityRegistry: PageCompatibilityRegistry,
  private val selectedCoursesParser: SelectedCoursesParser = SelectedCoursesParser(),
  private val courseDetailParser: CourseDetailParser = CourseDetailParser(),
) {

  private val studentDao = database.studentDao()
  private val courseDao = database.courseDao()
  private val syncMetadataDao = database.syncMetadataDao()
  private val syncMutex = Mutex()

  private val json = Json { ignoreUnknownKeys = true }

  fun observeStudent(studentKey: String = UserPreferencesStore.DEFAULT_STUDENT_KEY): Flow<StudentEntity?> =
    studentDao.observeStudent(studentKey)

  fun observeSemesters(studentKey: String = UserPreferencesStore.DEFAULT_STUDENT_KEY): Flow<List<SemesterEntity>> =
    courseDao.observeSemesters(studentKey)

  fun observeCourses(studentKey: String = UserPreferencesStore.DEFAULT_STUDENT_KEY): Flow<List<CourseWithEnrollment>> =
    courseDao.observeCoursesWithEnrollment(studentKey)

  fun observeCourseDetail(courseKey: String): Flow<CourseWithEnrollment?> =
    courseDao.observeCourseWithEnrollment(courseKey)

  fun observeCourseGrades(courseKey: String): Flow<List<GradeEntity>> =
    courseDao.observeGradesForCourse(courseKey)

  fun observeCoursesSyncMetadata(): Flow<SyncMetadataEntity?> =
    syncMetadataDao.observeModule(BtuModuleId.SELECTED_COURSES.id)

  suspend fun hasCachedCourses(): Boolean = courseDao.countAllCourses() > 0 ||
    syncMetadataDao.getModule(BtuModuleId.SELECTED_COURSES.id)?.lastSuccessAtMillis != null

  suspend fun refreshSelectedCourses(preFetchedHtml: String? = null): SyncOutcome = guardedSync {
    refreshSelectedCoursesInternal(preFetchedHtml)
  }

  private suspend fun refreshSelectedCoursesInternal(preFetchedHtml: String?): SyncOutcome {
    val now = System.currentTimeMillis()
    val initialSession = sessionManager.currentSessionValue()
    val generation = preferencesStore.readAcademicDataGeneration()
    suspend fun recordFailure(status: String, message: String?) {
      recordFailureSyncMeta(BtuModuleId.SELECTED_COURSES.id, now, status, message, generation, initialSession)
    }
    if (initialSession.isNullOrBlank()) {
      recordFailure("SESSION_EXPIRED", "No active session")
      return SyncOutcome.SessionExpired
    }
    val htmlToParse = if (!preFetchedHtml.isNullOrBlank()) {
      preFetchedHtml
    } else {
      val fetched = httpClient.fetchHtml(BtuRoutes.STUDENT_COURSES, initialSession)
      if (!isCurrentAcademicSession(initialSession, generation)) return SyncOutcome.SessionExpired
      when (val fetch = fetched) {
        is HttpFetchResult.SuccessHtml -> fetch.html
        is HttpFetchResult.SessionExpired -> {
          compatibilityRegistry.update(
            BtuModuleId.SELECTED_COURSES,
            ModuleCompatibilityStatus.SESSION_EXPIRED,
            fetch.reason,
            now,
          )
          recordFailure("SESSION_EXPIRED", fetch.reason)
          if (isCurrentAcademicSession(initialSession, generation)) sessionManager.handleServerSessionExpired(fetch.reason)
          return SyncOutcome.SessionExpired
        }
        is HttpFetchResult.CloudflareBlocked -> {
          compatibilityRegistry.update(
            BtuModuleId.SELECTED_COURSES,
            ModuleCompatibilityStatus.TEMPORARILY_UNAVAILABLE,
            fetch.reason,
            now,
          )
          recordFailure("CLOUDFLARE_BLOCKED", fetch.reason)
          return SyncOutcome.OfflineOrFailed(fetch.reason, isOffline = false)
        }
        is HttpFetchResult.TemporarilyUnavailable -> {
          compatibilityRegistry.update(
            BtuModuleId.SELECTED_COURSES,
            ModuleCompatibilityStatus.TEMPORARILY_UNAVAILABLE,
            fetch.reason,
            now,
          )
          recordFailure("TEMPORARILY_UNAVAILABLE", fetch.reason)
          return SyncOutcome.OfflineOrFailed(fetch.reason, isOffline = false)
        }
        is HttpFetchResult.NetworkError -> {
          sessionManager.markOfflineFallback(true)
          recordFailure("OFFLINE", fetch.reason)
          return SyncOutcome.OfflineOrFailed(fetch.reason, isOffline = true)
        }
        is HttpFetchResult.UnexpectedResponse -> {
          compatibilityRegistry.update(
            BtuModuleId.SELECTED_COURSES,
            ModuleCompatibilityStatus.UNEXPECTED_HTML,
            fetch.reason,
            now,
          )
          recordFailure("UNEXPECTED_RESPONSE", fetch.reason)
          return SyncOutcome.OfflineOrFailed(fetch.reason, isOffline = false)
        }
      }
    }

    if (!isCurrentAcademicSession(initialSession, generation)) return SyncOutcome.SessionExpired

    sessionManager.markOfflineFallback(false)
    val parseResult = selectedCoursesParser.parse(htmlToParse)
    if (!isCurrentAcademicSession(initialSession, generation)) return SyncOutcome.SessionExpired
    compatibilityRegistry.recordResult(BtuModuleId.SELECTED_COURSES, parseResult, now)

    return when (parseResult) {
      is ParseResult.Success -> {
        val stored = database.withTransaction {
          if (!isCurrentAcademicSession(initialSession, generation)) return@withTransaction false
          persistSelectedCourses(parseResult.data, now)
          syncMetadataDao.upsert(
            SyncMetadataEntity(
              moduleId = BtuModuleId.SELECTED_COURSES.id,
              lastAttemptAtMillis = now,
              lastSuccessAtMillis = now,
              outcomeStatus = "SUCCESS",
              message = "${parseResult.data.courses.size} courses synchronized",
            ),
          )
          true
        }
        if (!stored) return SyncOutcome.SessionExpired
        sessionManager.appendLog(
          "Selected courses synced (${parseResult.data.courses.size} course(s))",
          LogLevel.SUCCESS,
        )
        SyncOutcome.Success
      }
      ParseResult.Empty -> {
        val studentKey = preferencesStore.readLastStudentKey()
        val stored = database.withTransaction {
          if (!isCurrentAcademicSession(initialSession, generation)) return@withTransaction false
          courseDao.replaceSelectedCoursesSnapshot(studentKey, emptyList(), emptyList(), emptyList())
          studentDao.deleteAllStudents()
          syncMetadataDao.upsert(
            SyncMetadataEntity(
              moduleId = BtuModuleId.SELECTED_COURSES.id,
              lastAttemptAtMillis = now,
              lastSuccessAtMillis = now,
              outcomeStatus = "EMPTY",
              message = "No selected courses returned",
            ),
          )
          true
        }
        if (!stored) return SyncOutcome.SessionExpired
        SyncOutcome.Empty
      }
      ParseResult.SessionExpired -> {
        recordFailure("SESSION_EXPIRED", "Login page returned")
        if (isCurrentAcademicSession(initialSession, generation)) sessionManager.handleServerSessionExpired("Selected courses endpoint returned login markup")
        SyncOutcome.SessionExpired
      }
      ParseResult.TemporarilyUnavailable -> {
        recordFailure(
          "TEMPORARILY_UNAVAILABLE",
          "Portal reported module unavailable",
        )
        SyncOutcome.OfflineOrFailed("Selected courses temporarily unavailable", isOffline = false)
      }
      is ParseResult.UnsupportedStructure -> {
        recordFailure(
          "UNSUPPORTED_STRUCTURE",
          parseResult.reason,
        )
        sessionManager.appendLog("Selected courses parser mismatch: ${parseResult.reason}", LogLevel.WARNING)
        SyncOutcome.UnsupportedStructure(parseResult.reason)
      }
    }
  }

  suspend fun refreshCourseDetail(
    courseKey: String,
    courseId: String,
    detailPath: String?,
  ): SyncOutcome = guardedSync {
    refreshCourseDetailInternal(courseKey, courseId, detailPath)
  }

  private suspend fun refreshCourseDetailInternal(
    courseKey: String,
    courseId: String,
    detailPath: String?,
  ): SyncOutcome {
    val now = System.currentTimeMillis()
    val sessionCookie = sessionManager.currentSessionValue()
      ?: return SyncOutcome.SessionExpired
    val generation = preferencesStore.readAcademicDataGeneration()

    val targetPath = detailPath?.takeIf { it.isNotBlank() }
      ?: return SyncOutcome.UnsupportedStructure("No verified course detail link was supplied by the portal")
    val existing = courseDao.getCoursesSnapshot(preferencesStore.readLastStudentKey())
      .firstOrNull { it.courseKey == courseKey }
      ?: return SyncOutcome.UnsupportedStructure("This course is no longer in the selected courses snapshot")
    if (existing.courseId != courseId || existing.detailPath != targetPath) {
      return SyncOutcome.UnsupportedStructure("The requested course detail link does not match the cached portal link")
    }
    if (verifiedCoursePath(targetPath, "index", courseId, requireSemester = false) == null) {
      return SyncOutcome.UnsupportedStructure("The selected course detail link does not match the verified portal route")
    }
    val fetched = httpClient.fetchHtml(targetPath, sessionCookie)
    if (!isCurrentAcademicSession(sessionCookie, generation)) return SyncOutcome.SessionExpired
    val html = when (val fetch = fetched) {
      is HttpFetchResult.SuccessHtml -> fetch.html
      is HttpFetchResult.SessionExpired -> {
        compatibilityRegistry.update(BtuModuleId.COURSE_DETAILS, ModuleCompatibilityStatus.SESSION_EXPIRED, fetch.reason, now)
        if (isCurrentAcademicSession(sessionCookie, generation)) sessionManager.handleServerSessionExpired(fetch.reason)
        return SyncOutcome.SessionExpired
      }
      is HttpFetchResult.NetworkError -> {
        sessionManager.markOfflineFallback(true)
        return SyncOutcome.OfflineOrFailed(fetch.reason, isOffline = true)
      }
      is HttpFetchResult.CloudflareBlocked -> {
        compatibilityRegistry.update(BtuModuleId.COURSE_DETAILS, ModuleCompatibilityStatus.TEMPORARILY_UNAVAILABLE, fetch.reason, now)
        return SyncOutcome.OfflineOrFailed(fetch.reason, isOffline = false)
      }
      is HttpFetchResult.TemporarilyUnavailable -> {
        compatibilityRegistry.update(BtuModuleId.COURSE_DETAILS, ModuleCompatibilityStatus.TEMPORARILY_UNAVAILABLE, fetch.reason, now)
        return SyncOutcome.OfflineOrFailed(fetch.reason, isOffline = false)
      }
      is HttpFetchResult.UnexpectedResponse -> {
        compatibilityRegistry.update(BtuModuleId.COURSE_DETAILS, ModuleCompatibilityStatus.UNEXPECTED_HTML, fetch.reason, now)
        return SyncOutcome.OfflineOrFailed(fetch.reason, isOffline = false)
      }
    }

    if (!isCurrentAcademicSession(sessionCookie, generation)) return SyncOutcome.SessionExpired

    sessionManager.markOfflineFallback(false)
    val parsed = courseDetailParser.parse(html)
    if (!isCurrentAcademicSession(sessionCookie, generation)) return SyncOutcome.SessionExpired
    compatibilityRegistry.recordResult(BtuModuleId.COURSE_DETAILS, parsed, now)

    return when (parsed) {
      is ParseResult.Success -> {
        val detail = parsed.data
        val children = synchronizeCourseChildren(detail, courseId, sessionCookie, generation)
        if (children.failure is SyncOutcome.SessionExpired || !isCurrentAcademicSession(sessionCookie, generation)) {
          return SyncOutcome.SessionExpired
        }
        val studentKey = existing.studentKey
        val semesterKey = existing.semesterKey
        val gradeSnapshot = children.scores ?: detail.takeIf { it.hasGradeSnapshot && !children.scoresFailed }
        val cachedResources = decodeResources(existing.resourcesJson).map { ParsedCourseResource(it.title, it.url) }
        val resources = if (children.files != null) {
          detail.resources + children.files
        } else {
          detail.resources + cachedResources
        }.distinctBy { it.url }
        val updatedCourse = CourseEntity(
          courseKey = courseKey,
          courseId = existing.courseId,
          studentKey = studentKey,
          semesterKey = semesterKey,
          semesterName = detail.semesterName ?: existing.semesterName,
          code = detail.code ?: existing.code,
          title = detail.title.ifBlank { existing.title },
          credits = detail.credits ?: existing.credits,
          minCredit = detail.minCredit ?: existing.minCredit,
          lecturer = detail.lecturer ?: existing.lecturer,
          syllabusSummary = detail.syllabusSummary ?: existing.syllabusSummary,
          detailPath = existing.detailPath,
          resourcesJson = encodeResources(resources),
          detailSyncedAtMillis = if (children.failure == null) now else existing.detailSyncedAtMillis,
          detailStatus = detailStatus(children.failure),
        )
        val totalScore = if (children.scoresFailed) null else children.scores?.totalScore ?: detail.totalScore
        val updatedEnrollment = if (totalScore != null) {
          EnrollmentEntity(
            enrollmentKey = courseKey,
            studentKey = studentKey,
            semesterKey = semesterKey,
            courseId = updatedCourse.courseId,
            totalScore = totalScore,
            statusLabel = existingEnrollmentStatus(courseKey),
            updatedAtMillis = now,
          )
        } else {
          null
        }
        val gradeEntities = gradeSnapshot?.gradeComponents.orEmpty().mapIndexed { idx, comp ->
          GradeEntity(
            gradeKey = "$courseKey::$idx",
            courseKey = courseKey,
            studentKey = studentKey,
            semesterKey = semesterKey,
            courseId = updatedCourse.courseId,
            categoryName = comp.categoryName,
            score = comp.score,
            maxScore = comp.maxScore,
            notes = comp.notes,
            sortOrder = idx,
          )
        }
        val stored = database.withTransaction {
          if (!isCurrentAcademicSession(sessionCookie, generation)) return@withTransaction false
          courseDao.updateCourseDetailAndGrades(
            updatedCourse, updatedEnrollment, gradeEntities,
            replaceGrades = gradeSnapshot != null,
          )
          true
        }
        if (!stored) return SyncOutcome.SessionExpired
        children.failure?.also { recordDetailFailure(it, now) } ?: SyncOutcome.Success
      }
      ParseResult.Empty -> SyncOutcome.Empty
      ParseResult.SessionExpired -> {
        if (isCurrentAcademicSession(sessionCookie, generation)) sessionManager.handleServerSessionExpired("Course detail returned login page")
        SyncOutcome.SessionExpired
      }
      ParseResult.TemporarilyUnavailable ->
        SyncOutcome.OfflineOrFailed("Course detail temporarily unavailable", isOffline = false)
      is ParseResult.UnsupportedStructure -> {
        val stored = database.withTransaction {
          if (!isCurrentAcademicSession(sessionCookie, generation)) return@withTransaction false
          courseDao.insertCourses(listOf(existing.copy(detailStatus = "UNSUPPORTED_STRUCTURE")))
          true
        }
        if (!stored) return SyncOutcome.SessionExpired
        SyncOutcome.UnsupportedStructure(parsed.reason)
      }
    }
  }

  private data class CourseChildren(
    val scores: ParsedCourseDetail? = null,
    val files: List<ParsedCourseResource>? = null,
    val failure: SyncOutcome? = null,
    val scoresFailed: Boolean = false,
  )

  private data class CourseChildPath(val path: String, val semesterId: String?)

  private suspend fun synchronizeCourseChildren(
    index: ParsedCourseDetail,
    courseId: String,
    session: String,
    generation: Long,
  ): CourseChildren {
    val scoresPath = index.scoresPath?.let { verifiedCoursePath(it, "scores", courseId) }
    val filesPath = index.filesPath?.let { verifiedCoursePath(it, "files", courseId) }
    if ((index.scoresPath != null && scoresPath == null) || (index.filesPath != null && filesPath == null) ||
      (scoresPath != null && filesPath != null && scoresPath.semesterId != filesPath.semesterId)
    ) {
      return CourseChildren(
        failure = SyncOutcome.UnsupportedStructure("Course child links do not match the verified course and semester"),
        scoresFailed = index.scoresPath != null,
      )
    }

    var scores: ParsedCourseDetail? = null
    var files: List<ParsedCourseResource>? = null
    var failure: SyncOutcome? = null
    var scoresFailed = false
    if (scoresPath != null) {
      when (val fetched = fetchCourseChild(scoresPath.path, session, generation, courseDetailParser::parseScores)) {
        is CourseChildResult.Success -> {
          if (fetched.data.hasGradeSnapshot) scores = fetched.data
          else {
            scoresFailed = true
            failure = SyncOutcome.UnsupportedStructure("The linked scores page did not contain a verified grade snapshot")
          }
        }
        CourseChildResult.Empty -> {
          scoresFailed = true
          failure = SyncOutcome.UnsupportedStructure("The linked scores page did not provide a grade snapshot")
        }
        is CourseChildResult.Failure -> {
          scoresFailed = true
          failure = fetched.outcome
        }
      }
      if (failure is SyncOutcome.SessionExpired) return CourseChildren(failure = failure, scoresFailed = true)
    }
    if (filesPath != null) {
      when (val fetched = fetchCourseChild(filesPath.path, session, generation, courseDetailParser::parseResourcesPage)) {
        is CourseChildResult.Success -> files = fetched.data
        CourseChildResult.Empty -> files = emptyList()
        is CourseChildResult.Failure -> {
          if (fetched.outcome is SyncOutcome.SessionExpired || failure == null) failure = fetched.outcome
        }
      }
    }
    return CourseChildren(scores = scores, files = files, failure = failure, scoresFailed = scoresFailed)
  }

  private sealed interface CourseChildResult<out T> {
    data class Success<T>(val data: T) : CourseChildResult<T>
    data object Empty : CourseChildResult<Nothing>
    data class Failure(val outcome: SyncOutcome) : CourseChildResult<Nothing>
  }

  private suspend fun <T> fetchCourseChild(
    path: String,
    session: String,
    generation: Long,
    parse: (String) -> ParseResult<T>,
  ): CourseChildResult<T> {
    if (!isCurrentAcademicSession(session, generation)) return CourseChildResult.Failure(SyncOutcome.SessionExpired)
    val response = httpClient.fetchHtml(path, session)
    if (!isCurrentAcademicSession(session, generation)) return CourseChildResult.Failure(SyncOutcome.SessionExpired)
    val html = when (val fetched = response) {
      is HttpFetchResult.SuccessHtml -> fetched.html
      is HttpFetchResult.SessionExpired -> {
        if (isCurrentAcademicSession(session, generation)) sessionManager.handleServerSessionExpired(fetched.reason)
        return CourseChildResult.Failure(SyncOutcome.SessionExpired)
      }
      is HttpFetchResult.NetworkError -> return CourseChildResult.Failure(SyncOutcome.OfflineOrFailed(fetched.reason, isOffline = true))
      is HttpFetchResult.CloudflareBlocked -> return CourseChildResult.Failure(SyncOutcome.OfflineOrFailed(fetched.reason, isOffline = false))
      is HttpFetchResult.TemporarilyUnavailable -> return CourseChildResult.Failure(SyncOutcome.OfflineOrFailed(fetched.reason, isOffline = false))
      is HttpFetchResult.UnexpectedResponse -> return CourseChildResult.Failure(SyncOutcome.UnsupportedStructure(fetched.reason))
    }
    if (!isCurrentAcademicSession(session, generation)) return CourseChildResult.Failure(SyncOutcome.SessionExpired)
    return when (val parsed = parse(html)) {
      is ParseResult.Success -> CourseChildResult.Success(parsed.data)
      ParseResult.Empty -> CourseChildResult.Empty
      ParseResult.SessionExpired -> {
        if (isCurrentAcademicSession(session, generation)) sessionManager.handleServerSessionExpired("A linked course page requires authentication")
        CourseChildResult.Failure(SyncOutcome.SessionExpired)
      }
      ParseResult.TemporarilyUnavailable -> CourseChildResult.Failure(SyncOutcome.OfflineOrFailed("The linked course page is temporarily unavailable", isOffline = false))
      is ParseResult.UnsupportedStructure -> CourseChildResult.Failure(SyncOutcome.UnsupportedStructure(parsed.reason))
    }
  }

  private fun verifiedCoursePath(
    rawPath: String,
    kind: String,
    courseId: String,
    requireSemester: Boolean = true,
  ): CourseChildPath? {
    val resolved = BtuRoutes.resolveSafeBtuUrl(rawPath) ?: return null
    return try {
      val uri = URI(resolved)
      if (uri.rawQuery != null || !courseId.matches(Regex("[0-9]+"))) return null
      val suffix = if (requireSemester) "/([0-9]+)" else ""
      val matched = Regex("/ge/student/me/course/$kind/([0-9]+)$suffix/?").matchEntire(uri.rawPath) ?: return null
      if (matched.groupValues[1] != courseId) return null
      CourseChildPath(uri.rawPath, if (requireSemester) matched.groupValues[2] else null)
    } catch (_: Exception) {
      null
    }
  }

  private suspend fun existingEnrollmentStatus(courseKey: String): String? =
    courseDao.observeCourseWithEnrollment(courseKey).first()?.enrollment?.statusLabel

  private suspend fun isCurrentAcademicSession(session: String?, generation: Long): Boolean =
    sessionManager.currentSessionValue() == session &&
      preferencesStore.readAcademicDataGeneration() == generation && sessionManager.currentSessionValue() == session

  private fun detailStatus(failure: SyncOutcome?): String = when (failure) {
    null, SyncOutcome.Success, SyncOutcome.Empty -> "COMPATIBLE"
    is SyncOutcome.UnsupportedStructure -> "UNSUPPORTED_STRUCTURE"
    is SyncOutcome.OfflineOrFailed -> if (failure.isOffline) "OFFLINE" else "TEMPORARILY_UNAVAILABLE"
    SyncOutcome.SessionExpired -> "SESSION_EXPIRED"
  }

  private fun recordDetailFailure(failure: SyncOutcome, now: Long) {
    val status = when (failure) {
      is SyncOutcome.UnsupportedStructure -> ModuleCompatibilityStatus.PARSER_UPDATE_REQUIRED
      is SyncOutcome.OfflineOrFailed -> ModuleCompatibilityStatus.TEMPORARILY_UNAVAILABLE
      SyncOutcome.SessionExpired -> ModuleCompatibilityStatus.SESSION_EXPIRED
      else -> return
    }
    val message = when (failure) {
      is SyncOutcome.UnsupportedStructure -> failure.reason
      is SyncOutcome.OfflineOrFailed -> failure.message
      else -> "Sign in again to refresh course details"
    }
    if (failure is SyncOutcome.OfflineOrFailed && failure.isOffline) sessionManager.markOfflineFallback(true)
    compatibilityRegistry.update(BtuModuleId.COURSE_DETAILS, status, message, now)
  }

  fun decodeResources(resourcesJson: String?): List<CachedCourseResourceDto> {
    if (resourcesJson.isNullOrBlank()) return emptyList()
    return try {
      json.decodeFromString<List<CachedCourseResourceDto>>(resourcesJson)
    } catch (_: kotlinx.serialization.SerializationException) {
      emptyList()
    } catch (_: IllegalArgumentException) {
      emptyList()
    }
  }

  suspend fun clearAllAcademicData() {
    syncMutex.withLock {
      database.withTransaction {
        courseDao.deleteAllAcademicData()
        studentDao.deleteAllStudents()
        syncMetadataDao.clearAll()
        database.portalDao().clearAll()
      }
      compatibilityRegistry.resetAll()
    }
  }

  private suspend fun persistSelectedCourses(data: SelectedCoursesPageData, now: Long) {
    val studentKey = preferencesStore.readLastStudentKey()
    val currentSemRawKey = data.semesters.firstOrNull { it.isCurrent }?.semesterKey

    val summary = data.studentSummary
    studentDao.upsertStudent(
        StudentEntity(
          studentKey = studentKey,
          displayName = summary?.displayName,
          programName = summary?.programName,
          activeSemesterKey = currentSemRawKey?.let { "$studentKey::$it" },
          updatedAtMillis = now,
        ),
    )

    val semesterEntities = data.semesters.map { sem ->
      SemesterEntity(
        semesterKey = "$studentKey::${sem.semesterKey}",
        rawSemesterKey = sem.semesterKey,
        studentKey = studentKey,
        title = sem.title,
        sortOrder = sem.sortOrder,
        isCurrent = sem.isCurrent,
      )
    }

    val courseEntities = data.courses.map { item ->
      val semComposite = "$studentKey::${item.semesterKey}"
      val courseComposite = "$semComposite::${item.courseId}"
      CourseEntity(
        courseKey = courseComposite,
        courseId = item.courseId,
        studentKey = studentKey,
        semesterKey = semComposite,
        semesterName = item.semesterName,
        code = item.code,
        title = item.title,
        credits = item.credits,
        minCredit = item.minCredit,
        lecturer = null,
        syllabusSummary = null,
        detailPath = item.detailPath,
        resourcesJson = null,
        detailSyncedAtMillis = null,
        detailStatus = null,
      )
    }

    val enrollmentEntities = data.courses.map { item ->
      val semComposite = "$studentKey::${item.semesterKey}"
      val courseComposite = "$semComposite::${item.courseId}"
      EnrollmentEntity(
        enrollmentKey = courseComposite,
        studentKey = studentKey,
        semesterKey = semComposite,
        courseId = item.courseId,
        totalScore = item.score,
        statusLabel = item.statusLabel,
        updatedAtMillis = now,
      )
    }

    courseDao.replaceSelectedCoursesSnapshot(
      studentKey = studentKey,
      semesters = semesterEntities,
      courses = courseEntities,
      enrollments = enrollmentEntities,
    )
  }

  private suspend fun recordFailureSyncMeta(
    moduleId: String,
    now: Long,
    outcomeStatus: String,
    message: String?,
    expectedGeneration: Long,
    expectedSession: String?,
  ) {
    database.withTransaction {
      if (!isCurrentAcademicSession(expectedSession, expectedGeneration)) return@withTransaction
      val existingSuccess = findLastSuccessTimestamp(moduleId)
      syncMetadataDao.upsert(
        SyncMetadataEntity(
          moduleId = moduleId,
          lastAttemptAtMillis = now,
          lastSuccessAtMillis = existingSuccess,
          outcomeStatus = outcomeStatus,
          message = message,
        ),
      )
    }
  }

  private suspend fun findLastSuccessTimestamp(moduleId: String): Long? {
    return syncMetadataDao.getModule(moduleId)?.lastSuccessAtMillis
  }

  private fun encodeResources(resources: List<ParsedCourseResource>): String? {
    if (resources.isEmpty()) return null
    return json.encodeToString(resources.map { CachedCourseResourceDto(it.title, it.url) })
  }

  private suspend fun guardedSync(operation: suspend () -> SyncOutcome): SyncOutcome = syncMutex.withLock {
    try {
      operation()
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (_: Exception) {
      SyncOutcome.OfflineOrFailed("The local academic data could not be updated. Please try again.", isOffline = false)
    }
  }
}
