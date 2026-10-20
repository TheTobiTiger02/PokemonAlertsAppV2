#!/bin/bash
# App-wide performance capture on a real phone, for before/after comparisons.
#
# Usage: ./perf/app_perf.sh <label> [serial]
#
# Measure the staging build (R8 + baseline profile); debug is 3-4x slower and misleads.
# Starts on the Alerts tab with the bottom bar Alerts / Map / Events / Tools.
# Run while the live feed is busy (it empties around midnight).
#
# Phases:
#   feed-idle   15 s untouched on the Live feed: what the countdown ticks cost
#   feed-scroll flings through the feed: gfxinfo frame percentiles
#   tabs        tab switches: gfxinfo frame percentiles
#   map-idle    15 s untouched on the map: compass, ticks, location
#   map-pan     pans and zooms: main-thread work plus the map SurfaceView frame pacing
set -u
LABEL="${1:-baseline}"
SERIAL="${2:-192.168.178.181:41843}"
ADB="adb -s $SERIAL"
PKG=com.example.pokemonalertsv2
OUT="${OUT_DIR:-$(dirname "$0")/../.local/app-perf}/$LABEL"
PY="${PYTHON:-python}"
mkdir -p "$OUT"

SIZE=$($ADB shell wm size | sed -n 's/.*: *\([0-9]*\)x\([0-9]*\).*/\1 \2/p' | tail -1)
W=$(echo "$SIZE" | cut -d' ' -f1)
H=$(echo "$SIZE" | cut -d' ' -f2)
CX=$((W / 2)); CY=$((H / 2))
NAV_Y=$((H * 93 / 100))
focus_ok() { $ADB shell dumpsys window | grep mCurrentFocus | grep -q "$PKG/$PKG.MainActivity"; }
# A tap that lands while the bottom bar is hidden by a scroll hits a card instead (its map
# button opens Google Maps); come back and retry rather than measuring the wrong app.
tab() {
  for attempt in 1 2 3; do
    $ADB shell input tap $((W * (2 * $1 + 1) / 8)) $NAV_Y
    sleep 0.4
    focus_ok && return 0
    $ADB shell input keyevent KEYCODE_BACK; sleep 1.5
  done
}
pid() { $ADB shell pidof $PKG | tr -d '\r'; }

trace_start() { $ADB shell atrace --async_start -b 32768 -a $PKG view gfx input > /dev/null; }
trace_stop() {
  $ADB exec-out "atrace --async_stop -z" > "$OUT/$1.raw"
  $PY "$(dirname "$0")/atrace_summary.py" "$OUT/$1.raw" "$(pid)" "$2" | tee "$OUT/$1-summary.txt"
}
gfx_reset() { $ADB shell dumpsys gfxinfo $PKG reset > /dev/null; }
gfx_dump() {
  $ADB shell dumpsys gfxinfo $PKG \
    | sed -n '/Total frames rendered/,/Number Frame deadline missed/p' \
    | grep -vE "^\s*$" | tee "$OUT/$1-gfx.txt"
}

$ADB shell am start -n $PKG/.MainActivity > /dev/null
sleep 4
focus_ok || { echo "app not in focus; aborting"; $ADB shell dumpsys window | grep mCurrentFocus; exit 1; }
tab 0; sleep 3

echo "=== $LABEL: feed-idle ==="
trace_start; sleep 15; trace_stop feed-idle 15

echo; echo "=== $LABEL: feed-scroll ==="
gfx_reset
trace_start
for i in 1 2 3 4 5 6; do
  $ADB shell input swipe $CX $((H * 3 / 4)) $CX $((H / 4)) 120; sleep 0.8
done
for i in 1 2 3 4 5 6; do
  $ADB shell input swipe $CX $((H / 4)) $CX $((H * 3 / 4)) 120; sleep 0.8
done
trace_stop feed-scroll 0
gfx_dump feed-scroll
# Back to the top so the bottom bar is showing again.
$ADB shell input swipe $CX $((H / 4)) $CX $((H * 3 / 4)) 80; sleep 1

echo; echo "=== $LABEL: tabs ==="
gfx_reset
for round in 1 2 3; do
  tab 1; sleep 2.5; tab 2; sleep 2; tab 3; sleep 2; tab 0; sleep 2
done
gfx_dump tabs
focus_ok || { echo "lost focus during tabs"; exit 1; }

echo; echo "=== $LABEL: map-idle ==="
tab 1; sleep 6
$ADB exec-out screencap -p > "$OUT/map.png"
trace_start; sleep 15; trace_stop map-idle 15

echo; echo "=== $LABEL: map-pan ==="
LAYER="SurfaceView[$PKG/$PKG.MainActivity]@0(BLAST)"
$ADB shell dumpsys SurfaceFlinger --timestats -enable > /dev/null 2>&1
$ADB shell dumpsys SurfaceFlinger --timestats -clear > /dev/null 2>&1
trace_start
for round in 1 2 3; do
  $ADB shell input swipe $((CX + 300)) $CY $((CX - 300)) $CY 300; sleep 1.2
  $ADB shell input swipe $CX $((CY + 400)) $CX $((CY - 400)) 300; sleep 1.2
  $ADB shell input swipe $((CX - 300)) $((CY - 300)) $((CX + 300)) $((CY + 300)) 300; sleep 1.2
done
trace_stop map-pan 0
$ADB shell dumpsys SurfaceFlinger --timestats -dump \
  | awk -v layer="$LAYER" 'index($0, layer) {found=1} found' \
  | sed -n '1,25p' \
  | grep -E "totalFrames|droppedFrames|present2present histogram" -A 1 | tee "$OUT/map-pan-frames.txt"

tab 0
echo; echo "artifacts in $OUT"
