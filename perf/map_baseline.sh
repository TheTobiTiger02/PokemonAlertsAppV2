#!/bin/bash
# Map pan/zoom workload for the alerts map, with frame, marker-pipeline and memory capture.
#
# Usage: ./perf/map_baseline.sh <label> [serial]
#
# Assumes the map tab is already open, on the provider under test, over a dense
# area (central Darmstadt), with the alert types under test enabled. It does not
# touch settings itself - the point is to measure the map, not the panel.
#
# Three instruments, because no single one sees the whole picture:
#   * SurfaceFlinger timestats  - the map draws into its own SurfaceView, so
#     `dumpsys gfxinfo` sees the Compose chrome and never the map.
#   * MapPerf logcat            - debug-build timings for cull/cluster/icon/annotation
#     work, which is what actually stalls when the camera settles.
#   * dumpsys meminfo           - graphics and native heap, the crash vector.
set -u
LABEL="${1:-baseline}"
SERIAL="${2:-192.168.178.181:34165}"
ADB="adb -s $SERIAL"
PKG=com.example.pokemonalertsv2
OUT="${OUT_DIR:-$(dirname "$0")/../.local/map-perf}"
mkdir -p "$OUT"

SIZE=$($ADB shell wm size | sed -n 's/.*: *\([0-9]*\)x\([0-9]*\).*/\1 \2/p' | tail -1)
W=$(echo "$SIZE" | cut -d' ' -f1)
H=$(echo "$SIZE" | cut -d' ' -f2)
CX=$((W / 2))
CY=$((H / 2))
LAYER="SurfaceView[$PKG/$PKG.MainActivity]@0(BLAST)"
echo "=== $LABEL: ${W}x${H} on $SERIAL ==="

$ADB shell dumpsys SurfaceFlinger --timestats -enable > /dev/null 2>&1
$ADB shell dumpsys SurfaceFlinger --timestats -clear > /dev/null 2>&1
$ADB shell dumpsys gfxinfo $PKG reset > /dev/null
$ADB logcat -c 2>/dev/null

pan() { $ADB shell input swipe "$1" "$2" "$3" "$4" "${5:-300}"; }

# Pans are followed by a settle, because the expensive work is the rebuild that
# fires on camera idle - not the pan itself, which stays on the render thread.
for round in 1 2 3 4 5; do
  pan $((CX + 300)) $CY            $((CX - 300)) $CY
  sleep 1.2
  pan $CX           $((CY + 400))  $CX           $((CY - 400))
  sleep 1.2
  pan $((CX - 300)) $((CY - 300))  $((CX + 300)) $((CY + 300))
  sleep 1.2
  # Pinch out and back in: a zoom change re-clusters, which a pan may not.
  $ADB shell input swipe $((CX - 100)) $((CY - 100)) $((CX - 260)) $((CY - 260)) 250
  sleep 1.5
  $ADB shell input swipe $((CX - 260)) $((CY - 260)) $((CX - 100)) $((CY - 100)) 250
  sleep 1.5
  echo "round $round done"
done

echo
echo "=== $LABEL: map surface frames ==="
$ADB shell dumpsys SurfaceFlinger --timestats -dump \
  | awk -v layer="$LAYER" 'index($0, layer) {found=1} found' \
  | sed -n '1,25p' \
  | grep -E "totalFrames|droppedFrames|averageFPS|present2present histogram" -A 1 \
  | tee "$OUT/$LABEL-frames.txt"

echo
echo "=== $LABEL: marker pipeline (MapPerf) ==="
$ADB logcat -d -s MapPerf -t 2000 | tee "$OUT/$LABEL-mapperf.txt" > /dev/null
awk '
  /prepare / { split($0, a, "prepare "); split(a[2], b, "ms"); prep[++np] = b[1] + 0 }
  /osm.full.build/ { split($0, a, "osm.full.build "); split(a[2], b, "ms"); build[++nb] = b[1] + 0 }
  /osm.immediate.build/ { split($0, a, "osm.immediate.build "); split(a[2], b, "ms"); imm[++ni] = b[1] + 0 }
  /osm.labels.build/ { split($0, a, "osm.labels.build "); split(a[2], b, "ms"); lab[++nl] = b[1] + 0 }
  /osm.publish/ { split($0, a, "osm.publish "); split(a[2], b, "ms"); pub[++npb] = b[1] + 0 }
  /osm.full.apply/ { split($0, a, "osm.full.apply "); split(a[2], b, "ms"); pub[++npb] = b[1] + 0 }
  /osm.immediate.apply/ { split($0, a, "osm.immediate.apply "); split(a[2], b, "ms"); pub[++npb] = b[1] + 0 }
  /uploaded=/ { split($0, a, "uploaded="); split(a[2], b, " "); up[++nu] = b[1] + 0 }
  function report(name, arr, n,   i, total, mx) {
    if (n == 0) { print sprintf("%-24s no samples", name); return }
    total = 0; mx = 0
    for (i = 1; i <= n; i++) { total += arr[i]; if (arr[i] > mx) mx = arr[i] }
    print sprintf("%-24s n=%-4d mean=%8.1f  max=%9.1f", name, n, total / n, mx)
  }
  END {
    print "stage                    samples      mean        max   (ms unless noted)"
    report("cull+cluster", prep, np)
    report("pin build (IO)", build, nb)
    report("pin build (immediate)", imm, ni)
    report("countdown labels", lab, nl)
    report("publish (main thread)", pub, npb)
    report("images uploaded/pass", up, nu)
  }
' "$OUT/$LABEL-mapperf.txt" | tee "$OUT/$LABEL-mapperf-summary.txt"
echo "--- last counts ---"
grep -E "prepare.counts|osm.symbols" "$OUT/$LABEL-mapperf.txt" | tail -4

echo
echo "=== $LABEL: memory ==="
$ADB shell dumpsys meminfo $PKG \
  | sed -n '/App Summary/,/TOTAL SWAP/p' \
  | tee "$OUT/$LABEL-meminfo.txt"

echo
echo "=== $LABEL: crashes / OOM ==="
$ADB logcat -b crash -d -t 200 > "$OUT/$LABEL-crash.txt"
$ADB logcat -d -t 6000 2>/dev/null \
  | grep -iE "OutOfMemory|FATAL EXCEPTION|GL_OUT_OF_MEMORY|libmaplibre|Abort message" \
  > "$OUT/$LABEL-errors.txt"
if [ -s "$OUT/$LABEL-crash.txt" ] || [ -s "$OUT/$LABEL-errors.txt" ]; then
  cat "$OUT/$LABEL-crash.txt" "$OUT/$LABEL-errors.txt" | tail -30
else
  echo "none"
fi

echo
echo "artifacts in $OUT ($LABEL-*)"
