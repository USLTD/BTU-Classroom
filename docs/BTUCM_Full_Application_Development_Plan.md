# BTUCM — Full Application Development Plan

**Project:** BTU Classroom Mobile (BTUCM)  
**Platform:** Android  
**Language:** Kotlin  
**Architecture:** Native, offline-first, direct HTTP integration  
**Authentication:** On-demand GeckoView + bundled WebExtension  
**Repository:** https://github.com/USLTD/btu-classroom-mobile  
**Status:** Authentication PoC completed; application development next  
**Plan date:** November 16, 2026

---

## 1. Product vision

BTUCM will be an unofficial Android client for Business and Technology University's BTU Classroom student portal.

Its objective is to make academic information easier, faster, and more convenient to access on mobile devices.

### Core principles

1. **Native-first:** Use Jetpack Compose for the application interface rather than displaying BTU's HTML pages.
2. **Browser-on-demand:** Use GeckoView for authentication, then release the browser session.
3. **No custom backend:** Communicate directly with BTU Classroom using the existing PHP application's HTTP endpoints.
4. **Offline-first:** Previously synchronized information remains usable without internet access.
5. **Privacy-first:** Student information and authentication credentials remain on the device.
6. **Resilient:** Gracefully handle BTU website changes, session expiration, and temporary semester restrictions.
7. **Accurate:** Display information derived from BTU's actual responses; never invent missing grades, schedules, or course availability.
8. **Safe transactions:** Require explicit confirmation and server-side verification for every academic registration change.

The application will initially support individual student accounts. Multiple local profiles may be added later.

## 2. Application features

### 2.1 Feature matrix

| Module | Features | Priority | BTU integration status |
|---|---|---|---|
| Authentication | Google login, session validation, logout | P0 | Verified PoC |
| Dashboard | Academic overview, upcoming classes and exams | P0 | Composed from other modules |
| Selected Courses | Course list, codes, credits, scores | P0 | Authenticated HTML verified |
| Course Details | Course information, grades, related resources | P0 | Detail URL pattern identified |
| Weekly Schedule | Timetable, classroom and lecturer information | P0 | Navigation route identified |
| Academic Calendar | Semester events and important dates | P1 | Navigation route identified |
| Examinations | Upcoming exam dates and information | P1 | Navigation route identified |
| Academic Programs | Program and curriculum information | P1 | Navigation route identified |
| Grades | Detailed scores and summaries | P1 | Partially visible in selected-courses table |
| Academic Card | Academic progress and records | P1 | Navigation route identified |
| Courses to Take | Remaining or planned courses | P1 | Navigation route identified |
| Statements | Student statements and records | P2 | Navigation route identified |
| Rating | University-provided rating information | P2 | Navigation route identified |
| Notifications | Changes to grades and schedules | P2 | Requires snapshot comparison |
| File Downloads | Available academic documents | P2 | Endpoints need investigation |
| Course Registration | Selection, review, submission | P3 | Actual forms not yet captured |
| Settings | Language, sync, privacy, appearance | P1 | Native implementation |

**Evidence rule:** A discovered navigation link is not proof that the corresponding authenticated page is functional or that its HTML structure is known. Each module needs its own inspection and parser validation.

### 2.2 Dashboard

The dashboard will become the application's primary screen.

Suggested content:

- Student identity and currently active semester.
- Next scheduled class, when available.
- Today's timetable.
- Upcoming examinations.
- Recently changed grades.
- Academic course overview.
- Last synchronization timestamp.

Use independently loading sections. If the exam endpoint fails, the timetable and course sections should continue working.

Never display a blank screen just because one module is unavailable.

### 2.3 Courses and grades

Courses are the first fully native feature to implement.

The existing verified endpoint is:

`GET https://classroom.btu.edu.ge/ge/student/me/courses`

Initially extract:

- Course code.
- Course name.
- Course detail identifier.
- Current score, if available.
- Minimum credit information.
- Credit value.
- Current semester, when identifiable.

Then investigate course-detail pages using the discovered pattern:

`/ge/student/me/course/index/{id}`

Detailed grades should preserve the university's actual grading categories and terminology.

Do not assume that a missing grade means zero. Represent unavailable grades using nullable values and explicit status labels.

If BTU provides grade components, calculate a local total only after determining the official grading formula. Display the university-reported result as authoritative.

### 2.4 Weekly schedule

