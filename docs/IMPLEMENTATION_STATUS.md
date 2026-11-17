# Development plan implementation status

This status maps the November 16, 2026 development plan to the current work. Authenticated portal pages were inspected read-only on November 17, 2026; their sanitized structural contracts now support expanded native implementation. No portal forms, program selections, payments, or academic registration changes were submitted. An observed contract and an implemented code path are distinct from a release-ready feature. The complete expanded source has not yet received a fresh build/test/lint/device pass.

## Native implementation scope

| Plan area | Current implementation | Remaining validation |
| --- | --- | --- |
| Phase 0: authentication | On-demand GeckoView and BTU-scoped WebExtension, cookie validation, independent HTTP verification, expiry recovery, dedicated browser-storage purge, awaited bounded local reset, remote logout verification, sanitized debug diagnostics | Native device sign-in and interruption/failure coverage; browser inspection used a user-authenticated portal session |
| Phase 1: foundation | Hilt, Compose/Material 3, Navigation Compose, ViewModel/StateFlow, Room, DataStore, restricted OkHttp client, Jsoup result classification, opt-in Keystore session persistence, English/Georgian settings | Full build/test/lint evidence and device coverage |
| Phase 2: selected courses | Native courses/details, search/semester selection, validated syllabus/score/file child navigation, nullable grades, authoritative server totals, refresh and offline/error states | Fresh tests for expanded detail contracts, blank grades, maximum-score metadata and non-numeric eligibility labels |
| Phase 3: timetable and examinations | Native weekly schedule from verified weekday/six-column rows; native exam table preserving its original date/time text; selected future class reminders and calendar-editor export | Fresh parser/device checks; exam reminders/export remain unavailable when the source omits a year |
| Phase 4: academic information | View-only program fields, original-year academic calendar, academic-card GPA/average and semester transcript, verified remaining-course empty state, historical rating | Fresh parser/cache/UI coverage; no inferred GPA, current-year calendar rewrite, program editing, or populated remaining-course parser |
| Phase 5: supported synchronization | Optional six-hour course and previously cached module refresh, network/battery constraints, bounded retries, generic score-change notices; optional inexact class reminder checks | Android scheduling/permission/device tests, stale-account guards and cancellation; no real-time or exact-delivery guarantee |
| Phase 6: secondary reads and documents | Statement metadata and validated read-only view links; user-triggered authenticated same-origin downloads limited to 50 MB, private cache and FileProvider opening; adaptive navigation | Fresh download/error/privacy checks and tablet testing; widgets not implemented |

Room remains the source of truth. Refreshes and clearing are serialized, successful snapshots and metadata are persisted atomically, cached details survive list refreshes, and errors retain the previous successful data. A verified empty semester can also be resumed offline. Fresh interactive login clears the previous account's academic cache because the reference does not establish a stable verified account identifier for profile isolation.

Database version 2 adds typed JSON portal snapshots with an explicit version 1→2 migration. The eight additional modules are schedule, examinations, program, calendar, academic card, remaining courses, statements, and rating. Each retains independent freshness and failure status. Downloads and reminder state must also be cleared on account/local clearing; generation checks prevent a prior account's reminder from notifying after that clear.

Authentication uses GeckoView `160.0.20261104181552`, pinned from Mozilla's release metadata. The dedicated browser runtime is purged before each embedded login, after verified native cookie transfer, and on sign-out/local clearing. The signed-out transition awaits a bounded reset attempt; a pending or unconfirmed purge blocks new embedded login and produces feedback with official-site access. A process killed during sign-in may leave browser data until the next embedded login purges it. Live interruption and cleanup-failure tests remain required.

GeckoView 160 requires `compileSdk` API 37.2, so local and CI environments install `platforms;android-37.2`. The app's target SDK remains 37 and its minimum remains 26.

## Verified contracts and remaining gates

The [portal contract record](PORTAL_CONTRACTS.md) describes the observed structures without retaining personal records or credentials. Remaining limits follow the data actually provided by BTU.

| Plan area | Gate before native implementation |
| --- | --- |
| Examination reminders/calendar export | The observed source supplies month/day and time without a year. A year must be supplied authoritatively before making a dated event or reminder |
| Current academic-calendar dates | The inspected event array contains 2017–2018 holidays. Preserve those dates and show their age; do not relabel them as current-semester events |
| Populated remaining courses | Only the exact “ინფორმაცია არ მოიძებნა” empty response was observed. A populated response remains unsupported until inspected |
| Academic progress calculations | Display the portal's GPA/average/transcript fields. Establish the university's formulas and complete source data before computing additional progress or GPA |
| Program changes, registration and other portal writes | Keep the native client read-only. Registration stays in the official portal under the user's explicit no-mutation requirement |
| Widgets and distribution | Widgets are optional and unimplemented; optimized release validation and private signing remain outstanding |

No portal form, program change, payment, academic-registration request, or registration retry is implemented. The portal inspection and native implementation use reads only. Opening an event editor is a user-reviewed calendar handoff, not a direct calendar write.

## Release work

The version is `0.1.0`; a complete 1.0 implementation is not claimed.

- Record fresh debug build, unit-test, lint, and real-device/emulator results.
- Exercise sign-in, blocked sign-in fallback, offline launch, an empty snapshot, session expiry, account switching, interrupted refresh, and remote logout failure.
- Kill the process during embedded sign-in; verify the next embedded login waits for storage purge. Exercise purge failure/timeout, blocked login, bounded feedback, official-site fallback, and delayed completion.
- Test large fonts, TalkBack, Georgian text, dark mode, wider screens, rotation, and process recreation.
- Validate background-job cancellation, notification permission denial, grade changes, selected-class reminders, generation guards, and Android scheduling limits.
- Verify exam dates without years remain undated text, historical calendar years remain unchanged, and unexpected populated remaining-course responses retain the prior cache.
- Verify download authorization, same-origin/no-redirect policy, 50 MB bounds, user initiation, FileProvider grants, and cache clearing.
- Review security and pinned dependency updates, test Android-version compatibility, and measure launch/performance/crash behavior.
- Verify the Room 1→2 migration against exported schemas and preserved academic data; do not use destructive production migrations to hide failures.
- Configure a private release signing key and verify the optimized release build. No signing credentials belong in the repository.
- Maintain the privacy notice and unofficial-affiliation disclosure as features change.

## Evidence record

Automated reports are produced by `:app:testDebugUnitTest` and `:app:lintDebug` under `app/build/reports/`. Debug APKs are produced by `:app:assembleDebug` under `app/build/outputs/apk/debug/`. The workflow retains these artifacts. Add the exact command, date, result, and device/build identifier to this section when the current implementation has been checked.

Earlier alpha evidence: the debug APK and lint checks passed; the last full alpha test run passed 73 of 74 tests. The localized-settings failure was fixed and a focused run then passed all 14 UI tests. That evidence predates the expanded native modules and does not constitute a fresh full-suite pass for them. Fresh expanded-source build, tests, lint, device checks, and optimized unsigned release validation remain pending.

The local Windows shell initially selected Java 22. Build execution requires Java 25; the README shows how to select Android Studio's bundled runtime for the current PowerShell session.
