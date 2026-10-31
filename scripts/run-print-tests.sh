#!/usr/bin/env bash
set -uo pipefail
# android-emulator-runner invokes each script line separately; keep status in this shell.
test_status=0
gradle :app:connectedDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true --stacktrace --no-daemon || test_status=$?
mkdir -p verification/android-print
adb pull /sdcard/Android/data/com.blw.wreader/files/print-qa verification/android-print || true
adb pull /sdcard/Download/WReaderPrintQA verification/android-print || true
adb pull /sdcard/Download/WReaderSettingsQA verification/android-print || true
adb logcat -d > verification/android-print/emulator-logcat.txt
exit "$test_status"
