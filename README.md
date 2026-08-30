# Pokemon Alerts V2

Pokemon Alerts V2 is an Android app written in Kotlin and Jetpack Compose that keeps you up-to-date with the latest Pokémon Go alerts from the community API. The base URL comes from `BuildConfig.ALERTS_API_BASE_URL` (default `https://api.alsbach-scanner.uk/`).

## Features

- 🔔 **Push notifications:** Alerts arrive over Firebase Cloud Messaging and are handled by `FcmAlertWorker`, so a new spawn reaches you in seconds rather than on a poll interval. Notifications are grouped per channel with a summary.
- 🗺️ **Offline-capable map:** Google Maps or OpenStreetMap, with OSM raster tiles cached on disk so the map still draws with no signal.
- ⏱️ **Raid arrival Live Update:** tap **I’m going** and the first trustworthy fix within 80 m of the gym hands the journey off to a lock-screen raid card showing both hundo CPs. Expand it for the clean counter list, or tap it for the full counters screen and compact **Copy for GO** team search.
- 🚶 **Travel-time filtering:** filter and warn by real walking routes rather than straight-line distance.
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
- Ingestion is push-first. `PokemonFirebaseMessagingService` hands each message to `FcmAlertWorker`, which parses it, reconciles it against the Room cache and notifies. `AlertWorker` still exists but only as the authoritative resync `FcmAlertWorker` requests when a payload is invalid or a weather change invalidates active alerts; there is no periodic poll.
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

- If notifications are delayed, check that battery optimisation is disabled for the app and that Google Play services can reach FCM. Also check Settings → Notifications for an active quiet-hours window or a running "silence for N hours" timer.
- Images in the feed come directly from the API payload. If an alert does not include an image URL, the app shows a placeholder tile.

## Maps setup

The app includes a map screen that shows active alerts with their images as markers.

To enable Google Maps:

1. Create a Google Maps API key for Android (Maps SDK for Android) in Google Cloud Console.
2. Provide it locally so it isn't committed:
	- Preferred: Set an environment variable `GOOGLE_MAPS_API_KEY` before building.
	- Or update `local.properties` with a line `google.maps.api.key=YOUR_REAL_API_KEY` and adjust the Gradle placeholder to read it, if you prefer.
3. Build and run the app. From the list screen, tap the map icon in the top right to open the map.

Notes:
- Markers try to load `imageUrl` (falling back to `thumbnailUrl`) as the icon. If loading fails, the default pin is used.
- Tap a marker to show info; tap the info window to open the alert details.

## Next steps

- Integrate a local database to show alert history when offline.
- Add filters for quest/raid/other alert types.
- Expose manual refresh via pull-to-refresh once `SwipeRefresh` or `PullRefresh` dependencies are added.