Build a native schedule interface with:

- Day and week navigation.
- Class start and end times.
- Course name.
- Lecturer and classroom, if supplied.
- Lecture/seminar/laboratory classifications, where available.
- Current and upcoming class indicators.
- Calendar-style and list-style viewing.
- Local notifications before classes.

Use the `Asia/Tbilisi` time zone and retain original server timestamps where available.

Support cases where the timetable changes, the semester has not started, or no schedule has been published.

### 2.5 Examinations

Provide:

- Upcoming examination list.
- Course names.
- Examination date and time.
- Location, if provided.
- Exam type, if exposed.
- Calendar integration.
- Optional local reminders.

Treat BTU as the authoritative source. If an examination is rescheduled, update the local cache and clearly show the revised information.

### 2.6 Academic progress

Combine course, program, and academic-card data to provide progress indicators.

Potential metrics include earned credits, remaining credits, and grade averages, but only when sufficient source information exists.

Any locally calculated metric must be labeled as such.

Do not manufacture graduation progress or GPA values using assumptions about BTU's academic rules.

### 2.7 Course registration

Course registration is a later-phase, high-risk feature.

Implementation requirements:

- Retrieve the current registration page.
- Identify valid course options.
- Preserve server-issued form fields and CSRF values.
- Validate prerequisites, capacity, and semester status as represented by the server.
- Present an explicit confirmation screen.
- Submit once.
- Fetch the registered-course list again.
- Confirm that the requested change took effect.

If the server response is ambiguous, mark the operation **Unconfirmed**.

Never automatically retry registration submissions following network failures.

If BTU temporarily closes registration, display the restriction instead of attempting alternative endpoints.

---

## 3. Technical architecture

### 3.1 Technology stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Navigation | Navigation Compose |
| State management | ViewModel + StateFlow |
| Dependency injection | Hilt, or lightweight manual DI initially |
| Authentication browser | Mozilla GeckoView |
| Cookie bridge | Bundled WebExtension |
| HTTP | OkHttp |
| HTML parsing | Jsoup |
| JSON parsing | kotlinx.serialization |
| Database | Room / SQLite |
| Settings | DataStore |
| Background synchronization | WorkManager |
| Async operations | Kotlin Coroutines |
| Session encryption | Android Keystore-backed encryption |
| Image loading | Coil |
| Testing | JUnit, MockWebServer, Compose UI tests |
| Build | Gradle Kotlin DSL |
| CI/CD | GitHub Actions |

Do not introduce Retrofit unless the project develops enough structured JSON endpoints to benefit from it. For a largely HTML-based PHP portal, OkHttp plus dedicated parsers is sufficient.

### 3.2 Architecture diagram

```text
BTUCM Android Application
│
├── Presentation Layer
│   ├── Jetpack Compose Screens
│   ├── ViewModels
│   └── UI State
│
├── Domain Layer
│   ├── Course Models
│   ├── Schedule Models
│   ├── Academic Models
│   └── Use Cases
│
├── Data Layer
│   ├── CourseRepository
│   ├── ScheduleRepository
│   ├── ExamRepository
│   ├── ProfileRepository
│   ├── RegistrationRepository
│   └── SyncCoordinator
│
├── Local Storage
│   ├── Room Database
│   ├── DataStore
│   └── Secure Session Storage
│
├── Network Layer
│   ├── BtuHttpClient
│   ├── Session Interceptor
│   ├── Response Classifier
│   ├── HTML Parsers
│   └── Download Handler
│
└── Authentication Layer
    ├── GeckoAuthManager
    ├── GeckoExtensionMessageHandler
    ├── Bundled WebExtension
    ├── SessionRepository
    └── SessionVerifier
              │
              ▼
       BTU Classroom PHP
```

### 3.3 Recommended project structure

Start with one Gradle application module organized into clear packages. Split into separate Gradle modules only when complexity justifies the extra build overhead.

