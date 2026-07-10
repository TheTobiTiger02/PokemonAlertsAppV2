# Pokémon Alerts design system

Pokémon Alerts uses a static, One UI-inspired Material 3 design system built for quick scanning and one-handed use. Product behavior and data contracts stay independent from the visual layer.

## Foundation

- Follow the system light/dark setting; dynamic wallpaper color is disabled.
- Light roles: background `#F7F8FB`, surface `#FFFFFF`, container `#EEF1F6`, outline `#D8DEE8`, primary `#0057D9`.
- Dark roles: background `#090B0F`, surface `#12151B`, container `#191D25`, raised container `#202631`, outline `#323946`, primary `#7FA7FF`.
- Electric blue is the only brand accent. Red, amber, and green are reserved for destructive, urgent, and successful states.
- Use system sans-serif typography, tabular/monospace figures only for countdowns, opaque containers, 20dp screen margins, 24dp cards, and 48dp touch targets.
- Do not use animated ambient backgrounds, decorative grids, glass surfaces, or category rainbow palettes.

## Navigation and layout

- Primary destinations are **Alerts**, **Map**, and **Settings**.
- **Live** and **History** are persistent sections inside Alerts.
- Below 600dp use bottom navigation; from 600dp use a rail; from 840dp feeds may use two columns and map details use a side panel.
- Root destinations do not show back buttons. Preserve destination, section, filter, and scroll state.
- Phone headers use a large, bottom-aligned title area that collapses toward a compact toolbar as content scrolls.

## Content patterns

- Keep filter summaries compact and move detailed controls into bottom sheets or focused subpages.
- Alert cards use a 144dp preview, overlaid type/countdown badges, concise location and distance metadata, and 48dp actions.
- Use localized, friendly date/time text rather than exposing server timestamp strings.
- Loading, empty, error, dismissed, image-fallback, and permission-denied states use the same semantic roles and clear recovery actions.

## System surfaces

Widgets, widget configuration, notifications, share cards, onboarding, launcher/splash assets, and map styling use the same neutral/blue roles. Widgets choose compact, medium, large-focus, or large-list layouts based on available size and alert count. Action IDs, PendingIntents, notification channels, image fallbacks, permissions, and deep-link contracts remain unchanged.
