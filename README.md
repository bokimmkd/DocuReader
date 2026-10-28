# W Reader — Android project, version 0.1.0

**Status: Android debug APK compiled successfully via GitHub Actions on 28 October 2026. Not production-ready or fully device-tested.**

**0.1.0 UI:** The blue/white five-screen concept is the styling reference (original design asset retained in the source ZIP), not an actual screenshot. The source now includes the branded toolbar, a blue launcher icon, compact Recent/Favorites lists with All/PDF/Word/Excel filters, three clickable PDF Tools choices, thumbnail-based page selection/import, a 3-column page organizer with select/rotate/delete/reorder, and the prominent **Save as New PDF** action. `Clear Recent History` preserves starred favorites. See `BUILD_APK.md` for an honest build path.

- Kotlin, Jetpack Compose, Android `PdfRenderer`, PDFBox-Android (Apache 2.0).
- Min API 26, target API 36 (required for new Google Play Android apps by 31 Aug 2026).
- Local file selection through Storage Access Framework (no broad storage permission).
- **Visible app name: W Reader.** The existing Android application ID `com.blw.documentreader` is intentionally retained to preserve test install/update compatibility. Do not change this without an agreed migration/release plan.

## Source layout

- `app/src/main/java/.../pdf/PdfEngine.kt` — app-private snapshots, rendering, selectable-text search, password fallback.
- `pdf/PdfPageTools.kt` — page import, extraction, reorder, rotation, remove, new PDF export.
- `pdf/PdfPrint.kt` — Android print adapter for an original PDF.
- `data/Documents.kt` — URI metadata, recent list, favorites, reading position, appearance.
- `ui/Screens.kt` — Compose Home, PDF reading, page editor, settings; styled from the original design reference.
- `monetization/AdConsent.kt` — UMP consent and **test** anchored adaptive banner.
- `monetization/RemoveAdsBilling.kt` — Play Billing non-consumable `remove_ads` integration (unconfigured product).
- `MainActivity.kt` — Android picker, share/open-with intents, navigation.
- `ReaderApplication.kt` — PDFBox initialization and crash-leftover cache cleanup.

## Implemented in source (requires device/build verification)

- Launch from launcher, Android Open Document, `ACTION_VIEW`/`ACTION_SEND` for PDF attachments.
- PDF scrolling, bitmap raster rendering, pinch/slider zoom, page number, page thumbnails, go-to page, selectable text search via PDFBox.
- Password dialog; PDFBox-derived temporary, local decrypted copy for older Android versions. Clear/delete cached PDFs on close/process restart.
- PDF file recent history, favorites, last page, clear history and theme selection.
- PDF tools: import all or selected thumbnail pages (or a typed range), add more source PDFs, extract a range, move/rotate/delete, preview and save **NEW** file using Android Create Document. Warn about loss of interactive PDF features and signatures before export.
- Share original with secure URI grants and invoke Android printing for PDF.
- UMP before requesting AdMob's documented **test** adaptive banner; separate footer outside reading canvas.
- Play Billing product query, localized price, purchase, restore, pending handling, acknowledgment, offline entitlement memory.

## Incomplete / blocked — NOT V1 ready

