# Verification record — November 17, 2026

This record describes the final expanded-app checks, including the enlarged-text navigation adjustment. All seven build/check targets and all 11 Android tests passed on the final source/artifact. Complete phone/tablet and 200% Georgian navigation reviews passed, including two independent visual reviews with no blocking findings. All university access remained read-only, and all test academic content is synthetic. Live-account and distribution limits remain listed below.

## Build and automated checks

Environment: Windows, Android Studio's Java 25 runtime, Gradle 9.9.1, compile SDK 37.2, target SDK 37, minimum SDK 26.

| Check | Final result | Evidence |
| --- | --- | --- |
| Debug build and Android test APK | Passed | `build/verification/final-checks.log` |
| Automated tests | 28 suites, 240 tests, 0 failures/errors/skipped | `app/build/test-results/testDebugUnitTest/`; HTML at `app/build/reports/tests/testDebugUnitTest/index.html` |
| Debug lint | Passed: 0 errors, 38 warnings, 1 hint | `app/build/reports/lint-results-debug.xml` and `.html` |
| Optimized unsigned release APK | Passed | `build/verification/final-checks.log` |
| Release app bundle | Passed, unsigned | `build/verification/final-checks.log` |
| Release lint | Passed: 0 errors, 38 warnings, 1 hint | `app/build/reports/lint-results-release.xml` and `.html` |

The final combined run completed successfully in 8m 40s with `JAVA_HOME` pointing to Java 25:

```powershell
$env:JAVA_HOME = "$env:LOCALAPPDATA\Programs\Android Studio\jbr"
$env:LINT_PRINT_STACKTRACE = 'true'
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease :app:bundleRelease :app:lintRelease --max-workers=1 --continue --console=plain
```

The remaining lint warnings include dependency-update notices, unused resources and API/style suggestions; passing lint does not mean zero warnings. No lint check was disabled to obtain the result.

Automated coverage includes sanitized portal parsing, supported/unsupported/empty structures, origin and redirect restrictions, bilingual login detection, cache preservation and account generations, Room 1→2 migration, session cleanup, file-size/MIME/path restrictions, notification change detection, reminder occurrence validation, and native Compose screens.

The 19 downloader tests use a narrow test-only compatibility shadow for AndroidX's Unix separator assumption on Windows. Each test initializes the real FileProvider lifecycle to discard cached roots from previous Robolectric directories. Production roots and grants are unchanged; the Android tests below exercise the real provider without that host shadow.

## Android device checks

- Emulator: `medium_phone`, serial `emulator-5554`.
- Android 16, API 36, x86_64.
- Fingerprint: `google/sdk_gphone64_x86_64/emu64xa:16/BE2A.250530.026.D1/13818094:user/release-keys`.
- Installed with Android CLI. The disposable app install was cleared before permission and navigation scenarios.

| Test class | Result | What was exercised | Evidence |
| --- | --- | --- | --- |
| `NotificationDeliveryTest` | 6 passed in 6.164s | Initially denied permission; actual academic notification/channel/content/icon; tap opening the app; production reminder worker; account-generation and changed-occurrence suppression; scheduler persistence/cancellation | `build/verification/device-notifications.log` |
| `PrivateFileProviderTest` | 4 passed in 0.064s | Private provider metadata, exact PDF byte read through a content URI, outside/sibling root rejection, canonical and encoded traversal rejection | `build/verification/device-file-provider.log` |
| `AppJourneyTest` | 1 passed in 10.596s | Explore, courses search, native schedule, More/settings, dark theme, Georgian/English switching, and confirmed local clearing | `build/verification/device-journey.log` |

All 11 tests were rerun against the final debug APK identified below. They use local data and do not contact or mutate university records. The notification class must run alone after revoking `POST_NOTIFICATIONS`, before instrumentation starts; subsequent tests grant the permission. Revoking it during the test process can terminate that process.

```powershell
adb -s emulator-5554 shell pm clear ge.usltd.btu_classroom
adb -s emulator-5554 shell pm revoke ge.usltd.btu_classroom android.permission.POST_NOTIFICATIONS
adb -s emulator-5554 shell am instrument -w -e class ge.usltd.btu_classroom.NotificationDeliveryTest ge.usltd.btu_classroom.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 shell am instrument -w -e class ge.usltd.btu_classroom.PrivateFileProviderTest ge.usltd.btu_classroom.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 shell pm clear ge.usltd.btu_classroom
adb -s emulator-5554 shell am instrument -w -e class ge.usltd.btu_classroom.AppJourneyTest ge.usltd.btu_classroom.test/androidx.test.runner.AndroidJUnitRunner
```

Only use the clearing commands against the disposable test install. Build and install the current app/test APKs successfully before starting these commands.

## Final visual evidence

`app/build/outputs/qa/` contains 51 actual Compose captures using sanitized academic fixtures. They cover primary and academic pages, cached/error/empty states, English/Georgian, light/dark themes, tablet widths, and 200% Georgian course-detail/settings text. They are rendered native controls, not reference images embedded into the app.

Full Android captures under `build/verification/` include:

- `device-onboarding-en.png`, `device-home-phone.png`, `device-schedule-phone.png`.
- `device-settings-ka-large-font.png` at 200% text size.
- `device-more-tablet.png` with the complete navigation rail and app bar.
- `device-more-ka-large-font.png`, regenerated at 23:55 local time after the final navigation adjustment, with no crowded or overflowing bottom labels.
- `device-layout-more-ka-large-font.json`, whose navigation items retain the full spoken Georgian names for all four destinations.

The course metadata layout was changed to stack at narrow widths or enlarged text. Tests require complete text layout and at least 280 dp for the enlarged metadata value. The light error badge contrast was corrected, and app-authored authentication failures now use translated guidance.

The final build/test/lint/release and device recheck passed after the `AppNavigation.kt` change. At enlarged sizes, only the selected caption is shown, captions stay on one line, and other navigation icons retain full spoken destination names. Two independent visual reviews inspected the fresh navigation capture and layout and returned PASS with high confidence, with no blocking product or evidence findings.

## Artifacts and remaining release gates

| Artifact | Path | SHA-256 |
| --- | --- | --- |
| Installable debug APK | `app/build/outputs/apk/debug/app-debug.apk` | `A643E43550F2AE0ED6136E4D25C19BF02662A4876B88161D5D7859F41E7D1911` |
| Unsigned optimized APK | `app/build/outputs/apk/release/app-release-unsigned.apk` | `71AF799C260AA615B2CA54AA2FA15813EBBB5C10D8067B8609663D7C64976CDC` |
| Unsigned app bundle | `app/build/outputs/bundle/release/app-release.aab` | `6FDBA1AF24633B2C913BEA3FEB9933CAAB942749311A1338D9C256A63DA76A18` |

Release signing is not configured; distribution requires the maintainer's private key.

Live native university authentication, interrupted sign-in/browser-purge failure, remote logout, TalkBack, broader Android/OEM coverage, and performance measurement remain outside this checkpoint. Authenticated English academic layouts remain unverified, so native reads keep canonical Georgian contracts while known interface text translates. Missing exam years, archived calendar dates, unsupported populated remaining courses, and optional unimplemented widgets remain documented in [implementation status](IMPLEMENTATION_STATUS.md).
