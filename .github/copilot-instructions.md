# AI coding agent guide — PokemonAlertsV2

This repo is an Android app (Kotlin, Jetpack Compose, WorkManager, DataStore, Room, Retrofit) that surfaces community Pokémon Go alerts.

## Big picture

- **Modules**: Single `:app` module. Kotlin 2.0, Compose BOM 2024.09.00. Target SDK 35.
- **Data Flow**: `PokemonAlertsService` (Retrofit) → `PokemonAlertsRepository` updates Room `AppDatabase` → UI observes `Flow<List<PokemonAlert>>` via `PokemonAlertsViewModel`.
- **Deduplication**: Uses `uniqueId = "${name.trim()}|${endTime.trim()}"` stored in `AlertPreferences` (DataStore) to avoid duplicate notifications.
- **Background**: `AlertWorker` (WorkManager, 15m) + `AlertAlarmScheduler` (Exact Alarms, ~10m) for high-frequency updates. Both trigger `AlertNotifier` and `AlertsWidgetProvider`.

## Key files and boundaries

- **Networking**: [data/PokemonAlertsApi.kt](app/src/main/java/com/example/pokemonalertsv2/data/PokemonAlertsApi.kt). JSON ignores unknown keys.
- **Persistence**: [data/database/AppDatabase.kt](app/src/main/java/com/example/pokemonalertsv2/data/database/AppDatabase.kt) (Room) for alerts; [data/AlertPreferences.kt](app/src/main/java/com/example/pokemonalertsv2/data/AlertPreferences.kt) (DataStore) for settings/seen IDs.
- **Repository**: [data/PokemonAlertsRepository.kt](app/src/main/java/com/example/pokemonalertsv2/data/PokemonAlertsRepository.kt) is the SSOT for data. Use `PokemonAlertsRepository.create(context)` to instantiate.
- **UI**: Compose screens in `ui/`. ViewModels expose `StateFlow<UiState>`.
- **Time**: [util/TimeUtils.kt](app/src/main/java/com/example/pokemonalertsv2/util/TimeUtils.kt) handles ISO-8601 and epoch parsing for `endTime`.

## Build, run, test

- **Build**: `./gradlew.bat assembleDebug` (Windows) or `./gradlew assembleDebug` (Unix).
- **Test**: `./gradlew.bat test` for unit tests.
- **Dependencies**: Managed in [gradle/libs.versions.toml](gradle/libs.versions.toml).

## Conventions and patterns

- **State Management**: `collectAsStateWithLifecycle()` in Composables to observe `ViewModel` state.
- **Dedupe Pattern**: `repo.detectNewAlerts(alerts)` -> `AlertNotifier.notifyAlerts(...)` -> `repo.markAlertsAsSeen(new)`.
- **Worker Policy**: Use `AlertWorker.triggerImmediateSync(context)` for manual refreshes.
- **Formatting**: Use `TimeUtils` for all countdowns and time-ago strings in UI and widgets.
- **Images**: Use Coil for loading Pokémon icons; handle null `imageUrl` with placeholders.

## Integration points and gotchas

- **Cleartext**: API uses clarity HTTP; `android:networkSecurityConfig` is configured in `AndroidManifest.xml`.
- **Exact Alarms**: Requires conditional permission check via `AlertAlarmScheduler.canScheduleExact()`.
- **Widget**: Updates every 30s via alarm and on worker completion. Caches last-known location for distance calculations.
- **Serialization**: Always use `@SerialName` for API fields in [data/PokemonAlert.kt](app/src/main/java/com/example/pokemonalertsv2/data/PokemonAlert.kt).
