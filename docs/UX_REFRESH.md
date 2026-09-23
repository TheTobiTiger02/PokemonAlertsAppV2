# UX refresh implementation and verification

Status: presentation implementation is in place. Broad regression checks pass;
performance and the complete device scenario matrix are not yet acceptance-complete.
No commit, publication, or backend change is part of this delivery.

## Implemented

- Four stable destinations: Alerts, Map, Events and Tools. Live/History retain
  separate state. Legacy numeric entry points translate to stable destinations.
- Settings opens from the toolbar and returns to its origin. Tool search includes
  familiar synonyms and Permissions; four saved pins use stable IDs. The active
  activity title reopens Hunt, route guidance, or the tracked alert.
- Map exposes Hunt, Routes and location even while tiles load or fail. Filters and
  display controls are separate. Fresh installs use existing performance clustering;
  existing settings, marker selection, stacks and matching semantics remain intact.
- Compact/Visual feeds, clearer History timestamps/counts, primary tracking and
  secondary Google Maps, and consolidated detail actions. Feed controls scroll with
  content; the landscape navigation rail scrolls at 200% text.
- Filters show the editing destination, Unlimited, advanced rules, profile copy/link
  behavior and affected destinations. Draft Apply/Cancel and independent widget
  rules retain existing matcher semantics, including direct interaction-range exceptions.
- Catch routes use Setup, Preview and Guidance with a focused picker and fixed
  generation action. Existing planner constraints, ownership and no-auto-replan remain.
- Raid tools browse counters without starting Raid Live Update or requesting its
  permission. Live Update remains an explicit action. Existing ranking, imported
  roster, replacement preview and Copy for GO are reused.
- GoDex exposes live matches and existing match-kind pills, collection state and
  sync freshness. Existing write-back and undo implementation is retained.
- Events use a category sheet and retained state. Onboarding uses three steps and
  layout previews. Permissions have a dedicated searchable destination and effective
  notification status. Existing motion and system-surface components are reused.
- Presentation preferences use the existing backed-up settings store. DESIGN.md and
  USER_GUIDE.md describe current navigation and behavior.

## Verified evidence

Local evidence root: `C:/Temp/jtmp/ux-final/` (not committed).

- JVM: 1,188 tests, no failures/errors, two opt-in skips.
- Debug/test APKs, minified benchmark APK and lint passed (`delivery.log`,
  `layout-build.log`). The latest focused navigation/feed/raid run passed 23/23
  (`final-regression.log`). Earlier GoDex/clipboard checks passed.
- Previous stages passed 37 offline cases covering navigation, CSV review, Events,
  filters/profiles, widget isolation and routes; 17 Hunt movement cases plus battery
  denial, blackout/escape, route restart and filter Cancel checks also passed.
- Pixel_10_Pro was verified as `emulator-5554`. Both feed layouts were inspected at
  200% text in dark mode. Landscape at 200% exposed inaccessible navigation and a
  cramped feed viewport; both were fixed. Evidence: `landscape-loaded.png` and
  `landscape-scroll-fixed.png`.
- TalkBack focus and activation were checked (`talkback-ready.png`); this is not an
  exhaustive spoken-label audit. Reduced-motion landscape and keyboard navigation
  were exercised. Android font/rotation/animation/accessibility settings were reset.
- Samsung SM-G991B used the existing signed app and real authenticated GoDex account.
  Sync reached Up to date; collection search and imported-roster display were checked.
  Canonical comparison preserved 1,158 checklist entries (496 needed, 662 caught),
  2,513 imported Pokémon and zero pending writes. No real checklist mutation was
  performed. Proof: `samsung/preservation.json`; screenshots in `samsung/`.

## Performance

The baseline is commit `785fd70`; both APKs are minified release-equivalent with
baseline profiles, the same loopback API and offline OSM renderer. Fixtures are
emulator-only and stop existing session owners before seeding. Nonempty workloads
use 1,000 collection entries. No fixture writes target a real GoDex account.