```text
app/src/main/
├── java/ge/btucm/
│   ├── app/
│   │   ├── MainActivity.kt
│   │   ├── BtuCmApplication.kt
│   │   └── AppNavigation.kt
│   │
│   ├── core/
│   │   ├── auth/
│   │   │   ├── GeckoAuthManager.kt
│   │   │   ├── SessionManager.kt
│   │   │   └── SessionVerifier.kt
│   │   ├── network/
│   │   │   ├── BtuHttpClient.kt
│   │   │   ├── BtuRoutes.kt
│   │   │   └── ResponseClassifier.kt
│   │   ├── parsing/
│   │   │   ├── CourseParser.kt
│   │   │   ├── ScheduleParser.kt
│   │   │   └── ParserResult.kt
│   │   ├── database/
│   │   │   ├── BtuDatabase.kt
│   │   │   ├── entities/
│   │   │   └── dao/
│   │   ├── sync/
│   │   └── security/
│   │
│   ├── feature/
│   │   ├── dashboard/
│   │   ├── courses/
│   │   ├── schedule/
│   │   ├── exams/
│   │   ├── academic/
│   │   ├── registration/
│   │   ├── settings/
│   │   └── diagnostics/
│   │
│   └── design/
│       ├── theme/
│       └── components/
│
├── assets/
│   └── btucm_ext/
│       ├── manifest.json
│       └── background.js
│
└── res/
```

Preserve the current PoC's functioning authentication classes while migrating them into this structure.

---

## 4. Authentication and session management

### 4.1 Production authentication flow

```text
User opens BTUCM
        |
        v
Check saved BTU session
        |
        +---- Valid ---> Native dashboard
        |
        +---- Invalid / Missing
                    |
                    v
             Open GeckoView
                    |
                    v
          Normal BTU Google login
                    |
                    v
             BTU OAuth callback
                    |
                    v
        WebExtension obtains argus_session
                    |
                    v
          OkHttp independently verifies
                    |
          +---------+---------+
          |                   |
        Valid               Invalid
          |                   |
          v                   v
     Store session       Show login/error
          |
          v
     Release GeckoView
          |
          v
     Native dashboard
```

### 4.2 Required improvements to the existing PoC

**Remove unnecessary OAuth-code collection.**

The current extension contains logic that extracts the Google authorization-code parameter from the BTU callback.

BTUCM does not require this code. Remove the extraction and storage functionality. Retain only a sanitized callback-detection event.

**Remove experimental User-Agent switching from release builds.**

Your successful test used the default GeckoView user-agent. The release application should not need the Google host permission or request-header modification used in the experiment.

**Use verified session states.**

Adopt the following session model:

```kotlin
sealed interface AuthState {
    data object Unknown : AuthState
    data object SignedOut : AuthState
    data object LoggingIn : AuthState
    data object Verifying : AuthState
    data object Authenticated : AuthState
    data object Expired : AuthState
    data class Error(val reason: String) : AuthState
}
```

Navigation to `/ge/student/` is not sufficient evidence of authentication. Only successful native verification may transition the state to `Authenticated`.

**Handle server-side expiration.**

BTU's `argus_session` is a session cookie and may not expose a fixed expiration timestamp.

Check server-side validity when necessary. Do not assume that a cookie remains valid just because it exists locally.

Do not treat the previous known-good cookie as a replacement for a session the server has invalidated.

### 4.3 Session persistence

Offer two session modes.

| Mode | Behavior |
|---|---|
| Session-only | Credentials remain in memory and require login again after process termination |
| Remember login | Session is encrypted and restored on subsequent launches |

For persistence, use an Android Keystore-managed key with authenticated encryption and application-private storage.

Exclude credentials from Android backups.

If the saved session becomes invalid, erase it and request reauthentication.

Do not persist Google OAuth authorization codes.

### 4.4 Logout

Implement both:

- **Sign out:** Invoke BTU's confirmed `/ge/login/logout` endpoint, clear credentials and GeckoView's BTU session state.
- **Clear local data:** Explicitly delete cached academic data and session material without implying remote logout.

For failed remote logout, erase local credentials for safety but report that remote session invalidation could not be confirmed.

### 4.5 Authentication compatibility contingency

The integrated GeckoView solution has passed your live test, but Google's embedded-browser policy remains a compatibility and distribution risk.

Do not promise permanent support based on a single successful device test.

If Google later rejects the browser, provide an ordinary external-browser option for accessing the official portal while investigating a compliant session handoff.

Do not use User-Agent spoofing or browser-security bypasses as the production fallback.

---

## 5. HTTP and HTML integration

### 5.1 Centralized HTTP client

Create a single `BtuHttpClient` responsible for:

- Attaching the authenticated `argus_session`.
- Enforcing HTTPS and approved BTU hosts.
- Session-expiration detection.
- Redirect classification.
- Connection and request timeouts.
- Response-size limits.
- Request cancellation.
- Safe logging.

