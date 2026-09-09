#!/bin/bash
# Puts the app on the map tab, framed on the densest area, ready for map_baseline.sh.
#
# Usage: ./perf/goto_dense_map.sh [serial]
#
# Fit-all frames every alert, then double-taps zoom in on the dense middle. Double-tap rather
# than single: a single tap on a pin opens its detail screen, which is how earlier runs ended
# up measuring the wrong thing.
set -u
SERIAL="${1:-192.168.178.181:34165}"
ADB="adb -s $SERIAL"
PKG=com.example.pokemonalertsv2
ZOOM_STEPS="${ZOOM_STEPS:-3}"

$ADB shell am force-stop $PKG
$ADB shell am start -n $PKG/.MainActivity > /dev/null
sleep 9

SIZE=$($ADB shell wm size | sed -n 's/.*: *\([0-9]*\)x\([0-9]*\).*/\1 \2/p' | tail -1)
W=$(echo "$SIZE" | cut -d' ' -f1)
H=$(echo "$SIZE" | cut -d' ' -f2)

# Bottom nav: Alerts / History / Map / Settings. The tap is verified rather than assumed - a
# tap that lands while the alert list is still settling is swallowed, and every later tap in
# this script then lands on the list and opens an alert instead of driving the map.
on_map() {
  $ADB shell dumpsys SurfaceFlinger --list 2>/dev/null     | grep -q "SurfaceView\[$PKG/$PKG.MainActivity\]"
}

for attempt in 1 2 3 4 5; do
  $ADB shell input tap $((W * 5 / 8)) $((H * 189 / 200))
  sleep 7
  if on_map; then break; fi
  echo "map tab tap $attempt did not take, retrying"
  case "$($ADB shell dumpsys window | grep mCurrentFocus)" in
    *AlertDetailActivity*) $ADB shell input keyevent KEYCODE_BACK; sleep 3 ;;
  esac
done
on_map || { echo "could not reach the map tab"; exit 1; }
sleep 3

# Fit-all sits above the locate button in the bottom-right control stack.
$ADB shell input tap $((W * 887 / 1000)) $((H * 755 / 1000))
sleep 8

CX=$((W * 59 / 100))
CY=$((H * 45 / 100))

# Tapping a cluster is the app's own zoom-in. Tapping a *pin* opens its detail screen instead,
# so every tap is checked and backed out of, and the next attempt is nudged to a fresh point.
zoom_in() {
  local attempt
  for attempt in 0 1 2 3 4 5; do
    local x=$((CX + (attempt % 3) * 70 - 70))
    local y=$((CY + (attempt / 3) * 90 - 45))
    $ADB shell input tap $x $y
    sleep 4
    case "$($ADB shell dumpsys window | grep mCurrentFocus)" in
      *AlertDetailActivity*)
        $ADB shell input keyevent KEYCODE_BACK
        sleep 3
        ;;
      *)
        return 0
        ;;
    esac
  done
  return 1
}

for _ in $(seq "$ZOOM_STEPS"); do
  zoom_in || { echo "could not find a cluster to zoom into"; exit 1; }
done
sleep 4

# A cluster tap can also land on a pin and open the in-map alert sheet, which would then eat
# every pan the workload sends. Back out of it; if that leaves the map entirely, come back.
$ADB shell input keyevent KEYCODE_BACK
sleep 3
if ! on_map; then
  $ADB shell am start -n $PKG/.MainActivity > /dev/null
  sleep 6
  $ADB shell input tap $((W * 5 / 8)) $((H * 189 / 200))
  sleep 8
fi

FOCUS=$($ADB shell dumpsys window | grep mCurrentFocus)
case "$FOCUS" in
  *MainActivity*) echo "ready: $FOCUS" ;;
  *) echo "NOT ON THE MAP: $FOCUS"; exit 1 ;;
esac
