#!/bin/bash
# Measures frame stats for scripted navigation patterns with 1300+ live alerts.
# Usage: ./perf/measure_perf.sh <serial> <label>
set -e
SERIAL="${1:-adb-R3CR300ELJV-EFL2Te._adb-tls-connect._tcp}"
LABEL="${2:-baseline}"
ADB="adb -s $SERIAL"

TAP_ALERTS="132 2262"
TAP_HISTORY="390 2262"
TAP_MAP="678 2262"
TAP_SETTINGS="952 2262"

echo "=== $LABEL: resetting gfxinfo ==="
$ADB shell dumpsys gfxinfo com.example.pokemonalertsv2 reset > /dev/null

# Round 1-5: full tab loop. Map gets extra settle time (tile load is network-bound).
for i in 1 2 3 4 5; do
  $ADB shell input tap $TAP_HISTORY; sleep 2.2
  $ADB shell input tap $TAP_MAP; sleep 3.2
  $ADB shell input tap $TAP_SETTINGS; sleep 2.0
  $ADB shell input tap $TAP_ALERTS; sleep 2.0
  echo "round $i done"
done

echo "=== $LABEL: tab-switch stats ==="
$ADB shell dumpsys gfxinfo com.example.pokemonalertsv2 | sed -n '/Total frames rendered/,/95th percentile/p'
