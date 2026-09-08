## Root cause

The status bar chip is Android 16's "promoted ongoing" Live Update. Commit 1798d07 already established the acceptance rule: the notification must be requested as promoted and must not be `CATEGORY_SERVICE`, or the system filters it out of the chip.

The bug is a notification-demoting race in `ArrivalTrackingService`:

1. `ArrivalTrackingService.onStartCommand` unconditionally re-posts the `restoring()` placeholder (`ArrivalTrackingService.kt:72`) on **every** `start()` call — including when the service is already running with a live journey notification. That placeholder (`ArrivalTrackingNotifications.restoring()`, built by `ongoingBuilder()`, `ArrivalTrackingNotifications.kt:71-75, 325-332`) is `CATEGORY_SERVICE` with no promoted-ongoing request — exactly the combination that never reaches the status bar chip.
2. The map-PiP browse flow re-runs `repository.startTracking(alert)` + `ArrivalTrackingService.start()` on every settled alert change (`MapPipArrivalTracker.kt:47-49`, driven by the snapshotFlow at `AlertsMapScreen.kt:1108-1145`). Stepping through alerts with Next/Previous is the normal PiP pattern, so the service is *already running* when you settle on the alert you actually want.
3. When the service is already running, the DataStore write triggers `activate()` → `updateOngoing()` (promoted Live Update posted), and **then** the restarted service's `onStartCommand` replaces it with the unpromoted placeholder on the same notification ID. Since `destinationFlow` is `distinctUntilChanged()` (`ArrivalTrackingRepository.kt:95-100`), nothing re-posts the promoted notification until the next location callback — and the fused request only fires on ≥5 m of movement (`ArrivalLocationSource.kt:51-57`). Result: the notification sits in the tray but never in the status bar, exactly as you reported. (A fresh cold start doesn't hit this because the placeholder is immediately replaced by `activate()` in the same dispatch chain.)
4. The same demotion happens via `PokemonAlertsApplication.onStart` → `resumeIfActive()` → `ArrivalTrackingService.start()` every time you return to the app during an active journey.

## Fix (2 files)

**`ArrivalTrackingService.kt`** — in `onStartCommand`, when the service is already tracking (`currentDestination != null`), re-promote to foreground with a freshly built live notification (`ArrivalTrackingNotifications.ongoing(currentDestination, lastDirectDistanceMeters, walkingRoute, lastInRange, lastWaitingForPreciseLocation)`) instead of the `restoring()` placeholder. The `restoring()` placeholder is only used on a true cold start, where `activate()` replaces it immediately. The `startForeground` contract is still satisfied in both branches.

**`ArrivalTrackingNotifications.kt`** — on API 36+, build `restoring()` through the promoted live builder (CATEGORY_NAVIGATION, `requestPromotedOngoing`, indeterminate ProgressStyle, short-critical-text fallback) so even the brief cold-start placeholder is chip-eligible. Pre-36 fallback unchanged.

No changes to the PiP intent logic, repository, or main-UI flow — the same fix also covers the main-UI "switch destination" path, which has the identical race.

## Verification on your Pixel 10 Pro emulator (API 36.1)

1. Boot `Pixel_10_Pro`, build + install the debug APK, grant POST_NOTIFICATIONS and fine-location via `pm grant`, and point the emulator at Alsbach (49.74677, 8.62492) with `adb emu geo fix` so alerts and distances resolve. Alerts come from your compiled-in feed (`https://api.alsbach-scanner.uk/api/pokemon`); if it's unreachable at test time I'll seed one eligible alert into the app's Room DB via a small instrumented test instead.
2. **Reproduce pre-fix**: start a journey from the main UI ("I'm going"), confirm the chip shows (control), then enter map PiP and browse to a different alert. Capture `adb shell dumpsys notification --noredact` + status-bar screenshots before/after — expecting the promoted flag to disappear when the placeholder replaces the live notification (confirming the diagnosis; if the flag is set but the chip is still hidden while in PiP, that would instead point at SystemUI PiP gating, and I'll report that with the evidence).
3. **Verify post-fix** with the same scenarios:
   - Fresh PiP browse start → chip appears in the status bar while the PiP window is up.
   - Switch destination by browsing (the reported case) → chip must never drop, flag stays `PROMOTED_ONGOING`, category stays navigation.
   - Home → reopen the app during an active journey → chip survives `resumeIfActive`.
   - Expand PiP, stop via notification action, and expiry/arrival still behave as today.
4. Add instrumented tests (extending `ArrivalTrackingServiceInstrumentedTest`): a second `start()` while tracking must not demote the posted notification to the restoring placeholder, and `restoring()` must carry the promoted/nav markers on API 36. Run the new tests plus the existing tracking/PiP instrumented suites on the emulator.

No version bump or commit unless you ask for it.