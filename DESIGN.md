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

- Primary destinations are **Alerts**, **Map**, **Events**, and **Tools**. Settings opens from the toolbar and returns to the originating destination.
- **Live** and **History** are persistent sections inside Alerts.
- Below 600dp use bottom navigation; from 600dp use a rail; from 840dp feeds may use two columns and map details use a side panel.
- Root destinations do not show back buttons. Preserve destination, section, filter, and scroll state.
- Keep root headers compact. Tools groups Hunting, Raids, and Collection & research; search includes settings and familiar feature names. Up to four shortcuts use stable tool IDs.
- Navigation rails and feed controls scroll when space is limited, including landscape at 200% text. Avoid reserving all available height for fixed controls.
- An active activity bar reopens existing Hunt, route, or tracked-alert sessions. It uses the existing session owners.

## Content patterns

- Keep filter summaries compact and move detailed controls into bottom sheets or focused subpages.
- Visual alert cards use a 144dp preview; Compact rows use 64dp artwork. Both preserve identity, expiry, real routed distance, essential category information, and 48dp actions. Existing users retain Visual.
- Map exposes Hunt, Routes, and location directly. Filters edit alert rules; Map display groups presentation controls. Marker-budget feedback flows below the filter summary.
- Hunt and Routes remain available while map tiles load or fail. Activity-bar titles reopen the corresponding Hunt controls, route guidance, or tracked-alert details.
- Raid counters starts with boss selection and opens recommendations without starting a notification session. Starting a Raid Live Update remains a separate explicit action.
- Permissions has its own Settings destination and search entry. Notification preferences show when Android blocks delivery.
- Filters edit a draft with fixed matching counts and Apply. Advanced rules and profiles remain accessible. Widget configuration stages rules until Save widget.
- Catch routes use Setup, Preview, and Guidance, with a fixed Generate route action. Predictions remain distinct from observations.
- Use localized, friendly date/time text rather than exposing server timestamp strings.
- Loading, empty, error, dismissed, image-fallback, and permission-denied states use the same semantic roles and clear recovery actions.

## System surfaces

Widgets, widget configuration, notifications, share cards, onboarding, launcher/splash assets, and map styling use the same neutral/blue roles. Widgets choose compact, medium, large-focus, or large-list layouts based on available size and alert count. Action IDs, PendingIntents, notification channels, image fallbacks, permissions, and deep-link contracts remain unchanged.