Avoid allowing arbitrary URLs from remote HTML to become authenticated requests.

Only resolve relative links against an explicitly approved BTU origin.

### 5.2 Page parsing

Every page should have its own parser and response contract.

Example:

```kotlin
interface BtuPageParser<T> {
    fun parse(html: String): ParseResult<T>
}

sealed interface ParseResult<out T> {
    data class Success<T>(val data: T) : ParseResult<T>
    data object Empty : ParseResult<Nothing>
    data object TemporarilyUnavailable : ParseResult<Nothing>
    data object SessionExpired : ParseResult<Nothing>
    data class UnsupportedStructure(
        val reason: String
    ) : ParseResult<Nothing>
}
```

Use Jsoup for DOM extraction, not regular expressions as the primary HTML parser.

Identify tables using headings, semantic attributes, and recognizable links rather than CSS position alone.

A parsed empty list must be distinguishable from a missing or changed table.

### 5.3 Page compatibility

Maintain a `PageCompatibilityRegistry` that tracks the current parser's status for each BTU module.

Possible states:

- Compatible.
- No data available.
- Temporarily unavailable.
- Session expired.
- Unexpected HTML.
- Parser update required.

If BTU changes the selected-courses page, only that module should fail. The app should continue displaying unaffected cached information.

### 5.4 Site inspection backlog

The following pages should be inspected using authenticated, redacted HTML and Network captures:

| Route | Investigation target |
|---|---|
| `/ge/student/me/course/index/{id}` | Course details |
| `/ge/student/me/schedule` | Weekly timetable |
| `/ge/student/me/calendar` | Academic calendar |
| `/ge/exams/list` | Examination information |
| `/ge/student/me/program` | Academic program |
| `/ge/training/list` | Courses to take |
| `/ge/student/card` | Academic record |
| `/ge/student/rating` | Rating information |
| `/ge/student/me/statements` | Statements |

The routes were found in BTU navigation. They should be treated as inspection targets, not validated API contracts.

Do not automatically call the previously observed `/ge/student/me/updBalance` during generic synchronization: despite using GET, its name and JavaScript usage suggest an update-related operation requiring separate review.

---

## 6. Local database and offline support

### 6.1 Core entities

Create Room entities for:

| Entity | Purpose |
|---|---|
| `StudentEntity` | Authorized student profile |
| `SemesterEntity` | Academic period |
| `CourseEntity` | Course identity and metadata |
| `EnrollmentEntity` | Course participation and current result |
| `GradeEntity` | Grade components, where available |
| `ScheduleEventEntity` | Classes and timetable |
| `ExamEntity` | Examination dates |
| `ProgramEntity` | Academic program |
| `AcademicEventEntity` | Calendar events |
| `AttachmentEntity` | Downloaded resource metadata |
| `SyncMetadataEntity` | Refresh timestamps and outcomes |
| `PendingOperationEntity` | Explicitly initiated, unresolved write operations |

Use composite identifiers based on the student, semester, and BTU-provided resource identifiers to prevent unrelated records from overwriting one another.

### 6.2 Source of truth

The UI should observe Room using Kotlin `Flow`.

```text
Compose UI
    ↑
ViewModel
    ↑
Room Flow
    ↑
Repository
    ↑
BTU HTTP + HTML Parser
```

When the network is available, the repository refreshes its data and updates Room transactionally.

When offline, the UI continues observing Room.

### 6.3 Synchronization policy

| Trigger | Action |
|---|---|
| Application launch | Show cache immediately, refresh if needed |
| Pull to refresh | Fetch current module |
| App returns to foreground | Refresh stale important data |
| Background WorkManager job | Refresh eligible read-only modules |
| Network restored | Retry interrupted read synchronization |
| Login completed | Perform initial academic synchronization |
| Logout | Stop account-specific synchronization |

Use appropriate backoff and operating-system scheduling constraints. Do not promise exact minute-by-minute background updates.

Most importantly, background synchronization must never submit academic registration changes.

This aligns with Android's recommended offline-first repository and background-work model.

### 6.4 Cache presentation

Every cached screen should expose its data freshness.

Examples:

- Updated just now.
- Updated 2 hours ago.
- Offline — showing saved information.
- Semester data may be outdated.

