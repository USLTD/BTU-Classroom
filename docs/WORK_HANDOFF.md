# Paused work handoff

Paused at the user's request on November 17, 2026 (Asia/Tbilisi). Preserve all current changes. Do not resume implementation or checks until the user asks.

## User constraints

- Implement the attached development plan using `__reference__/btu-classroom-mobile` and a modern modular Kotlin stack with dependency injection.
- Use the android-cli skill. It was used for documentation, SDK installation, emulator creation and lifecycle work.
- University portal access is strictly read-only. The user clarified that local implementation and builds are permitted. Do not submit forms, change programs or registrations, make payments, or alter university records.
- The attached Library file and the reference source have not been edited.

## Current implementation

- Hilt, Compose/Material 3, typed navigation, ViewModel/StateFlow, Room v2 with a v1-to-v2 migration, DataStore, OkHttp, Jsoup and Kotlin serialization.
- On-demand GeckoView authentication and restricted WebExtension; optional Keystore-encrypted persistence, independent verification, guarded cleanup and account isolation.
- Courses and linked grade/resource pages, search and semester filters, native timetable, examinations, program information, academic calendar, academic record, remaining-course status, statements and rating.
- English/Georgian, light/dark themes, adaptive navigation, explicit cached/error states and absolute Tbilisi sync times.
- Optional background synchronization, class reminders, calendar-app handoff, private authenticated document downloads and offline document access.
- Sanitized parser fixtures, repository/migration/security/worker/UI tests, privacy and portal-contract documentation, and CI.

The signed-in portal was inspected read-only. Its exam dates omit the year, its calendar publishes historical 2017-2018 dates, and the observed remaining-course response is empty. The app preserves these limits instead of inventing data. Academic mutations and populated remaining-course parsing remain gated.

## Verification state

- Earlier alpha debug assembly and lint passed. A 74-test run passed 73 tests; the remaining localized-settings context issue was then fixed, and all 14 focused UI tests passed.
- The expanded source subsequently compiled its main debug and release Kotlin/Java code. The latest run reached `:app:assembleDebug` successfully.
- The expanded implementation is **not fully verified**. The latest full run was stopped on request before completion.
- Latest log: `build-verification-stable.log`. Earlier evidence: `build-ui-verification.log`, `build-verification-loopback.log`, `build-verification-expanded.log`.
- Current debug APK: `app/build/outputs/apk/debug/app-debug.apk`; do not present it as fully validated.

## First issues to resolve on resume

1. `PortalMigrationTest.kt` has Kotlin reified intersection-type inference errors at heterogeneous SQL binding arrays (reported at lines 63, 67, 75, 83, 87 and 91). Use explicitly typed binding arrays and rerun the migration test.
2. `PortalScreensTest.kt:17` imports unresolved `onAllNodes`; align the import/call with the installed Compose test API.
3. An earlier expanded run crashed inside lint's Kotlin FIR analysis of `BtuFileDownloader.kt` while source was changing. The final stable lint run was interrupted. Recheck with `LINT_PRINT_STACKTRACE=true`; diagnose any repeat without disabling lint.
4. Rerun the full expanded tests, debug assembly/lint, instrumentation APK and optimized release assembly after fixes. Confirm the latest cleanup/download generation guards in the resulting build.
5. Perform fresh visual review of every native screen and the emulator navigation journey. Old screenshots under `app/build/outputs/qa` are partial evidence, not a final pass.

## Local tools and restart

- Use Java 25: the local Android Studio runtime is `%LOCALAPPDATA%\Programs\Android Studio\jbr`. The normal shell initially selected Java 22.
- Compile SDK is Android 37.2, target SDK 37, minimum SDK 26. GeckoView is pinned to `160.0.20261104181552`.
- MockWebServer tests must use `http://127.0.0.1:${server.port}` explicitly. This machine reverse-resolves the default local hostname to another name, which the strict origin guard correctly rejects.
- Emulator `medium_phone` (Android 16/API 36) is stopped with a saved `default_boot` snapshot. Restart with `android emulator start medium_phone --headless`.
- The machine has 16 GB RAM; running the emulator alongside compilation caused heavy paging. Build workers are limited to two. Prefer finishing tests before restarting the emulator.
- All delegated agents had completed when paused. No new background implementation tasks were left active.

Suggested verification command after the user resumes and the test compilation issues are fixed:

```powershell
$env:JAVA_HOME = "$env:LOCALAPPDATA\Programs\Android Studio\jbr"
$env:LINT_PRINT_STACKTRACE = 'true'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest :app:assembleRelease --continue --console=plain
```

Production signing keys have not been created or configured. Live native authentication, remote logout and academic writes were not exercised against the university account.
