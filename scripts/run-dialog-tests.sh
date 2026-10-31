#!/usr/bin/env bash
set -uo pipefail
test_status=0
gradle :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.blw.documentreader.DocumentDialogTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true --stacktrace --no-daemon || test_status=$?
mkdir -p verification/document-dialog
adb pull /sdcard/Android/data/com.blw.wreader/files/dialog-qa verification/document-dialog || true
adb logcat -d > verification/document-dialog/logcat.txt
exit "$test_status"
