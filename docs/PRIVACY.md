# Privacy notice

BTU Classroom Mobile is an unofficial Android client for Business and Technology University's portal. It is not affiliated with or endorsed by the university. The university's portal and Google control their own services and authentication policies.

## Information used

The app retrieves academic information you are authorized to view: available student/program labels, selected courses, semesters, university-reported scores, supported course details/resources, weekly classes, examinations, academic-calendar events, academic-card records, remaining-course status, statement metadata, and rating. It stores parsed records and freshness metadata in a Room database inside Android's app-private storage so they can be viewed offline. Additional modules use typed JSON snapshots; the cache does not retain raw portal pages.

The academic-card parser allows academic program, GPA/average, and semester-transcript fields. It deliberately excludes identification numbers, dates of birth, and administrative orders from the native academic-card cache. Statement metadata may contain personal information; it receives the same app-private storage protection as other cached records.

Interactive sign-in takes place in an on-demand GeckoView browser on the portal's authentication pages. The app does not provide its own password form, save passwords, or prefill credentials. The bundled extension reads the BTU session cookie and passes it to the native app for independent verification. Authentication pages and identity providers may process the information entered into their own pages according to their policies.

## Session storage

Remembering login is optional and disabled by default. Without it, the native session cookie is held in memory. When enabled, the cookie is encrypted with an Android Keystore-backed AES-GCM key before it is saved in DataStore. If decryption fails or the server rejects the session, a new sign-in is required.

The dedicated GeckoView runtime has its own browser cookies and storage. The app purges this storage before every embedded login, after independently verified transfer of the cookie to the native client, and on sign-out or local clearing. If Android kills the process during sign-in, browser data may remain until the next embedded login, when it is purged before navigation. Session-only mode therefore means the native session is memory-only; it cannot guarantee that an interrupted browser sign-in leaves no data on disk.

Browser cleanup has a bounded wait and user feedback. Another embedded login is blocked while cleanup remains pending or cannot be confirmed; the official-site fallback remains available. Browser-storage cleanup failures require retry or recovery rather than silently reusing the prior browser state.

Academic records in Room are not separately encrypted by this app. They depend on Android's app isolation, device encryption, and the device's screen lock. Someone who can unlock your device may be able to open the app and read cached information. The app does not add a separate biometric or app-lock gate.

The manifest disables app backup, and backup/device-transfer rules exclude app data. These protections do not make a compromised or rooted device safe.

## Network requests and diagnostics

The app communicates directly with BTU over HTTPS for supported academic reads and logout. It has no custom data backend, advertising SDK, or analytics integration. GeckoView also contacts the authentication services needed for interactive sign-in. Opening the official portal or a resource in another app subjects that interaction to the selected browser/app's own storage and privacy behavior.

Program information is displayed read-only. The native client does not submit portal forms, program selections, payment operations, or academic-registration changes. Authenticated portal inspection used to establish the page contracts was also read-only.

Diagnostic controls are restricted to debug builds. Session and token values are redacted from diagnostic messages, and normal academic requests do not log response bodies. Do not share raw portal HTML, cookies, authentication URLs, screenshots, or academic records when reporting a problem; use sanitized examples.

## Background updates and notifications

Background updates and notifications are optional and disabled by default. Background refresh requires remembered login and schedules eligible work approximately every six hours when Android's network and battery conditions permit it. It refreshes courses and previously cached supported modules. Android may delay this work; it is not a live alert service.

If enabled and allowed by Android's notification permission, notices report that course scores changed without placing student names, course names, or grades in the notification. A notice is generated only for a changed existing score, not the first synchronization.

Optional class reminders are selected for a future class occurrence in `Asia/Tbilisi` and scheduled through a one-time WorkManager request, by default 15 minutes before class. Android may delay delivery; the app does not use an exact alarm. Reminders use generic notification text and require notification permission. Account/local clearing cancels or invalidates prior reminder state; a generation guard prevents an old account's reminder from notifying afterward. Exam reminders are not created from source dates that omit a year.

Calendar export opens the device calendar app's event editor using `ACTION_INSERT`. The user chooses whether and where to save the event. The client requests no calendar write permission and does not write directly to the calendar provider. If the user saves an event, the calendar app and any linked calendar account control that copy.

## Files and external viewers

Course resources are downloaded only after a user action. Authenticated requests are limited to verified same-origin HTTPS document routes, do not follow redirects, do not log cookies, and enforce a 50 MB limit. Attachments are not downloaded automatically.

Downloaded files are kept in a private app cache. Opening a selected file uses a FileProvider read grant to the chosen viewer. That viewer can read and may retain the selected document under its own policies. Clearing the app's download cache removes the app's copy; it cannot remove a copy retained by another app or saved elsewhere by the user.

## Removing information

The local-clear action removes the native session, saved identity/session data, cached academic/module records, private downloaded files, and local reminder state, then requests the embedded browser's authentication-storage purge. The app waits for the bounded browser-reset attempt before reporting the signed-out state and reports cleanup failures. It does not ask the university to end its remote session.

Sign out additionally attempts BTU's remote logout and verifies the result. Local information is cleared even if the network request fails. When the app cannot confirm remote logout, it reports that uncertainty; you may still need to end the university session through the official portal. Clearing embedded storage does not sign you out of unrelated external browsers or revoke your Google account.

A fresh interactive sign-in clears the previous account's cached academic data to avoid mixing accounts. Uninstalling the app also removes its app-private data. Disabling remembered login removes the saved encrypted cookie; it does not by itself clear cached courses or end the active in-memory session.

## Scope changes

The current client does not submit academic registration changes. Adding new data types, automatic downloads, external services, portal write operations, or another account profile requires this notice and the relevant security controls to be reviewed. The `0.1.0` expanded implementation still requires fresh build, test, and device validation before distribution.
