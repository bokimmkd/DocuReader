#!/usr/bin/env bash
set -uo pipefail
test_status=0
gradle :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.blw.documentreader.ImageDisplayTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true --stacktrace --no-daemon || test_status=$?
mkdir -p verification/image-display
adb pull /sdcard/Android/data/com.blw.wreader/files/image-display-qa verification/image-display || true
adb logcat -d > verification/image-display/logcat.txt
exit "$test_status"
