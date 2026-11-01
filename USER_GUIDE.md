# Quick Start Guide - New Features

## 🎯 Sorting Your Alerts

1. Open the **Alerts** tab (the **Live** section)
2. Tap the sort chip next to the search icon (it shows the current order, e.g. "Newest")
4. Choose from:
   - **Distance** - Nearest alerts first (requires location permission)
   - **Time** - Ending soonest first
   - **Name** - Alphabetical order

Your choice is saved automatically!

## 🔔 Customizing Notifications

1. Tap the **Settings** gear (⚙️) in the top right of Alerts, Events, Tools or the Map
2. Scroll to the **Notifications** section
3. Configure your preferences:

### Master Control

- Toggle **Enable Notifications** on/off for all alerts

### Per-Type Control

When notifications are enabled, you can control:

- **Raids** - Raid battle notifications
- **Spawns** - Wild Pokémon spawn notifications
- **Quests** - Field research notifications
- **Vibration** - Toggle vibration on/off

### Quiet Hours

- Toggle **Quiet hours** to enable or disable the standing nightly window
- Tap **From** and **To** to set the times; the default is 22:00 to 07:00
- A window that runs past midnight is handled correctly
- Separate from **Silence Notifications**, which is a one-off "quiet for the next N hours"
  timer. Either can be on without the other.

## 🚶 I’m going and raid arrival

- **I’m going** tracks one active destination with precise location updates.
- Gyms and PokéStops use an 80 m arrival radius; the configurable Settings radius applies
  to other alert locations.
- Arriving no longer ends the journey: a short heads-up announces it, and the live
  notification stays, now showing what you came for (CP, quest task and reward, grunt type,
  the stop's name). Walk away and it goes back to the distance; it ends when the alert
  expires, or with **Got it** (retires the alert) or **Stop**.
- At a raid, the first trustworthy in-range fix replaces the journey notification with a raid
  Live Update, so the normal and weather-boosted hundo CPs appear immediately.
- Expand the raid Live Update for a numbered counter list; the boss image remains its artwork
  because Android's promoted ongoing surface cannot render custom per-counter image rows. Tap
  it for the full counters screen and a compact **Copy for GO** query grouped by unique species,
  all six CPs, fast moves, and charged moves. Dismiss removes the raid card without restarting
  the journey.

## 🏠 Using the Widget

1. Long-press on your home screen
2. Select **Widgets**
3. Find **PokemonAlertsV2**
4. Drag the widget to your home screen
5. Resize as needed - it adapts to different sizes!

The widget shows:

- Up to 3 nearest alerts
- Distance from your location
- Time remaining
- Tap an alert to open details
- Tap refresh button to update

### Events widget

- **Now** lists running events with their end day, date and time, and how long is left
  (e.g. "Ends Sun 3 Nov 20:00 · 2 d 4 h left").
- **Upcoming** lists every future event, with its date and how far away it is
  (e.g. "Sat 9 Nov 14:00 – 17:00 · in 8 days"). The list scrolls.
- Long-press → reconfigure to hide event types. New kinds of events always show up until you hide them.

## 📏 Distance limits

The maximum-distance slider moves in 10 m steps below 100 m (10, 20 … 90 m), then 100 m steps
up to 1 km, then 1 km steps. There are quick chips for 50 m and 100 m.

## 🥾 Hunt history

**Tools → Hunt history** groups your hunts by month. Each card shows the date, time, catches
(sprites, shinies and hundos), distance, duration and a small sketch of the path you walked. Tap
a hunt for its map, pace, best IV and every catch with its IV, CP and time.

The path is recorded during the hunt from the GPS fixes the app already uses (about 1 point
every 8 m, a few KB per hunt, stored only on your phone). The map in a hunt's summary loads its
tiles when you open it; the list's sketches need no data at all.

## ⌚ Galaxy Watch (Wear OS)

A companion watch app (Galaxy Watch4 or newer, or any Wear OS 3+ watch) shows the running hunt or
catch route and adds heart rate, steps and calories to its summary.

1. Download `pokemon-alerts-watch.apk` from the same GitHub release as the phone app.
2. Install it on the watch: enable **Developer options → ADB debugging** and **Debug over Wi-Fi** on
   the watch, then run `adb connect <watch-ip>:<port>` and `adb install pokemon-alerts-watch.apk`
   (or use a sideloading app such as *Bugjaeger* or *Wear Installer*).
3. Start a hunt or catch route on the phone. The watch shows a notification; tap it (or open the app)
   and allow the sensor permissions.

On the watch: the elapsed time, catches, distance, current target/next stop and live heart rate;
**Got it** (hunt) or **Skip** (route), **Pause/Go**, and **End**. Everything travels over the
phone–watch Bluetooth link, not mobile data. The watch and phone apps must come from the same
release so they share a signing key.

## 📜 Viewing History

1. Open the **Alerts** tab and choose **History** at the top
2. Use filters to find specific alert types
3. **Pull down to refresh** the history
4. Tap any past alert to see details

## 🖼️ Picture-in-Picture (PiP) for Alert Images

1. Tap any alert to open the detail screen
2. On the image header, tap the **PiP** button near the back button
3. The app enters Picture-in-Picture with a compact, image-focused view

Tip: You can keep the alert image visible while using other apps.

## 💡 Pro Tips

### Getting the Most Out of Sorting

- Use **Distance** when actively hunting nearby
- Use **Time** to catch alerts before they expire
- Use **Name** to find specific Pokémon

### Notification Best Practices

- Disable notification types you don't care about
- Enable Quiet Hours if you don't want late-night alerts
- Keep vibration on for subtle notifications

### Widget Usage

- Place on your main home screen for quick access
- Larger widget sizes show more alerts
- Widget auto-updates every 30 seconds

### Performance

- All your preferences are saved locally
- No internet needed to change settings
- Preferences sync instantly across features

## 🎨 Visual Improvements

### Loading States

- Shimmer animations show while loading
- Smooth transitions between states
- No more blank screens!

### Pull to Refresh

- Works on Active Alerts and History
- Smooth animation with haptic feedback
- Shows loading indicator while refreshing

### Sort & Filter UI

- Clean dropdown menu
- Icons for each option
- Current selection highlighted

## ❓ Troubleshooting

### Notifications Not Appearing

1. Check Settings → Notifications → Enable Notifications is ON
2. Ensure the specific alert type (Raids/Spawns/Quests) is enabled
3. Check you're not in Quiet Hours time period
4. Verify app has notification permission in Android settings

### Distance Sorting Not Working

1. Grant location permission when prompted
2. Ensure location services are enabled on your device
3. Try refreshing the alerts

### Widget Not Updating

1. Tap the refresh button on the widget
2. Check that the app has permission to run in background
3. Ensure battery optimization is disabled for the app

## 🚀 Coming Soon

These features are on the roadmap:

- Custom notification sounds per alert type
- Advanced filtering options
- Export alert history
- Alert sharing with friends
- More widget size options

Already shipped since this list was written: a dark/light theme toggle (Settings →
Appearance), quiet hours with configurable start and end times, saved filter presets,
travel-time filtering, offline map tiles, and settings backup and restore.

---

Enjoy your enhanced Pokémon hunting experience! 🎮✨
