# Route only relevant destinations, verify on emulator, release as 1.9.7

## 1. Straight-line relevance cutoff — `util/WalkingRouteRepository.kt`

Every batch to `POST api/routes/walking` currently carries the 500 nearest alerts however far away they are. Alerts beyond ~an hour's walk can never yield a useful walking time, yet they ride along in every request (~40 KB bodies, and the whole batch re-downloads every 75 m while walking). Add a straight-line cutoff so batches only carry walkable destinations; everything farther keeps today's fallback (the "~" straight-line estimate, filters keep the alert, nothing disappears).

- New constant derived from the app's own walking math (public constants in `WalkingRouteUtils`):
  `MAX_ROUTED_STRAIGHT_LINE_METERS = 60f * 60f * AVERAGE_WALKING_SPEED_MPS / DETOUR_FACTOR` ≈ 4451 m — the farthest straight-line distance that can still fall inside a 60-minute walk (the largest `TravelTime.PRESET_MINUTES` entry).
- New injectable constructor param `maxRoutedStraightLineMeters` (existing pattern).
- Filter candidates `.filter { it.directDistanceMeters <= maxRoutedStraightLineMeters }` before sort and `take(MAX_DESTINATIONS)`; the 500 cap stays as the server-ceiling backstop.
- All callers (feed, map, widget, notifier, arrival tracking, settings) go through this one choke point and already handle a missing route by showing the straight-line estimate; no call-site changes.

Tests — extend `WalkingRouteRepositoryTest.kt` (helper gains the new param):
1. near (~450 m) + far (~5.6 km) alerts with the default cutoff → the single request contains only the near alert, no route entry for the far one.
2. inclusive boundary: injected 1000 m cutoff; ~890 m alert kept, ~1110 m dropped.
All existing tests keep passing (their alerts sit at ~1 km, the 520-alert batch spans ~580 m).

## 2. Unit tests

`gradlew.bat :app:testDebugUnitTest` — full suite green (route repo, widget gate, widget provider, everything else).

## 3. Instrumented tests on the Pixel 10 Pro emulator

- Locate the Android SDK adb (`%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`), start the Pixel 10 Pro AVD if not running.
- `gradlew.bat :app:connectedDebugAndroidTest` — map (`MapLiveTrackingComposeTest`, `DenseMapInteractionTest`), widget, arrival-tracking and the rest of the androidTest suite must pass against the emulator.

## 4. Version bump to 1.9.7

`app/build.gradle.kts`: `versionName "1.9.7"`, `versionCode` +1 (keeping the existing numbering scheme).

## 5. Commit and push to main

- Commit 1: the routing/battery work from this session (negative cache, map movement gate, widget data gate, feed location refresh, relevance cutoff, tests).
- Commit 2: version bump to 1.9.7.
- `git push origin main`.

Messages follow the repo's existing style (`fix:`/`feat:` summaries).