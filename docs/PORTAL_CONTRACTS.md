# Read-only portal contracts

Authenticated BTU pages were inspected read-only on November 17, 2026, after the user signed in. This record contains structural facts and route patterns only. No credentials, raw authenticated HTML, student names, identifiers, scores, financial values, or document contents are included. No portal forms, program selections, payments, or enrollment changes were submitted.

These contracts guide native parser/cache implementation. They are not a claim that the expanded source has completed build, test, or device verification. BTU can change a page at any time; parsers must distinguish success, verified empty content, unauthenticated content, temporary unavailability, and unsupported structure while retaining the last valid cache on failures.

## Academic modules

All paths below resolve against `https://classroom.btu.edu.ge`. Native data access is authenticated HTTPS GET, restricted to approved routes; no portal write contract is implemented.

| Module and read route | Observed structure and native fields | Required limit |
| --- | --- | --- |
| Selected courses: `/ge/student/me/courses` | Semantic course table, verified course-detail links, course code/title, credits, nullable reported score, semester where present | Missing grades remain `null`; do not assume an official grading formula or fabricate progress |
| Weekly schedule: `/ge/student/me/schedule` | `table#groups`, weekday rows with `.info`/`h4`, Georgian weekday names, six columns: time, room, course, group, lecturers, additional information | Preserve source fields; compute a future weekly occurrence only for an explicit user-selected date/week in `Asia/Tbilisi`; reject malformed or conflicting rows |
| Examinations: `/ge/exams/list` | One `table.exams-table` with six columns: course, day, time, room, place, format | The observed day is month/day without a year. Preserve the text; no guessed-year event, “upcoming” classification, export, or reminder |
| Program: `/ge/student/me/program` | Read-only `fieldset#program_wrapper` and disabled faculty/program text inputs | Read values only; never select a program, enable inputs, or submit the page |
| Academic calendar: `/ge/student/me/calendar` | `#calendar` with a literal JSON `events` array referenced by the calendar script; title, start and optional end | Parse the literal JSON without executing scripts. The observed events are historical 2017–2018 holidays; preserve original years and do not present them as current-semester dates |
| Academic card: `/ge/student/card` | Official GPA/average and semester transcript tables; course title, credits, reported score | Whitelist academic fields. Exclude personal identification numbers, birth dates and administrative orders. Preserve university metrics rather than recomputing them |
| Remaining courses: `/ge/training/list` | Exact empty notice `ინფორმაცია არ მოიძებნა` in the main content area | This validates only the empty response. Populated content remains unsupported until it has been observed and tested |
| Statements: `/ge/student/me/statements` | Five metadata columns: number, title, date, response, comment; links shaped `/ge/student/me/statements/view/{number}/{number}` | Retain verified read-only view links only. No create, edit, reply, delete, or form submission |
| Rating: `/ge/student/rating` | Official average for courses completed before the current semester, under the rating fieldset | Preserve the source label and historical meaning; do not rebrand it as GPA, a rank, or a newly calculated current-semester average |

## Course details, grades and resources

The default course index is syllabus content. Follow only validated child navigation discovered for that course and semester:

- Score page: `/ge/student/me/course/scores/{courseId}/{semester}`.
- File list: `/ge/student/me/course/files/{courseId}/{semester}`.
- Syllabus document: `/ge/student/me/courseSilabusFile/{courseId}/{semester}`.
- Observed uploaded document pattern: `/uploads/{numericDirectory}/{numericFile}.docx`.

Treat the route patterns as boundaries for validated links, not permission to guess arbitrary identifiers. Verify the course/semester association against the current cached course before requesting details. Blank grade cells remain unknown. Maximum-score metadata must be explicit. The server-reported total is authoritative; eligibility/status text is non-numeric and must never become zero.

Documents are fetched only when the user requests them. The native downloader uses authenticated same-origin HTTPS, rejects redirects and unsupported paths, applies a 50 MB bound, stores files in private cache, and opens a chosen file through a FileProvider read grant. No bulk or automatic attachment download is authorized. Account/sign-out clearing removes the private cache; an external viewer can retain its own copy.

## Cache, reminders and calendar handoff

Room database version 2 adds typed JSON snapshots for the eight additional modules through an explicit version 1→2 migration. Store parsed, whitelisted fields rather than raw HTML. Keep each module's freshness/error state independently, preserve prior content on unsupported or failed reads, and prevent a response started for an old session from repopulating data after account clearing.

Opt-in six-hour background updates refresh courses and previously cached supported modules. Selected future classes can use an optional generic reminder scheduled with a one-time WorkManager request, by default 15 minutes before class. Delivery is inexact under Android's scheduling constraints. Reminder generation/account guards must prevent notification after sign-out/local clearing. Do not promise exact delivery or derive exam reminders from an absent year.

Calendar export uses `ACTION_INSERT` to open the user's calendar editor. The client does not request calendar write permission or write directly to a calendar. The user reviews and saves any proposed event.

## Languages

The public [Georgian login](https://classroom.btu.edu.ge/ge/login/index) and [English login](https://classroom.btu.edu.ge/en/login/index) expose reciprocal language links. The English heading/button reads `Login`; Georgian uses `ავტორიზაცია` and `შესვლა`. OAuth callback routes use the corresponding `/ge/login/oauth/` or `/en/login/oauth/` prefix. Native and extension authentication observation recognize both language prefixes, followed by independent native session verification.

The app supplies paired English/Georgian interface resources. Browser navigation follows the selected app language. Validated native data reads continue using canonical Georgian routes: authenticated English academic layouts have not been inspected, so switching the interface language does not replace cached records or generate change alerts. Known table labels, module headings, weekdays, and explicit semester-heading patterns can be translated at display time while retaining published years. Unknown text, course and lecturer names, results, documents, and calendar event titles remain as supplied by the university. Recognizing an English interface label for display is not validation of an English parsing contract.

## Sanitization and remaining gates

Fixtures must preserve structural selectors, heading order, blank cells, nullable fields and relevant route shapes while replacing all real personal information and identifiers with clearly synthetic values. Never store session cookies, OAuth parameters, raw authenticated pages, private documents, real student records, or financial information in fixtures, reports, screenshots, logs, or this contract record.

Before distribution, run fresh expanded-source build, full tests, lint, migration and device checks, including download size/origin failures, reminder cancellation, unknown exam years, old calendar dates, unsupported populated remaining-course content, and account clearing during a request. Validate an optimized unsigned release before arranging private-key signing. Widgets remain optional and unimplemented.

Academic registration and other portal mutations remain official-portal tasks under the user's read-only requirement. A future implementation would require new explicit scope and a separately validated transaction contract; it is not authorized by these read contracts.
