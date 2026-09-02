# Unified Filter Studio

## Rules and ownership

Feed, Map, Notifications, and each widget resolve their own `FilterAssignment`
to a versioned `FilterDefinition`. `AlertFilterMatcher` is the single eligibility
implementation. A linked assignment follows its profile; a copied assignment
contains independent rules. Editing a linked custom profile warns about its
consumers before applying to all of them. Deletion copies the current rules into
linked consumers before removing the profile.

Selectors have explicit All, None, and Selected states. Enabled alert-type
branches are alternatives (OR), including overlapping spawn/value tags. Rules
inside one branch combine with AND. Quest exact pairs are alternatives to the
task/reward facet rule; enabled task and reward facets combine with AND. All
exact quests explicitly accepts every quest, while an empty Selected set accepts
none. Area restrictions reject missing areas. Unavailable distances/routes do
not reject alerts. Unknown alert categories are exposed as Other.

Notification delivery, quiet hours, silence and GoDex checks remain outside the
matcher. Map display options, dismissal visibility, arrival tracking and widget
sorting are also independent of the filter definition.

## Persistence and migration

- DataStore key: `filter_state_v1` (JSON).
- Widget assignments: each widget ID in the existing widget preference store,
  using the same JSON assignment codec.
- Catalog cache: SharedPreferences `filter_catalog_cache`, key `catalog_v1`.
- Legacy keys are retained. The first read persists a migrated document;
  widget instances migrate when loaded or edited. Settings-backup imports use
  the same migration when no newer filter document is present.
- Legacy empty species sets become All; `_none_` becomes None. Legacy feed
  presets become profiles and retain their feed-only sorting metadata.
- Cached quests, the local species database, built-in tiers/Rocket types and
  current alerts populate offline selectors. Unavailable selected values stay
  visible and continue matching.

## UI and validation

The settings landing page uses four surface cards. Editors keep changes in a
draft until Apply, with location-aware live counts. Species selectors use the
existing artwork; quests have exact/task/reward tabs. Arrival tracking moved to
Appearance & behavior. Feed/Map buttons open their corresponding editor.

On Android 16, the app's Compose 1.7 full-height dialog measurement includes
system bars. The editor therefore uses the usable window height from
WindowMetrics, avoiding an off-screen Apply button without hardcoded device
padding.

Tests cover the shared fixture matrix, selector states, quest union logic,
normalization, migration, linking/copying/deletion, offline catalog merging,
actual allowed/rejected notifications, atomic Apply/Cancel and profile warnings.
`FilterStudioComposeTest` captures all advanced selectors in light/dark themes
at 1.0x/1.5x font scale. Device captures are retained at
`/data/local/tmp/filter-studio-qa` on the Pixel emulator.

Verification on September 2, 2026 used `Pixel_10_Pro` / `emulator-5554`, Android
16. The 790 JVM tests, debug build, Android-test build and debug lint passed.
The Gradle-connected run passed 95 tests with one expected GoDex-history skip.
An additional eight focused tests passed with the actual system font scale at
150%, including real notification posting and linked-versus-copied widget
loading. Manual checks confirmed live species artwork, live surface counts,
widget configuration and launcher output: applying Areas = None refreshed the
placed widget to zero alerts without changing Feed or Map. The widget was then
restored to unrestricted rules. Font scale was restored to 100%.

The frontend-design pass retained Material 3 and category accents, and replaced
uneven large-font segmented controls with wrapping choice chips. No production
test alerts were required; deterministic fixtures cover the full alert matrix.

A final direct-instrumentation run exercised all 97 cases after the widget test
was added. Its only failure was an existing arrival test reading notifications
immediately after the trip cleared, before notification posting completed.
The assertion now polls for the actual notification for up to five seconds;
that corrected test passed on its focused rerun. Arrival service code was not
changed. All filter and widget tests passed in the full rerun.
The final selector matrix also passed at an actual 150% system font after the
choice-chip polish. Feed and Map were manually restricted to no areas and each
showed zero results; their own filter buttons reopened the correct editors.
Both were restored afterward, leaving the debug app on Filter Studio at 100%
font size.

## Catalog service

AlsbachScanner exposes public read-only `GET /api/filter-catalog`, schema 1.
It combines active alerts with a 90-day, maximum 2,000-row SQLite observation
window. Output caps: 100 areas, 1,000 species per list, 100 Rocket types and
1,000 normalized quest pairs. Responses include an ETag and a five-minute
public cache lifetime. Existing endpoints are unchanged.

The September 2 deployment used copies of production's current files with only
the intended patches. A one-shot active-alert recovery snapshot preserved
scanner activation keys and memory-only IDs through reload. SQLite backup
`backups/filter-catalog-2026-09-02T09-26-10-006Z/alerts.db` was nonempty and passed
`PRAGMA integrity_check`. The protected `.env` hash stayed unchanged. PM2 and
`/health/ready` were verified, as were catalog 200/304 responses, the five-field
public waypoint contract and Android-safe integer alert IDs. No production QA
alerts were created. No version bump, push or Android release is included.