The first same-boot empty-data pair completed ten cold starts per APK: median
initial display was 1,107.5 ms before and 1,198.2 ms after. Ranges overlap
(989.3–1,381.9 ms before; 837.6–1,436.1 ms after). This does not establish a speed gain.
The populated journey then waited for an offscreen Widgets row. The harness now
waits for the Filters introduction. Emulator exit required a new boot; measurements
from different boots are not combined as one campaign. Populated/dense rerun status
and restoration proof are recorded below when complete.

## Preservation and limitations

Latest Pixel archive: `pixel/original.tar`, 28 files, SHA-256
`57b7a744185f939abba87d9f6d4198603d0ae18477343e5ab421e42296affe83`.
Latest Samsung archive: `samsung/original.tar`, 33 files, SHA-256
`f04bbaf994674e1ec4f96d2dff2e7a8cb1a2dae166777e7f1ace00bfdea067ab`.
Samsung is not rolled back wholesale: account content is unchanged while legitimate
sync timestamps are retained. Pixel fixture restoration must compare every file and
reject missing, changed or additional files in databases/files/shared_prefs.

The full cross-product of every category, provider, state, deep link, widget size,
process-restoration case and accessibility mode has not been exhaustively exercised.
Live account write failure/undo and authentication failure tests were not induced on
the user's account. Emulator tests cannot establish GPS accuracy or battery life;
Samsung checks did not include a sustained real walk or battery measurement.

Final cleanup replaced the remaining quest summary jargon ("no facets") with
"no task or reward rule". `final-build.log` passed debug assembly and lint in
2m 52s. The benchmark click helper now retries stale Compose nodes until its
existing timeout, rather than exhausting three immediate retries during a
transition. A captured failed click showed Pikachu present in both screenshot
and semantics tree; this is harness reliability work, not a claimed app speed fix.

## Completed paired measurements (23 September 2026)

Populated and dense pairs completed on boot
`574ddae9-783c-42d2-8095-40fc7f96beff`, with ten cold starts per APK/workload
and five journey iterations per APK. The populated after journey was retried after
fixing stale-node handling; successful startup runs were not repeated. Runner changes
and a build occurred between those journeys, so this is exploratory evidence, not
proof of a statistically stable performance change. The final wording-only app edit
was not part of the minified timing APK.

| Workload / metric | Before | After |
| --- | ---: | ---: |
| Empty startup median, ms (earlier same-boot pair) | 1,107.5 | 1,198.2 |
| 200 alerts startup median, ms | 755.6 | 852.0 |
| 200 alerts journey CPU frame duration p90, ms | 33.1 | 64.8 |
| 200 alerts journey maximum heap median, KiB | 45,263 | 39,478 |
| 2,000 alerts startup median, ms | 970.0 | 1,026.0 |
| Dense-map/PiP CPU frame duration p90, ms | 101.6 | 182.1 |
| Dense-map/PiP maximum heap median, KiB | 44,858 | 47,780 |

Screen-readiness medians also increased for most destinations; map readiness was
similar (1,454 ms before versus 1,417 ms after). These timing results are a regression
risk. No speed gain is claimed, and the performance goal is unresolved. Evidence:
`performance-final/`, `performance-remaining.log`. CSV summaries use only the last
five complete iterations because Android test output directories retain older rows.

A paired middle-iteration Perfetto inspection (`trace-profile.log`) found increased
draw/traversal time and a 203 ms monitor wait in
`org.maplibre.android.maps.renderer.surfaceview.MapLibreSurfaceView.surfaceRedrawNeededAsync`.
This identifies rendering contention worth investigating; it does not establish that
one app change caused the aggregate regression. No speculative renderer change was made.

## Final restoration

Final debug APK installed on Pixel without clearing data or removing its keystore.
All 28 files in databases/files/shared_prefs match the latest original archive by
SHA-256 with no extras (`pixel/restoration.json`). Runtime permission grants and
recorded user-set/user-fixed flags were restored, along with overlay access,
accessibility, font, rotation, animation, night mode and network settings
(`pixel/system-restoration.json`). Pixel is left force-stopped to preserve the exact
verified file snapshot. Samsung account content remains unchanged; its legitimate
sync state was not overwritten. Final `git diff --check` passed.
