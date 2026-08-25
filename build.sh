#!/bin/bash
# Build, install and launch on the emulator.
# The unixdomain.tmpdir flags work around an AF_UNIX bind failure when TEMP is an 8.3 path.
set -e
cd "$(dirname "$0")"
mkdir -p /c/Temp/jtmp
# The Gradle daemon inherits TEMP, and AF_UNIX cannot bind under an 8.3 short path
# like C:\Users\GBTB45~1\..., which is what this shell sets.
export TMP='C:\Temp\jtmp'
export TEMP='C:\Temp\jtmp'
export JAVA_HOME="${JAVA_HOME:-C:/Program Files/Android/Android Studio/jbr}"
export GRADLE_OPTS="-Djdk.net.unixdomain.tmpdir=C:\Temp\jtmp"
JVMARGS="-Xmx2048m -Dfile.encoding=UTF-8 -Djdk.net.unixdomain.tmpdir=C:\Temp\jtmp"
ADB="/c/Users/GBT B450M-S2H/AppData/Local/Android/Sdk/platform-tools/adb.exe"
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
