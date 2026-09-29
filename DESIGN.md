# Pokémon Alerts design system

Pokémon Alerts uses a static, One UI-inspired Material 3 design system built for quick scanning and one-handed use. Product behavior and data contracts stay independent from the visual layer.

## Foundation

- Follow the system light/dark setting; dynamic wallpaper color is disabled.
- Light roles: background `#F7F8FB`, surface `#FFFFFF`, container `#EEF1F6`, outline `#D8DEE8`, primary `#0057D9`.
- Dark roles: background `#090B0F`, surface `#12151B`, container `#191D25`, raised container `#202631`, outline `#323946`, primary `#7FA7FF`.
- Electric blue is the only brand accent. Red, amber, and green are reserved for destructive, urgent, and successful states.
- Use system sans-serif typography, tabular/monospace figures only for countdowns, opaque containers, 20dp screen margins, 24dp cards, and 48dp touch targets.
- Do not use ambient background animation, decorative grids, glass surfaces, or category rainbow palettes. Motion is allowed, but it must explain a change rather than decorate one (see Motion).

## Motion

Motion is defined in `ui/motion/AppMotion.kt` and split by what causes it.

- **Duration curves (tween)** drive navigation: screen transitions, tab switches, filter
  changes. These fire constantly, so a predictable, clipped duration beats a bounce.
  `AppMotion.Quick` (140ms), `Standard` (240ms), `Emphasized` (320ms).
- **Springs** drive direct manipulation: a card expanding, a press settling, a selection pill
  moving, a digit ticking over. `AppMotion.springQuick()`, `springBouncy()`, and
  `springSize()` — the last carries an explicit `IntSize.VisibilityThreshold` so size
  animations stop once the remaining distance is invisible.
- **Countdowns roll per digit.** `RollingNumberText` animates only the characters that
  changed, so `12m 05s` -> `12m 04s` moves one digit. It is used on the alert card badge and
  the detail countdown row. It is deliberately *not* used on map markers (drawn into marker
  bitmaps, many on screen at once) or in picture-in-picture.
- **Segmented controls move one pill** between options rather than recolouring each segment,
  via `SpringSegmentedRow`. Segments are equal width, so position is an index.
- **Reduced motion is honoured.** `rememberReducedMotion()` reads
  `Settings.Global.ANIMATOR_DURATION_SCALE`; rolling digits fall back to static text when
  animations are off. Anything that repeats on a timer must check this.

## Navigation and layout

- Primary destinations are **Alerts**, **Map**, **Events**, and **Tools**. Events has quick views
  (All, Now, Upcoming, Starred) and a filter button for which event types show; all show by default.
- **Settings** is not a tab: the gear in the header of Alerts, Events, Tools and the Map (also a row
  in the map's tools sheet) opens it full screen, and its back arrow returns to where the gear was
  pressed. On the map the filter button is a funnel so the gear is unambiguous.
  The old Settings screen id (4) is kept for notification, widget and deep-link intents.
- **Live** and **History** are sections of Alerts, switched by one segmented control in the tab
  header. **Spawn insights** opens from the chart button on the History screen and has its own back
  arrow. The old History screen id (1) is kept for widget and notification intents.
- **Tools** groups what the app does beyond the feed (hunt, catch routes, raid counters, My Pokémon,
  GoDex checklist, spawn insights). Up to four tools can be starred into a quick-access row.
  Settings pages are not repeated there.
- Tabs stay composed once opened (`ui/components/KeepAlive.kt`): a hidden tab is unplaced and its
  lifecycle is capped at CREATED, so switching back is instant and nothing hidden keeps working.
- Filter Studio opens over the tab that asked for it; closing it returns there.
- Below 600dp use bottom navigation; from 600dp use a rail; from 840dp feeds may use two columns and map details use a side panel.
- Root destinations do not show back buttons. Preserve destination, section, filter, and scroll state.
- Phone headers use a large, bottom-aligned title area that collapses toward a compact toolbar as content scrolls.

## Content patterns

- Keep filter summaries compact and move detailed controls into bottom sheets or focused subpages.
- Alert rows default to **compact** (`CompactAlertCard`): sprite, title, "type · place", one line of
  numbers, countdown and a navigate button. Long press holds the other actions. The large card
  (144dp map preview, 48dp actions) remains as a choice in Settings › Appearance.
- Titles come from `formatAlertTitle` everywhere (cards, notifications, widgets, map): quests are
  named by their reward, and the subtitle carries the category and place.
- Alert type colours appear only as a small dot or marker; selection and emphasis use the blue primary.
- Alert detail keeps two bottom actions (I'm going, Navigate); window actions sit on the hero.
- Permissions are asked only in onboarding; afterwards a missing one shows as a card in Settings.
- Use localized, friendly date/time text rather than exposing server timestamp strings.
- Loading, empty, error, dismissed, image-fallback, and permission-denied states use the same semantic roles and clear recovery actions.

## System surfaces

Widgets, widget configuration, notifications, share cards, onboarding, launcher/splash assets, and map styling use the same neutral/blue roles. Widgets choose compact, medium, large-focus, or large-list layouts based on available size and alert count. Action IDs, PendingIntents, notification channels, image fallbacks, permissions, and deep-link contracts remain unchanged.
