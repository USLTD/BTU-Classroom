# Weekly timetable fixture

The parent agent inspected the authenticated `/ge/student/me/schedule` page on
November 17, 2026. This fixture preserves the observed `table#groups` structure:
weekday rows containing `h4`, repeated six-column headings in bold `td` cells,
and class rows with `HH:mm - HH:mm` times.

Course titles, room numbers, groups, lecturer names, and notes are replacement
test values. No identity, credentials, cookies, financial information, or private
student records are included. A header-only timetable is an explicit empty
contract; a missing or incomplete table remains unsupported rather than empty.
