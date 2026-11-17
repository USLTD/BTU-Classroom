# BTU identity sources

The personal app uses BTU's actual mark, imported on 2026-11-17 from the
[official pink SVG](https://btu.edu.ge/wp-content/uploads/2021/06/BTU_Logo_new-1.svg).
The source is retained at `assets/btu-logo-original.svg`. Android vectors retain
all 11 original paths and their transform. Only empty SVG margins are cropped;
the logo's proportions and pink fill (`#FE017E`) are preserved. The notification
icon uses the same silhouette in white for Android's monochrome rendering.
The adaptive launcher uses a white background and fits the mark within its safe area.

The [official website's current CSS](https://btu.edu.ge/wp-content/uploads/elementor/css/post-9005.css)
declares pink `#FE017E`, blue `#0000B5`, and gold `#F5A753`. These are observed
website tokens, not a published brand manual. The app derives accessible tonal
colors for text and controls; its default appearance retains the BTU identity.
Users may explicitly opt into Android's wallpaper colors.

The user stated that this is a personal app and will obtain logo permission.
The app continues to identify itself as an unofficial companion. This file
records provenance and does not assert a trademark license or BTU endorsement.

Material 3 is explicitly pinned to `1.5.0-rc01`, the current Google Maven release
on the import date, because it exposes `MaterialExpressiveTheme` and
`MotionScheme.expressive()` publicly. Other Compose dependencies use the BOM.
