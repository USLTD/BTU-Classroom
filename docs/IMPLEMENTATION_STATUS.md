# Development plan implementation status

This record maps the November 16, 2026 development plan to the November 17 implementation. Authenticated portal pages were inspected read-only and represented by sanitized structural fixtures. No university forms, program selections, payments, academic registration changes, or remote logout were exercised during verification. The attached Library file and reference source remain unchanged.

The successful checkpoint includes 240 automated tests, 11 Android device tests, debug/release lint, and optimized unsigned APK/app-bundle builds. A later adjustment to enlarged-text navigation is awaiting a final build and device recheck. See [verification evidence](VERIFICATION.md) for the distinction; the newest source is not yet claimed fully rechecked.

## Native implementation scope

| Plan area | Implemented behavior | Evidence and remaining limits |
| --- | --- | --- |
| Phase 0: authentication | On-demand GeckoView, BTU-scoped WebExtension, cookie validation and independent native verification, expiry recovery, optional Keystore session storage, bounded browser purge and local cleanup, sanitized diagnostics | Automated URL, response, bridge, persistence and cleanup tests pass. Live native sign-in, interrupted sign-in, browser-purge failure/timeout and remote-logout scenarios remain untested against a university account |
| Phase 1: foundation | Hilt, typed Compose navigation, ViewModel/StateFlow, Room, DataStore, restricted OkHttp, Jsoup, Kotlin serialization, English/Georgian settings | Debug and optimized release checkpoint builds pass. Genuine Material 3 Expressive APIs, BTU logo/colors, light/dark themes, and adaptive navigation are implemented; final enlarged-navigation recheck is pending |
| Phase 2: selected courses | Courses/details, search and semester filters, validated linked score/resource pages, nullable scores, authoritative totals, private document access, explicit freshness and retained offline data | Parser, repository, cache, migration and download tests pass. Source values, course names and lecturer names remain as supplied by BTU |
| Phase 3: timetable and examinations | Native weekly timetable, day/week/date navigation, room/group/lecturer metadata, selected future-class reminders, calendar-editor handoff, native exam table | Parser/UI/occurrence tests and real Android reminder checks pass. Exam dates omit a year, so no guessed exam calendar event or reminder is produced |
| Phase 4: academic information | View-only program fields, original-year academic calendar, academic-card GPA/average/transcript, verified remaining-course empty state, historical rating | Parser/cache/UI tests pass. No inferred GPA formula, rewritten calendar years, program editing, or populated remaining-course parser |
| Phase 5: supported synchronization | Optional six-hour course/previously cached module refresh, network/battery constraints, bounded retry, generic academic alerts, optional inexact class reminders | Change detection, permissions, delivery, tap intent, cancellation and generation guards are tested. Android/OEM scheduling can delay delivery; no real-time or exact-alarm promise |
| Phase 6: secondary reads and documents | Statement metadata and validated view links; user-triggered authenticated same-origin downloads up to 50 MB; private cache/FileProvider opening; responsive navigation | All 19 downloader tests and four unshadowed Android FileProvider tests pass. Widgets remain optional and unimplemented |

## Architecture and identity

The app uses one Gradle module with separate `app`, `feature`, `design`, `core` and `di` responsibilities. Room is the source of truth. Refreshes and clearing are serialized; successful snapshots and metadata are committed atomically; errors preserve the last successful content and timestamp. Fresh interactive login clears the previous account's academic cache because a stable verified account identifier for multiple local profiles has not been established.

Database version 2 adds typed JSON snapshots for schedule, exams, program, calendar, academic card, remaining courses, statements and rating through an explicit 1→2 migration. Account generations prevent late refreshes, cached files or reminders from resurfacing after local clearing.

Material 3 is explicitly pinned to `1.5.0-rc01` for public `MaterialExpressiveTheme` and `MotionScheme.expressive()` APIs. Shared color, type, shape and state components use BTU's pink identity with accessible blue/amber tonal roles. The actual official logo paths are retained in Android vectors; see [asset provenance](BRAND_ASSETS.md) and the [design guide](DESIGN.md).

Both translations remain bundled for offline switching. Browser links and authentication observation support `/ge/` and `/en/`. Native academic reads keep the verified Georgian contracts: authenticated English academic layouts have not been inspected. Known interface labels and semester headings translate at display time without changing cached records or generating language-only change alerts. Unknown text and official academic values remain verbatim. Known sign-in failures use translated guidance instead of raw diagnostic text.

GeckoView `160.0.20261104181552` requires compile SDK 37.2. Target SDK is 37, minimum SDK 26, and the Gradle daemon uses Java 25. Dedicated browser storage is purged before embedded login, after verified cookie transfer, and during local sign-out/clearing. A pending or unconfirmed purge blocks another embedded login and offers recovery guidance.

## Source-data and authorization boundaries

| Area | Current boundary |
| --- | --- |
| Exam dates | Month/day and time are retained without inventing a year |
| Academic calendar | Observed 2017–2018 events retain their years and an archive notice |
| Remaining courses | Only the exact verified empty response is supported; unknown populated content preserves the prior cache |
| Academic progress | Display BTU's GPA, average and transcript fields; no unverified university formula is calculated |
| Portal mutations | Native academic access remains read-only. Registration stays in the official portal; no academic submission or retry is implemented |
| Calendar export | `ACTION_INSERT` opens the user's event editor; the client does not write directly to calendars |
| Distribution | Optimized unsigned artifacts exist at the checkpoint. Private signing and optional widgets remain outstanding |

## Remaining release work

- Complete the final enlarged-text navigation build/test/lint and device recheck.
- Exercise live native sign-in, blocked sign-in recovery, interruption, browser-purge failure/timeout, account switching and remote-logout failure with an interactive account session.
- Broaden Android/OEM testing, TalkBack, rotation/process recreation, and launch/performance/crash measurement. Existing enlarged-text and tablet captures do not replace those checks.
- Review pinned dependency updates and configure the maintainer's private release signing key. No signing credentials belong in the repository.

The version remains `0.1.0`; a completed 1.0 release is not claimed. Detailed commands and current evidence are in [VERIFICATION.md](VERIFICATION.md).