Never silently present cached information as guaranteed live data.

---

## 7. UI and user experience

### 7.1 Navigation

Use four primary bottom-navigation destinations.

| Destination | Content |
|---|---|
| Home | Dashboard |
| Courses | Courses, grades, academic progress |
| Schedule | Timetable, exams, calendar |
| More | Programs, statements, settings, diagnostics |

Use nested navigation for course details and supporting academic screens.

### 7.2 Recommended screens

**Onboarding and login**

Minimal BTUCM branding, an explanation that the application is unofficial, a sign-in action, and a privacy notice.

**Home**

Overview cards, next class, academic summary, important upcoming events, and synchronization status.

**Courses**

Current and past semesters, course list, course details, grades, credits, and progress information.

**Schedule**

Daily and weekly timetable views, a date selector, class details, and calendar export.

**Exams**

Upcoming examinations, date/time information, and reminder settings.

**Academic**

Programs, academic card, course requirements, and other university-provided information.

**Settings**

Language, appearance, notifications, refresh policy, session management, data deletion, and about information.

**Diagnostics**

Separate development-focused screen exposing parser health, sanitized request outcomes, and authentication diagnostics.

### 7.3 Design language

Keep the current PoC's Material 3 foundation, but simplify the production interface.

Design requirements:

- Dark and light themes.
- Georgian and English localization.
- Responsive layouts for different phone sizes.
- Dynamic color support as an option.
- Clear typography and accessible contrast.
- Compact academic tables that work on small screens.
- Proper handling of long Georgian course titles.
- Tablet support later.

Avoid exposing developer-oriented cookie fields, User-Agent selectors, or HTTP telemetry on the normal dashboard.

### 7.4 Accessibility

Support font scaling, screen readers, meaningful content descriptions, large touch targets, and reduced animation.

Do not rely on color alone to distinguish academic statuses.

---

## 8. Notifications and device integrations

### 8.1 Notifications

Potential notifications include:

- New or changed grade.
- Upcoming class.
- Upcoming examination.
- Schedule modification.
- Academic deadline.
- Session requiring reauthentication.

Differentiate local reminders from server-change detection.

A class reminder is calculated locally using a known event time.

A grade-change notification requires another successful synchronization and comparison against the previous snapshot.

No notification should imply that the app receives instantaneous BTU updates.

### 8.2 Calendar integration

Provide optional export of schedule and exam events to the user's calendar.

Avoid duplicate event creation by maintaining a stable relationship between BTU event identifiers and exported calendar entries.

### 8.3 Widgets

Add Android home-screen widgets after the timetable data layer stabilizes.

Candidates include:

- Next class.
- Today's schedule.
- Upcoming exam.
- Academic countdown.

Widgets must operate from locally cached data and must not launch the login browser in the background.

---

## 9. Security and privacy

### 9.1 Credential handling

- Restrict session-cookie handling to the authentication and network layers.
- Remove Google OAuth-code capture from the current code.
- Never output session values into Logcat, crash reports, or diagnostic exports.
- Avoid storing complete authenticated HTML unless necessary.
- Disable Android backups for sensitive credentials.
- Use HTTPS with normal certificate validation.
- Avoid arbitrary cookie-domain inspection in release builds.
- Clear secrets when the user signs out.

### 9.2 Academic data

Student grades, schedules, documents, and personal profile information should remain in app-private storage.

For stronger local protection, evaluate database encryption and optional biometric app locking.

Keep analytics disabled by default. No third-party analytics SDK is necessary for the initial release.

### 9.3 Public repository hygiene

The GitHub repository should contain:

- Source code.
- Redacted test fixtures.
- Documentation.
- Automated tests.
- Build configuration.

It must not contain:

- Active session identifiers.
- Unredacted HAR captures.
- OAuth authorization codes.
- Google authentication cookies.
- Student profiles or grades.
- Release keystore passwords.

### 9.4 University compatibility

Identify the application as unofficial and unaffiliated with BTU unless authorization is obtained.

Use only functionality accessible to the authorized student.

Do not bypass semester locks, rate limits, authentication restrictions, prerequisites, or university-side registration rules.

---

## 10. Testing strategy

### 10.1 Parser tests

Create sanitized HTML fixtures for every supported page.

Test:

- Ordinary authenticated response.
- Empty table.
- Missing optional fields.
- Georgian and English layouts.
- Changed table order.
- Unexpected markup.
- Login page returned instead of data.
- Semester-disabled state.

