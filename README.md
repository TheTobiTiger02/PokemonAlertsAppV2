# Pokemon Alerts V2

Pokemon Alerts V2 is an Android app written in Kotlin and Jetpack Compose that keeps you up-to-date with the latest Pokémon Go alerts from the community endpoint at `http://match-profiles.gl.at.ply.gg:1855/api/pokemon`.

## Features

- 🔔 **Background notifications:** A WorkManager job polls the endpoint and sends high-priority notifications whenever new alerts are published.
- 🗺️ **Rich alert detail:** Each alert shows the full description, a generated map preview, and a one-tap shortcut into Google Maps for navigation.
- 📋 **Composable UI:** A Material 3 list of current alerts with thumbnails, end times, and quick access to detailed dialogs.
- 💾 **Smart deduplication:** Previously seen alerts are cached locally with Jetpack DataStore so you are only notified about truly new items.
- ✅ **Unit tested core logic:** Repository tests cover the alert parsing and deduplication logic to guard against regressions.

## Getting started

1. **Clone the project** and open it in Android Studio Ladybird or newer.
2. **Sync Gradle** when prompted. All dependencies are declared in `gradle/libs.versions.toml`.
3. **Set your device or emulator** to API level 26+ (the app targets Android 14/15 and supports from Android 8.0).
4. **Run the app** using the `app` run configuration.

## How it works

- The `PokemonAlertsRepository` wraps Retrofit + Kotlin Serialization to fetch the list of alerts and keeps track of what has already been seen.
- `AlertWorker` is scheduled on app startup and re-runs every 15 minutes (WorkManager minimum). It compares the latest payload to the cached IDs and pushes notifications for anything new.
- Notifications open `AlertDetailActivity`, which renders the alert content with Compose and gives a button to jump straight into Google Maps.
- While browsing the main list (`PokemonAlertsRoute`), tapping an alert opens an in-app dialog with the same details.

## Permissions

- **Internet** is required to download active alerts and preview images.
- **Post notifications** is requested on Android 13+ so the app can send heads-up alerts. If you deny the permission, you can re-enable it later from system settings.

## Testing

Run unit tests from the terminal or Android Studio:

```powershell
.\gradlew.bat test
```

The suite currently focuses on repository behaviour. Add more tests around UI or workers as you expand the project.

## Troubleshooting

- If notifications are delayed, ensure battery optimizations are disabled for the app. WorkManager honours system constraints and may defer jobs under heavy restrictions.
- Images in the feed come directly from the API payload. If an alert does not include an image URL, the app shows a placeholder tile.

## Next steps

- Integrate a local database to show alert history when offline.
- Add filters for quest/raid/other alert types.
- Expose manual refresh via pull-to-refresh once `SwipeRefresh` or `PullRefresh` dependencies are added.
