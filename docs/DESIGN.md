# BTU Classroom native design

The app is a useful student companion with BTU's vivid pink identity, warm tonal
surfaces and clear academic information. Material 3 Expressive supplies the theme,
shape changes and motion; data remains the focus. The official logo is rendered
as an image, with its original pink fill and proportions.

## Reference and layout

The reference app establishes native Material 3 navigation, semantic status
badges, course summaries, semester chips, and explicit freshness states. These
patterns are retained as reusable Compose components. The BTU palette and expressive type/shape
hierarchy make the experience more cohesive. The reference is a behavioral
reference, not an exact visual clone.

## Color tokens

| Role | Light | Dark |
|---|---|---|
| BTU source accent | `#FE017E` | `#FE017E` |
| Primary | `#B60058` | `#FFB0CB` |
| On primary | `#FFFFFF` | `#64002D` |
| Primary container | `#FFD9E6` | `#8C0042` |
| Ink | `#261820` | `#F7DDE8` |
| Secondary ink | `#63515A` | `#D7BBC9` |
| Background | `#FFF8FA` | `#1B1017` |
| Surface | `#FFF8FA` | `#21151D` |
| Surface variant | `#F5E0E9` | `#51404A` |
| Outline | `#84717C` | `#A08A97` |

The seed is taken from the official BTU SVG at
`https://btu.edu.ge/wp-content/uploads/2021/06/BTU_Logo_new-1.svg` (single fill
`#FE017E`). This is an observed source color, not a claim of a published brand
standard. The official website's generated stylesheet also uses blue `#0000B5`
and amber `#F5A753`; derived indigo and amber roles support secondary navigation
and attention without diluting the pink identity. A darker tonal primary protects white button text contrast in light
mode; the exact source pink remains the logo color. Additional surface-container
roles create grouping without heavy repeated outlines.

Status colors communicate success, attention, or failure only. Every status has
a text label; color alone never communicates a state. Optional Android dynamic
color and system/light/dark theme settings are supported.

## Type and spacing

Use platform sans-serif for Georgian coverage, with 32/36 headline, 24/30 section
heading, 18/24 card title, 16/24 body, 14/20 secondary body, and 12/16 metadata.
Font sizes scale with user settings. Use 4, 8, 12, 16, 20, 24, and 32 dp spacing;
16 dp screen gutters, 16 dp card padding, 12 dp card spacing. Shapes use 12 dp
compact, 20 dp section and 28 dp prominent radii; the theme shares these scales
across screens. Material expressive buttons animate between their public shape
tokens. Interactive targets are at least 48 dp. Text wraps rather than depending
on fixed card heights; selected labels use weight rather than uppercase.

## Primitives and states

- `StatusBadge`: labeled neutral, primary, success, warning, and error states.
- `FreshnessBanner`: unsynchronized, syncing, current, cached/offline, and failed
  refresh with prior successful timestamp.
- `EmptyStateCard`: icon, explanatory title/body, and useful action.
- `CourseCard`: code, wrapping title, nullable official score, credits, semester,
  and clear navigation affordance.
- Material buttons, chips, switches, fields, navigation items, and confirmation
  dialogs provide focus, pressed, disabled, and accessibility semantics.

## Navigation and responsive behavior

Home, Courses, Schedule, and More are stable top-level destinations with saved
back stacks. At 600 dp the bottom bar becomes a navigation rail. Content widths
are capped for readability. Detail and settings pages have explicit back actions.
Exploration before sign-in uses genuine empty states, never fabricated academic
data. Sign-in remains accessible from the app shell.

## Motion and accessibility

Use Material's built-in state transitions and pull-to-refresh; no decorative
animation. The theme uses `MotionScheme.expressive()` and public Material 3
Expressive controls, inheriting Android's motion duration scale. Scrollable
layouts prevent large fonts or long Georgian labels from
clipping. Icon-only actions have content descriptions; decorative icons are
excluded from accessibility. Language selection updates the visible UI.

## Entry and settings

Entry keeps the BTU mark, a concise academic purpose, language choice, sign-in and
exploration in one scrollable flow. The optional encrypted session switch is
adjacent to its explanation. Settings groups appearance, notifications, session
storage and app information. Notification consent is independent of saved login:
local reminders also work with a temporary session. Background grade checks have
separate saved-login and background-sync requirements. Returning from Android
notification settings refreshes the permission state.

## Data honesty and accepted limits

The official server's values are authoritative. Missing scores remain unavailable.
Verified timetable, exams, program, academic record, remaining courses, statements,
rating, and academic calendar pages now have native screens backed by Room snapshots.
Every module loads independently and shows its own absolute last-sync timestamp.
Course registration has an explicit More card that opens the canonical official
portal at `/ge/`; no guessed form endpoint or native submission is used. Browser auth is on demand, and
technical diagnostics appear only in debug builds. Full authenticated visual QA
requires a student signing in interactively; unauthenticated exploration is
available for device checks without credentials.

## Native academic modules

Reusable academic field cards, labeled data rows, section headings, archive notices,
search fields, and module freshness banners present the verified HTML schemas.
Programs remain read-only. Academic records show server-provided averages and GPA;
the app does not calculate either. Statement view links open official read-only
pages. Native exams retain all six server fields and raw month/day values: the
portal does not provide an exam year, so calendar export and exam reminders remain
unavailable. Archived calendar events keep their actual 2017-2018 years and an
explicit notice when the saved calendar has no events for the current year.

The timetable has saved day/week navigation, localized dates, Tbilisi class times,
and real room/group/lecturer metadata. Home labels its next-class preview as a
weekly pattern rather than a semester-date guarantee. Calendar export opens the
calendar app confirmation sheet. Class reminders require an explicit confirmation
of the selected class/date and notification permission; the interface explains
that Android may deliver the requested 15-minute reminder later.

Course resources use a download action with an in-progress state. Downloaded files
open through a temporary content URI grant. Sign-out, clearing local data, and a
new account login remove academic snapshots and downloaded files and cancel local
class reminders together.