Use a test to ensure every parser rejects structurally incompatible pages instead of returning misleading data.

### 10.2 Network tests

Use MockWebServer to simulate:

- HTTP 200 authenticated page.
- HTTP 302 login redirect.
- HTTP 401/403.
- Cloudflare challenge.
- Server error.
- Timeout.
- Partial connection failure.
- Unexpected content type.

### 10.3 Authentication tests

Validate:

- Fresh Google login.
- Existing valid session.
- App restart.
- Session expiration.
- Remote logout.
- Local reset.
- Cookie removal.
- Repeated login.
- Browser process release.
- Network interruption during OAuth.

The successful GeckoView test should be retained as a manual device regression test, without placing live credentials in CI.

### 10.4 UI tests

Test:

- Empty screens.
- Loading states.
- Offline screens.
- Error states.
- Long course names.
- Large fonts.
- Navigation restoration.
- Orientation changes.
- System dark/light theme.
- Android back navigation.

### 10.5 Performance targets

Treat these as goals to measure, not guaranteed benchmarks:

- Cached dashboard appears without waiting for networking.
- No browser engine is required during ordinary navigation.
- No unnecessary repeated HTML downloads.
- Database writes occur off the main thread.
- Large course lists scroll smoothly.
- Battery use remains controlled during background synchronization.
- No authenticated information appears in logs.

---

## 11. Development roadmap

### Phase 0 — Stabilize the authentication PoC

**Priority: Critical**

Tasks:

- Preserve the working GeckoView integration.
- Remove unnecessary OAuth-code capture.
- Remove release-build User-Agent overrides.
- Narrow extension permissions.
- Correct session-validity and expiration handling.
- Improve logout-result verification.
- Separate diagnostics from normal UI.
- Establish release package naming and signing strategy.
- Preserve current working tests.

**Acceptance:** Login, verification, session expiration, and logout work reliably without exposing credentials.

### Phase 1 — Establish the application foundation

**Priority: Critical**

Tasks:

- Introduce navigation and application-wide design components.
- Add Room and DataStore.
- Implement the centralized BTU HTTP client.
- Build the common parser framework.
- Define domain models and repositories.
- Add secure optional session persistence.
- Implement offline/error-state handling.
- Add English and Georgian localization.

**Acceptance:** A user can authenticate, open the native app shell, and resume using cached data.

### Phase 2 — Implement selected courses

**Priority: Critical**

Tasks:

- Parse the confirmed courses page.
- Store courses in Room.
- Display them natively.
- Implement semester grouping when supported.
- Add pull-to-refresh and offline states.
- Investigate and implement course details.
- Add parser fixtures and tests.

**Acceptance:** A student can view their current courses without opening GeckoView after login.

### Phase 3 — Implement schedules and exams

**Priority: High**

Tasks:

- Inspect the authenticated schedule and exam pages.
- Build parsers and Room entities.
- Implement daily/weekly timetable views.
- Implement the examinations screen.
- Add local reminders.
- Support calendar export.

**Acceptance:** A student can view the last synchronized timetable offline and receive locally scheduled reminders.

### Phase 4 — Implement academic information

**Priority: High**

Tasks:

- Programs and curriculum.
- Academic card.
- Detailed grading information.
- Academic calendar.
- Courses to take.
- Academic progress summary.

**Acceptance:** The main academic information available through the verified BTU pages is accessible in native screens.

### Phase 5 — Add synchronization and notifications

**Priority: Medium**

Tasks:

- WorkManager integration.
- Per-module refresh rules.
- Change detection.
- Notifications for supported changes.
- Connection-aware synchronization.
- Battery and network efficiency.
- Improved session-recovery behavior.

**Acceptance:** The application refreshes eligible information reliably within Android's scheduling constraints, without fabricating real-time availability.

### Phase 6 — Remaining read-only modules

**Priority: Medium**

Tasks:

- Statements.
- Rating.
- Document metadata and downloads.
- Advanced search and filtering.
- Optional widgets.
- Tablet interface improvements.

**Acceptance:** Supported secondary features have native interfaces, validated parsers, and correct offline behavior.

### Phase 7 — Academic registration

**Priority: High impact, gated**

Tasks:

