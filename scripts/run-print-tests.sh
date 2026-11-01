#!/usr/bin/env bash
set -uo pipefail
# android-emulator-runner invokes each script line separately; keep status in this shell.
test_status=0
variant="${READER_TEST_VARIANT:-Debug}"
if [ "$variant" = "Qa" ]; then
  gradle :app:connectedQaAndroidTest -PreaderTestBuildType=qa -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true --stacktrace --no-daemon || test_status=$?
else
  gradle :app:connectedDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true --stacktrace --no-daemon || test_status=$?
fi
mkdir -p verification/android-print
adb pull /sdcard/Android/data/com.blw.wreader/files/print-qa verification/android-print || true
adb pull /sdcard/Download/WReaderPrintQA verification/android-print || true
adb pull /sdcard/Download/WReaderSettingsQA verification/android-print || true
adb pull /sdcard/Android/data/com.blw.wreader/files/direct-pdf-qa verification/android-print || true
adb pull /sdcard/Android/data/com.blw.wreader/files/image-display-qa verification/android-print || true
adb pull /sdcard/Android/data/com.blw.wreader/files/dialog-qa verification/android-print || true
adb pull /sdcard/Android/data/com.blw.wreader/files/workflow-qa verification/android-print || true
adb logcat -d > verification/android-print/emulator-logcat.txt
if [ "$test_status" -eq 0 ] && [ -f verification/release-universal.apk ]; then
  python scripts/release-smoke.py || test_status=$?
fi
exit "$test_status"