1. **DOCX/XLSX true layout renderers:** NOT IMPLEMENTED. Picker recognizes them and reports the limitation explicitly. Must select and validate a commercially redistributable engine. Plain text extraction is NOT an acceptable substitute; `.doc` and `.xls` are unsupported.
2. **Local build environment limitation (GitHub Actions can build):** Android debug APK compilation succeeded using GitHub Actions; instrumented and on-device QA remain incomplete. See setup below.
3. **Purchase security:** Play Billing client reports `PURCHASED` and acknowledges. A trusted Play Developer API verification backend must be integrated for fraud-resistant production entitlement. No Play product exists/configured in this project; the USD 1.99 target price is not hardcoded (actual store price displayed if configured).
4. **AdMob:** documented SAMPLE app ID and test banner only. Replace both before publication. Complete live UMP form/consent QA.
5. **Public privacy policy:** Settings has a local privacy overview, NOT a published privacy policy URL. Host a compliant policy and wire a URL to the Settings row before release; complete Play Data safety disclosures.
6. **PDFBox-Android maintenance/security audit:** Latest Maven Central Android port 2.0.27.0 was published 2023-01-02; this is not the latest upstream Apache PDFBox. Review CVEs and PDF input handling, and evaluate a maintained alternative before production. Some PDF page transformations may lose forms, tagged PDF, annotations, bookmarks, layers, embedded files, signatures. The explicit warning does NOT make feature preservation complete.
7. **Search:** searches selectable text and reports matching pages, but no highlighted text rectangles. OCR deliberately excluded.
8. **Printing:** original PDF is forwarded to Android Print Framework. App-side page-range selection is not implemented; verify with system print services.
9. **Very large PDFs / stress handling:** rendering uses a pixel cap and lazy views, but full-PDF search and PDFBox rewrite can consume substantial RAM. Need stress tests, cancellation and progress UX for large files.
10. **Partial real-user Samsung testing only:** PDF paging and extraction selection issues have been reported and remain unresolved.

## Building locally

Open the root folder in Android Studio with Android SDK API 36, JDK 17, and Gradle 8.13.

GitHub Actions CI (`.github/workflows/android-ci.yml`) builds the debug APK with API 36 / Gradle 8.13; the earlier debug build succeeded in the isolated test branch on 28 October 2026.

This artifact contains Gradle project files and a distribution URL in `gradle/wrapper/gradle-wrapper.properties`, but **does not contain the generated Gradle wrapper jar** because Gradle itself is unavailable in this environment. If your Android Studio needs the wrapper, run `gradle wrapper --gradle-version 8.13` once from a machine with Gradle installed.

Then run `:app:assembleDebug` and `:app:testDebugUnitTest`. Fix compiler/dependency issues revealed by a real Gradle sync before using any AAB. Use **only test ads** during QA.

## Production configuration checklist

- Publish and link AdMob app/ad unit IDs, configure UMP privacy message for jurisdictions, test on consent-required and consent-not-required devices.
- Create `remove_ads` one-time non-consumable in Play Console; set base USD 1.99; connect production verification and Play licensing testers; verify offline/cancel/pending/refund/restore.
- Implement real DOCX/XLSX renderers and review distribution rights (see `ENGINE_EVALUATION.md`).
- Add hosted Privacy Policy URL, support email, app icons, Play listing, screenshots, Data safety form.
- Test signed/encrypted/scanned/large PDFs, page operations preserving ordinary PDF resources, malformed PDFs and low memory.
- Run Gradle dependency and vulnerability/license audit; include all required notices in the release.

## Verification currently executed

- Kotlin CLI execution of pure `parsePageSelection` tests: **PASS** (ranges, repeats, out-of-bounds, empty).
- Android manifest/resources XML syntax parsing: **PASS**.
- Android debug build on GitHub: **PASS**. Instrumentation and comprehensive device QA: **NOT RUN**. Initial user testing: issues recorded in `KNOWN_ISSUES.md`.

## Privacy & file handling

No document contents are uploaded by this app. No analytics SDK is included. Ad requests receive no document filename/text. File bytes remain in app-private cache during reading/tools, and export is user-directed through SAF. Password-protected PDFs may produce private unencrypted temporary copies to support older Android versions; these are removed when closing and on a fresh process start. A process crash can leave them at rest until next app launch; production should assess encrypted temporary storage and advanced threat model.

## Third-party licensing

`com.tom-roush:pdfbox-android:2.0.27.0` is licensed Apache 2.0 with additional third-party NOTICE information. The full Apache text is in `THIRD_PARTY_APACHE_LICENSE.txt`. Before distribution, bundle the upstream PDFBox-Android NOTICE.txt and dependency license notices; the project zip itself is not the completed disclosure package. Google services have separate SDK agreements. See `ENGINE_EVALUATION.md`.
