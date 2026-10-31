#!/usr/bin/env bash
set -uo pipefail
test_status=0
gradle :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.blw.documentreader.UpdateFlowTest --stacktrace --no-daemon || test_status=$?
mkdir -p verification/android-update
adb pull /sdcard/Download/WReaderUpdateQA verification/android-update || true
adb logcat -d > verification/android-update/emulator-logcat.txt
exit "$test_status"
