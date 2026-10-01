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

### Backend change (AlsbachScanner, deployed 2026-10-01)

`GET /api/history?area=Darmstadt` matches the city and its zones (`Darmstadt-*`), the same
roll-up the push topics use, and echoes `area` back so the app can tell an old server from a new
one. The dashboard's exact `area` filter on `/api/admin/history` is unchanged. Files:
`src/database.js` (`areaGroup`, LIKE wildcards escaped), `src/http_app.js`, new
`src/history_area_group.test.js` and its `package.json` entry. Deployed with `npm run deploy`;
the dry run showed only these four files differed from production. Still uncommitted in that
repo's working tree.

## Follow-up round

- **Feed/map freeze:** the walking-route stage and the alert/Filter Studio collectors now keep only
  the latest input (`mapLatest` / `collectLatest`), so a slow routing call can no longer leave the
  feed and map behind the database.
- **Stale instrumented test** updated for the Settings Back button; the Poké Genie confirm step
  follows the new "Import" / "Replace roster" label. 24/24 instrumented tests in the touched areas pass.
- **Polish notes, all addressed:** onboarding intro steps, check marks on selected chips, no fake
  Allow button for background location, "IV spawns" in the preset; feed cards lead with distance;
  long-press menu icons and an outline on the card it belongs to; arrival messages use the detail
  page's snackbar; counters rows align, My Pokémon explains how to enable it, Pokébox moves keep
  their casing, Battle setup says "Your levels"; Poké Genie dialog says "Import" with no roster and
  drops the column count; Filter Studio shows zones covered by their city; Spawn insights tab
  colours, lighter partial "today" bar, "Outside named areas", clearer surge wording; map tools
  rows aligned and the filter sheet header gets an edge; Mega boost hides "0 of 0" rows; catch
  route start chips reflect the start source; species pickers find sprites for form species
  (Shaymin, Thundurus, Urshifu...); GoDex form labels title-cased and not repeated, Sign out vs
  Disconnect explained; Nearby Radar credits OSM once and the alarm prompt asks once; readable
  Retry on the offline banner.
- The GoDex "no back button" note was wrong: the header is there, the earlier screenshot was scrolled.
  Sprite size differences in the GoDex grid come from the artwork itself and were left alone.

## Still open

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
