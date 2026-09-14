# Catch routes

Open **Map → Route**. Use your location or tap the map to choose a start. Choose a departure, play duration and finish mode, then generate a route. Save a setup to reuse its locations and preferences; its spawn availability is recalculated each time.

Routes maximize potential encounters, not guaranteed catches. Walking time is the entire time model: no catching delay and no deliberate waiting. Default pace is 1.36 m/s. Automatic timing is the default: the backend uses learned lifetimes where available, with its 30-minute fallback for unknown timing. Advanced settings retain explicit 30- or 60-minute assumptions and supported-only windows. Existing saved prediction choices are preserved.

## Timing and range

An hourly despawn anchor alone does not establish when a spawn starts. Observed encounters supersede assumptions for their cycle. Imported schedules, assumed durations, stale metadata, conflicting timing and incomplete source coverage remain distinguishable. Open an itinerary group to inspect evidence. A duration lower bound is not an exact start or proof of recurrence. Learned 60-minute lifetimes remain estimates. Details label likely event spawnpoints and separate Wingull live coverage from catalogue freshness. Points requiring live confirmation accept only observed encounter windows; historical schedules never create availability locally.

The optimizer scores time spent within each spawn's 40 m circle, or 80 m with Spacial Rend, along actual pedestrian geometry. Each absolute opportunity ID counts once. Later hourly cycles can count separately. Null matrix edges are unavailable. The optimizer does not subtract radii from successive walking legs or invent straight-line connections.

Generation uses three bounded candidate sets, directed matrices and beam search, then bounded insertion, removal and reordering, geometry validation and detour refinements. All fetched opportunities can score along a path, including opportunities outside candidate anchors. The generation budget is eight routing requests and 30 seconds. This heuristic does not prove a global optimum.

## Guidance

Starting guidance replaces the active Hunt or arrival journey. Starting a Hunt or arrival journey stops Catch route guidance. Saved setups and session snapshots use their own Room database, `catch_routes.db`.

Fresh, accurate fixes confirm visits after two seconds in range. GPS gaps do not imply visits; visits do not imply catches. Record catches with **+ Catch**, adjust with **− Catch**, or undo the last manual action. Pause keeps the session visible. Resume refreshes timing. Replanning preserves the original finish deadline and reconciles expiry corrections against visited points and times.

The notification opens the route. **Small map** uses Android picture-in-picture. **Floating map** requires display-over-other-apps permission; drag its header to move it. Stop guidance from the route screen. Every replan marks timing stale until a successful replacement. Network failures or empty availability retain the path with a retryable status, hide remaining opportunities across route surfaces, and pause automatic visits. Restored sessions require refreshed timing before recording visits. A deadline timer ends foreground guidance even without GPS; the finished session keeps its visit and manual catch totals. Session writes and ownership changes are serialized to prevent stale callbacks from restoring stopped routes.

## Backend

The app uses `/api/spawnpoints/windows`, the modern catalogue envelope, `/api/routes/matrix`, and `/api/routes/path` on the configured alerts API host. Windows are not HTTP-cached. Catalogue ETags are query-specific. A revision conflict discards partial pages and permits two automatic restarts.

The path endpoint accepts 2–30 ordered points and explicit pedestrian costing. It returns ordered legs with GeoJSON LineString geometry and snapped endpoints. Starts or finishes more than 25 m from their routed position must be moved onto a nearby walking path.

## Verification

Run `:app:testDebugUnitTest --tests 'com.example.pokemonalertsv2.catchroutes.*'` for deterministic timing, geometry, pagination, cancellation and optimizer checks. `CatchRouteInstrumentedTest` exercises persistence and guidance on `emulator-5554`. `CatchRouteLiveInstrumentedTest` explicitly calls production for Alsbach and Darmstadt route previews and writes screenshots to the app's external files directory. Live tests require the deployed path endpoint and can encounter the shared routing rate limit.

Run `CatchRouteRestartSeedTest` and then `CatchRouteRestartRestoreTest` in separate instrumentation invocations to verify recovery across process death. These write and clear a fixture in the dedicated active-session store; use a test device without an active navigation session.

### Verified 2026-09-13

- Debug app and instrumentation APKs built; 1,096 Android unit tests passed.
- `emulator-5554`: 13 guidance/persistence/surface tests passed, including injected location fixes, manual catch undo preserving later visits, concurrent Hunt handover, rapid stop/restart, deadline expiry without GPS, notification removal, PiP and floating-map cleanup.
- Separate seed/restore instrumentation processes passed both restart checks. Restored visits/catches survive and timing is marked for refresh.
- Both live 20-minute round-trip planner tests passed against the redeployed API. Latest runs: Alsbach 91 potential encounters / 1,618 m; Darmstadt 189 / 1,612 m. Counts change with departure time and remain estimates. Preview screenshots are in `.codex-qa/catch-routes/` locally.
- Backend path/matrix tests passed 26 cases. Spawnpoint contract tests passed 15 cases, with separate importer and database-worker checks passing. Live modern pagination, catalogue ETag/304 and no-store windows were verified. PoGoMapper reported incomplete coverage; absent source metadata remains unknown.

The two backend path hardening fixes (null provider legs and shutdown after quota wait) are local and require the operator's next deployment. No app release was published.

### Learned timing and event compatibility verification, 2026-09-13

- Focused Catch Routes unit suite: 26 tests passed; debug app and instrumentation APKs built.
- Pixel_10_Pro (`emulator-5554`): 13 existing lifecycle tests, the new Automatic/event-details/stale-state UI test, both process-restart checks, and both live route checks passed. The initial UI selector encoding failure was corrected and the UI check rerun successfully.
- Automatic live previews: Alsbach 80 potential encounters over 1,553 m; Darmstadt 245 over 1,604 m. Source coverage warnings remained visible. Event labels were verified with fixtures; these counts do not establish live activity at predicted points.
- Existing app databases, files, preferences, and no-backup state were restored after testing; all 35 backed-up file hashes matched. The updated debug APK remains installed. No release was published.


### Static route readability, verified 2026-09-14

The shared map now draws direction arrows and numbered stages in route order, with Start / Finish and Turn back labels. Repeated roads use separate drawing lanes, including when outgoing and return geometry use different segment boundaries. These offsets affect presentation only; scoring, distances and visits still use the original route. Green circles show spawnpoint counts and yield space to direction arrows and stage labels. Nearby stage labels use non-overlapping positions with leader lines. Dense previews may need zooming to show every label.

Labels are drawn in the map view using native Canvas projection, avoiding inconsistent MapLibre sprite rendering observed on the Pixel emulator. The static preview provides direction without starting guidance or marking catches.

- All 30 focused Catch Routes unit tests passed; debug app and instrumentation APKs built.
- Pixel_10_Pro (`emulator-5554`): drawing fixtures, backend compatibility UI and live Alsbach/Darmstadt tests passed. After final label spacing changes, the drawing fixture and both live tests passed again (3 tests). Fresh screenshots verified crossing and repeated-road presentation.
- Production predictions and incomplete source coverage remain explicitly labeled.
- Original app state was restored and all 36 backed-up file hashes matched. Updated debug APK remains installed; no app release was published.
