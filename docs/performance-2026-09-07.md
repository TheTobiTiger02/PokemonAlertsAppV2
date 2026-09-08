# Map controls and performance verification — 7–8 September 2026

## Delivered behavior

The map and Settings → Appearance & behavior share a persistent Clustering panel. Less clustering is the initial default. Current behavior, Less clustering, Overlap only, Same-location stacks, Performance, and a retained Custom configuration are available. Advanced controls cover grouping method, distance, zoom cutoff, and overview/close marker limits. Settings changes retain the camera and use the existing backup format.

Coincident alerts use the highest-priority alert's normal marker in both providers. Quest-only alerts have the lowest priority. “Also at this stop” provides each eligible companion's summary and opens its full details. Geographic clusters keep their count and progressive zoom. Marker-limit overflow remains bounded and is indicated in the UI.

The performance changes coalesce simultaneous requests for identical marker artwork, replace repeated coordinate formatting with tested numeric coordinate keys, cache category/priority work during grouping, and move database-to-domain flow conversion off the main thread. Existing arrival-tracking work was retained.

## Reproduction and evidence

Device: Pixel_10_Pro (`emulator-5554`). Workload runs use debug APKs built from the same feature state before and after optimization; release Macrobenchmarks compare the original source APK with the final optimized/profiled APK. Debug and release numbers are not compared with each other.

The opt-in `PerformanceFixtureSetupTest` supplies 0/200-alert fixtures and a 1,000-entry synthetic GoDex collection for the populated fixture. `PerformanceWorkloadTest` separately exercises 0/200/1,000/3,000/10,000 alerts at zoom 10/14/20 with seven measured samples after two warmups. Image tests make 128 simultaneous requests for eight unique 512-pixel PNGs, rendering at 96 pixels, over seven repetitions. Their local server has a fixed 30 ms response delay. This is a controlled image test, not a live-network speed claim. Network connectivity must be enabled because Coil treats airplane mode as offline even for localhost.

Startup benchmarks use ten iterations; screen, scrolling, gesture, and system-PiP benchmarks use five. Screen-readiness measurements include accessibility-query/input overhead and are not exact first-pixel render times. The offline map-opening readiness check covers the map controls, not completion of external map tiles. Native marker taps and dense expansion are verified separately on connected providers. Perfetto frame metrics and peak memory supplement those measurements. Map preparation and image timings are isolated workloads, not whole-screen latency.

The separate gesture/PiP runs enable network connectivity so the map provider can load. Backend refresh and live tiles make those runs observational rather than a controlled fixture-speed comparison. PiP entry is measured until Android reports a pinned activity, not until every map tile finishes. Relaunching a pinned activity does not necessarily expand it; the next iteration closes it explicitly in setup.

Local artifacts are under `.local/performance-20260907/` (excluded from Git). They include APKs, the original source archive, the pre-existing-work patch, raw CSVs, logs, traces, screenshots, and private emulator data backups. Do not publish the data archives. `baseline-artifacts.json` records the original artifact hashes. The original emulator data is restored after fixture testing; installation uses the existing debug certificate without uninstalling the app.

## Verification notes

- Unit tests cover preset budgets/membership, cutoff boundaries, priority, coordinate-equivalence compatibility, protected companions, malformed settings, and representative refresh.
- Connected tests cover persistence after reopening the store, settings backup/reset, real provider marker taps, Quest full-details navigation, dense progressive zoom, tracking, PiP, filters, and widgets.
- The first full connected run encountered a live GoDex audit against the synthetic catalog and an existing test selector for the older flat Quest list. The selector was updated to expand the current reward group and select its task. The live audit must run against original data, not the synthetic benchmark catalog.
- Screenshots are taken after transition animations settle. Earlier loading/mid-transition screenshots and failed benchmark attempts are retained as diagnostic artifacts and excluded from successful evidence.

## Results

The isolated workloads below use the median of three pre-optimization run medians and two final-build run medians, on the same emulator boot. Each run contains seven samples per workload. Two intermediate optimized runs were used to investigate the memory-cache path and are not included in the final column. The final pair was run in after/before/after order.

| Workload | Before, ms | Final, ms | Change |
|---|---:|---:|---:|
| 1,000 alerts, zoom 14 | 34.90 | 27.50 | −21.2% |
| 3,000 alerts, zoom 14 | 91.18 | 42.39 | −53.5% |
| 10,000 alerts, zoom 10 | 136.93 | 97.51 | −28.8% |
| 10,000 alerts, zoom 14 | 272.27 | 145.27 | −46.6% |
| 10,000 alerts, zoom 20 | 348.22 | 199.38 | −42.7% |
| 128 artwork requests, cold disk | 956.37 | 96.84 | −89.9% |
| 128 artwork requests, warm disk | 59.99 | 35.95 | −40.1% |
| 128 artwork requests, warm memory | 6.41 | 7.09 | +10.7% |

Cold-image HTTP requests fell from 842–862 to exactly 56 over seven repetitions (eight unique images per repetition). The owner-cancellation/visible-waiter regression test passed. Warm-disk medians varied between 23.76 and 48.14 ms in the final runs, so its precise percentage is less stable than the cold-load result. Warm-memory scheduling remains slightly slower in this test, by 0.68 ms across 128 concurrent calls: the 5% regression target is **not met** for that micro-workload. No whole-app improvement is inferred from these isolated results.

