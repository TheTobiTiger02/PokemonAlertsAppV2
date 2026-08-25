#!/bin/bash
# Build, install and launch on the emulator.
# The unixdomain.tmpdir flags work around an AF_UNIX bind failure when TEMP is an 8.3 path.
set -e
cd "$(dirname "$0")"

TASK_TMP_DIR="${TASK_TMP_DIR:-/c/Temp/jtmp}"
mkdir -p "$TASK_TMP_DIR"
if command -v cygpath >/dev/null 2>&1; then
  TASK_TMP_WINDOWS="$(cygpath -w "$TASK_TMP_DIR")"
else
  TASK_TMP_WINDOWS="$TASK_TMP_DIR"
fi
# The Gradle daemon inherits TEMP, and AF_UNIX cannot bind under an 8.3 short path
# like C:\Users\GBTB45~1\..., which is what this shell sets.
export TMP="$TASK_TMP_WINDOWS"
export TEMP="$TASK_TMP_WINDOWS"
export JAVA_HOME="${JAVA_HOME:-C:/Program Files/Android/Android Studio/jbr}"
export GRADLE_OPTS="-Djdk.net.unixdomain.tmpdir=$TASK_TMP_WINDOWS"
JVMARGS="-Xmx2048m -Dfile.encoding=UTF-8 -Djdk.net.unixdomain.tmpdir=$TASK_TMP_WINDOWS"

ADB="${ADB:-}"
if [ -z "$ADB" ]; then
  for sdk_root in "${ANDROID_SDK_ROOT:-}" "${ANDROID_HOME:-}"; do
    if [ -n "$sdk_root" ] && [ -x "$sdk_root/platform-tools/adb.exe" ]; then
      ADB="$sdk_root/platform-tools/adb.exe"
      break
    fi
  done
fi
ADB="${ADB:-adb}"
DEV="${DEV:-emulator-5554}"
TASK="${1:-:app:assembleDebug}"
./gradlew "$TASK" --console=plain -q \
  -Dorg.gradle.jvmargs="$JVMARGS" \
  -Dkotlin.daemon.jvmargs="-Djdk.net.unixdomain.tmpdir=C:\Temp\jtmp" || exit 1
if [ "$TASK" = ":app:assembleDebug" ] && [ "${INSTALL:-1}" = "1" ]; then
  "$ADB" -s "$DEV" install -r -d app/build/outputs/apk/debug/app-debug.apk >/dev/null
  "$ADB" -s "$DEV" shell am force-stop com.example.pokemonalertsv2
  "$ADB" -s "$DEV" shell am start -n com.example.pokemonalertsv2/.MainActivity >/dev/null
  echo "installed + launched on $DEV"
fi
