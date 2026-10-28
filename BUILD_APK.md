# W Reader 0.1.0 — Debug APK build

This project is maintained in `bokimmkd/DocuReader`. Android UI name is **W Reader**.

## GitHub Actions

1. Open the repository's **Actions** tab and workflow **W Reader 0.1.0 - Debug APK**.
2. A push to `main` starts the build, or use **Run workflow**.
3. Confirm green **success**, then download **W-Reader-0.1.0-debug-apk** in the run's Artifacts section.
4. Unzip the artifact and install `app-debug.apk` on an Android device with permission to install downloaded APKs.

The workflow installs Android API 36 and build-tools 36.0.0 on GitHub runners, uses JDK 17, Gradle 8.13, and runs `:app:testDebugUnitTest` plus `:app:assembleDebug`. This is a debug build with test AdMob identifiers, not a Play release.

## Existing Android application ID

`com.blw.documentreader` is intentionally unchanged for compatibility with previously installed 0.1.0 test APKs. W Reader is the *display name*; decide a production ID **before** publishing any Play listing.

## Before production

See `README.md`, `KNOWN_ISSUES.md`, `ENGINE_EVALUATION.md`, and `QA_TEST_PLAN.md`. DOCX/XLSX document rendering remains unimplemented and monetization is not configured or production-verified.