- Inspect genuine registration forms.
- Document the exact request/response workflow.
- Create a read-only registration preview.
- Implement explicit confirmation.
- Preserve server validation and CSRF fields.
- Submit a single approved request.
- Verify resulting enrollment through a fresh read.
- Handle ambiguous outcomes without automatic retries.

**Acceptance:** Registration changes are possible only when the real server contract has been validated and the resulting state can be confirmed.

Until then, this module should open the university's official registration page rather than executing speculative requests.

### Phase 8 — Release preparation

**Priority: Critical before distribution**

Tasks:

- Final security review.
- Signed release builds.
- Compatibility testing across Android versions.
- Accessibility review.
- Documentation and privacy notice.
- Crash and performance testing.
- Reliable upgrade and database migration tests.
- Production/debug configuration separation.
- University affiliation disclaimer.

**Acceptance:** Release builds contain no experimental authentication controls, sensitive logs, or undocumented academic write operations.

---

## 12. Immediate development backlog

The first implementation work should be organized into small, independently reviewable changes.

| Order | Task | Outcome |
|---|---|---|
| 1 | Clean up authentication credentials and extension permissions | Safer production-ready auth foundation |
| 2 | Introduce `BtuHttpClient` | Reusable authenticated networking |
| 3 | Introduce Jsoup and `CourseParser` | Structured course extraction |
| 4 | Add Room course entities and DAO | Offline storage |
| 5 | Add `CourseRepository` | Network/database synchronization |
| 6 | Build native Courses screen | First real product feature |
| 7 | Introduce app navigation and Home | Usable application shell |
| 8 | Investigate schedule endpoint | Next supported module |
| 9 | Build timetable and offline cache | Second major product feature |
| 10 | Add settings, localization and sync controls | Daily-use foundation |

Do not begin implementing course-registration submissions or a large set of speculative HTML parsers before completing the first verified feature.

---

## 13. Definition of done

A feature is complete only when:

- Its BTU response structure has been inspected.
- Its parser has realistic, sanitized tests.
- It distinguishes valid, empty, unavailable, and unauthenticated responses.
- It has a native Compose interface.
- It handles connectivity loss correctly.
- It does not disclose private student data.
- It can recover from process recreation.
- It does not interfere with other modules.
- It has documented limitations.
- Its implementation passes the project build and test workflows.

## 14. Final target

BTUCM should feel like a native Android university application, not a miniature browser.

A student should be able to sign in, check their schedule, view courses and grades, receive useful reminders, and continue using previously synchronized information when offline.

GeckoView remains a private, on-demand authentication implementation detail. OkHttp handles normal HTTP requests, Jsoup transforms the university's HTML into structured data, Room maintains the local source of truth, and Compose presents the information in a mobile-native interface.

The application must remain transparent about its unofficial status, the freshness of its data, and any incompatibility introduced by BTU or Google's authentication policies.

**The primary next milestone is a real, native Selected Courses screen backed by the existing verified endpoint.**

---

## 15. Target release milestones

| Milestone | Scope |
|---|---|
| **BTUCM 0.1 — Functional alpha** | Authentication, dashboard shell, selected courses, supported course details, secure session management, basic offline caching |
| **BTUCM 0.5 — Daily-use beta** | Timetable, examinations, academic information, synchronization, reminders, localization, native navigation |
| **BTUCM 1.0 — Full student client** | Validated read-only modules, mature offline support, hardened authentication, reliable recovery, optional registration only after its live workflow is safely verified |

### Critical architectural decisions

1. **Keep authentication modular.** Preserve the integrated GeckoView/WebExtension approach already shown to work on the test device. Retain an alternative path to the official site if Google changes embedded-browser restrictions.
2. **Make Room the UI's source of truth.** Present cached content immediately and synchronize safely in the background; do not block rendering on network calls.
3. **Build verified features first.** Prioritize the selected-courses page before implementing hypothetical endpoints or high-risk registration workflows.
4. **Preserve privacy and honest status reporting.** Data freshness, login validity, and uncertain server actions must remain explicit.

### Reference links

- Source repository: https://github.com/USLTD/btu-classroom-mobile
- BTU Classroom: https://classroom.btu.edu.ge/ge/
- GeckoView: https://mozilla.github.io/geckoview/
- Android offline-first guidance: https://developer.android.com/topic/architecture/data-layer/offline-first
- Google OAuth policy: https://developers.google.com/identity/protocols/oauth2/policies
