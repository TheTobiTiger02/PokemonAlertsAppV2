# Catch routes

Open **Map → Route**. Use your location or tap the map to choose a start. Choose a departure, play duration and finish mode, then generate a route. Save a setup to reuse its locations and preferences; its spawn availability is recalculated each time.

Routes maximize potential encounters, not guaranteed catches. Walking time is the entire time model: no catching delay and no deliberate waiting. Default pace is 1.36 m/s. Advanced settings allow changing pace, selecting a 30- or 60-minute assumed duration, or accepting only supported windows.

## Timing and range

An hourly despawn anchor alone does not establish when a spawn starts. Observed encounters supersede assumptions for their cycle. Imported schedules, assumed durations, stale metadata, conflicting timing and incomplete source coverage remain distinguishable. Open an itinerary group to inspect evidence. A duration lower bound is not an exact start or proof of recurrence.

The optimizer scores time spent within each spawn's 40 m circle, or 80 m with Spacial Rend, along actual pedestrian geometry. Each absolute opportunity ID counts once. Later hourly cycles can count separately. Null matrix edges are unavailable. The optimizer does not subtract radii from successive walking legs or invent straight-line connections.

Generation uses three bounded candidate sets, directed matrices and beam search, then bounded insertion, removal and reordering, geometry validation and detour refinements. All fetched opportunities can score along a path, including opportunities outside candidate anchors. The generation budget is eight routing requests and 30 seconds. This heuristic does not prove a global optimum.

## Guidance

Starting guidance replaces the active Hunt or arrival journey. Starting a Hunt or arrival journey stops Catch route guidance. Saved setups and session snapshots use their own Room database, `catch_routes.db`.

Fresh, accurate fixes confirm visits after two seconds in range. GPS gaps do not imply visits; visits do not imply catches. Record catches with **+ Catch**, adjust with **− Catch**, or undo the last manual action. Pause keeps the session visible. Resume refreshes timing. Replanning preserves the original finish deadline and reconciles expiry corrections against visited points and times.

The notification opens the route. **Small map** uses Android picture-in-picture. **Floating map** requires display-over-other-apps permission; drag its header to move it. Stop guidance from the route screen. Network failures retain the path and show a retryable status. Restored sessions require refreshed timing before recording visits. A deadline timer ends foreground guidance even without GPS; the finished session keeps its visit and manual catch totals. Session writes and ownership changes are serialized to prevent stale callbacks from restoring stopped routes.

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
