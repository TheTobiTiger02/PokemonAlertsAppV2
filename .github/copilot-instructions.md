# AI coding agent guide — PokemonAlertsV2

This repo is an Android app (Kotlin, Jetpack Compose, WorkManager, DataStore, Retrofit) that surfaces community Pokémon Go alerts. Use this as your “crib sheet” to be productive fast.

## Big picture
- Modules: single `:app` module. Kotlin 2.0, AGP 8.9, Compose BOM 2024.09.00. Min SDK 26, target 35, compile 36.
- Data flow: `PokemonAlertsService` (Retrofit + kotlinx.serialization) → `PokemonAlertsRepository` (fetch + dedupe via DataStore) → UI/ViewModel and background workers → notifications and homescreen widget.
- Background: `AlertWorker` runs on WorkManager (15 min) and optionally via exact alarms (`AlertAlarmScheduler` + `AlertAlarmReceiver`) for ~10 min cadence when permission is granted. Worker triggers `AlertNotifier` and refreshes the widget.
- UI: Compose screens in `ui/alerts` with a `PokemonAlertsViewModel` (AndroidViewModel) exposing `StateFlow<AlertsUiState>`. Route auto-refreshes every 30s while visible.

## Key files and boundaries
- Networking: `data/PokemonAlertsApi.kt` defines `BASE_URL` and Retrofit service. JSON is lenient and `ignoreUnknownKeys=true`.
- Model: `data/PokemonAlert.kt` (kotlinx.serialization). The app’s dedupe key is `uniqueId = "${name.trim()}|${endTime.trim()}"`.
- Persistence: `data/AlertPreferences.kt` (DataStore Preferences). Stores `seen_alert_ids` (trimmed to 200) and exposes a `Flow<Set<String>>`.
- Repository: `data/PokemonAlertsRepository.kt` is the only place that knows service + preferences. Use `create(context)` to obtain an instance.
- Background: `work/AlertWorker.kt`, `work/AlertAlarmScheduler.kt`, `work/AlertAlarmReceiver.kt`.
- Notifications: `notifications/AlertNotifier.kt` (channel id `pokemon_alerts_channel`).
- Widget: `widget/AlertsWidgetProvider.kt` updates every 30s via exact alarm and on worker completion.
- App bootstrap: `PokemonAlertsApplication.kt` wires channel, WorkManager periodic work, immediate sync, and primes exact alarms.

## Build, run, test
- Build (CI/local): use the Gradle wrapper.
  - Windows PowerShell: `./gradlew.bat assembleDebug`
  - Unit tests: `./gradlew.bat test`
- Android Studio: open project, sync, run `app`. Use an API 26+ device/emulator.
- Dependencies are centralized in `gradle/libs.versions.toml`; add new libs there and reference via `libs.*` aliases.

## Conventions and patterns
- Networking: Retrofit + `converter-kotlinx-serialization`; logging level gates on `BuildConfig.DEBUG`.
- Serialization: Prefer `@SerialName` for API fields. Unknown fields are ignored — safe to add new properties.
- Dedupe: Always call `detectNewAlerts()` then `markAlertsAsSeen()` after delivering notifications to avoid re-alerting.
- Time handling: `util/TimeUtils.kt` parses multiple formats (epoch, ISO-8601, common patterns) and formats countdowns. UI and widget use it for end-time and remaining duration.
- Work scheduling: Use `AlertWorker.schedule(context)` once (unique names are inside). Trigger ad‑hoc syncs with `AlertWorker.triggerImmediateSync(context)`.
- Exact alarms: Only scheduled if `AlertAlarmScheduler.canScheduleExact()`; on Android 12+ the permission may be required. Use `AlertAlarmScheduler.createSettingsIntent()` to navigate users.
- PendingIntent flags: Use `FLAG_MUTABLE` on S+ where needed (see `AlertNotifier`, widget provider).
- UI: Composables read state from `StateFlow` via `collectAsStateWithLifecycle()`. Long-running work is done in `ViewModel` or workers; in Composables use `LaunchedEffect` loops with delays, never block the UI thread.

## Integration points and gotchas
- API base URL is cleartext HTTP. Manifest sets `android:networkSecurityConfig` to allow this; keep that in mind for new endpoints.
- Location is optional: both UI and widget attempt best-effort last-known location; handle `null` and `SecurityException` as shown (no runtime request here).
- Unique ID stability: dedupe relies on `name|endTime`. Changing either will affect notification deduplication and widget click `requestCode` generation.
- Widget image loading uses Coil off the main thread; fall back to a placeholder if load fails.

## Practical examples
- Fetch and deliver new alerts (e.g., inside a worker):
  - `val alerts = repo.fetchAlerts(); val new = repo.detectNewAlerts(alerts); if (new.isNotEmpty()) { AlertNotifier.notifyAlerts(ctx, new); repo.markAlertsAsSeen(new) }`
- Add an API field:
  - In `PokemonAlert`, add `@SerialName("foo") val foo: String? = null`; use it in UI — unknown keys won’t crash older builds.
- Trigger a manual sync from UI or receiver:
  - `AlertWorker.triggerImmediateSync(context)`; optionally call `AlertsWidgetProvider.requestUpdate(context)` to refresh widgets.

When making changes, prefer extending the repository and ViewModel boundaries instead of calling Retrofit or DataStore directly from UI or widget code.