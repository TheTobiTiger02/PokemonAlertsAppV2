# Full-app QA review, 2026-10-01

A hands-on pass over every screen on the `Pixel_10_Pro` emulator, walking a mocked GPS route
around Darmstadt (Luisenplatz, Hauptbahnhof, Lichtwiese). Test alerts came from a local
AlsbachScanner on a copy of the 2026-10-01 production backup, with FCM delivery off, so nothing
reached other phones. Pokébattler and GoDex were tested signed in.

Everything below marked **Fixed** is on branch `qa/full-review-2026-10`, one commit per fix,
verified on the emulator with the original repro. Unit tests pass.

## Fixed

| | Problem | Commit |
|---|---|---|
| High | Choosing the "Darmstadt" area hid every Darmstadt-North/-South alert (about three quarters of them) in the feed, map, notifications, widgets and History. A fresh install that picked Darmstadt saw an empty feed. | `bd26005` |
| High | Changing dark mode, font size or rotation re-applied the tab or deep link the app was launched with, throwing the user back to e.g. the Map. | `fbb420f` |
| High | "Reachable on foot ≤ 10 min" still showed alerts 15 km away: anything beyond the ~4.5 km routing radius is never routed, and unrouted alerts were always kept. | `d53c148` |
| High | Settings backups carried the alert-sync cursor again (the denylist used key names from before a rename), plus an active silence or Raid Watch, the Poké Genie import summary and the Pokébattler trainer number. | `dbc8463` |
| Medium | A fresh install's first sync posted a notification for every alert already live (8 at once). | `687690e` |
| Medium | History's area filter only filtered the 50 loaded rows, and the stats card called that the all-time total. Now server-side. **Needs the backend change below deployed**; until then the app behaves as before. | `aa67600` |
| Medium | Long-press menu had no Dismiss, though onboarding promises it; swipe was the only way. Now Dismiss / Restore with the same Undo. | `ee5ee3d` |
| Medium | Searching species by Dex # ("147") also matched 38 form species with no Dex number. The map sheet also drew two different distance chip rows for one setting. | `9a6a6f9` |
| Low | Compact widget truncated alert names ("Groudon R…") to fit "N active". | `9d45f9a` |
| Low | Selected "All" chip in the History filter looked disabled. | `86e8a4b` |
| Low | Event detail promised a reminder for events that had already started. | `f406883` |
| Low | Copy: "1 areas", "1 alert types", mega hint pointed at the wrong card, onboarding said the gear holds map filters, quiet hours said notifications are "held" though they are dropped, "Pokemon". | `215538e`, `f403c24`, `15fe2bf`, `dbc8463` |

### Backend change (AlsbachScanner, not committed, not deployed)

`GET /api/history?area=Darmstadt` now matches the city and its zones (`Darmstadt-*`), the same
roll-up the push topics use, and echoes `area` back so the app can tell an old server from a new
one. The dashboard's exact `area` filter on `/api/admin/history` is unchanged.

- `src/database.js`: `areaGroup` option in `buildHistoryFilters` (LIKE wildcards escaped).
- `src/http_app.js`: public `historyResult` maps `?area=` to it and echoes it.
- `src/history_area_group.test.js` (new) and its entry in `package.json`'s `test` script.

`npm test` passes. These sit in a working tree that already had other uncommitted changes, so
they were left uncommitted rather than mixed into an unrelated commit.

## Worth a look

- **Feed and map froze on a stale list once (not reproduced).** After about five hours and a
  burst of 300 injected alerts, the feed showed "0 alerts" and the map a pool of 13 old alerts
  while the database held 32 live raids, 57 rockets and two hundos 50 m away. A force-stop fixed
  it; a second burst did not reproduce it. The distance pipeline in `PokemonAlertsViewModel`
  (`combine` into a suspending `getWalkingRoutes` that can wait 6 s) can back up; `mapLatest`
  would let the newest alert list always win. The local backend rejected every routing request
  (50-destination cap; production allows 500), so routing was failing throughout.
- **Stale instrumented test.** `MainNavigationComposeTest.settingsUsesOverviewAndFocusedSubpages`
  expects no Back button on the Settings overview, but the Settings-gear redesign (`fad0a36`)
  added one. This branch does not touch that screen's header.
- **Widgets** log `RemoteViews: Possibly notifying updates for nonexistent view Id` on every refresh.
- **Compact widget** never shows the nearest alert's distance, even when sorted by Nearest; at
  2×2 the navigate button is clipped.
- **Catch route planning** took ~35 s (23 paged spawnpoint-window calls) and stop 1 was already
  in the past when the plan appeared.
- **Map rail:** "All" respects the distance/walk filters but the per-type counts do not
  (All 7, Raids 13).
- **Dark theme:** the alert detail header map stays light, and the white status bar icons over
  it are unreadable.
- **About** credits openrouteservice; routing is Valhalla.

## Smaller polish notes

- Onboarding: a lot of empty space on page 1; selected chips are a faint grey with no check;
  "Location all the time → Allow" looks enabled before location is granted but does nothing;
  "High-value catches" includes all spawns.
- Feed cards put distance last, so "Ends before you arrive · IV · CP" pushes it off screen
  exactly when it matters. The long-press menu opens well above the pressed card, and only
  "I'm going" has an icon.
- Detail: the "Arrival alert set" message covers the Stop/Navigate buttons.
- Counters: top three rows use larger sprites so names don't line up; "My Pokémon" is disabled
  without a roster but looks like a normal tab and gives no hint; with Pokébox, moves are
  lowercase ("Bullet punch") and "Battle setup: L40" shows while the L40 chip is disabled.
- Poké Genie import: "Replace roster" with no roster yet; "30 columns are not used" is noise.
- Filter Studio: with zones now rolled up, picking a zone next to its city is redundant;
  nesting zones under the city chip would show it.
- Spawn insights: zone area names differ from the rest of the app; partial "today" bar is not
  marked; the unselected tab label has the selected colour.
- Map tools sheet: the "Catch route" row is ~9 px left of the others. Map filter sheet: scrolled
  content shows under the sticky header.
- Mega boost with no data still lists every mega as "0 of 0 (0%)".
- Catch routes: "Pick start on map" is highlighted while the text says "Start: Your location".
- Species pickers: ~38 form species (Shaymin, Thundurus, Urshifu, Zygarde…) have no sprite or Dex #.
- GoDex: no top bar/back; "Sign out" and "Disconnect GoDex" sit together unexplained; form
  subtitles repeat the title in lowercase ("alola").
- Nearby Radar: OSM attribution shown twice; a second widget re-asks for Alarms & reminders.
- Offline banner (dark): light-blue "Retry" on dark red is low contrast.

## Checked and fine

Onboarding and permissions, live feed (countdowns, search, swipe-dismiss + undo, offline cache
banner, recovery), History paging, Insights (both tabs), alert detail, "I'm going" through to
"in range", Raid Watch Live Update with Copy team, raid counters (Pokébattler, Poké Genie and
Pokébox), Poké Genie import and roster, GoDex checklist, map markers, hunt mode, catch route
generation and guidance while walking, Events with stars, quiet hours and silence, backup
export/restore, both widgets and their tap targets, picture-in-picture, landscape layout,
1.3× font, process death restore, deep links.

Not covered: real FCM delivery of injected alerts (deliberately off), performance numbers
(the emulator debug build janks even on an empty list; measure on the S21 staging build).
