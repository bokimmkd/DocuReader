#!/usr/bin/env bash
set -uo pipefail
test_status=0
gradle :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.blw.documentreader.DirectPdfExportTest,com.blw.documentreader.ImageDisplayTest,com.blw.documentreader.DocumentDialogTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true --stacktrace --no-daemon || test_status=$?
mkdir -p verification/file-export
adb pull /sdcard/Android/data/com.blw.wreader/files/direct-pdf-qa verification/file-export || true
adb pull /sdcard/Android/data/com.blw.wreader/files/image-display-qa verification/file-export || true
adb pull /sdcard/Android/data/com.blw.wreader/files/dialog-qa verification/file-export || true
adb logcat -d > verification/file-export/logcat.txt
exit "$test_status"
