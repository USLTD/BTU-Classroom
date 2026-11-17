# BTU Classroom Mobile

A native, unofficial Android client for Business and Technology University's student portal. This implementation follows the [development plan](docs/BTUCM_Full_Application_Development_Plan.md) and uses the authentication and verified course contracts in `__reference__/btu-classroom-mobile`.

The `0.1.0` implementation uses authenticated, read-only portal contracts inspected on November 17, 2026. Its native scope includes courses and grades, weekly schedule, examinations, program information, academic calendar, academic card, remaining-course status, statements, and rating, with offline storage and settings. Course resources support user-triggered private downloads. A verification checkpoint passed 240 automated tests, 11 Android device tests, debug/release lint, and optimized unsigned APK/app-bundle builds. A subsequent enlarged-text navigation adjustment is awaiting its final recheck; the [verification record](docs/VERIFICATION.md) distinguishes that follow-up from the successful checkpoint. See the [portal contracts](docs/PORTAL_CONTRACTS.md) and [implementation status](docs/IMPLEMENTATION_STATUS.md) for source-data limits and remaining release work.

## Architecture

The application starts as one Gradle module with separate responsibilities:

| Package | Responsibility |
| --- | --- |
| `app`, `feature` | Navigation, Compose screens, ViewModels, lifecycle-aware state |
| `design` | Material 3 Expressive components, BTU color/shape/type tokens, light/dark themes |
| `core/auth`, `core/security` | On-demand GeckoView, bundled cookie bridge, verified sessions, Keystore encryption |
| `core/network`, `core/parsing` | Origin-restricted OkHttp requests, response classification, Jsoup parsers |
| `core/database` | Room source of truth, atomic snapshots, cached details and sync metadata |
| `core/portal`, `core/schedule` | Typed read-only module snapshots, verified page contracts, weekly class occurrences |
| `core/preferences` | DataStore settings and opt-in encrypted session persistence |
| `core/sync`, `core/notifications` | WorkManager refreshes, selected class reminders, permission-gated generic notices |
| `di` | Hilt bindings and application-scoped dependencies |

Normal data requests use OkHttp directly against BTU. GeckoView is created for interactive authentication and released after the captured session has been independently verified. Its dedicated browser storage is purged before each embedded login, after verified cookie transfer, and during sign-out/local clearing. A pending or failed purge blocks another embedded login and provides bounded feedback with official-site access. Room supplies cached content immediately; a failed refresh preserves the last successful snapshot and its timestamp. Unknown grades remain unknown rather than becoming zero.

GeckoView is pinned to `160.0.20261104181552`, selected from [Mozilla's release metadata](https://maven.mozilla.org/maven2/org/mozilla/geckoview/geckoview/maven-metadata.xml). Dependency updates should be reviewed and tested with the authentication bridge.

The UI supports English and Georgian, system/light/dark appearance, and navigation appropriate to wider screens. Its default Material 3 Expressive theme uses BTU's actual logo and accessible pink, blue, and amber tones; [asset provenance](docs/BRAND_ASSETS.md) and [design tokens](docs/DESIGN.md) are recorded. Both translations stay bundled for offline switching. Browser links follow the selected language; native fetching keeps the verified Georgian contracts, with known interface labels translated without rewriting university values. The app has no custom backend or analytics integration. Read the [privacy notice](docs/PRIVACY.md) before using a student account.

Portal data remains authoritative. Exam dates without a year are displayed as supplied and cannot produce a guessed calendar event or reminder. The observed academic-calendar response contains historical 2017–2018 events; those years are preserved. The remaining-course response has only a verified empty-state contract. Program information is view-only, and academic registration stays in the official portal. The inspection submitted no portal forms or program, payment, or enrollment changes.

Optional class reminders use inexact WorkManager scheduling for a selected future class in `Asia/Tbilisi`. Calendar export opens the calendar app's event editor for the user to review and save; the client does not write directly to calendars. Optional six-hour synchronization updates courses and previously cached supported modules. Downloads are authenticated, same-origin, limited to 50 MB, and initiated by the user rather than fetched automatically.

## Build and run

Requirements:

- Android SDK platform 37.2 (`platforms;android-37.2`) and Android SDK Build Tools; the local project uses Build Tools 36.0.0. GeckoView 160 requires compilation against API 37.2.
- Java 25 for the Gradle daemon, as configured in `gradle/gradle-daemon-jvm.properties`. Android source compatibility remains Java 17.
- Network access to Google Maven, Maven Central, Mozilla Maven, and Gradle distributions.
- Android 8.0/API 26 or newer to run the app. The app compiles against API 37.2 and targets API 37.

Open the project in a compatible Android Studio installation, or set `ANDROID_HOME`/`local.properties` to your SDK directory. Use the checked-in Gradle 9.9.1 wrapper.

Windows PowerShell: check the Java selected by your shell. On the development machine it initially selected Java 22; the build uses Android Studio's bundled Java 25. For a per-user Android Studio installation:

```powershell
$studioJbr = Join-Path $env:LOCALAPPDATA 'Programs\Android Studio\jbr'
if (!(Test-Path -LiteralPath $studioJbr)) {
    throw 'Set $studioJbr to the jbr directory inside your Android Studio installation.'
}
$env:JAVA_HOME = $studioJbr
& (Join-Path $env:JAVA_HOME 'bin\java.exe') -version
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Confirm that the displayed Java version is 25. If Android Studio is installed elsewhere or bundles another version, point `JAVA_HOME` to an installed JDK 25 instead. These commands affect only the current PowerShell session.

macOS/Linux:

```sh
chmod +x gradlew
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. Its application ID is `ge.usltd.btu_classroom`.

The requested Android CLI can inspect the environment and deploy the built APK:

```sh
android info
android run app/build/outputs/apk/debug/app-debug.apk
android layout
```

Connect an Android device with USB debugging enabled or start an emulator first. Authentication is interactive; do not put student credentials in build files, fixtures, command arguments, or CI variables. If Google rejects embedded sign-in, the app offers the official portal in an external browser. An external browser session is not automatically imported into the native app.

## Validation and releases

Tests cover sanitized parser fixtures, HTTP classification and URL restrictions, session-message handling, repository/cache behavior, and grade-change detection, with expanded-module, download, reminder, and Room migration checks added alongside their implementations. The database is now version 2 with an explicit migration from version 1 for typed JSON portal snapshots. The GitHub Actions workflow builds the debug APK, runs unit tests and lint, and retains the reports. CI does not sign a distribution release.

The [verification record](docs/VERIFICATION.md) contains commands, reports, device identity, and the scope of the successful checkpoint. Tests include the Room 1→2 migration, download and account-generation guards, notification delivery and permissions, local reminders, language/theme changes, and local-data clearing. Georgian course details and settings also have enlarged-text coverage. The final navigation recheck remains pending after the latest UI adjustment.

Live native university sign-in, authentication interruption/remote-logout scenarios, broader Android/OEM coverage, TalkBack, and performance testing remain release gates. Release signing must use the maintainer's own securely managed key; the optimized release artifacts are unsigned. Widgets remain optional and are not implemented. The app is not claimed as a completed 1.0 release.

BTU Classroom Mobile is not affiliated with or endorsed by Business and Technology University. University data and authentication policies remain authoritative.
