# Paused after final verification

The November 17, 2026 work added English/Georgian translations, verified notifications, the BTU logo/colors, Material 3 Expressive, and UI/UX improvements. Final verification is complete for this checkpoint. The user's latest request is to push the changes and pause. Do not resume implementation or additional checks until the user asks.

The emulator is stopped, its original display/font settings are restored, and the Gradle daemon is stopped. The final Git push result is reported in the chat.

## Constraints

- University portal actions remain strictly read-only. Do not submit forms, alter programs or registrations, make payments, or mutate university records.
- Local implementation, builds and checks are authorized. Android CLI has been used for documentation, SDK/emulator management, deployment, layout inspection and screen captures.
- The attached Library plan and `__reference__/btu-classroom-mobile` remain unchanged.
- Preserve other contributors' edits. Build/emulator work is serialized by the root agent to avoid memory pressure on the 16 GB machine.

## Implementation

Native courses/grades/resources, weekly timetable, exams, academic program/calendar/card, remaining-course status, statements and rating use Room-backed snapshots. The app includes Hilt, Compose, typed navigation, DataStore, optional Keystore session persistence, guarded on-demand GeckoView authentication, private downloads, optional background updates and selected-class reminders.

The UI uses actual Material 3 Expressive APIs, the official BTU logo and accessible BTU tonal colors. English/Georgian resources, known cached interface labels, browser routes and sign-in recovery messages are paired. Native data reads remain canonical Georgian until authenticated English academic contracts are verified. Official names, scores and unknown university text are preserved.

## Final verified checkpoint

- 28 suites / 240 automated tests, zero failures, errors or skipped tests.
- All seven final targets passed in `build/verification/final-checks.log`: automated tests, debug lint, debug APK, Android test APK, optimized unsigned release APK, app bundle and release lint. Both lint reports contain zero errors, 38 warnings and one hint.
- Android 16/API 36 emulator: six notification tests, four private FileProvider tests and one navigation/language/theme/local-clearing journey were rerun successfully against the final debug APK.
- Real Compose fixture captures cover native modules, light/dark modes, Georgian text, tablet widths and 200% course/settings text. Full-device phone/tablet captures are under `build/verification/`.

Evidence paths, commands and final artifact SHA-256 hashes are recorded in [VERIFICATION.md](VERIFICATION.md).

## Resolved final follow-up

The full-device 200% Georgian review exposed crowded bottom-navigation labels. `AppNavigation.kt` now shows the selected caption at enlarged sizes, keeps captions to one line, and supplies full spoken names for other navigation icons. The final seven-target build and all 11 Android tests passed after this change. The regenerated `device-more-ka-large-font.png` and matching layout show the corrected navigation; two independent visual reviews passed with high confidence and no blocking product or evidence findings.

The prior migration binding/import blockers and lint failures are resolved. Downloader tests needed two test-only host adaptations: AndroidX's `/` descendant check versus Windows separators, and real per-test `FileProvider.attachInfo` initialization to invalidate roots retained from earlier Robolectric directories. All 19 downloader tests now pass; the four Android provider tests exercise production restrictions without those host adaptations. No production file-sharing root was broadened.

## Remaining external validation

Live native university sign-in, interrupted authentication, browser-purge failure/timeout and remote logout were not exercised. No portal mutations were performed. Missing exam years, archived 2017–2018 calendar dates and the unsupported populated remaining-course contract remain deliberate limits. Release artifacts are unsigned; private signing and optional widgets remain outstanding.

## Local tools

- Java 25: `%LOCALAPPDATA%\Programs\Android Studio\jbr`; the default shell initially selected Java 22.
- Compile SDK 37.2, target SDK 37, minimum SDK 26; Gradle 9.9.1.
- Emulator `medium_phone`, Android 16/API 36, is stopped. Baseline settings are restored to 1080×2400, density 420 and font scale 1.0. Do not restart it until the user resumes work.
- MockWebServer tests use `http://127.0.0.1:${server.port}` explicitly to avoid the host's reverse-DNS name conflicting with strict origin checks.
