# Pokémon Alerts design system

Pokémon Alerts uses a static, neutral Material 3 design system. It is designed for fast scanning of time-sensitive alerts, not for decorative category colour.

## Visual foundation

- Follow the system light/dark setting. Dynamic wallpaper colour is intentionally disabled so the product keeps a consistent identity.
- Primary accent: electric blue — `#0058BE` in light mode and `#ADC6FF` in dark mode.
- Surfaces, outlines, and secondary roles are neutral blue-grey. Blue communicates selection, primary actions, focus, and the most important metric.
- Red, amber, and green are reserved for semantic feedback such as destructive actions, warnings, and success; they never identify an alert type.
- Use Android system typography, Material 3 type scales, and standard 4/8/12/16/28dp corner radii. There is no bundled custom font.

## Navigation and layout

- The three primary destinations are **Alerts**, **Map**, and **Settings**.
- **Live** and **History** are persistent secondary tabs inside Alerts so active discovery remains the default entry point.
- Below `600dp`, use a Material navigation bar. At `600dp` and above, use a navigation rail. At `840dp` and above, feeds use two columns and map details move into a side panel.
- Preserve destination, section, filter, and scroll state when moving between destinations.

## Components and feedback

- Use Material top app bars, cards, chips, bottom sheets, dialogs, snackbars, and 48dp minimum touch targets.
- Alert type is expressed with a short code, label, and icon rather than a type-specific palette.
- Loading, empty, error, dismissed, image-fallback, and permission-denied states must use the same semantic tokens and clear recovery actions.
- Keep all actions visible and accessible: refresh, filter, search, sort, snooze, dismiss/undo, directions, share, and Picture-in-Picture.

## Non-Compose surfaces

RemoteViews widgets, notifications, share cards, map styling, launcher/splash assets, and widget configuration must use matching neutral resources and the electric-blue accent. Their action IDs, PendingIntents, notification channels, image fallbacks, and deep-link contracts are unchanged.
