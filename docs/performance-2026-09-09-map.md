# Map rendering, scrolling and redesign — 8–9 September 2026

## What was wrong

Three reports, all from real use in central Darmstadt with every alert type enabled.

**The map panel barely scrolled.** Reproduced on the device: the panel opened partially expanded,
so the sheet's own drag handling consumed the first vertical gesture to grow itself and the
content did not move. A second swipe was needed before anything scrolled. Inside the panel a
320dp-capped `LazyVerticalGrid` sat within a `Column` + `verticalScroll` within the draggable
sheet — three scroll containers deep — so a drag that started over the species grid could never
reach the panel at all.

**The map stuttered and occasionally crashed.** The OpenStreetMap provider drew alerts with
MapLibre's legacy annotation API. Every marker icon went through `IconFactory.fromBitmap`, which
mints a *separate* style image, and therefore a separate GPU texture, per call. The countdown was
baked into the marker bitmap, so with countdown labels on every visible marker's artwork changed
on every tick — and the render pass ran twice (an immediate pass and a full pass).

**The panel was disorganised**: twelve sections in one flat list, only one openable at a time, with
the clustering controls behind a bare text link that opened a second modal sheet on top of the first.

## How it was measured

Device: Samsung SM-G991B (`192.168.178.181:34165`), debug builds, live backend data.

The map draws into its own `SurfaceView`, so `dumpsys gfxinfo` sees the Compose chrome and never
the map — the first attempt reported 32 frames for a 25-second workload. Three instruments are
used instead, driven by `perf/goto_dense_map.sh` (navigate to the dense area) and
`perf/map_baseline.sh` (workload + capture):

- **SurfaceFlinger `--timestats`** for the map layer's real frame pacing.
- **`MapPerf` logcat timings** (`MapPerfLog`, debug builds only) around the cull/cluster pass, the
  icon build and the symbol update. This is the instrument that matters: the expensive work fires
  when the camera *settles*, which no frame counter attributes to anything.
- **`dumpsys meminfo`** — graphics and native heap, the most likely crash vector.

The workload is five rounds of three fast pans and a pinch pair, each followed by a settle.

Raw artifacts are under `.local/map-perf/` and are not committed.

## Results

Matched conditions: OpenStreetMap provider, central Darmstadt, zoom 14, countdown labels on,
~900 live alerts, 355 (before) and 362 (after) markers on screen with 229 clusters in both.

| | Before | After |
|---|---:|---:|
| Textures re-uploaded per countdown tick | 126, twice | **0** |
| Render passes per update | 2 | **1** |
| Main-thread publish, mean | 10.3 + 10.7 ms | **8.2 ms** |
| Main-thread publish, max | 32.2 + 34.3 ms | **18.4 ms** |
| Native heap | 345.7 MB | **231.6 MB** (−33%) |
| Total PSS | 615.6 MB | **492.3 MB** (−20%) |
| Graphics | 87.2 MB | 84.6 MB |

The countdown result is the headline and is a direct log comparison, not a derived figure:
`osm.annotations total=355 added=0 reiconed=126 removed=0` twice per tick, against
`osm.symbols total=345 uploaded=0` on every tick that adds no new markers.

### What did not improve, and what is not established

- **Frame pacing during a pan was already fine and is unchanged.** SurfaceFlinger reports the map
  layer at ~60 fps in the 16 ms bucket in both builds (1032/1107 frames before, 1051/1142 after),
  with a handful of 33 ms frames either way. The long gaps in the present-to-present histogram are
  idle periods — the map does not redraw when nothing changes — and must not be read as jank. **No
  frame-rate improvement is claimed.**
- **The pin build's mean got worse in the like-for-like run** (40.2 → 162.9 ms). The two samples are
  not the same population: before, this pass ran on *every countdown tick* and mostly hit warm
  caches; after, it runs only when the visible marker set changes, so every sample is genuine work.
  Fewer, larger units of work — but no improvement is claimed for this stage from these numbers.
  A per-alert pin cache was added afterwards to address it (below).
- **The crash was never reproduced.** The crash buffer was empty after every run, before and after.
  Memory pressure — the most likely cause, at 616 MB PSS with textures churning every second — is
  substantially lower, but this is a plausible fix, **not a verified one**.

### Not yet measured

Two changes landed after the comparison above and are **unvalidated at Darmstadt density**, because
the live alert count dropped from ~900 to ~101 at midnight when the day's quests expired:

- the per-alert pin cache, which should make a pan cost only the markers that are genuinely new
  (at the 101-alert density available afterwards it reported 0.0 ms rebuilds, which is the right
  shape but not a meaningful load);
- the `StringBuilder` cache key, replacing a 19-element `listOf(...).joinToString` run three times
  per marker per camera idle.

Raising the marker budgets (`overviewLimit` 120 / `closeLimit` 400) was deliberately **not** done.
The agreed criterion was to raise them only as far as measurements stay clean, and that measurement
needs dense live data. Re-run `perf/goto_dense_map.sh` then `perf/map_baseline.sh` when the feed is
busy again.

## What changed

### Rendering (`OpenStreetMapView.kt`)

Alert markers moved from legacy annotations to a `GeoJsonSource` + `SymbolLayer` stack — the pattern
the file already used for weather glyphs and the user pose. Pin artwork is registered once as a
style image and referenced by id, so every marker drawing the same pin shares one texture; the
update itself is a single `setGeoJson`. Hit testing moved to `queryRenderedFeatures` with a
fingertip-sized box, which also covers clusters and weather cells through one code path.

The countdown became its own shared sprite, keyed by what it says rather than by which marker says
it, so a screenful at minute precision needs a few dozen small images between them. Building pins
and publishing countdowns are now separate passes: a tick touches only which label image each
symbol points at.

`mapMarkerBaseIconCacheKey` now keeps the alert's expiry so that urgency reaches the key. It
previously nulled `endTime`, which collapsed urgent and calm pins onto one cache entry and let
whichever rendered first decide how both looked — a latent bug that the shared-image layer would
have made visible.

### The map panel (`MapFilterSheet.kt`, `MapClusteringSettings.kt`)

Rewritten as a single `LazyColumn` in a sheet that opens fully expanded, with no scroller nested
inside another. The species picker moved to a full-height sheet of its own with search, sort and
Set-all; the clustering controls were folded in as an ordinary section instead of a sheet on a
sheet. Sections are grouped under three plain-language headings — what's on the map, how near, how
it looks — and several can be open at once (a bitmask, so it survives rotation without a Saver).
Alert types are colour-coded tiles carrying live counts, which lets them double as the legend.

### Map chrome (`AlertsMapScreen.kt`, `MapHeader.kt`)

The settings button moved from a pinned top-end overlay into the bottom-right control column with
fit-all and locate. Reserving rail space for it only ever kept the *last* chip clear; scrolled back
to the start, the leading chips still ran underneath it.

## Verification

- Unit suite green: 901 tests, including 10 new in `MapSymbolIdentityTest` covering what makes two
  markers share one drawing — species, size and urgency change a pin's identity; countdown text and
  expiry time do not.
- On-device: marker taps, cluster taps and the alert sheet all work through the new hit-testing
  path; countdown sprites place correctly under pins at both marker sizes; the panel scrolls on the
  first drag from anywhere, including over the tile grid; the species grid scrolls with its header
  pinned.
- `MapPerfLog` is debug-only and probes for an Android runtime, so the marker pipeline stays
  testable on the JVM.