The regenerated baseline profile contains 34,205 rules, including 5,737 application rules and 217 map-marker/clustering rules. Profile collection passed on Pixel_10_Pro, and the final release APK contains the compiled profile.

### Release results and limits

The initial pair ran before/after on one emulator boot; the repeat ran after/before on that same boot. Both builds deteriorated substantially over time. These measurements **do not establish a whole-app speed improvement or compliance with the 5% regression target**. The initial pair contains substantial regressions. The reverse-order repeat does not reproduce a consistent build-specific slowdown, but environmental drift is too large to establish equivalence. No additional performance percentage is claimed from that repeat.

| Startup, median ms | Before initial | After initial | After repeat | Before repeat |
|---|---:|---:|---:|---:|
| Cold, packaged profile | 826.0 | incomplete | 1,741.2 | 6,153.0 |
| Cold, no compilation | 1,033.3 | 1,600.9 | 2,165.0 | 4,555.9 |
| Warm, packaged profile | 380.3 | 1,109.5 | invalid repeat | 4,500.7 |

Startup cases request ten iterations. The initial optimized profiled-cold case stopped on an Alerts-readiness assertion; its partial results are excluded. The optimized warm repeat produced only nine metric samples and a 61.9-second outlier, so it is excluded from valid comparisons. Other startup columns have ten samples.

| Frame CPU P50 / P95, ms | Before initial | After initial | After repeat | Before repeat |
|---|---:|---:|---:|---:|
| Screen journeys | 22.7 / 37.0 | 50.9 / 67.7 | incomplete | 429.0 / 809.8 |
| Profiled navigation/scrolling | 50.1 / 73.1 | 119.9 / 245.6 | 432.9 / 784.1 | 462.1 / 737.0 |
| Uncompiled navigation/scrolling | 47.4 / 74.4 | 119.2 / 205.2 | 428.1 / 701.8 | 472.9 / 840.9 |

Each complete interaction scenario has five iterations. The optimized screen repeat stopped when its required Pikachu row was unavailable; partial CSV rows are excluded. The initial complete screen pair includes 22 first/repeat measurements. For example, first feed readiness was 1,071.3 → 826.6 ms, first Settings 462.3 → 957.6 ms, first map controls 987.5 → 1,340.5 ms, and repeat detail 450.1 → 617.1 ms. This mixed result cannot support a blanket improvement claim. Full screen, frame-overrun and memory tables are in `.local/performance-20260907/release-measurements.md`, with raw samples in `final-measurements.json` and each run's `traces/*benchmarkData.json`.

Initial screen-journey memory, median of per-iteration maxima: heap 48,754 → 57,274 KB (+17.5%); anonymous RSS 182,640 → 181,876 KB (−0.4%); file RSS 135,400 → 135,688 KB (+0.2%). Heap is an observed regression in this pair, with no valid optimized screen repeat to establish reproducibility.

Both live-map gesture/PiP cases completed all five iterations. They validate execution and provide traces, but live refresh/tile loading prevents controlled before/after latency claims.

### Trace findings

Representative iteration-2 screen traces show main-thread scheduled CPU decreasing from 5,880.6 to 4,883.9 ms while inclusive `postAndWait` drawing time increases from 5,286.9 to 12,282.3 ms. Render-thread scheduled CPU changes from 7,835.7 to 8,353.6 ms. Accessibility-node queries also occupy substantial main-thread time (2,874.2 → 2,266.0 ms inclusive). These nested slice durations must not be summed as independent costs. They support drawing/wait variability as a major factor; they do not prove its cause. Startup traces also show roughly 312–320 ms of Google Maps monitor contention, an unresolved bottleneck. Exact SQL results and trace filenames are in `trace-analysis.json`.

### Validation status

The final unit suite passed 856 tests. Debug lint and release assembly passed. The full connected run executed 116 tests with two failures: the stale Quest selector passed after correction; the GoDex history audit was incompatible with the synthetic catalog and correctly assumption-skipped on restored original data because there is no synced GoDex catalog. Additional targeted settings, shared-stop, dense-map, cancellation and image-workload checks passed. The generated baseline profile passed collection. A single completely green rerun of the entire connected suite is not claimed.

The repeat matrix ended with the optimized debug build installed, original app data restored, and networking enabled. Benchmark fixture injection remains confined to opt-in instrumentation code. No release was published.

Final restoration verification returned SQLite `integrity_check=ok`, with the original 1,190 alerts, zero history rows and zero GoDex entries before normal live synchronization. The final UI check reopened Pixel_10_Pro after the benchmark emulator had stopped; timing results were not mixed across boots. Overlap only survived force-stop/restart, then Reset restored Less clustering through the map's shared panel. Before/after screenshots show the same camera position. Live tiles and artwork were visible after synchronization. Airplane mode is 0 and Wi-Fi is 1. The final boot's crash buffer is empty and its main/system logs contain no app ANR or fatal-exception entries.

Useful screenshots under `.local/performance-20260907/screenshots/`: `clustering-final.png`, `clustering-persisted.png`, `map-before-preset.png`, `map-after-preset.png`, and the `dense-map-qa/` provider series. Shared-stop evidence also includes `google-rocket-details.png`, `google-marker.png`, `osm-marker.png` at the artifact root, plus `google-kecleon-marker.png` and `osm-kecleon-details.png` in the screenshots directory. All evidence stays local; the original private data archive is retained separately from shareable screenshots.
