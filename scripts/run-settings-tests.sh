#!/usr/bin/env bash
set -uo pipefail
result=0
gradle :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.blw.documentreader.ExtendedFormatsTest#compactSettingsAndEmbeddedHelpPrivacyWorkOffline --stacktrace --no-daemon || result=$?
mkdir -p verification/settings
adb pull /sdcard/Download/WReaderSettingsQA verification/settings || true
adb logcat -d > verification/settings/logcat.txt
exit "$result"
